package com.ku.pierdoon.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A folder with music in it: [path] relative to the storage root ("Music/Pier.doon"), [count] tracks. */
data class MusicFolder(val path: String, val count: Int)

class MusicRepository(private val context: Context) {

    private val overrides = TagOverrides.get(context)

    /** The library: every track, less those in the folders the user left out. */
    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = query(MUSIC, arrayOf(MIN_DURATION_MS.toString()), skip = SettingsStore.settings.value.excludedFolders)
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

    /** Every folder holding tracks, left-out ones included, by path. */
    suspend fun folders(): List<MusicFolder> = withContext(Dispatchers.IO) {
        val counts = HashMap<String, Int>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(FOLDER_COLUMN), MUSIC, arrayOf(MIN_DURATION_MS.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) counts.merge(folderOf(c.getString(0)), 1, Int::plus)
        }
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        counts.map { (path, n) -> MusicFolder(path, n) }.sortedWith { a, b -> collator.compare(a.path, b.path) }
    }

    private fun query(
        selection: String,
        args: Array<String>?,
        withOverrides: Boolean = true,
        skip: Set<String> = emptySet(),
    ): ArrayList<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            FOLDER_COLUMN,
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
            val folderCol = c.getColumnIndexOrThrow(FOLDER_COLUMN)
            songs.ensureCapacity(c.count)
            while (c.moveToNext()) {
                if (skip.isNotEmpty() && folderOf(c.getString(folderCol)) in skip) continue
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
        val MUSIC = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"

        /** Android 10 tells the folder outright; before that it's taken from the file's path. */
        @Suppress("DEPRECATION")
        val FOLDER_COLUMN: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.RELATIVE_PATH
            else MediaStore.Audio.Media.DATA

        /** "Music/Pier.doon" for either column: no trailing slash, relative to the storage root when it can be. */
        @Suppress("DEPRECATION")
        fun folderOf(raw: String?): String {
            if (raw == null) return ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return raw.trimEnd('/')
            val parent = File(raw).parent ?: return ""
            val root = Environment.getExternalStorageDirectory().path
            return if (parent.startsWith("$root/")) parent.removePrefix("$root/") else parent
        }
    }
}
