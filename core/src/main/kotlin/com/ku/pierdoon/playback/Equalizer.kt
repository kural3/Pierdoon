package com.ku.pierdoon.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Centre frequencies of the bands, an octave apart, in Hz. */
val EQ_BANDS: List<Int> = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

/** How far a band moves either way, in dB. */
const val EQ_MAX_DB = 12f

val EQ_FLAT: List<Float> = List(EQ_BANDS.size) { 0f }

/** Ready-made curves. Picking one copies its gains; moving a band after that makes it [Custom]. */
enum class EqPreset(gains: List<Int>?) {
    Flat(List(EQ_BANDS.size) { 0 }),
    Bass(listOf(6, 5, 4, 2, 0, 0, 0, 0, 0, 0)),
    BassBoost(listOf(9, 8, 6, 3, 1, 0, 0, 0, 0, 0)),
    Vocal(listOf(-2, -2, -1, 0, 2, 4, 4, 2, 0, -1)),
    /** Body of the voice (low mids) over its edge; sibilance a little down. */
    DeepVocal(listOf(-2, -1, 1, 3, 3, 2, 1, 0, -1, -2)),
    Rock(listOf(4, 3, 2, 0, -1, -1, 1, 2, 3, 4)),
    Electronic(listOf(5, 4, 1, 0, -2, 1, 0, 2, 4, 5)),
    /** Raised nearly evenly, most where the ear is keenest (2–4 kHz): louder, the limiter keeping it clean. */
    Loud(listOf(3, 3, 2, 1, 1, 2, 3, 3, 2, 2)),
    Custom(null);

    /** Null for [Custom], whose gains are the user's own. */
    val gains: List<Float>? = gains?.map { it.toFloat() }
}

/** Saved gains back from "1.5,0,-3,…"; anything malformed reads as flat. */
fun parseEqGains(saved: String?): List<Float> {
    val gains = saved?.split(',')?.map { it.toFloatOrNull() ?: return EQ_FLAT } ?: return EQ_FLAT
    if (gains.size != EQ_BANDS.size) return EQ_FLAT
    return gains.map { it.coerceIn(-EQ_MAX_DB, EQ_MAX_DB) }
}

fun formatEqGains(gains: List<Float>): String = gains.joinToString(",") { ((it * 10).roundToInt() / 10f).toString() }

/**
 * A graphic equalizer for interleaved 16-bit PCM: one peaking filter per band (the "Audio EQ
 * Cookbook" biquad), run on each channel, after a pre-gain that lowers the whole signal by half
 * the curve's highest point. Lowering it by all of it would keep every raised band from clipping
 * but make any boost sound like turning the volume down; instead the rare peak that still goes
 * over is caught by a limiter just under full scale.
 *
 * [setGains] may be called from any thread; the audio thread picks the change up at its next
 * [beginBlock]. Everything else belongs to the audio thread. A change keeps the filters' memory,
 * so moving a band mid-song glides instead of clicking.
 */
class Equalizer(private val sampleRate: Int, private val channels: Int) {
    @Volatile private var pending: List<Float>? = null
    @Volatile private var changed = true

    /** Per active band: b0, b1, b2, a1, a2 (normalised by a0). */
    private var coeffs = DoubleArray(0)
    private var bands = 0
    private var preGain = 1.0
    /** Per channel, per band: x1, x2, y1, y2. */
    private var memory = DoubleArray(0)

    /** The limiter's gain per channel, and how many more samples it stays down before rising. */
    private val limiterGain = DoubleArray(channels) { 1.0 }
    private val limiterHold = IntArray(channels)
    private val holdSamples = (sampleRate * HOLD_S).toInt()
    /** How far towards 1 the gain rises each sample once the hold is over. */
    private val release = 1 - exp(-1.0 / (sampleRate * RELEASE_S))

    /** Null switches it off: samples pass through untouched. */
    fun setGains(gains: List<Float>?) {
        pending = gains
        changed = true
    }

    /** Takes up a gain change; false when there's nothing to do, so the caller can skip [process]. */
    fun beginBlock(): Boolean {
        if (changed) {
            changed = false
            rebuild(pending)
        }
        return bands > 0
    }

