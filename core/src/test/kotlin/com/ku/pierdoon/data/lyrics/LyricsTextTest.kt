package com.ku.pierdoon.data.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LyricsTextTest {

    @Test
    fun noStampsArePlainLyrics() {
        assertEquals(Lyrics.Plain(listOf("one", "", "two")), LyricsText.toLyrics("\n one\n\ntwo \n\n"))
    }

    @Test
    fun allStampedAreSynced() {
        val l = LyricsText.toLyrics("[00:01.50]a\r\n[00:03]b")
        assertEquals(Lyrics.Synced(listOf(LyricLine(1_500, "a"), LyricLine(3_000, "b"))), l)
    }

    @Test
    fun unstampedLinesSpreadBetweenTheirNeighbours() {
        val l = LyricsText.toLyrics("[00:10.00]a\nb\nc\n[00:16.00]d\ne") as Lyrics.Synced
        assertEquals(listOf(10_000L, 12_000L, 14_000L, 16_000L, 19_000L), l.lines.map { it.timeMs })
        assertEquals(listOf("a", "b", "c", "d", "e"), l.lines.map { it.text })
    }

    @Test
    fun linesBeforeTheFirstStampStartFromZero() {
        val l = LyricsText.toLyrics("a\n[00:04.00]b") as Lyrics.Synced
        assertEquals(listOf(2_000L, 4_000L), l.lines.map { it.timeMs })
    }

    @Test
    fun tagsAreDroppedAndDoubleStampsMakeTwoLines() {
        val lines = LyricsText.lines("[ar:Someone]\n[offset:+100]\n[00:01.00][00:20.00]chorus")
        assertEquals(listOf(EditLine(1_000, "chorus"), EditLine(20_000, "chorus")), lines)
    }

    @Test
    fun blankTextIsNoLyrics() {
        assertEquals(Lyrics.NotFound, LyricsText.toLyrics(" \n\n"))
    }

    @Test
    fun textRoundTrips() {
        val text = "[01:02.03]a\nb\n[10:00.00]"
        assertEquals(text, LyricsText.text(LyricsText.lines(text)))
    }

    @Test
    fun ownLyricsWinAndOutliveTheCache() = runBlocking {
        val root = Files.createTempDirectory("lyrics").toFile()
        try {
            val repo = LyricsRepository(root.resolve("cache"), "test", root.resolve("files"))
            val query = LyricsQuery(7, "t", "a", "", 0)
            assertFalse(repo.isOwn(7))
            repo.saveOwn(7, "[00:01.00]mine")
            assertTrue(repo.isOwn(7))
            repo.clear()
            val fresh = LyricsRepository(root.resolve("cache"), "test", root.resolve("files"))
            assertEquals(Lyrics.Synced(listOf(LyricLine(1_000, "mine"))), fresh.get(query, allowNetwork = false))
            assertEquals("[00:01.00]mine", fresh.ownText(7))
            fresh.saveOwn(7, "  ")
            assertFalse(fresh.isOwn(7))
            assertNull(fresh.ownText(7))
        } finally {
            root.deleteRecursively()
        }
    }
}
