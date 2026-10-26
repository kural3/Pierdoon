package com.ku.pierdoon.data

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

// What a Song is to MediaStore and Media3: Android only, so not in the shared Song.

val Song.uri: Uri
    get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

/** A cover picked in the app wins over the file's own. */
val Song.artworkUri: Uri
    get() = CoverStore.songUri(id) ?: ContentUris.withAppendedId(Song.ALBUM_ART_URI, albumId)

/** Each call is a new queue entry with its own [QUEUE_KEY], so a song can be queued twice. */
fun Song.toMediaItem(): MediaItem = toMediaItem(Song.newQueueKey())

/** The same queue entry ([queueKey]) with this song's current tags and cover. */
fun Song.toMediaItem(queueKey: Long): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(mediaMetadata(queueKey))
    .build()

/** Metadata for a queue entry; [queueKey] keeps an existing entry's identity on refresh. */
fun Song.mediaMetadata(queueKey: Long): MediaMetadata = MediaMetadata.Builder()
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

/** Metadata extra: stable identity of a queue entry while it moves around. */
const val QUEUE_KEY = "com.ku.pierdoon.queue_key"
const val ALBUM_ID = "com.ku.pierdoon.album_id"

val Song.Companion.ALBUM_ART_URI: Uri get() = albumArtUri
private val albumArtUri: Uri by lazy { "content://media/external/audio/albumart".toUri() }

fun Song.Companion.uriFor(mediaId: String): Uri? = mediaId.toLongOrNull()?.let {
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
}
