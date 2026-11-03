package com.ku.pierdoon.radio

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.download.trackName
import org.json.JSONObject

/**
 * A track the radio found online and doesn't have yet: a YouTube video, named the way a
 * download of it would be.
 */
@Immutable
data class RadioTrack(val videoId: String, val title: String, val artist: String, val durationSec: Int?) {
    val url: String get() = "https://www.youtube.com/watch?v=$videoId"

    /**
     * Its id in the queue while it plays. Negative, so it never clashes with a library track's,
     * and listening stats (which count only the library's) leave it out.
     */
    val songId: Long get() = idFor(videoId)

    companion object {
        fun idFor(videoId: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (c in "yt:$videoId") {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            return -(h and Long.MAX_VALUE) - 1
        }

        fun isRadio(songId: Long): Boolean = songId < -1
    }
}

/** YouTube's own radio: for any video, a mix of what its listeners also play. */
object YouTubeMix {
    /** A track's mix: the video itself first, then about fifty that go with it. */
    fun url(videoId: String): String = "https://www.youtube.com/watch?v=$videoId&list=RD$videoId"

    /** What yt-dlp's `--flat-playlist -J` says about a mix or a search, as tracks. */
    fun parse(json: String): List<RadioTrack> {
        val entries = runCatching { JSONObject(json).optJSONArray("entries") }.getOrNull() ?: return emptyList()
        return List(entries.length()) { entries.optJSONObject(it) }.mapNotNull { e ->
            val id = e?.optString("id")?.takeIf { it.length == VIDEO_ID_LENGTH } ?: return@mapNotNull null
            val duration = e.optDouble("duration").takeIf { !it.isNaN() }?.toInt()
            val name = trackName(
                title = e.optString("title").takeIf { it.isNotBlank() },
                track = null,
                artist = null,
                uploader = (e.optString("channel").takeIf { it.isNotBlank() } ?: e.optString("uploader")).takeIf { it.isNotBlank() },
            )
            if (name.title.isBlank()) null else RadioTrack(id, name.title, name.artist, duration)
        }
    }

    private const val VIDEO_ID_LENGTH = 11
}
