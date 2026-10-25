package com.ku.pierdoon.data

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.compose.runtime.Immutable
import java.util.concurrent.atomic.AtomicLong
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    /** Seconds since epoch, as MediaStore reports it. */
    val dateAdded: Long = 0L,
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    /** A cover picked in the app wins over the file's own. */
    val artworkUri: Uri
        get() = CoverStore.songUri(id) ?: ContentUris.withAppendedId(ALBUM_ART_URI, albumId)

    /** Each call is a new queue entry with its own [QUEUE_KEY], so a song can be queued twice. */
    fun toMediaItem(): MediaItem = toMediaItem(nextQueueKey.incrementAndGet())

    /** The same queue entry ([queueKey]) with this song's current tags and cover. */
    fun toMediaItem(queueKey: Long): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(mediaMetadata(queueKey))
        .build()

    /** Metadata for a queue entry; [queueKey] keeps an existing entry's identity on refresh. */
    fun mediaMetadata(queueKey: Long): MediaMetadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artworkUri)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setExtras(Bundle().apply {
            putLong(QUEUE_KEY, queueKey)
            // The artwork URI may point at a custom cover file, so the album travels on its own.
            putLong(ALBUM_ID, albumId)
        })
        .build()

    companion object {
        /** Metadata extra: stable identity of a queue entry while it moves around. */
        const val QUEUE_KEY = "com.ku.pierdoon.queue_key"
        const val ALBUM_ID = "com.ku.pierdoon.album_id"
        // Lazy, so plain JVM tests can make songs without the Android runtime.
        private val nextQueueKey by lazy { AtomicLong(SystemClock.elapsedRealtimeNanos()) }

        val ALBUM_ART_URI: Uri by lazy { "content://media/external/audio/albumart".toUri() }

        fun uriFor(mediaId: String): Uri? = mediaId.toLongOrNull()?.let {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
        }
    }
}
