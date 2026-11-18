package com.ku.pierdoon.data.download

import org.json.JSONObject

// What yt-dlp prints, read the same way by both apps' downloaders.

/** A download that failed for a [reason] the user can act on, with yt-dlp's own words in [detail]. */
class DownloadException(val reason: FailReason, val detail: String?) : Exception(detail)

/**
 * yt-dlp picks the stream; "/bestaudio" covers sites that have only one kind (SoundCloud's mp3, for
 * one). Under a [maxKbps] cap (0: none) the streams within it come first, then any, so a site with
 * nothing that small still gives something.
 */
fun formatSelector(format: DownloadFormat, maxKbps: Int = 0): String {
    val picks = when (format) {
        DownloadFormat.M4a -> listOf("bestaudio[ext=m4a]", "bestaudio[acodec^=mp4a]", "bestaudio")
        DownloadFormat.Opus -> listOf("bestaudio[acodec=opus]", "bestaudio")
        // Lossless where there is any; otherwise the best there is, never a conversion.
        DownloadFormat.Flac -> listOf("bestaudio[acodec=flac]", "bestaudio[ext=flac]", "bestaudio[acodec^=alac]", "bestaudio[ext=wav]", "bestaudio")
        DownloadFormat.Best, DownloadFormat.Mp3 -> listOf("bestaudio")
    }
    val capped = if (maxKbps > 0) picks.map { "$it[abr<=$maxKbps]" } else emptyList()
    return (capped + picks).joinToString("/")
}

/** The `-f` for [task]: the stream picked for it, or what its format and the [maxKbps] cap choose. */
fun selectorFor(task: DownloadTask, maxKbps: Int): String =
    task.streamId?.let { "$it/bestaudio" } ?: formatSelector(task.format, maxKbps)

/** yt-dlp's post-processing with FFmpeg: [format]'s conversion, or the audio as served in its own container. */
fun conversionArgs(format: DownloadFormat): List<String> = when (format) {
    DownloadFormat.Mp3 -> listOf("-x", "--audio-format", "mp3", "--audio-quality", "0")
    else -> listOf("-x", "--audio-format", "best")
}

/** What came down: the stream's codec ("Opus") and average bitrate, as far as the site says. */
fun JSONObject.audioQuality(): AudioQuality? {
    val codec = codecName(str("acodec"))
    val kbps = kbps()
    if (codec == null && kbps == null) return null
    return AudioQuality(codec, kbps)
}

/**
 * The audio-only streams in a link's `-J` output, lossless ones first, then by bitrate; empty for
 * a playlist or a site that lists none (it still has its one stream, which a download takes as "best").
 * A site that gives no bitrate (archive.org) has it worked out from the size and the length.
 */
fun JSONObject.audioStreams(): List<AudioStream> {
    val formats = optJSONArray("formats") ?: return emptyList()
    val seconds = optDouble("duration").takeIf { !it.isNaN() && it > 0 }
    return List(formats.length()) { formats.optJSONObject(it) }.filterNotNull()
        .filter { it.str("vcodec").let { v -> v == null || v == "none" } && it.str("acodec") != "none" && it.str("format_id") != null }
        // HLS and DRM copies of the same audio would only repeat it.
        .filter { it.str("protocol")?.startsWith("m3u8") != true && !it.optBoolean("has_drm") }
        .map { f ->
            val bytes = f.optLong("filesize").takeIf { it > 0 } ?: f.optLong("filesize_approx").takeIf { it > 0 }
            val kbps = f.kbps() ?: if (bytes != null && seconds != null) (bytes * 8 / seconds / 1000).toInt() else null
            AudioStream(f.str("format_id")!!, codecName(f.str("acodec")) ?: codecName(f.str("ext")), kbps, bytes)
        }
        .sortedWith(compareByDescending<AudioStream> { it.lossless }.thenByDescending { it.kbps ?: 0 })
}

private fun JSONObject.kbps(): Int? =
    (optDouble("abr").takeIf { !it.isNaN() && it > 0 } ?: optDouble("tbr").takeIf { !it.isNaN() && it > 0 })?.toInt()

/** "mp4a.40.2" as people know it: "AAC". */
private fun codecName(acodec: String?): String? = when (val c = acodec?.substringBefore('.')?.lowercase()?.takeIf { it != "none" }) {
    null -> null
    "mp4a", "aac" -> "AAC"
    "opus" -> "Opus"
    "vorbis" -> "Vorbis"
    "mp3" -> "MP3"
    "flac" -> "FLAC"
    "alac" -> "ALAC"
    "wav" -> "WAV"
    "aif", "aiff" -> "AIFF"
    else -> if (c.startsWith("pcm")) "PCM" else c.uppercase()
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

