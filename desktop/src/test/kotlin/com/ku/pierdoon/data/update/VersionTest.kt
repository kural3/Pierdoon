package com.ku.pierdoon.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun numericNotLexicographic() {
        assertTrue(isNewerVersion("1.10", "1.9"))
        assertFalse(isNewerVersion("1.9", "1.10"))
    }

    @Test
    fun patchAndMissingParts() {
        assertTrue(isNewerVersion("1.2.1", "1.2"))
        assertFalse(isNewerVersion("1.2", "1.2.0"))
        assertFalse(isNewerVersion("1.2.0", "1.2"))
        assertTrue(isNewerVersion("2", "1.9.9"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(isNewerVersion("1.2.1", "1.2.1"))
    }

    @Test
    fun olderIsNotNewer() {
        assertFalse(isNewerVersion("1.1", "1.2.1"))
    }

    @Test
    fun releaseBeatsItsPreRelease() {
        assertTrue(isNewerVersion("1.3", "1.3-beta"))
        assertFalse(isNewerVersion("1.3-beta", "1.3"))
    }

    @Test
    fun preReleaseOfNextVersionIsNewer() {
        assertTrue(isNewerVersion("1.3-beta", "1.2.1"))
        assertFalse(isNewerVersion("1.2.1", "1.3-beta"))
    }

    @Test
    fun preReleasesOfSameVersionAreNotRanked() {
        assertFalse(isNewerVersion("1.3-beta2", "1.3-beta1"))
        assertFalse(isNewerVersion("1.3-beta1", "1.3-beta2"))
    }
}
