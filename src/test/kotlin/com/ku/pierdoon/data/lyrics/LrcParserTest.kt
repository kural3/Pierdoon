package com.ku.pierdoon.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun fractionsOfOneTwoAndThreeDigits() {
        val lines = LrcParser.parse("[00:01.5]a\n[00:02.34]b\n[00:03.123]c")
        assertEquals(listOf(1_500L, 2_340L, 3_123L), lines.map { it.timeMs })
        assertEquals(listOf("a", "b", "c"), lines.map { it.text })
    }

    @Test
    fun noFractionAndColonSeparator() {
        val lines = LrcParser.parse("[1:05]a\n[00:10:50]b")
        assertEquals(listOf(65_000L, 10_500L).sorted(), lines.map { it.timeMs })
    }

    @Test
    fun severalStampsRepeatTheLineInTimeOrder() {
        val lines = LrcParser.parse("[00:30.00][00:10.00]chorus\n[00:20.00]verse")
        assertEquals(listOf(10_000L, 20_000L, 30_000L), lines.map { it.timeMs })
        assertEquals(listOf("chorus", "verse", "chorus"), lines.map { it.text })
    }

    @Test
    fun positiveOffsetShowsLinesEarlierButNotBeforeZero() {
        val lines = LrcParser.parse("[offset:+500]\n[00:00.20]a\n[00:02.00]b")
        assertEquals(listOf(0L, 1_500L), lines.map { it.timeMs })
    }

    @Test
    fun negativeOffsetShowsLinesLater() {
        val lines = LrcParser.parse("[offset:-250]\n[00:01.00]a")
        assertEquals(1_250L, lines.single().timeMs)
    }

    @Test
    fun metadataTagsAndPlainTextAreSkipped() {
        val lines = LrcParser.parse("[ar:Artist]\n[ti:Title]\nno stamp\n[00:01.00]  text  ")
        assertEquals(1, lines.size)
        assertEquals("text", lines.single().text)
    }

    @Test
    fun emptyTextKeepsTheBreak() {
        val lines = LrcParser.parse("[00:01.00]a\n[00:02.00]")
        assertEquals("", lines[1].text)
    }

    @Test
    fun indexAt() {
        val synced = Lyrics.Synced(LrcParser.parse("[00:01.00]a\n[00:02.00]b\n[00:03.00]c"))
        assertEquals(-1, synced.indexAt(999))
        assertEquals(0, synced.indexAt(1_000))
        assertEquals(0, synced.indexAt(1_999))
        assertEquals(1, synced.indexAt(2_000))
        assertEquals(2, synced.indexAt(60_000))
        assertTrue(Lyrics.Synced(emptyList()).indexAt(5_000) == -1)
    }
}
