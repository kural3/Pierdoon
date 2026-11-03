package com.ku.pierdoon.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "download-log.json")

    @Test
    fun keepsEntriesBetweenRunsNewestFirst() {
        val log = DownloadLog(file())
        log.add(DownloadLogEntry(1, ok = true, url = "a", title = "A", artist = null))
        log.add(DownloadLogEntry(2, ok = false, url = "b", title = null, artist = "X", reason = "Network", message = "ERROR: timed out"))
        val again = DownloadLog(file()).entries.value
        assertEquals(listOf("b", "a"), again.map { it.url })
        assertEquals("Network", again[0].reason)
        assertEquals("ERROR: timed out", again[0].message)
        assertEquals(null, again[0].title)
    }

    @Test
    fun dropsTheOldestPastTheLimitAndKeepsTheEndOfLongErrors() {
        val log = DownloadLog(file(), limit = 3)
        repeat(5) { log.add(DownloadLogEntry(it.toLong(), ok = false, url = "$it", title = null, artist = null, message = "x".repeat(5000) + "END")) }
        val entries = DownloadLog(file(), limit = 3).entries.value
        assertEquals(listOf("4", "3", "2"), entries.map { it.url })
        assertTrue(entries[0].message!!.endsWith("END"))
        assertEquals(4000, entries[0].message!!.length)
    }

    @Test
    fun clearEmptiesItForGood() {
        val log = DownloadLog(file())
        log.add(DownloadLogEntry(1, ok = true, url = "a", title = null, artist = null))
        log.clear()
        assertTrue(DownloadLog(file()).entries.value.isEmpty())
    }
}
