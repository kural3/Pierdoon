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

class CustomFontsTest {

    @get:Rule val tmp = TemporaryFolder()

    private val trueType = byteArrayOf(0, 1, 0, 0) + ByteArray(60)
    private val openType = "OTTO".toByteArray() + ByteArray(60)

    @Test
    fun aFontIsCopiedInUnderItsOwnName() {
        val dir = File(tmp.root, "fonts")
        val imported = CustomFonts.import(trueType.inputStream(), "Inter-Regular.ttf", dir)
        assertNotNull(imported)
        assertEquals("Inter-Regular", imported!!.name)
        assertTrue(File(dir, imported.file).isFile)
        assertTrue(imported.file.endsWith(".ttf"))
    }

    @Test
    fun anotherFontReplacesTheFirst() {
        val dir = File(tmp.root, "fonts")
        val first = CustomFonts.import(trueType.inputStream(), "A.ttf", dir)!!
        Thread.sleep(2)
        val second = CustomFonts.import(openType.inputStream(), "B.otf", dir)!!
        assertFalse(File(dir, first.file).exists())
        assertTrue(File(dir, second.file).isFile)
        assertEquals(listOf(second.file), dir.list()!!.toList())
    }

    @Test
    fun somethingElseIsTurnedAwayAndLeavesNothing() {
        val dir = File(tmp.root, "fonts")
        val kept = CustomFonts.import(trueType.inputStream(), "A.ttf", dir)!!
        assertNull(CustomFonts.import("<html>not a font</html>".toByteArray().inputStream(), "fake.ttf", dir))
        assertEquals(listOf(kept.file), dir.list()!!.toList())
    }
}
