package com.ku.pierdoon.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldersTest {

    private var nextId = 1L
    private fun song(folder: String) = Song(nextId++, "t$nextId", "", "", 0, 1000, folder = folder)

    @Test
    fun phoneFoldersKeepTheirPlaceButNotWhatAllShare() {
        val folders = MusicCollections.folders(
            listOf(song("Music/Rock"), song("Music/Rock"), song("Music/Jazz/Live"), song("Download")),
        )
        assertEquals(listOf("Download", "Live", "Rock"), folders.map { it.name })
        assertEquals(listOf("", "Music/Jazz", "Music"), folders.map { it.location })
        assertEquals(2, folders.first { it.name == "Rock" }.songs.size)
    }

    @Test
    fun aComputerShowsOnlyWhatComesAfterItsMusicFolder() {
        val folders = MusicCollections.folders(
            listOf(song("C:\\Users\\me\\Music\\A"), song("C:\\Users\\me\\Music\\B\\C"), song("C:\\Users\\me\\Music\\B")),
        )
        assertEquals(listOf("A", "B", "C"), folders.map { it.name })
        assertEquals(listOf("", "", "B"), folders.map { it.location })
    }

    @Test
    fun foldersOfTheSameNameAreBothThere() {
        val folders = MusicCollections.folders(listOf(song("Music/A/CD1"), song("Music/B/CD1")))
        assertEquals(listOf("Music/A/CD1", "Music/B/CD1"), folders.map { it.path })
        assertEquals(listOf("A", "B"), folders.map { it.location })
    }

    @Test
    fun tracksWithoutAFolderAreLeftOut() {
        assertTrue(MusicCollections.folders(listOf(song(""))).isEmpty())
        assertEquals(1, MusicCollections.from(listOf(song(""), song("Music"))).folders.size)
    }
}
