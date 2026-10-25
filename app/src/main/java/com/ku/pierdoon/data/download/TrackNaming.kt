package com.ku.pierdoon.data.download

/** Title and artist a downloaded track gets in its tags. */
data class TrackName(val title: String, val artist: String)

/**
 * Turns what a site knows about a video or track into tags.
 *
 * Music services (YouTube Music, SoundCloud, Bandcamp) give the track and artist outright.
 * Plain videos only have a title, usually "Artist - Title (Official Video)", and the channel.
 */
internal fun trackName(
    title: String?,
    track: String?,
    artist: String?,
    uploader: String?,
): TrackName {
    // Auto-generated YouTube channels are named "<Artist> - Topic".
    val channel = uploader?.removeSuffix(" - Topic")?.trim()?.takeIf { it.isNotEmpty() }
    if (!track.isNullOrBlank()) {
        return TrackName(track.trim(), artist?.trim()?.takeIf { it.isNotEmpty() } ?: channel.orEmpty())
    }
    val clean = cleanTitle(title.orEmpty())
    if (artist.isNullOrBlank()) {
        val dash = DASHES.find(clean)
        if (dash != null && dash.range.first > 0 && dash.range.last < clean.lastIndex) {
            return TrackName(clean.substring(dash.range.last + 1).trim(), clean.substring(0, dash.range.first).trim())
        }
    }
    return TrackName(clean, artist?.trim()?.takeIf { it.isNotEmpty() } ?: channel.orEmpty())
}

/** Drops "(Official Video)", "[Lyrics]", "| 4K" and the like: video labels, not part of the song's name. */
internal fun cleanTitle(title: String): String =
    title.replace(NOISE, "").replace(Regex("""\s{2,}"""), " ").trim().ifEmpty { title.trim() }

/** The first web link in shared text ("Check this out https://youtu.be/…"). */
fun findUrl(text: String): String? = URL.find(text)?.value?.trimEnd('.', ',', ')', '!', '?')

/** A file name every filesystem accepts, short enough to leave room for "(1)" and the extension. */
internal fun safeFileName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trimStart('.').take(120).ifBlank { "track" }

private val DASHES = Regex("""\s[-–—]\s""")
private val NOISE = Regex(
    """\s*[(\[](?:[^)\]]*\b(?:official|lyrics?|lyric video|music video|video|audio|visualizer|clip|mv|hd|4k|hq)\b[^)\]]*)[)\]]""" +
        """|\s*\|.*$""",
    RegexOption.IGNORE_CASE,
)
private val URL = Regex("""https?://\S+""")
