package com.ku.pierdoon.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeniusTest {

    @Test
    fun parsesBlocksWithoutHeadersSectionsOrTags() {
        val html = """
            <div data-lyrics-container="true" class="x"><div data-exclude-from-selection="true" class="h">
            <button><span>299 Contributors</span></button><div class="d"><span>Translations</span></div></div>
            [Verse 1]<br/>When you were here before<br/><a href="/1"><span>Couldn&#x27;t look you in the eye</span></a><br/><br/>[Chorus]<br/>
            <i>But I&#39;m a creep</i></div>
            <div data-exclude-from-selection="true"><div>ad</div></div>
            <div data-lyrics-container="true">I&#x27;m a weirdo &amp; more</div>
        """.trimIndent()
        assertEquals(
            listOf("When you were here before", "Couldn't look you in the eye", "", "But I'm a creep", "I'm a weirdo & more"),
            Genius.parse(html),
        )
    }

    @Test
    fun noBlocksNoLyrics() {
        assertTrue(Genius.parse("<html><body>Not here</body></html>").isEmpty())
    }

    @Test
    fun matchesOnlyTheSameSong() {
        val hit = Genius.Hit("Creep (Remastered)", "Radiohead", "u")
        assertTrue(Genius.matches(hit, "Creep", "Radiohead"))
        assertFalse(Genius.matches(hit, "Karma Police", "Radiohead"))
        assertFalse(Genius.matches(Genius.Hit("Creep", "TLC", "u"), "Creep", "Radiohead"))
    }
}
