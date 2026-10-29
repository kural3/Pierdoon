package com.ku.pierdoon.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
    Vocal(listOf(-2, -2, -1, 0, 2, 4, 4, 2, 0, -1)),
    Rock(listOf(4, 3, 2, 0, -1, -1, 1, 2, 3, 4)),
    Electronic(listOf(5, 4, 1, 0, -2, 1, 0, 2, 4, 5)),
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
 * Cookbook" biquad), run on each channel, after a pre-gain that lowers the whole signal by the
 * curve's highest point so a raised band doesn't clip.
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
        return x.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    }

    /** Forgets the filters' memory: after a seek the old samples have nothing to do with the new. */
    fun reset() {
        memory.fill(0.0)
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
        preGain = 1 / peak
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
    }
}
