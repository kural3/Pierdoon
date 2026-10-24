package com.ku.pierdoon.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.compose.runtime.Immutable

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

    companion object {
        val ALBUM_ART_URI: Uri = "content://media/external/audio/albumart".toUri()

        fun uriFor(mediaId: String): Uri? = mediaId.toLongOrNull()?.let {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
        }
    }
}
