package com.ku.pierdoon.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UserLibraryReadTest {

    @get:Rule val dir = TemporaryFolder()

    private fun parse(text: String): UserLibrary.Companion.Parsed {
        val parsed = UserLibrary.parseUserData(text)
        assertNotNull(parsed)
        return parsed!!
    }

    @Test
    fun readsAWholeFile() {
        val p = parse(
            """{"version":1,"playlists":[{"id":7,"name":"Road","songs":[1,2],"shape":"Heart"}],
               "favorites":[3,4],"recent":[2,1],"smart":[]}"""
        )
        assertFalse(p.damaged)
        assertEquals(listOf(Playlist(7, "Road", listOf(1, 2), "Heart")), p.data.playlists)
        assertEquals(setOf(3L, 4L), p.data.favorites)
        assertEquals(listOf(2L, 1L), p.data.recent)
        assertTrue(p.data.loaded)
    }

    @Test
    fun aBrokenPlaylistCostsOnlyItself() {
        // The second playlist has no "songs" key name it can use and no name at all.
        val p = parse(
            """{"playlists":[{"id":7,"name":"Road","songs":[1]},{"id":8,"songIds":[2]}],
               "favorites":[3,4]}"""
        )
        assertTrue(p.damaged)
        assertEquals(listOf(7L), p.data.playlists.map { it.id })
        assertEquals(setOf(3L, 4L), p.data.favorites)
    }

    @Test
    fun aPlaylistWithUnreadableTracksStaysButMarksTheFile() {
        val p = parse("""{"playlists":[{"id":8,"name":"Old","songIds":[2]}]}""")
        assertTrue(p.damaged)
        assertEquals(listOf(Playlist(8, "Old", emptyList())), p.data.playlists)
    }

    @Test
    fun aBrokenIdCostsOnlyItself() {
        val p = parse("""{"favorites":[3,"x",4,null],"recent":["5"]}""")
        assertTrue(p.damaged)
        assertEquals(setOf(3L, 4L), p.data.favorites)
        assertEquals(listOf(5L), p.data.recent)
    }

    @Test
    fun anEmptyLibraryIsNotDamaged() {
        val p = parse("{}")
        assertFalse(p.damaged)
        assertTrue(p.data.playlists.isEmpty())
    }

    @Test
    fun textThatIsNoLibraryIsNull() {
        assertNull(UserLibrary.parseUserData("{\"playlists\":[{\"id\":1"))
        assertNull(UserLibrary.parseUserData(""))
    }

    @Test
    fun keepsTheDamagedFileAndOnlyTheNewestFew() {
        val file = File(dir.root, "library.json")
        repeat(5) { i ->
            file.writeText("broken $i")
            keepDamagedCopy(file)
            Thread.sleep(5)
        }
        val kept = dir.root.listFiles { f -> f.name.startsWith("library-damaged-") }!!.sortedBy { it.name }
        assertEquals(3, kept.size)
        assertEquals("broken 4", kept.last().readText())
        // The file itself stays where it was.
        assertEquals("broken 4", file.readText())
    }
}
