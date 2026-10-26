package com.ku.pierdoon.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicWriteTest {

    @get:Rule val dir = TemporaryFolder()

    @Test
    fun replacesExistingFile() {
        val file = File(dir.root, "data.json")
        writeAtomically(file, "old")
        writeAtomically(file, "new")
        assertEquals("new", file.readText())
        assertFalse(File(dir.root, "data.json.tmp").exists())
    }

    @Test
    fun recoversWhatAnInterruptedSaveLeft() {
        val file = File(dir.root, "data.json")
        File(dir.root, "data.json.tmp").writeText("saved")
        assertTrue(recoverAtomicWrite(file))
        assertEquals("saved", file.readText())
    }

    @Test
    fun keepsTheFileOverAStaleTemp() {
        val file = File(dir.root, "data.json")
        file.writeText("current")
        File(dir.root, "data.json.tmp").writeText("half")
        assertTrue(recoverAtomicWrite(file))
        assertEquals("current", file.readText())
    }

    @Test
    fun nothingToRecover() {
        assertFalse(recoverAtomicWrite(File(dir.root, "missing.json")))
    }
}