    /** One sample of [channel], in and out as 16-bit values. */
    fun process(sample: Int, channel: Int): Int {
        var x = sample * preGain
        var m = channel * bands * 4
        var c = 0
        repeat(bands) {
            val y = coeffs[c] * x + coeffs[c + 1] * memory[m] + coeffs[c + 2] * memory[m + 1] -
                coeffs[c + 3] * memory[m + 2] - coeffs[c + 4] * memory[m + 3]
            memory[m + 1] = memory[m]
            memory[m] = x
            memory[m + 3] = memory[m + 2]
            memory[m + 2] = y
            x = y
            m += 4
            c += 5
        }
        return limit(x, channel).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    }

    /**
     * A peak limiter: a sample that would go past [CEILING] turns the gain down to just fit it, held
     * there a while and then let back up slowly, so a loud passage is made quieter as a whole
     * rather than having its waves' tops cut flat. Levels under the ceiling pass untouched.
     */
    private fun limit(x: Double, channel: Int): Double {
        var g = limiterGain[channel]
        val m = abs(x)
        if (m * g > CEILING) {
            g = CEILING / m
            limiterHold[channel] = holdSamples
        } else if (limiterHold[channel] > 0) {
            limiterHold[channel]--
        } else if (g < 1.0) {
            g = minOf(1.0, g + (1.0 - g) * release)
        }
        limiterGain[channel] = g
        return x * g
    }

    /** Forgets the filters' memory: after a seek the old samples have nothing to do with the new. */
    fun reset() {
        memory.fill(0.0)
        limiterGain.fill(1.0)
        limiterHold.fill(0)
    }

    private fun rebuild(gains: List<Float>?) {
        // Bands at or above Nyquist can't be filtered; flat ones needn't be.
        val used = gains.orEmpty().withIndex()
            .filter { (i, g) -> abs(g) >= 0.05f && EQ_BANDS[i] < sampleRate * 0.45 }
        val next = DoubleArray(used.size * 5)
        used.forEachIndexed { k, (i, g) -> peaking(EQ_BANDS[i].toDouble(), g.toDouble(), next, k * 5) }
        // As many filters as before (a band moved, not switched on or off): keep the memory.
        if (used.size != bands) memory = DoubleArray(channels * used.size * 4)
        coeffs = next
        bands = used.size
        // Neighbouring boosts add up, so the room to make is the curve's real peak, not the
        // biggest single band: sampled along the spectrum, a twelfth of an octave apart.
        var peak = 1.0
        var f = 20.0
        while (f < sampleRate / 2.0) {
            peak = maxOf(peak, response(2 * PI * f / sampleRate))
            f *= 2.0.pow(1.0 / 12)
        }
        // Half the peak in dB: the square root of it as a linear gain.
        preGain = 1 / sqrt(peak)
    }

    /** How much the filters as a whole amplify at [w] radians per sample (a linear gain). */
    private fun response(w: Double): Double {
        val c1 = cos(w)
        val c2 = cos(2 * w)
        var gain = 1.0
        for (k in 0 until bands) {
            val (b0, b1, b2, a1, a2) = DoubleArray(5) { coeffs[k * 5 + it] }
            val num = b0 * b0 + b1 * b1 + b2 * b2 + 2 * (b0 * b1 + b1 * b2) * c1 + 2 * b0 * b2 * c2
            val den = 1 + a1 * a1 + a2 * a2 + 2 * (a1 + a1 * a2) * c1 + 2 * a2 * c2
            gain *= sqrt(num / den)
        }
        return gain
    }

    private fun peaking(freq: Double, gainDb: Double, out: DoubleArray, at: Int) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * freq / sampleRate
        val alpha = sin(w0) / (2 * Q)
        val cosW = cos(w0)
        val a0 = 1 + alpha / a
        out[at] = (1 + alpha * a) / a0
        out[at + 1] = -2 * cosW / a0
        out[at + 2] = (1 - alpha * a) / a0
        out[at + 3] = -2 * cosW / a0
        out[at + 4] = (1 - alpha / a) / a0
    }

    private companion object {
        /** About one octave wide, so neighbouring bands blend into a smooth curve. */
        const val Q = 1.41

        /** The loudest the limiter lets through: about 1 dB under full scale. */
        const val CEILING = 32767.0 * 0.9

        /** Longer than a wave of the lowest band (31 Hz), so a held bass note isn't pumped. */
        const val HOLD_S = 0.05
        const val RELEASE_S = 0.2
    }
}
