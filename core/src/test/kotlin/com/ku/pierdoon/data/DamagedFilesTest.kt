package com.ku.pierdoon.data

import com.ku.pierdoon.radio.RadioMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Files of the user's own work that come back partly unreadable keep what can be read. */
class DamagedFilesTest {

    @get:Rule val dir = TemporaryFolder()

    private fun damagedCopies(prefix: String) = dir.root.listFiles { f -> f.name.startsWith("$prefix-damaged-") }!!.toList()

    @Test
    fun tagEditsReadWhole() {
        val (edits, damaged) = TagOverrides.parseTagOverrides("""{"5":{"title":"Road","album":"A"},"6":{"artist":"B"}}""")!!
        assertFalse(damaged)
        assertEquals(TagOverride(title = "Road", album = "A"), edits[5])
        assertEquals(TagOverride(artist = "B"), edits[6])
    }

    @Test
    fun aBrokenTagEditCostsOnlyItself() {
        val parsed = TagOverrides.parseTagOverrides("""{"5":{"title":"Road"},"x":{"title":"?"},"7":"text","8":{"title":3}}""")
        assertNotNull(parsed)
        val (edits, damaged) = parsed!!
        assertTrue(damaged)
        assertEquals(TagOverride(title = "Road"), edits[5])
        // The entry with a field of the wrong kind keeps the rest of it.
        assertEquals(TagOverride(), edits[8])
        assertEquals(setOf(5L, 8L), edits.keys)
    }

    @Test
    fun textThatIsNoTagFileIsNull() {
        assertNull(TagOverrides.parseTagOverrides("{\"5\":{\"title\""))
    }

    @Test
    fun radioKeepsWhatItCanReadAndPutsTheFileAside() {
        val file = File(dir.root, "radio.json")
        file.writeText("""{"videos":["a",5,"b"],"songs":[1,"x",2],"artists":{"band":2,"other":"many"}}""")
        val memory = RadioMemory.get(dir.root)
        assertTrue(memory.isDisliked(1))
        assertTrue(memory.isDisliked(2))
        assertEquals(1, damagedCopies("radio").size)
        assertEquals(file.readText(), damagedCopies("radio").single().readText())
    }

    @Test
    fun aRadioFileThatReadsWholeIsLeftAlone() {
        File(dir.root, "radio.json").writeText("""{"videos":["a"],"songs":[1],"artists":{"band":1}}""")
        val memory = RadioMemory.get(dir.root)
        assertTrue(memory.isDisliked(1))
        assertTrue(damagedCopies("radio").isEmpty())
    }

    @Test
    fun theCopyKeepsTheExtensionAndOnlyTheNewestFew() {
        val file = File(dir.root, "tag_overrides.json")
        repeat(5) { i ->
            file.writeText("broken $i")
            keepDamagedCopy(file)
            Thread.sleep(5)
        }
        val kept = damagedCopies("tag_overrides").sortedBy { it.name }
        assertEquals(3, kept.size)
        assertTrue(kept.all { it.name.endsWith(".json") })
        assertEquals("broken 4", kept.last().readText())
    }
}
