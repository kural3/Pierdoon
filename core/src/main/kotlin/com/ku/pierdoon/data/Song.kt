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
) {
    val file: File get() = File(path)

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
