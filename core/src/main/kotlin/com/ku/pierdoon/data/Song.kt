package com.ku.pierdoon.data

import androidx.compose.runtime.Immutable
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * A track of the library, the same on both platforms. What only one of them needs lives there:
 * MediaStore URIs and Media3 items in the Android app (SongMedia.kt), [path] and the id
 * helpers below on Windows, where tracks are plain files.
 */
@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    /** Seconds since epoch: when the track appeared in the library (MediaStore's date, or the file's creation time). */
    val dateAdded: Long = 0L,
    /** Absolute path of the audio file on Windows; empty on Android, where MediaStore gives a URI instead. */
    val path: String = "",
    /** Android: [album] is only the folder's name, which MediaStore gives tracks without an album tag. */
    val albumFromFolder: Boolean = false,
    /** The folder the file lies in: "Music/Rock" on Android, the absolute path on Windows. */
    val folder: String = "",
    /** Android: the file name's extension ("flac"), as MediaStore gives no path; on Windows it's read off [path]. */
    val fileType: String = "",
    /** Average kbit/s over the whole file (its size by its length); 0 where unknown. */
    val kbps: Int = 0,
) {
    val file: File get() = File(path)

    private val extension: String get() = fileType.ifEmpty { path.substringAfterLast('.', "") }.lowercase()

    /** "FLAC", "MP3", "AAC"… as people know the file's kind; null where it can't be told. */
    val formatName: String? get() = formatNameOf(extension)

    /** How good the file can sound, by its kind and bitrate: what its badge makes of it. */
    val audioTier: AudioTier get() = when {
        extension in LOSSLESS_EXTENSIONS -> AudioTier.Lossless
        kbps >= HIGH_KBPS -> AudioTier.High
        else -> AudioTier.Standard
    }

    companion object {
        /** Identity of a queue entry while it moves around; a song can be queued twice. */
        private val nextQueueKey = AtomicLong(System.nanoTime())
        fun newQueueKey(): Long = nextQueueKey.incrementAndGet()

        /** Windows: stable across launches and rescans, derived from the file's path, case-insensitively. */
        fun idFor(file: File): Long = stableHash(file.absolutePath.lowercase())

        /** Windows: tracks of one album share it; untagged files of a folder count as that folder's album. */
        fun albumIdFor(album: String, albumArtist: String, folder: String): Long =
            if (album.isBlank()) stableHash("dir:" + folder.lowercase())
            else stableHash("album:" + album.lowercase() + "\u0000" + albumArtist.lowercase())

        /** 64-bit FNV-1a, kept positive: ids are written to JSON and file names. */
        private fun stableHash(s: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (c in s) {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            return h and Long.MAX_VALUE
        }
    }
}

/** A file's sound, from best: lossless, lossy at a high bitrate (320 MP3), and the rest. */
enum class AudioTier { Lossless, High, Standard }

/** Lossy files from this average up count as [AudioTier.High]; a 320 MP3 with its cover clears it, a V0 one (~245) doesn't. */
private const val HIGH_KBPS = 280

private val LOSSLESS_EXTENSIONS = setOf("flac", "wav", "aif", "aiff", "alac", "ape", "wv", "dsf", "dff")

/** The kind of audio file by its extension; ALAC hides in .m4a, so that one reads as AAC. */
fun formatNameOf(extension: String): String? = when (val e = extension.lowercase()) {
    "" -> null
    "flac" -> "FLAC"
    "wav" -> "WAV"
    "aif", "aiff" -> "AIFF"
    "alac" -> "ALAC"
    "ape" -> "APE"
    "wv" -> "WV"
    "dsf", "dff" -> "DSD"
    "m4a", "aac", "mp4" -> "AAC"
    "opus", "webm" -> "OPUS"
    "ogg", "oga" -> "OGG"
    else -> e.uppercase()
}

/** kbit/s of a file of [bytes] that plays [durationMs]; 0 when either is unknown. */
fun averageKbps(bytes: Long, durationMs: Long): Int =
    if (bytes <= 0 || durationMs <= 0) 0 else (bytes * 8 / durationMs).toInt()
