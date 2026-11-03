package com.ku.pierdoon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CassetteSoundTest {
    private val rate = CASSETTE_SOUND_RATE
    private val sound = cassetteChangeSound()

    @Test
    fun lastsAsLongAsItSaysInStereo() {
        assertEquals(CASSETTE_SOUND_MS.toDouble(), sound.size / 2.0 * 1000 / rate, 5.0)
    }

    @Test
    fun staysBelowFullScale() {
        val peak = sound.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak in 15_000..30_000)
    }

    @Test
    fun endsInSilenceSoTheTrackStartsClean() {
        assertTrue(sound.takeLast(2 * rate / 200).all { abs(it.toInt()) < 40 })
    }

    @Test
    fun theRewindLastsAsLongAsItSaysAndFadesOut() {
        val rewind = cassetteRewindSound()
        assertEquals(CASSETTE_REWIND_MS.toDouble(), rewind.size / 2.0 * 1000 / rate, 15.0)
        assertTrue(rewind.takeLast(2 * rate / 1000).all { abs(it.toInt()) < 300 })
        assertTrue(rewind.maxOf { abs(it.toInt()) } in 2_000..30_000)
    }

    @Test
    fun theWindIsShortAndStartsAndEndsWithoutAClick() {
        val wind = cassetteWindSound()
        assertEquals(CASSETTE_WIND_MS.toDouble(), cassetteSoundMs(wind).toDouble(), 5.0)
        assertTrue(abs(wind[0].toInt()) < 100 && abs(wind[1].toInt()) < 100)
        assertTrue(wind.takeLast(2 * rate / 1000).all { abs(it.toInt()) < 1_500 })
        assertTrue(wind.maxOf { abs(it.toInt()) } in 10_000..30_000)
    }

    @Test
    fun theKeysAreShortClicksThatDieAway() {
        for (play in listOf(true, false)) {
            val key = cassetteKeySound(play)
            assertTrue("play=$play", cassetteSoundMs(key) in 140..160)
            assertTrue("play=$play", key.maxOf { abs(it.toInt()) } in 9_000..20_000)
            assertTrue("play=$play", key.takeLast(2 * rate / 1000).all { abs(it.toInt()) < 100 })
        }
    }

    @Test
    fun keyPressesVaryButStayTheSameKey() {
        val base = cassetteKeySound(play = true)
        val keys = SoundVariety(base, pitchSpread = 0.04f, gainSpreadDb = 1.5f, random = kotlin.random.Random(7))
        val takes = List(50) { keys.next() }
        val basePeak = base.maxOf { abs(it.toInt()) }
        for (take in takes) {
            // Length follows the speed: within 4% either way.
            assertTrue(take.size.toDouble() / base.size in 0.96 / 1.0..1.0 / 0.96)
            // Within 1.5 dB, give or take what resampling does to the peak.
            assertTrue(take.maxOf { abs(it.toInt()) }.toDouble() / basePeak in 0.75..1.25)
        }
        // Never two alike in a row.
        takes.zipWithNext().forEach { (a, b) -> assertTrue(a.size != b.size || !a.contentEquals(b)) }
        assertTrue(takes.map { it.size }.toSet().size > 10)
    }

    @Test
    fun aSpinIsAsLongAsItsSpeedSays() {
        val winds = SoundVariety(cassetteWindSound(), pitchSpread = 0.06f, gainSpreadDb = 0f, random = kotlin.random.Random(3))
        repeat(30) {
            val ms = cassetteSoundMs(winds.next())
            assertTrue("$ms ms", ms in (CASSETTE_WIND_MS / 1.06).toLong()..(CASSETTE_WIND_MS / 0.94).toLong() + 1)
        }
    }

    @Test
    fun holdsTheRecordingAndNotNoise() {
        fun loudest(fromMs: Int, toMs: Int) =
            sound.slice(fromMs * rate / 1000 * 2 until toMs * rate / 1000 * 2).maxOf { abs(it.toInt()) }
        // The tape clicking home stands out from the quiet just after it.
        assertTrue(loudest(1_150, 1_250) > 15_000)
        assertTrue(loudest(1_420, 1_460) < 1_000)
    }
}
