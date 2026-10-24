package com.ku.pierdoon.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>,
)

@Immutable
data class UserData(
    val loaded: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val favorites: Set<Long> = emptySet(),
    /** Most recent first. */
    val recent: List<Long> = emptyList(),
)

/**
 * Playlists, favourites and listening history in one small JSON file.
 *
 * Deliberately not a database: a few kilobytes read once at start-up, then kept in memory.
 * Writes are coalesced (one write per burst of changes) and atomic (temp file + rename),
 * so skipping through ten tracks costs one tiny write, not ten.
 */
class UserLibrary(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "library.json")
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val _data = MutableStateFlow(UserData())
    val data: StateFlow<UserData> = _data.asStateFlow()
    private var pendingWrite: Job? = null

    init {
        scope.launch(io) { _data.value = read() }
    }

    fun createPlaylist(name: String, initialSongs: List<Long> = emptyList()): Long {
        val id = System.currentTimeMillis()
        mutate { it.copy(playlists = it.playlists + Playlist(id, name.trim(), initialSongs.distinct())) }
        return id
    }

    fun renamePlaylist(id: Long, name: String) = mutate { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deletePlaylist(id: Long) = mutate { d -> d.copy(playlists = d.playlists.filterNot { it.id == id }) }

    fun addToPlaylist(id: Long, songId: Long) = mutate { d ->
        d.copy(playlists = d.playlists.map {
            if (it.id == id && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it
        })
    }

    fun removeFromPlaylist(id: Long, songId: Long) = mutate { d ->
        d.copy(playlists = d.playlists.map {
            if (it.id == id) it.copy(songIds = it.songIds - songId) else it
        })
    }

    fun toggleFavorite(songId: Long) = mutate { d ->
        d.copy(favorites = if (songId in d.favorites) d.favorites - songId else d.favorites + songId)
    }

    fun recordPlayed(songId: Long) = mutate(delayMs = HISTORY_WRITE_DELAY_MS) { d ->
        if (d.recent.firstOrNull() == songId) d
        else d.copy(recent = (listOf(songId) + (d.recent - songId)).take(MAX_RECENT))
    }

    private fun mutate(delayMs: Long = WRITE_DELAY_MS, transform: (UserData) -> UserData) {
        val before = _data.value
        _data.update(transform)
        if (_data.value == before) return
        pendingWrite?.cancel()
        pendingWrite = scope.launch(io) {
            delay(delayMs)
            write(_data.value)
        }
    }

    private fun read(): UserData {
        if (!file.exists()) return UserData(loaded = true)
        return runCatching {
            val o = JSONObject(file.readText())
            val playlists = o.optJSONArray("playlists")?.let { arr ->
                List(arr.length()) { i ->
                    val p = arr.getJSONObject(i)
                    Playlist(p.getLong("id"), p.getString("name"), p.getJSONArray("songs").toLongList())
                }
            }.orEmpty()
            UserData(
                loaded = true,
                playlists = playlists,
                favorites = o.optJSONArray("favorites")?.toLongList().orEmpty().toSet(),
                recent = o.optJSONArray("recent")?.toLongList().orEmpty(),
            )
        }.getOrElse { UserData(loaded = true) }
    }

    private fun write(d: UserData) {
        val o = JSONObject()
            .put("version", 1)
            .put("playlists", JSONArray().apply {
                d.playlists.forEach { p ->
                    put(JSONObject().put("id", p.id).put("name", p.name).put("songs", JSONArray(p.songIds)))
                }
            })
            .put("favorites", JSONArray(d.favorites.toList()))
            .put("recent", JSONArray(d.recent))
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private fun JSONArray.toLongList(): List<Long> = List(length()) { getLong(it) }

    private companion object {
        const val MAX_RECENT = 30
        const val WRITE_DELAY_MS = 300L
        const val HISTORY_WRITE_DELAY_MS = 3_000L
    }
}
