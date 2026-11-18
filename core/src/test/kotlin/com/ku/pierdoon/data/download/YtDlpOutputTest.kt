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

    @Test
    fun losslessStreamsComeFirstWithBitrateFromTheirSize() {
        // archive.org: no bitrate, no vcodec, only a size and the length.
        val info = JSONObject(
            """{"duration": 100, "formats": [
                {"format_id": "0", "ext": "mp3", "acodec": "mp3", "filesize": 2000000},
                {"format_id": "1", "ext": "flac", "acodec": "flac", "filesize": 10000000}
            ]}"""
        )
        val streams = info.audioStreams()
        assertEquals(listOf("1", "0"), streams.map { it.id })
        assertEquals(true, streams[0].lossless)
        assertEquals(800, streams[0].kbps)
        assertEquals(160, streams[1].kbps)
    }

    @Test
    fun flacIsNeverAConversion() {
        assertEquals(false, DownloadFormat.Flac.converts)
        assertEquals(listOf("-x", "--audio-format", "best"), conversionArgs(DownloadFormat.Flac))
        assertEquals(true, formatSelector(DownloadFormat.Flac).startsWith("bestaudio[acodec=flac]"))
        assertEquals(true, formatSelector(DownloadFormat.Flac).endsWith("/bestaudio"))
    }
}
