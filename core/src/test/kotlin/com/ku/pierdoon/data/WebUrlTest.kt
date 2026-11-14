package com.ku.pierdoon.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.MalformedURLException

class WebUrlTest {
    @Test
    fun plainLinkUnchanged() {
        val link = "https://lrclib.net/api/search?track_name=a%20b&artist_name=c+d"
        assertEquals(link, webUrl(link).toString())
    }

    @Test
    fun spacesPipesAndCyrillicAreEscaped() {
        assertEquals(
            "https://example.com/a%20b%7Cc?q=%D0%B9",
            webUrl("https://example.com/a b|c?q=й").toString(),
        )
    }

    @Test
    fun encodedPercentKept() {
        assertEquals("https://example.com/x%2Fy%20z", webUrl("https://example.com/x%2Fy z").toString())
    }

    @Test(expected = MalformedURLException::class)
    fun relativeRejected() {
        webUrl("/api/search")
    }

    @Test(expected = MalformedURLException::class)
    fun unknownSchemeRejected() {
        webUrl("nosuchscheme://x")
    }
}
