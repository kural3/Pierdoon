package com.ku.pierdoon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class LoudnessMeterTest {

    private fun sine(rate: Int, channels: Int, seconds: Double, dbfs: Double, hz: Double = 1000.0): ShortArray {
        val amplitude = 10.0.pow(dbfs / 20) * 32767
        val frames = (rate * seconds).toInt()
        return ShortArray(frames * channels) { i ->
            (amplitude * sin(2 * PI * hz * (i / channels) / rate)).roundToInt().toShort()
        }
    }

    private fun measure(rate: Int, channels: Int, vararg parts: ShortArray): TrackLoudness? {
        val meter = LoudnessMeter(rate, channels)
        parts.forEach { meter.add(it) }
        return meter.result()
    }

    @Test
    fun theStandardsReferenceToneIsMinus23() {
        // EBU Tech 3341, case 1: a stereo 1 kHz sine at -23 dBFS reads -23 LUFS.
        for (rate in listOf(44100, 48000)) {
            val l = measure(rate, 2, sine(rate, 2, 20.0, -23.0))!!
            assertEquals(-23.0, l.lufs.toDouble(), 0.1)
        }
    }

    @Test
    fun aMonoTrackReadsAsTheSameTrackInStereo() {
        val mono = measure(44100, 1, sine(44100, 1, 10.0, -20.0))!!
        val stereo = measure(44100, 2, sine(44100, 2, 10.0, -20.0))!!
        assertEquals(stereo.lufs, mono.lufs, 0.05f)
    }

    @Test
    fun silenceBetweenDoesntMakeATrackQuieter() {
        val rate = 44100
        val tone = measure(rate, 2, sine(rate, 2, 10.0, -18.0))!!
        val withGap = measure(rate, 2, sine(rate, 2, 10.0, -18.0), ShortArray(rate * 2 * 20), sine(rate, 2, 10.0, -18.0))!!
        assertEquals(tone.lufs, withGap.lufs, 0.1f)
    }

    @Test
    fun aQuietPassageIsGatedOut() {
        // EBU Tech 3341, case 3 in short: 10 s at -36, 60 s at -23, 10 s at -36 read -23.
        val rate = 48000
        val l = measure(rate, 2, sine(rate, 2, 10.0, -36.0), sine(rate, 2, 60.0, -23.0), sine(rate, 2, 10.0, -36.0))!!
        assertEquals(-23.0, l.lufs.toDouble(), 0.1)
    }

    @Test
    fun nothingToMeasureIsNull() {
        assertNull(measure(44100, 2, ShortArray(44100 * 2 * 5)))
        assertNull(measure(44100, 2, sine(44100, 2, 0.2, -10.0)))
    }

    @Test
    fun loudTracksComeDownAndQuietOnesUpAsFarAsTheirPeakAllows() {
        assertEquals(10.0.pow(-6.0 / 20).toFloat(), TrackLoudness(-8f, 1f).gain(), 0.001f)
        // 6 dB too quiet with 3 dB of room: up by 3 dB only.
        assertEquals(10.0.pow(3.0 / 20).toFloat(), TrackLoudness(-20f, 10.0.pow(-3.0 / 20).toFloat()).gain(), 0.01f)
        // A track peaking at full scale stays where it is.
        assertEquals(1f, TrackLoudness(-20f, 1f).gain(), 0f)
        assertTrue(TrackLoudness(-60f, 0.001f).gain() <= TrackLoudness.MAX_BOOST)
    }

    @Test
    fun aLibraryIsEvenedOutToItsTypicalTrack() {
        assertEquals(TrackLoudness.TARGET_LUFS, TrackLoudness.targetFor(listOf(-6f, -7f)), 0f)
        assertEquals(-10f, TrackLoudness.targetFor(listOf(-12f, -10f, -9.5f, -20f, -10f)), 0f)
        // A library of very loud tracks still comes down to a level that leaves some room.
        assertEquals(TrackLoudness.LOUDEST_TARGET, TrackLoudness.targetFor(listOf(-5f, -6f, -4f)), 0f)
        assertEquals(TrackLoudness.TARGET_LUFS, TrackLoudness.targetFor(listOf(-20f, -22f, -18f)), 0f)
    }
}
