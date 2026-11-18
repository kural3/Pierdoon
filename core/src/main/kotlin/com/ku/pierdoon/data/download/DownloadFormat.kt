package com.ku.pierdoon.data.download

/**
 * What the user asked for. All but [Mp3] are the site's own audio, as is; MP3 is converted from it
 * by FFmpeg. Nothing is ever converted *to* FLAC: lossy audio in a lossless file is only bigger.
 */
enum class DownloadFormat {
    /** AAC in .m4a: plays and takes tags everywhere. */
    M4a,

    /** Opus, YouTube's best audio; repackaged from WebM into .opus without re-encoding. */
    Opus,

    /** Whatever stream has the highest bitrate, in its own codec. */
    Best,

    /** Converted to MP3 at the highest VBR quality: plays on anything, no better than its source. */
    Mp3,

    /**
     * Real lossless where the source has it (archive.org, a Bandcamp purchase); a site with only
     * lossy streams (YouTube) gives its best one as is instead.
     */
    Flac;

    /** Made by converting the downloaded audio, which needs FFmpeg. */
    val converts: Boolean get() = this == Mp3
}

/** The bitrate caps offered, in kbit/s; 0 is none. */
val DOWNLOAD_KBPS_CAPS = listOf(0, 160, 128, 64)

/** How the format of a download is chosen. */
enum class DownloadMode {
    /** M4A or Opus, nothing else to set. */
    Simple,

    /** A codec (MP3 and FLAC among them) and a bitrate cap, set once in Settings. */
    Custom,

    /**
     * Picked on each download from the streams the link has. Downloads that start without anyone
     * to ask (a link sent from the phone to the computer, the radio) take the settings' format.
     */
    Ask,
}

/** One audio stream a link offers: yt-dlp's [id] for it, its codec, bitrate and size where known. */
data class AudioStream(val id: String, val codec: String?, val kbps: Int?, val bytes: Long?) {
    /** The source's own lossless audio, not a conversion. */
    val lossless: Boolean get() = codec in LOSSLESS_CODECS
}

/** Codec names (as [AudioStream.codec] gives them) that lose nothing. */
val LOSSLESS_CODECS = setOf("FLAC", "ALAC", "WAV", "PCM", "AIFF", "APE", "WavPack")
