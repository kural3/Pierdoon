package com.ku.pierdoon.data

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    /** Cover silhouette by name ([com.ku.pierdoon.ui.library.PlaylistShapes]); null = default. */
    val shape: String? = null,
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
 *
 * One instance per process, shared by the UI and the playback service. It owns its scope,
 * so a pending write outlives the screen that made the change.
 */
class UserLibrary private constructor(context: Context) {

    private val file = File(context.filesDir, "library.json")
    private val scope = CoroutineScope(SupervisorJob())
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val _data = MutableStateFlow(UserData())
    val data: StateFlow<UserData> = _data.asStateFlow()

    private val lock = Any()
    private var pendingWrite: Job? = null
    private var writeAtMs = 0L
    /** Changes made before the file was read; replayed on top of it. Null once loaded. */
    private var beforeLoad: MutableList<(UserData) -> UserData>? = ArrayList()

    init {
        scope.launch(io) {
            val disk = read()
            synchronized(lock) {
                val ops = beforeLoad.orEmpty()
                beforeLoad = null
                _data.value = ops.fold(disk) { d, op -> op(d) }
                if (ops.isNotEmpty()) scheduleWrite(WRITE_DELAY_MS)
            }
        }
    }

    /** Writes pending changes now instead of after the coalescing delay. */
    fun flush() {
        synchronized(lock) {
            if (pendingWrite?.isActive != true) return
            pendingWrite?.cancel()
            pendingWrite = scope.launch(io) { write(_data.value) }
        }
    }

    fun createPlaylist(name: String, initialSongs: List<Long> = emptyList()): Long {
        val id = System.currentTimeMillis()
        mutate { it.copy(playlists = it.playlists + Playlist(id, name.trim(), initialSongs.distinct())) }
        return id
    }

    fun renamePlaylist(id: Long, name: String) = mutate { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun setPlaylistShape(id: Long, shape: String?) = mutate { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(shape = shape) else it })
    }

    fun deletePlaylist(id: Long) = mutate { d -> d.copy(playlists = d.playlists.filterNot { it.id == id }) }

    fun addToPlaylist(id: Long, songId: Long) = addToPlaylist(id, listOf(songId))

    /** Appends [songIds] in order, skipping ones already there. */
    fun addToPlaylist(id: Long, songIds: List<Long>) = mutate { d ->
        d.copy(playlists = d.playlists.map { p ->
            if (p.id == id) p.copy(songIds = p.songIds + songIds.filter { it !in p.songIds }.distinct()) else p
        })
    }

    fun removeFromPlaylist(id: Long, songId: Long) = removeFromPlaylist(id, setOf(songId))

    fun removeFromPlaylist(id: Long, songIds: Set<Long>) = mutate { d ->
        d.copy(playlists = d.playlists.map { p ->
            if (p.id == id) p.copy(songIds = p.songIds.filterNot { it in songIds }) else p
        })
    }

    fun addFavorites(songIds: List<Long>) = mutate { d ->
        d.copy(favorites = d.favorites + songIds.filter { it !in d.favorites }.distinct())
    }

    fun toggleFavorite(songId: Long) = mutate { d ->
        d.copy(favorites = if (songId in d.favorites) d.favorites - songId else d.favorites + songId)
    }

    /** The file was deleted: drop it from playlists, favourites and history. */
    fun forgetSong(songId: Long) = mutate { d ->
        d.copy(
            playlists = d.playlists.map { if (songId in it.songIds) it.copy(songIds = it.songIds - songId) else it },
            favorites = d.favorites - songId,
            recent = d.recent - songId,
        )
    }

    fun recordPlayed(songId: Long) = mutate(delayMs = HISTORY_WRITE_DELAY_MS) { d ->
        if (d.recent.firstOrNull() == songId) d
        else d.copy(recent = (listOf(songId) + (d.recent - songId)).take(MAX_RECENT))
    }

    private fun mutate(delayMs: Long = WRITE_DELAY_MS, transform: (UserData) -> UserData) {
        synchronized(lock) {
            val before = _data.value
            _data.update(transform)
            // Not loaded yet: show the change now, but never write until it is merged with the file.
            beforeLoad?.let { it += transform; return }
            if (_data.value != before) scheduleWrite(delayMs)
        }
    }

    /** A slower change (history) never postpones a write that is already due sooner. */
    private fun scheduleWrite(delayMs: Long) {
        val at = SystemClock.uptimeMillis() + delayMs
        if (pendingWrite?.isActive == true && writeAtMs <= at) return
        pendingWrite?.cancel()
        writeAtMs = at
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
                    Playlist(
                        id = p.getLong("id"),
                        name = p.getString("name"),
                        songIds = p.getJSONArray("songs").toLongList(),
                        shape = p.optString("shape").takeIf { it.isNotEmpty() },
                    )
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
                    put(
                        JSONObject().put("id", p.id).put("name", p.name).put("songs", JSONArray(p.songIds))
                            .apply { p.shape?.let { put("shape", it) } }
                    )
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

    companion object {
        @Volatile private var instance: UserLibrary? = null

        fun get(context: Context): UserLibrary = instance ?: synchronized(this) {
            instance ?: UserLibrary(context.applicationContext).also { instance = it }
        }

        private const val MAX_RECENT = 30
        private const val WRITE_DELAY_MS = 300L
        private const val HISTORY_WRITE_DELAY_MS = 3_000L
    }
}
