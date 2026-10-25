package com.ku.pierdoon.data

import org.json.JSONObject
import java.io.File
import com.ku.pierdoon.platform.AppDirs
import com.ku.pierdoon.platform.recoverAtomicWrite
import com.ku.pierdoon.platform.writeAtomically

/** Tags the user changed "in the app": null fields keep what the file says. */
data class TagOverride(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
)

/**
 * Track tags edited without touching the files, in one small JSON file. Applied by
 * [MusicRepository] on every load, so the whole app (and the notification) sees them.
 * Read on first use from a background thread; a few hundred bytes per edited track.
 */
class TagOverrides private constructor() {

    private val file = File(AppDirs.files, "tag_overrides.json")
    private var map: MutableMap<Long, TagOverride>? = null

    @Synchronized
    fun get(songId: Long): TagOverride? = loaded()[songId]

    @Synchronized
    fun has(songId: Long): Boolean = songId in loaded()

    @Synchronized
    fun set(songId: Long, override: TagOverride?) {
        val m = loaded()
        if (override == null || override == TagOverride()) m.remove(songId) else m[songId] = override
        write(m)
    }

    @Synchronized
    fun apply(song: Song): Song {
        val o = loaded()[song.id] ?: return song
        return song.copy(
            title = o.title ?: song.title,
            artist = o.artist ?: song.artist,
            album = o.album ?: song.album,
        )
    }

    private fun loaded(): MutableMap<Long, TagOverride> = map ?: read().also { map = it }

    private fun read(): MutableMap<Long, TagOverride> {
        val out = HashMap<Long, TagOverride>()
        if (!recoverAtomicWrite(file)) return out
        runCatching {
            val o = JSONObject(file.readText())
            o.keys().forEach { k ->
                val t = o.getJSONObject(k)
                out[k.toLong()] = TagOverride(t.optStringOrNull("title"), t.optStringOrNull("artist"), t.optStringOrNull("album"))
            }
        }
        return out
    }

    private fun write(m: Map<Long, TagOverride>) {
        val o = JSONObject()
        m.forEach { (id, t) ->
            o.put(id.toString(), JSONObject().apply {
                t.title?.let { put("title", it) }
                t.artist?.let { put("artist", it) }
                t.album?.let { put("album", it) }
            })
        }
        writeAtomically(file, o.toString())
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key)) getString(key) else null

    companion object {
        @Volatile private var instance: TagOverrides? = null

        fun get(): TagOverrides = instance ?: synchronized(this) {
            instance ?: TagOverrides().also { instance = it }
        }
    }
}
