package com.ku.pierdoon.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatchTest {

    private val library = TrackMatcher(
        listOf(
            TrackKey("Ёлочка", "Кино", 200_000),
            TrackKey("Don’t Stop Me Now", "Queen", 209_000),
            TrackKey("Intro", "", 60_000),
            TrackKey("Untitled", "<unknown>", 0),
        )
    )

    @Test
    fun sameTrackWithOtherSpelling() {
        assertTrue(library.contains(TrackKey("елочка", "КИНО", 201_500)))
        assertTrue(library.contains(TrackKey("Don't Stop Me Now ", "queen", 208_000)))
    }

    @Test
    fun lengthTellsVersionsApart() {
        // A live version of the same song runs much longer.
        assertFalse(library.contains(TrackKey("Don't Stop Me Now", "Queen", 260_000)))
    }

    @Test
    fun otherArtistIsAnotherTrack() {
        assertFalse(library.contains(TrackKey("Ёлочка", "Другая группа", 200_000)))
    }

    @Test
    fun withoutArtistTheLengthMustAgree() {
        assertTrue(library.contains(TrackKey("Intro", "Some Band", 61_000)))
        assertFalse(library.contains(TrackKey("Intro", "", 0)))
        // Neither side knows the artist or the length: too little to go on.
        assertFalse(library.contains(TrackKey("Untitled", "", 0)))
    }

    @Test
    fun emptyTitleNeverMatches() {
        assertFalse(TrackMatcher(listOf(TrackKey("", "A", 1000))).contains(TrackKey("", "A", 1000)))
    }

    @Test
    fun normalizesPunctuationAndSpacing() {
        assertEquals("a - b", normalizeTag("  A  — B. "))
        assertEquals("hey", normalizeTag("«Hey»"))
    }
}
