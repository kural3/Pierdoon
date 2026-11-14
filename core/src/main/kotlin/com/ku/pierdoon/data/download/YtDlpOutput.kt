package com.ku.pierdoon.data.download

import org.json.JSONObject

// What yt-dlp prints, read the same way by both apps' downloaders.

/** A download that failed for a [reason] the user can act on, with yt-dlp's own words in [detail]. */
class DownloadException(val reason: FailReason, val detail: String?) : Exception(detail)

/** yt-dlp picks the stream; "/bestaudio" covers sites that have only one kind (SoundCloud's mp3, for one). */
fun formatSelector(format: DownloadFormat) = when (format) {
    DownloadFormat.M4a -> "bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio"
    DownloadFormat.Opus -> "bestaudio[acodec=opus]/bestaudio"
}

fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

/** The smallest picture still sharp in a list row; sites without sizes get their last (usually biggest) one. */
fun JSONObject.thumbnail(): String? {
    val all = optJSONArray("thumbnails")?.let { a -> List(a.length()) { a.optJSONObject(it) }.filterNotNull() }.orEmpty()
        .filter { it.str("url") != null }
    val sized = all.filter { it.optInt("width") > 0 }.sortedBy { it.optInt("width") }
    return (sized.firstOrNull { it.optInt("width") >= 160 } ?: sized.lastOrNull() ?: all.lastOrNull())?.str("url")
        ?: str("thumbnail")
}

fun JSONObject.trackName(): TrackName {
    val artists = optJSONArray("artists")?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } }
    return trackName(
        title = str("title"),
        track = str("track"),
        artist = artists?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: str("artist") ?: str("creator"),
        uploader = str("uploader") ?: str("channel"),
    )
}

/** The reason yt-dlp gives, roughly sorted into what the user can do about it. */
fun classify(message: String?): FailReason {
    val m = message.orEmpty()
    return when {
        "Unsupported URL" in m || "is not a valid URL" in m -> FailReason.Unsupported
        // Before "unavailable": the bot check also says "Sign in".
        "not a bot" in m || "Sign in to confirm" in m -> FailReason.Bot
        "DRM" in m -> FailReason.Drm
        "Requested format is not available" in m -> FailReason.NoAudio
        "Unable to download" in m || "Errno" in m || "timed out" in m || "getaddrinfo" in m -> FailReason.Network
        "unavailable" in m || "Private video" in m || "removed" in m || "Sign in" in m -> FailReason.Unavailable
        else -> FailReason.Other
    }
}

fun errorLine(message: String?): String? =
    message?.lineSequence()?.lastOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()

