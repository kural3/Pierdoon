package com.ku.pierdoon.data.download

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YtDlpOutputTest {

    @Test
    fun failuresSortedByWhatTheUserCanDo() {
        assertEquals(FailReason.Bot, classify("ERROR: Sign in to confirm you're not a bot"))
        assertEquals(FailReason.Unavailable, classify("ERROR: Video unavailable"))
        assertEquals(FailReason.Unsupported, classify("ERROR: Unsupported URL: https://example.com"))
        assertEquals(FailReason.Network, classify("ERROR: Unable to download webpage: timed out"))
        assertEquals(FailReason.Other, classify(null))
    }

    @Test
    fun lastErrorLineWithoutItsPrefix() {
        assertEquals("second", errorLine("WARNING: x\nERROR: first\nERROR: second\n"))
        assertNull(errorLine("WARNING: only a warning"))
    }

    @Test
    fun smallestSharpThumbnail() {
        val info = JSONObject(
            """{"thumbnails":[{"url":"a","width":120},{"url":"b","width":320},{"url":"c","width":1280}],"thumbnail":"d"}""",
        )
        assertEquals("b", info.thumbnail())
        assertEquals("d", JSONObject("""{"thumbnail":"d"}""").thumbnail())
    }

    @Test
    fun blankAndNullFieldsAreAbsent() {
        val info = JSONObject("""{"title":"  ","artist":null,"track":"Song"}""")
        assertNull(info.str("title"))
        assertNull(info.str("artist"))
        assertEquals("Song", info.str("track"))
    }
}
