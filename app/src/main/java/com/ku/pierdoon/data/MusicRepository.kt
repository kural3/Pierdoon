package com.ku.pierdoon.data

import android.content.Context
import android.provider.MediaStore
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf(MIN_DURATION_MS.toString())
        // MediaProvider rejects "COLLATE LOCALIZED", so locale-aware sorting happens below.
        val order = "${MediaStore.Audio.Media.TITLE} ASC"

        val songs = ArrayList<Song>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, args, order
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            songs.ensureCapacity(c.count)
            while (c.moveToNext()) {
                songs += Song(
                    id = c.getLong(idCol),
                    title = c.getString(titleCol).orEmpty(),
                    artist = c.getString(artistCol)?.takeUnless { it == UNKNOWN }.orEmpty(),
                    album = c.getString(albumCol).orEmpty(),
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durationCol),
                    dateAdded = c.getLong(addedCol),
                )
            }
        }
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        songs.sortWith { a, b -> collator.compare(a.title, b.title) }
        songs
    }

    private companion object {
        const val MIN_DURATION_MS = 10_000L
        const val UNKNOWN = "<unknown>"
    }
}
