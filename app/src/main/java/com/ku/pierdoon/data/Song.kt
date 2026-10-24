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

    val artworkUri: Uri
        get() = ContentUris.withAppendedId(ALBUM_ART_URI, albumId)

    /** Each call is a new queue entry with its own [QUEUE_KEY], so a song can be queued twice. */
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(Bundle().apply { putLong(QUEUE_KEY, nextQueueKey.incrementAndGet()) })
                .build()
        )
        .build()

    companion object {
        /** Metadata extra: stable identity of a queue entry while it moves around. */
        const val QUEUE_KEY = "com.ku.pierdoon.queue_key"
        private val nextQueueKey = AtomicLong(SystemClock.elapsedRealtimeNanos())

        val ALBUM_ART_URI: Uri = "content://media/external/audio/albumart".toUri()

        fun uriFor(mediaId: String): Uri? = mediaId.toLongOrNull()?.let {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
        }
    }
}
