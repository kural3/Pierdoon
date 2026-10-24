package com.ku.pierdoon.data

import android.content.Context
import android.provider.MediaStore
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    private val overrides = TagOverrides.get(context)

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val songs = query(selection, arrayOf(MIN_DURATION_MS.toString()))
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        songs.sortWith { a, b -> collator.compare(a.title, b.title) }
        songs
    }

    /** Songs that still exist among [ids], in no particular order. Call off the main thread. */
    fun loadSongs(ids: Collection<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        // Inlined numbers, not "?" args: queues can outgrow SQLite's bound-parameter limit.
        return query("${MediaStore.Audio.Media._ID} IN (${ids.distinct().joinToString(",")})", null)
    }

    /** The track as its file tags it, without the user's in-app edits. */
    suspend fun fileTags(id: Long): Song? = withContext(Dispatchers.IO) {
        query("${MediaStore.Audio.Media._ID} = $id", null, withOverrides = false).firstOrNull()
    }

    private fun query(selection: String, args: Array<String>?, withOverrides: Boolean = true): ArrayList<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        // MediaProvider rejects "COLLATE LOCALIZED", so loadSongs() sorts by locale itself.
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
                val song = Song(
                    id = c.getLong(idCol),
                    title = c.getString(titleCol).orEmpty(),
                    artist = c.getString(artistCol)?.takeUnless { it == UNKNOWN }.orEmpty(),
                    album = c.getString(albumCol).orEmpty(),
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durationCol),
                    dateAdded = c.getLong(addedCol),
                )
                songs += if (withOverrides) overrides.apply(song) else song
            }
        }
        return songs
    }

    private companion object {
        const val MIN_DURATION_MS = 10_000L
        const val UNKNOWN = "<unknown>"
    }
}
