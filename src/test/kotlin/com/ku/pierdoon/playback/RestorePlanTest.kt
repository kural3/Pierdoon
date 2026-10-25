package com.ku.pierdoon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestorePlanTest {

    private fun queue(vararg ids: Long, index: Int, positionMs: Long = 42_000L) =
        SavedQueue(ids.toList(), index, positionMs, shuffle = false, repeatMode = 0)

    @Test
    fun everythingStillThere() {
        val plan = queue(1, 2, 3, index = 1).restorePlan { true }!!
        assertEquals(RestorePlan(listOf(1, 2, 3), 1, 42_000L), plan)
    }

    @Test
    fun deletedTrackBeforeCurrentShiftsIndex() {
        val plan = queue(1, 2, 3, 4, index = 2).restorePlan { it != 1L }!!
        assertEquals(RestorePlan(listOf(2, 3, 4), 1, 42_000L), plan)
    }

    @Test
    fun deletedTrackAfterCurrentChangesNothingForIt() {
        val plan = queue(1, 2, 3, index = 1).restorePlan { it != 3L }!!
        assertEquals(RestorePlan(listOf(1, 2), 1, 42_000L), plan)
    }

    @Test
    fun deletedCurrentStartsTheNextOneFromTheTop() {
        val plan = queue(1, 2, 3, index = 1).restorePlan { it != 2L }!!
        assertEquals(RestorePlan(listOf(1, 3), 1, 0L), plan)
    }

    @Test
    fun deletedCurrentAtTheEndFallsBackToTheLast() {
        val plan = queue(1, 2, 3, index = 2).restorePlan { it != 3L }!!
        assertEquals(RestorePlan(listOf(1, 2), 1, 0L), plan)
    }

    @Test
    fun songQueuedTwiceKeepsBothEntries() {
        val plan = queue(7, 8, 7, index = 2).restorePlan { true }!!
        assertEquals(RestorePlan(listOf(7, 8, 7), 2, 42_000L), plan)
    }

    @Test
    fun nothingLeft() {
        assertNull(queue(1, 2, index = 0).restorePlan { false })
        assertNull(queue(index = 0).restorePlan { true })
    }
}
