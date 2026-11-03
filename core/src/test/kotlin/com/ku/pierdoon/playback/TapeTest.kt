package com.ku.pierdoon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class TapeTest {
    private val rate = 44_100

    @Test
    fun sidesAreCutNearTheMiddleOfTheTime() {
        // 60 of 120 minutes in the first long track: the second side starts right after it.
        assertEquals(1, tapeSides(listOf(60L, 20, 20, 20), 0).sideBStart)
        assertEquals(2, tapeSides(listOf(10L, 10, 10, 10), 0).sideBStart)
        assertEquals(3, tapeSides(listOf(10L, 10, 10, 30), 0).sideBStart)
    }

    @Test
    fun aSingleTrackHasOnlySideA() {
        val tape = tapeSides(listOf(100L), 0)
        assertEquals(1, tape.sideBStart)
        assertFalse(tape.onSideB)
        assertEquals(100L, tape.sideLengthMs)
    }

    @Test
    fun knowsTheSideAndWhatCameBeforeOnIt() {
        val onA = tapeSides(listOf(10L, 20, 30, 40, 5), 1)
        assertEquals(3, onA.sideBStart)
        assertFalse(onA.onSideB)
        assertEquals(10L, onA.elapsedMs)
        assertEquals(60L, onA.sideLengthMs)

        val onB = tapeSides(listOf(10L, 20, 30, 40, 5), 4)
        assertTrue(onB.onSideB)
        assertEquals(40L, onB.elapsedMs)
        assertEquals(45L, onB.sideLengthMs)
    }

    @Test
    fun anEmptyQueueIsNoTrouble() {
        assertEquals(TapeSides(0, false, 0, 0), tapeSides(emptyList(), -1))
    }

    @Test
    fun sideAIsTurnedOverAndSideBRewoundWhenTheQueueStartsOver() {
        assertEquals(SideEnd.Flip, sideEnd(current = 1, next = 2, size = 4, sideBStart = 2))
        assertEquals(SideEnd.Rewind, sideEnd(current = 3, next = 0, size = 4, sideBStart = 2))
        assertEquals(SideEnd.None, sideEnd(current = 0, next = 1, size = 4, sideBStart = 2))
        assertEquals(SideEnd.None, sideEnd(current = 2, next = 3, size = 4, sideBStart = 2))
        // Two tracks: one on each side.
        assertEquals(SideEnd.Flip, sideEnd(current = 0, next = 1, size = 2, sideBStart = 1))
        assertEquals(SideEnd.Rewind, sideEnd(current = 1, next = 0, size = 2, sideBStart = 1))
        // Jumping back over the middle or repeating one track isn't running off the end.
        assertEquals(SideEnd.None, sideEnd(current = 3, next = 2, size = 4, sideBStart = 2))
        assertEquals(SideEnd.None, sideEnd(current = 1, next = 1, size = 4, sideBStart = 2))
        assertEquals(SideEnd.None, sideEnd(current = 0, next = 0, size = 1, sideBStart = 1))
    }

    @Test
    fun theButtonsTurnTheCassetteOverAcrossTheMiddleAndSpinItElsewhere() {
        // Four tracks, side B from the third.
        fun move(from: Int, to: Int, forward: Boolean) = tapeMove(from, to, size = 4, sideBStart = 2, forward = forward)
        assertEquals(TapeMotion.Forward, move(0, 1, true))
        assertEquals(TapeMotion.Flip, move(1, 2, true))
        assertEquals(TapeMotion.Forward, move(2, 3, true))
        // Repeat all, next on the last track: round to the start.
        assertEquals(TapeMotion.Rewind, move(3, 0, true))
        assertEquals(TapeMotion.Back, move(3, 2, false))
        assertEquals(TapeMotion.Flip, move(2, 1, false))
        // Back to the start of the same track.
        assertEquals(TapeMotion.Back, move(2, 2, false))
        // A tape with one side has nothing to turn over.
        assertEquals(TapeMotion.Forward, tapeMove(0, 0, size = 1, sideBStart = 1, forward = true))
    }

    private fun run(tape: TapeEffect, frames: Int, sample: (Int) -> Int = { 0 }): IntArray {
        val out = IntArray(frames)
        for (f in 0 until frames) {
            out[f] = tape.process(sample(f), 0)
        }
        return out
    }

    private fun effect() = TapeEffect(rate, 1).apply { enabled = true }

    @Test
    fun offItLeavesTheSoundAlone() {
        val tape = TapeEffect(rate, 1)
        assertFalse(tape.beginBlock())
        val out = run(tape, 1000) { (it * 7) % 3000 }
        for (f in out.indices) assertEquals((f * 7) % 3000, out[f])
    }

    @Test
    fun hissIsThereButQuiet() {
        val out = run(effect(), rate * 2).drop(rate)
        val rms = sqrt(out.sumOf { it.toDouble() * it } / out.size)
        assertTrue("rms $rms", rms in 5.0..40.0)
        assertTrue(out.all { abs(it) < 100 })
    }

    /** Where a tone's upward zero crossings fall, in frames, between samples. */
    private fun crossings(signal: IntArray): List<Double> = buildList {
        for (i in 1 until signal.size) {
            if (signal[i - 1] < 0 && signal[i] >= 0) add(i - 1 + -signal[i - 1].toDouble() / (signal[i] - signal[i - 1]))
        }
    }

    @Test
    fun theSpeedWandersByAFractionOfAPercent() {
        val hz = 1_000.0
        val out = run(effect(), rate * 4) { (20_000 * sin(2 * PI * hz * it / rate)).toInt() }.drop(rate / 2)
        val cycles = crossings(out.toIntArray())
        // Tone periods over stretches of 20 cycles (20 ms) average out the hiss but not the wow.
        val periods = cycles.windowed(21, step = 21) { (it.last() - it.first()) / 20 }
        val speeds = periods.map { rate / hz / it }
        val spread = speeds.max() - speeds.min()
        assertTrue("spread $spread", spread in 0.002..0.01)
    }

    @Test
    fun itFadesInWithoutAClick() {
        val tape = effect()
        val out = run(tape, rate / 2) { 10_000 }
        // The delay line starts empty, so all that may happen is a slide, never a jump.
        for (f in 1 until out.size) assertTrue("frame $f", abs(out[f] - out[f - 1]) < 400)
        assertTrue(abs(out.last() - 10_000) < 200)
    }

    @Test
    fun itFadesOutToTheDrySound() {
        val tape = effect()
        run(tape, rate / 2) { 5_000 }
        tape.enabled = false
        assertTrue(tape.beginBlock())
        run(tape, rate / 2) { 5_000 }
        assertFalse(tape.beginBlock())
    }

    @Test
    fun theLoudestSampleStaysInRange() {
        val out = run(effect(), rate) { if (it % 2 == 0) 32767 else -32768 }
        assertTrue(out.all { it in -32768..32767 })
    }

    @Test
    fun channelsStayInStep() {
        val tape = TapeEffect(rate, 2).apply { enabled = true }
        // A click on the left only must come out on the left only.
        var right = 0
        for (f in 0 until rate / 2) {
            tape.process(if (f == rate / 4) 20_000 else 0, 0)
            right = maxOf(right, abs(tape.process(0, 1)))
        }
        assertTrue("right $right", right < 100)
    }
}
