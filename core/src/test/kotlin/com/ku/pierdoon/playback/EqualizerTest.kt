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
    fun boostIsMadeRoomFor() {
        val eq = Equalizer(rate, 1)
        eq.setGains(gains(125 to EQ_MAX_DB))
        // The boosted band comes out as loud as it went in; the rest is turned down instead.
        assertEquals(1.0, gainAt(eq, 125.0), 0.03)
        eq.reset()
        assertEquals(0.25, gainAt(eq, 8000.0), 0.03)
    }

    @Test
    fun overlappingBoostsDoNotClip() {
        // 31, 62 and 125 Hz all raised: around 62 Hz they add up to more than any one band.
        val eq = Equalizer(rate, 1)
        eq.setGains(EqPreset.Bass.gains)
        assertTrue(peakAt(eq, 50.0, amplitude = 30_000) < Short.MAX_VALUE)
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
