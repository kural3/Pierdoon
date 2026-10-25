package com.ku.pierdoon.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackNamingTest {

    @Test
    fun musicServicesGiveTrackAndArtist() {
        assertEquals(
            TrackName("Locked and Loaded", "Elusin"),
            trackName(title = "Locked and Loaded (Official Audio)", track = "Locked and Loaded", artist = "Elusin", uploader = "Elusin - Topic"),
        )
    }

    @Test
    fun topicChannelNamesTheArtist() {
        assertEquals(TrackName("Song", "Band"), trackName("Song", track = "Song", artist = null, uploader = "Band - Topic"))
    }

    @Test
    fun plainVideoTitleIsSplitOnTheDash() {
        assertEquals(
            TrackName("Never Gonna Give You Up", "Rick Astley"),
            trackName("Rick Astley - Never Gonna Give You Up (Official Music Video)", null, null, "Rick Astley"),
        )
        assertEquals(TrackName("Песня", "Группа"), trackName("Группа — Песня [Lyric Video]", null, null, "label"))
    }

    @Test
    fun noDashFallsBackToTheChannel() {
        assertEquals(TrackName("credits song", "ONDA ANDAR"), trackName("credits song | 4K", null, null, "ONDA ANDAR"))
    }

    @Test
    fun featuringAndRemixStay() {
        assertEquals("Song (feat. X) (Remix)", cleanTitle("Song (feat. X) (Remix) [Official Video]"))
    }

    @Test
    fun urlIsFoundInSharedText() {
        assertEquals("https://youtu.be/abc?si=1", findUrl("Смотри: https://youtu.be/abc?si=1."))
        assertNull(findUrl("no links here"))
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("AC_DC - Back In Black", safeFileName("AC/DC - Back In Black"))
        assertEquals("track", safeFileName("  "))
    }
}
