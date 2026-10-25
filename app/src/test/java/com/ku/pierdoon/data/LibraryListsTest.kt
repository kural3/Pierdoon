package com.ku.pierdoon.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryListsTest {

    private fun song(id: Long, title: String, artist: String = "", album: String = "", duration: Long = 0, added: Long = 0) =
        Song(id, title, artist, album, albumId = id, durationMs = duration, dateAdded = added)

    private val songs = listOf(
        song(1, "Ёлка", "Band B", "Zeta", duration = 300, added = 3),
        song(2, "apple", "Band A", "Alpha", duration = 100, added = 1),
        song(3, "Banana", "Band A", "Alpha", duration = 200, added = 2),
    )

    @Test
    fun sortsByEachKey() {
        assertEquals(listOf(2L, 3L, 1L), sortSongs(songs, SongSort.Title, false).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), sortSongs(songs, SongSort.Title, true).map { it.id })
        // Equal artists fall back to the title.
        assertEquals(listOf(2L, 3L, 1L), sortSongs(songs, SongSort.Artist, false).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), sortSongs(songs, SongSort.Artist, true).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), sortSongs(songs, SongSort.DateAdded, true).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), sortSongs(songs, SongSort.Duration, false).map { it.id })
    }

    @Test
    fun searchMatchesEveryWordAnywhere() {
        val albums = listOf(Album(10, "Alpha", "Band A", songs.filter { it.album == "Alpha" }))
        val artists = listOf(Artist("Band A", songs.filter { it.artist == "Band A" }, 1), Artist("Band B", songs.take(1), 1))
        val collections = MusicCollections(albums, artists)

        assertEquals(listOf(3L), searchLibrary("band banana", songs, collections).songs.map { it.id })
        // ё and е are the same letter here, and case doesn't matter.
        assertEquals(listOf(1L), searchLibrary("елка", songs, collections).songs.map { it.id })
        assertEquals(listOf("Band A", "Band B"), searchLibrary("band", songs, collections).artists.map { it.name })
        assertEquals(listOf(10L), searchLibrary("alpha", songs, collections).albums.map { it.id })
        assertTrue(searchLibrary("   ", songs, collections).isEmpty)
        assertTrue(searchLibrary("zzz", songs, collections).isEmpty)
    }

    @Test
    fun searchKeepsShortI() {
        val list = listOf(song(1, "Мой"), song(2, "Мои"))
        assertEquals(listOf(1L), searchLibrary("мой", list, MusicCollections()).songs.map { it.id })
    }

    @Test
    fun titlesStartingWithTheQueryComeFirst() {
        val list = listOf(song(1, "Love Me Do", "Love"), song(2, "Lovely"))
        val found = searchLibrary("love", list, MusicCollections()).songs.map { it.id }
        assertEquals(listOf(1L, 2L), found)
        val found2 = searchLibrary("lovely", list + song(3, "So Lovely"), MusicCollections()).songs.map { it.id }
        assertEquals(listOf(2L, 3L), found2)
    }
}
