package com.ku.pierdoon.playback

import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Where the current track is on the tape the queue makes. */
@Immutable
data class TapeSides(
    /** Position in the play order where side B starts; the queue's size when there's only side A. */
    val sideBStart: Int,
    val onSideB: Boolean,
    /** What's on the current side before the current track. */
    val elapsedMs: Long,
    val sideLengthMs: Long,
)

/**
 * The queue as a cassette: two sides of about the same length, cut between tracks, in play order.
 * A single track makes a tape with one side.
 */
fun tapeSides(durationsMs: List<Long>, current: Int): TapeSides {
    val start = sideBStart(durationsMs)
    val onB = current >= start
    val side = if (onB) durationsMs.subList(start, durationsMs.size) else durationsMs.subList(0, start)
    val before = if (onB) (current - start).coerceAtLeast(0) else current.coerceAtLeast(0)
    return TapeSides(start, onB, side.take(before).sum(), side.sum())
}

private fun sideBStart(durationsMs: List<Long>): Int {
    if (durationsMs.size < 2) return durationsMs.size
    val total = durationsMs.sum()
    var best = 1
    var bestGap = Long.MAX_VALUE
    var sum = 0L
    for (i in 1 until durationsMs.size) {
        sum += durationsMs[i - 1]
        val gap = abs(total - 2 * sum)
        if (gap < bestGap) {
            bestGap = gap
            best = i
        }
    }
    return best
}

/** What the deck does between two tracks of the tape. */
enum class SideEnd {
    /** Same side: one track runs into the next. */
    None,
    /** Side A ran out: the cassette is turned over. */
    Flip,
    /** Side B ran out and the queue starts over: the tape rewinds to the start of side A. */
    Rewind,
}

/** What going from [current] to [next] (positions in play order) means on the tape. */
fun sideEnd(current: Int, next: Int, size: Int, sideBStart: Int): SideEnd = when {
    current == next || size < 2 -> SideEnd.None
    next == 0 && current == size - 1 -> SideEnd.Rewind
    next == sideBStart && current == sideBStart - 1 -> SideEnd.Flip
    else -> SideEnd.None
}

/**
 * What a tape does to the sound: the speed wanders slowly (wow) and quickly (flutter), the top end
 * dulls, and a little hiss runs under it all. Interleaved 16-bit PCM, like [Equalizer].
 *
 * [enabled] may be set from any thread; going on or off slides over [MIX_MS], so there's no click.
 * Everything else belongs to the audio thread.
 */
class TapeEffect(private val sampleRate: Int, private val channels: Int) {
    @Volatile var enabled: Boolean = false

    private val capacity = (sampleRate * MAX_DELAY_S).toInt() + 2
    private val lines = Array(channels) { FloatArray(capacity) }
    private var write = 0
    private var wowPhase = 0.0
    private var flutterPhase = 0.0
    private val wowStep = 2 * PI * WOW_HZ / sampleRate
    private val flutterStep = 2 * PI * FLUTTER_HZ / sampleRate
    private val baseDelay = BASE_DELAY_S * sampleRate
    private val wowDepth = WOW_SPEED / (2 * PI * WOW_HZ) * sampleRate
    private val flutterDepth = FLUTTER_SPEED / (2 * PI * FLUTTER_HZ) * sampleRate
    private var delay = baseDelay

    private var mix = 0f
    private val mixStep = 1f / (sampleRate * MIX_MS / 1000f)

    private val dull = FloatArray(channels)
    private val dullAmount = (1 - kotlin.math.exp(-2 * PI * DULL_HZ / sampleRate)).toFloat()
    private val hissLow = FloatArray(channels)
    private val hissLowAmount = (1 - kotlin.math.exp(-2 * PI * HISS_ABOVE_HZ / sampleRate)).toFloat()
    private val noise = IntArray(channels) { 0x2545F491 + it * 7919 }

    /** False when there's nothing to do, so the caller can skip [process]. */
    fun beginBlock(): Boolean = enabled || mix > 0f

    /** One sample of [channel], in and out as 16-bit values; channels in order, frame by frame. */
    fun process(sample: Int, channel: Int): Int {
        val line = lines[channel]
        line[write] = sample.toFloat()

        val back = write - delay
        val at = if (back < 0) back + capacity else back
        val i = floor(at).toInt()
        val part = (at - i).toFloat()
        val a = line[i % capacity]
        val b = line[(i + 1) % capacity]
        val delayed = a + (b - a) * part
        dull[channel] += dullAmount * (delayed - dull[channel])

        val out = sample + (dull[channel] + hiss(channel) - sample) * mix
        if (channel == channels - 1) nextFrame()
        return out.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    }

    /** Noise with its low end taken away: hiss, not rumble. */
    private fun hiss(channel: Int): Float {
        var x = noise[channel]
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noise[channel] = x
        val white = x / Int.MAX_VALUE.toFloat()
        hissLow[channel] += hissLowAmount * (white - hissLow[channel])
        return (white - hissLow[channel]) * HISS_LEVEL
    }

    private fun nextFrame() {
        write = (write + 1) % capacity
        wowPhase = (wowPhase + wowStep) % (2 * PI)
        flutterPhase = (flutterPhase + flutterStep) % (2 * PI)
        delay = baseDelay + wowDepth * sin(wowPhase) + flutterDepth * sin(flutterPhase)
        mix = if (enabled) (mix + mixStep).coerceAtMost(1f) else (mix - mixStep).coerceAtLeast(0f)
    }

    /** After a seek the delayed samples have nothing to do with the new ones. */
    fun reset() {
        lines.forEach { it.fill(0f) }
        dull.fill(0f)
        hissLow.fill(0f)
    }

    private companion object {
        const val WOW_HZ = 0.55
        const val FLUTTER_HZ = 6.3
        /** The speed's largest departure, as a fraction: well-worn deck, not a broken one. */
        const val WOW_SPEED = 0.0025
        const val FLUTTER_SPEED = 0.0008
        const val BASE_DELAY_S = 0.004
        const val MAX_DELAY_S = 0.01
        const val DULL_HZ = 12_000.0
        const val HISS_ABOVE_HZ = 3_000.0
        /** Peak of the noise in 16-bit units, about -58 dB. */
        const val HISS_LEVEL = 40f
        const val MIX_MS = 100
    }
}
