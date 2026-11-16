package com.ku.pierdoon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class EqualizerTest {

    private val rate = 48_000

    /** How a [freq] Hz sine of [amplitude] comes out: its RMS against the input's, once settled. */
    private fun gainAt(eq: Equalizer, freq: Double, amplitude: Int = 10_000): Double {
        eq.beginBlock()
        var inSum = 0.0
        var outSum = 0.0
        for (n in 0 until rate / 2) {
            val x = (amplitude * sin(2 * PI * freq * n / rate)).roundToInt()
            val y = eq.process(x, 0)
            if (n > rate / 4) {
                inSum += x.toDouble() * x
                outSum += y.toDouble() * y
            }
        }
        return sqrt(outSum / inSum)
    }

    /** Loudest sample out of a full-scale [freq] Hz sine. */
    private fun peakAt(eq: Equalizer, freq: Double, amplitude: Int): Int {
        eq.beginBlock()
        var peak = 0
        for (n in 0 until rate) peak = maxOf(peak, abs(eq.process((amplitude * sin(2 * PI * freq * n / rate)).roundToInt(), 0)))
        return peak
    }

    private fun gains(vararg pairs: Pair<Int, Float>): List<Float> =
        EQ_FLAT.toMutableList().apply { pairs.forEach { (band, db) -> this[EQ_BANDS.indexOf(band)] = db } }

    @Test
    fun flatOrOffDoesNothing() {
        val eq = Equalizer(rate, 2)
        eq.setGains(EQ_FLAT)
        assertFalse(eq.beginBlock())
        eq.setGains(null)
        assertFalse(eq.beginBlock())
    }

    @Test
    fun cutLowersItsBandOnly() {
        val eq = Equalizer(rate, 1)
        eq.setGains(gains(1000 to -6f))
        assertEquals(0.5, gainAt(eq, 1000.0), 0.03)
        eq.reset()
        assertEquals(1.0, gainAt(eq, 8000.0), 0.03)
    }

    @Test
    fun boostIsMadeHalfTheRoomFor() {
        val eq = Equalizer(rate, 1)
        eq.setGains(gains(125 to EQ_MAX_DB))
        // 12 dB up: the band comes out 6 dB louder, the rest 6 dB quieter, not 12.
        assertEquals(2.0, gainAt(eq, 125.0), 0.06)
        eq.reset()
        assertEquals(0.5, gainAt(eq, 8000.0), 0.03)
    }

    @Test
    fun presetsDoNotTurnTheMusicDown() {
        // Issue #8: with the whole peak made room for, "Bass" left the mids at about -8.5 dB.
        listOf(EqPreset.Bass, EqPreset.Rock, EqPreset.Electronic).forEach { p ->
            val eq = Equalizer(rate, 1)
            eq.setGains(p.gains)
            assertTrue("$p at 1 kHz", gainAt(eq, 1000.0) > 0.55)
        }
        val loud = Equalizer(rate, 1)
        loud.setGains(EqPreset.Loud.gains)
        assertTrue(gainAt(loud, 3000.0) > 1.0)
    }

    @Test
    fun overlappingBoostsDoNotClip() {
        // 31, 62 and 125 Hz all raised: around 62 Hz they add up to more than any one band.
        listOf(EqPreset.Bass, EqPreset.BassBoost).forEach { p ->
            val eq = Equalizer(rate, 1)
            eq.setGains(p.gains)
            assertTrue("$p", peakAt(eq, 50.0, amplitude = 32_000) < Short.MAX_VALUE)
        }
    }

    @Test
    fun loudBassIsTurnedDownNotCutFlat() {
        val eq = Equalizer(rate, 1)
        eq.setGains(EqPreset.BassBoost.gains)
        eq.beginBlock()
        val out = IntArray(rate) { n -> eq.process((32_000 * sin(2 * PI * 50.0 * n / rate)).roundToInt(), 0) }
        val settled = out.drop(rate / 4).map(::abs)
        val peak = settled.max()
        // A sine spends about 9 % of its time within 1 % of its peak; a wave cut flat, far more.
        val nearPeak = settled.count { it >= peak * 0.99 }.toDouble() / settled.size
        assertTrue("near peak $nearPeak", nearPeak < 0.12)
    }

    @Test
    fun limiterLeavesOrdinaryLevelsAlone() {
        // A cut makes no room: what's under the ceiling passes as it is, what's over is turned down.
        val eq = Equalizer(rate, 1)
        eq.setGains(gains(125 to -6f))
        assertEquals(1.0, gainAt(eq, 8000.0, amplitude = 20_000), 0.02)
        eq.reset()
        // 1 kHz: at 48 kHz its samples hit the top of the wave (8 kHz ones reach only 87 % of it).
        val peak = peakAt(eq, 1000.0, amplitude = 32_767)
        // Turned down to the ceiling, about 1 dB under full scale, not cut flat at it.
        assertTrue(peak in 29_000..29_500)
    }

    @Test
    fun savedGainsRoundTrip() {
        val g = EqPreset.Rock.gains!!.mapIndexed { i, v -> v + i * 0.1f }
        assertEquals(formatEqGains(g), formatEqGains(parseEqGains(formatEqGains(g))))
        assertEquals(EQ_FLAT, parseEqGains("1,2,3"))
        assertEquals(EQ_FLAT, parseEqGains(null))
        assertEquals(EQ_MAX_DB, parseEqGains(List(EQ_BANDS.size) { "40" }.joinToString(","))[0])
    }

    @Test
    fun everyPresetFitsTheBands() {
        EqPreset.entries.forEach { p -> p.gains?.let { assertEquals(EQ_BANDS.size, it.size) } }
    }
}
