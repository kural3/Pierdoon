package com.ku.pierdoon.playback

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * How loud the music is at a moment: [energy] overall and [bass] below ~150 Hz, both 0..1
 * against the track's own recent peaks, so quiet and loud recordings move the picture alike.
 * [beats] counts the kicks heard so far; a new number means a new beat.
 */
class AudioLevels(val energy: Float, val bass: Float, val beats: Long) {
    companion object {
        val Silent = AudioLevels(0f, 0f, 0)
    }
}

/**
 * Measures what [AudioEngine] writes, piece by piece, and remembers each piece by the line frame
 * it starts at; [at] then answers for the frame being heard, so the picture follows the speakers
 * rather than the decoder ~170 ms ahead of them. Fed on the audio thread, read on the UI thread.
 */
internal class LevelMeter(private val rate: Int) {
    private val lowAlpha = 1.0 - exp(-2.0 * PI * BASS_HZ / rate)
    private var low = 0.0
    private var sumSquares = 0.0
    private var sumLowSquares = 0.0
    private var frames = 0
    private var pendingLeft = NO_LEFT

    // Slowly falling peaks to measure against, and the bass level beats stand out from.
    private var energyPeak = FLOOR
    private var bassPeak = FLOOR
    private var bassAverage = 0.0
    private var beats = 0L
    private var lastBeatFrame = Long.MIN_VALUE / 2

    private val lock = Any()
    private val startFrames = LongArray(SIZE)
    private val energies = FloatArray(SIZE)
    private val basses = FloatArray(SIZE)
    private val beatCounts = LongArray(SIZE)
    private var head = 0
    private var count = 0

    /** One interleaved stereo sample, before the volume is applied. */
    fun sample(s: Short) {
        if (pendingLeft == NO_LEFT) {
            pendingLeft = s.toInt()
            return
        }
        val mono = (pendingLeft + s.toInt()) / 65536.0
        pendingLeft = NO_LEFT
        sumSquares += mono * mono
        low += lowAlpha * (mono - low)
        sumLowSquares += low * low
        frames++
    }

    /** Closes the piece just fed, which starts at [startFrame] of the line. */
    fun commit(startFrame: Long) {
        if (frames == 0) return
        val seconds = frames.toDouble() / rate
        val rms = sqrt(sumSquares / frames)
        val bassRms = sqrt(sumLowSquares / frames)
        val fall = exp(-seconds / PEAK_FALL_S)
        energyPeak = max(rms, max(FLOOR, energyPeak * fall))
        bassPeak = max(bassRms, max(FLOOR, bassPeak * fall))
        bassAverage += (bassRms - bassAverage) * (1 - exp(-seconds / AVERAGE_S))
        // A kick: the bass jumps well over its recent level, and isn't the same kick again.
        if (bassRms > bassAverage * BEAT_RISE && bassRms > bassPeak * BEAT_SHARE &&
            startFrame - lastBeatFrame > rate * BEAT_GAP_S
        ) {
            beats++
            lastBeatFrame = startFrame
        }
        synchronized(lock) {
            startFrames[head] = startFrame
            energies[head] = (rms / energyPeak).toFloat()
            basses[head] = (bassRms / bassPeak).toFloat()
            beatCounts[head] = beats
            head = (head + 1) % SIZE
            if (count < SIZE) count++
        }
        sumSquares = 0.0
        sumLowSquares = 0.0
        frames = 0
    }

    /** The levels of the piece playing at line frame [frame]; silence before anything was measured. */
    fun at(frame: Long): AudioLevels = synchronized(lock) {
        for (i in 1..count) {
            val j = (head - i + SIZE) % SIZE
            if (startFrames[j] <= frame) return AudioLevels(energies[j], basses[j], beatCounts[j])
        }
        AudioLevels.Silent
    }

    /** The line was replaced and counts from zero again: what's remembered no longer lines up. */
    fun reset() = synchronized(lock) { count = 0 }

    private companion object {
        const val SIZE = 256
        const val NO_LEFT = Int.MIN_VALUE
        const val BASS_HZ = 150.0
        const val FLOOR = 0.02
        const val PEAK_FALL_S = 4.0
        const val AVERAGE_S = 0.4
        const val BEAT_RISE = 1.5
        const val BEAT_SHARE = 0.6
        const val BEAT_GAP_S = 0.3
    }
}
