package com.ku.pierdoon.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/** How loud a track is as a whole, and its highest sample (0..1 of full scale). */
data class TrackLoudness(val lufs: Float, val peak: Float) {
    /**
     * The factor that brings the track to [targetLufs]: down as far as it takes, up only as far
     * as its peak leaves room for (nothing may clip) and never by more than [MAX_BOOST].
     */
    fun gain(targetLufs: Float = TARGET_LUFS): Float {
        val wanted = 10.0.pow((targetLufs - lufs) / 20.0).toFloat()
        if (wanted <= 1f) return wanted
        val room = if (peak > 0f) 1f / peak else MAX_BOOST
        return min(wanted, min(max(room, 1f), MAX_BOOST))
    }

    companion object {
        /** Where streaming services put music; most of today's tracks are well above it. */
        const val TARGET_LUFS = -14f
        /** The loudest a library's level is taken to be: past it everything would only get louder. */
        const val LOUDEST_TARGET = -9f
        /** +12 dB. */
        const val MAX_BOOST = 4f

        /**
         * The level a library is evened out to: its typical track's, so that one plays as it
         * always did and only the ones out of line move. Kept between [TARGET_LUFS] and
         * [LOUDEST_TARGET]; [TARGET_LUFS] until a few tracks are measured.
         */
        fun targetFor(levels: Collection<Float>): Float {
            if (levels.size < 3) return TARGET_LUFS
            val sorted = levels.sorted()
            val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
            return median.coerceIn(TARGET_LUFS, LOUDEST_TARGET)
        }
    }
}

/**
 * Integrated loudness after EBU R128 / ITU-R BS.1770: K-weighted mean square over 400 ms
 * blocks a quarter apart, blocks under -70 LUFS dropped, then those more than 10 LU under the
 * rest. Feed it a whole track of interleaved 16-bit samples and ask for [result].
 */
class LoudnessMeter(sampleRate: Int, private val channels: Int) {
    private val shelf = Array(channels) { Biquad.highShelf(sampleRate) }
    private val highPass = Array(channels) { Biquad.highPass(sampleRate) }
    private val weights = DoubleArray(channels) { c -> weight(c, channels) }
    private val subBlockFrames = max(1, sampleRate / 10)
    private val sums = DoubleArray(channels)
    private var frames = 0
    private var channel = 0
    /** Weighted mean square of each 100 ms; four in a row make one gating block. */
    private val subBlocks = ArrayList<Double>()
    private var peak = 0

    fun add(samples: ShortArray, count: Int = samples.size) {
        for (i in 0 until count) {
            val s = samples[i].toInt()
            val a = abs(s)
            if (a > peak) peak = a
            val c = channel
            val y = highPass[c].process(shelf[c].process(s / 32768.0))
            sums[c] += y * y
            if (++channel == channels) {
                channel = 0
                if (++frames == subBlockFrames) closeSubBlock()
            }
        }
    }

    private fun closeSubBlock() {
        var power = 0.0
        for (c in 0 until channels) {
            power += weights[c] * sums[c] / frames
            sums[c] = 0.0
        }
        subBlocks += power
        frames = 0
    }

    /** Null for less than one block of sound, or nothing above the silence gate. */
    fun result(): TrackLoudness? {
        if (subBlocks.size < 4) return null
        val blocks = DoubleArray(subBlocks.size - 3) { i ->
            (subBlocks[i] + subBlocks[i + 1] + subBlocks[i + 2] + subBlocks[i + 3]) / 4
        }
        val audible = blocks.filter { lufs(it) > ABSOLUTE_GATE }
        if (audible.isEmpty()) return null
        val relativeGate = lufs(audible.average()) - RELATIVE_GATE
        val gated = audible.filter { lufs(it) > relativeGate }
        return TrackLoudness(lufs(gated.average()).toFloat(), min(1f, peak / 32768f))
    }

    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double,
    ) {
        private var z1 = 0.0
        private var z2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        companion object {
            private fun of(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
                Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

            // The two stages of BS.1770's K-weighting for any sample rate, as libebur128 derives them.

            /** The head's effect: about +4 dB above 1.5 kHz. */
            fun highShelf(rate: Int): Biquad {
                val f0 = 1681.974450955533
                val q = 0.7071752369554196
                val k = tan(PI * f0 / rate)
                val vh = 10.0.pow(3.999843853973347 / 20)
                val vb = vh.pow(0.4996667741545416)
                return of(vh + vb * k / q + k * k, 2 * (k * k - vh), vh - vb * k / q + k * k, 1 + k / q + k * k, 2 * (k * k - 1), 1 - k / q + k * k)
            }

            /** Rumble under 40 Hz hardly counts. */
            fun highPass(rate: Int): Biquad {
                val f0 = 38.13547087602444
                val q = 0.5003270373238773
                val k = tan(PI * f0 / rate)
                val a0 = 1 + k / q + k * k
                return Biquad(1.0, -2.0, 1.0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0)
            }
        }
    }

    private companion object {
        const val ABSOLUTE_GATE = -70.0
        const val RELATIVE_GATE = 10.0

        fun lufs(power: Double) = -0.691 + 10 * log10(power)

        /**
         * A mono track plays from both speakers, so it counts twice, as the same track in stereo
         * would. Surround: the rear pair counts more, the LFE (fourth of six) not at all.
         */
        fun weight(c: Int, channels: Int): Double = when {
            channels == 1 -> 2.0
            c < 3 -> 1.0
            c == 3 && channels == 6 -> 0.0
            else -> 1.41
        }
    }
}
