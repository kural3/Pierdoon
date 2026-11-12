package com.ku.pierdoon.data

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
    val smartPlaylists: List<SmartPlaylist> = emptyList(),
)

/**
 * Playlists, favourites and listening history in one small JSON file.
 *
 * Deliberately not a database: a few kilobytes read once at start-up, then kept in memory.
 * Writes are coalesced (one write per burst of changes) and atomic (temp file + rename),
 * so skipping through ten tracks costs one tiny write, not ten.
 *
 * One instance per process, shared by the UI and the playback service. It owns its scope,
 * so a pending write outlives the screen that made the change. Each app opens it with its own
 * folder (`UserLibrary.get(context)` on Android, `UserLibrary.get()` on Windows).
 */
class UserLibrary private constructor(private val file: File) {

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

    fun putSmartPlaylist(playlist: SmartPlaylist) = mutate { d ->
        val p = playlist.copy(name = playlist.name.trim())
        d.copy(
            smartPlaylists = if (d.smartPlaylists.any { it.id == p.id }) d.smartPlaylists.map { if (it.id == p.id) p else it }
            else d.smartPlaylists + p
        )
    }

    fun deleteSmartPlaylist(id: Long) = mutate { d -> d.copy(smartPlaylists = d.smartPlaylists.filterNot { it.id == id }) }

    /**
     * Changes brought by a sync with the other device or a restored backup, in one write:
     * favourites added and removed, playlists put in whole (by id; a new one goes at the end) or
     * dropped, smart playlists the same.
     */
    fun apply(changes: LibraryChanges) = mutate { d ->
        var playlists = d.playlists.filterNot { it.id in changes.playlistsDelete }
        for (p in changes.playlistsPut) {
            playlists = if (playlists.any { it.id == p.id }) {
                playlists.map { if (it.id == p.id) it.copy(name = p.name, songIds = p.songIds.distinct()) else it }
            } else {
                playlists + Playlist(p.id, p.name, p.songIds.distinct())
            }
        }
        var smart = d.smartPlaylists.filterNot { it.id in changes.smartDelete }
        for (p in changes.smartPut) smart = if (smart.any { it.id == p.id }) smart.map { if (it.id == p.id) p else it } else smart + p
        d.copy(
            favorites = d.favorites - changes.favoritesRemove + changes.favoritesAdd,
            playlists = playlists,
            smartPlaylists = smart,
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
        val at = (System.nanoTime() / 1_000_000) + delayMs
        if (pendingWrite?.isActive == true && writeAtMs <= at) return
        pendingWrite?.cancel()
        writeAtMs = at
        pendingWrite = scope.launch(io) {
            delay(delayMs)
            write(_data.value)
        }
    }

    private fun read(): UserData {
        if (!recoverAtomicWrite(file)) return UserData(loaded = true)
        val text = runCatching { file.readText() }.getOrNull()
        val parsed = text?.let(::parseUserData)
        // Whatever couldn't be read would be gone with the next save (one played track is
        // enough), so the file as it was is put aside first, for the user or a fix to bring back.
        if (parsed == null || parsed.damaged) keepDamaged(file)
        return parsed?.data ?: UserData(loaded = true)
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
            .put("smart", JSONArray(d.smartPlaylists.map { it.toJson() }))
        writeAtomically(file, o.toString())
    }

    companion object {
        @Volatile private var instance: UserLibrary? = null

        /** What a library file holds, and whether any of it had to be skipped. */
        internal class Parsed(val data: UserData, val damaged: Boolean)

        /**
         * Reads a library file. A broken playlist or id is skipped rather than costing
         * everything else; null when the text isn't a library at all.
         */
        internal fun parseUserData(text: String): Parsed? {
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
            var damaged = false
            fun JSONArray.longs(): List<Long> = (0 until length()).mapNotNull { i ->
                val v = opt(i)
                (v as? Number)?.toLong() ?: (v as? String)?.toLongOrNull() ?: run { damaged = true; null }
            }
            fun <T> JSONObject.items(key: String, item: (JSONObject) -> T?): List<T> {
                val arr = opt(key) ?: return emptyList()
                if (arr !is JSONArray) { damaged = true; return emptyList() }
                return (0 until arr.length()).mapNotNull { i ->
                    (arr.opt(i) as? JSONObject)?.let { runCatching { item(it) }.getOrNull() } ?: run { damaged = true; null }
                }
            }
            fun JSONObject.ids(key: String): List<Long> {
                val arr = opt(key) ?: return emptyList()
                if (arr !is JSONArray) { damaged = true; return emptyList() }
                return arr.longs()
            }
            val data = UserData(
                loaded = true,
                playlists = o.items("playlists") { p ->
                    Playlist(
                        id = p.getLong("id"),
                        name = p.getString("name"),
                        // Kept, empty, if its tracks can't be read; the file is put aside with them.
                        songIds = p.optJSONArray("songs")?.longs() ?: run { damaged = true; emptyList() },
                        shape = p.optString("shape").takeIf { it.isNotEmpty() },
                    )
                },
                favorites = o.ids("favorites").toSet(),
                recent = o.ids("recent"),
                smartPlaylists = o.items("smart", SmartPlaylist::fromJson),
            )
            return Parsed(data, damaged)
        }

        /** Copies [file] next to itself under a name no save touches; the newest few are kept. */
        internal fun keepDamaged(file: File) {
            runCatching {
                val dir = file.parentFile ?: return
                val prefix = file.nameWithoutExtension + "-damaged-"
                file.copyTo(File(dir, "$prefix${System.currentTimeMillis()}.json"), overwrite = true)
                dir.listFiles { f -> f.name.startsWith(prefix) }.orEmpty()
                    .sortedByDescending { it.name }
                    .drop(KEEP_DAMAGED)
                    .forEach { it.delete() }
            }
        }

        private const val KEEP_DAMAGED = 3

        /** The one instance, kept in `library.json` in [dir] (the folder of whoever asks first). */
        fun get(dir: File): UserLibrary = instance ?: synchronized(this) {
            instance ?: UserLibrary(File(dir, "library.json")).also { instance = it }
        }

        private const val MAX_RECENT = 30
        private const val WRITE_DELAY_MS = 300L
        private const val HISTORY_WRITE_DELAY_MS = 3_000L
    }
}
