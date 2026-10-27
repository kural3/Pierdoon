package com.ku.pierdoon.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistsTest {

    @Test
    fun splitsTheUsualSeparators() {
        assertEquals(listOf("A", "B"), splitArtists("A, B"))
        assertEquals(listOf("A", "B", "C"), splitArtists("A;B , C"))
        assertEquals(listOf("A", "B"), splitArtists("A feat. B"))
        assertEquals(listOf("A", "B"), splitArtists("A ft B"))
        assertEquals(listOf("Simon & Garfunkel"), splitArtists("Simon & Garfunkel"))
        assertEquals(listOf("Tyler, The Creator", "Kali Uchis"), splitArtists("Tyler, The Creator, Kali Uchis"))
        assertEquals(listOf("A"), splitArtists("A, a"))
        assertEquals(emptyList<String>(), splitArtists(" "))
    }

    @Test
    fun aTrackCountsForEachOfItsArtists() {
        val songs = listOf(
            Song(1, "One", "A, B", "X", albumId = 1, durationMs = 0),
            Song(2, "Two", "b", "Y", albumId = 2, durationMs = 0),
            Song(3, "Three", "A", "X", albumId = 1, durationMs = 0),
        )
        val c = MusicCollections.from(songs)
        assertEquals(listOf("A", "B"), c.artists.map { it.name })
        assertEquals(listOf(1L, 3L), c.artist("A")!!.songs.map { it.id })
        assertEquals(listOf(1L, 2L), c.artist("B")!!.songs.map { it.id })
        assertEquals(2, c.artist("B")!!.albumCount)
        // The whole tag, as the player passes it, opens the first artist.
        assertEquals("A", c.artist("A, B")!!.name)
    }
}
