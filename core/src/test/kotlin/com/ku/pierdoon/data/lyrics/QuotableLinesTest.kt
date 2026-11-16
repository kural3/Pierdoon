package com.ku.pierdoon.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class QuotableLinesTest {

    @Test
    fun keepsLinesWithWordsOnly() {
        val lyrics = Lyrics.Plain(
            listOf(
                "[Chorus]",
                "  Мы танцуем до утра под этот свет  ",
                "",
                "(oh-oh-oh-oh)",
                "♪ ♪ ♪",
                "La la",
                "Мы танцуем до утра под этот свет",
                "x".repeat(120),
                "I keep the radio on all night long",
            )
        )
        assertEquals(
            listOf("Мы танцуем до утра под этот свет", "I keep the radio on all night long"),
            lyrics.quotableLines(),
        )
    }

    @Test
    fun syncedLinesCountTooAndTheRestHaveNone() {
        val synced = Lyrics.Synced(listOf(LyricLine(0, ""), LyricLine(1_000, "Somewhere over the rainbow")))
        assertEquals(listOf("Somewhere over the rainbow"), synced.quotableLines())
        assertEquals(emptyList<String>(), Lyrics.Instrumental.quotableLines())
        assertEquals(emptyList<String>(), Lyrics.NotFound.quotableLines())
    }
}
