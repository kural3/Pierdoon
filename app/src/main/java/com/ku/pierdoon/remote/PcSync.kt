package com.ku.pierdoon.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ku.pierdoon.data.LibrarySnapshot
import com.ku.pierdoon.data.LibrarySync
import com.ku.pierdoon.data.PlayHistory
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.SyncBase
import com.ku.pierdoon.data.UserLibrary
import com.ku.pierdoon.data.recoverAtomicWrite
import com.ku.pierdoon.data.writeAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    /** Done: [matched] tracks are on both sides. */
    data class Done(val matched: Int) : SyncStatus
    data object Failed : SyncStatus
    /** The computer's Pier.doon is older than syncing. */
    data object Outdated : SyncStatus
}

/**
 * Favourites, playlists, smart playlists and plays, kept the same on the phone and the paired
 * computer. The phone does the merging ([LibrarySync]) and remembers what both sides agreed on
 * last time, per computer, so a removal on one side reaches the other instead of coming back.
 *
 * Plays go both ways, each once; after the first sync only the last days' are exchanged.
 */
object PcSync {
    private lateinit var prefs: SharedPreferences
    private lateinit var dir: File
    private val lock = Mutex()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(0L)
    /** When the last sync with this computer finished (epoch ms), or 0. */
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    /** The phone's library once it's loaded (null before): set by the app's view model. */
    @Volatile var library: () -> List<Song>? = { null }

    private val _auto = MutableStateFlow(true)
    /** Sync on its own when the phone connects to the computer, at most once an hour. */
    val auto: StateFlow<Boolean> = _auto.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("pc_sync", Context.MODE_PRIVATE)
        dir = context.applicationContext.filesDir
        _auto.value = prefs.getBoolean(K_AUTO, true)
        _lastSyncAt.value = if (prefs.getString(K_PC, null) == PcRemote.pairedId()) prefs.getLong(K_LAST, 0L) else 0L
    }

    fun setAuto(on: Boolean) {
        _auto.value = on
        prefs.edit { putBoolean(K_AUTO, on) }
    }

    /** The phone just connected: sync if that's wanted and the last one was long enough ago. */
    suspend fun onConnected(context: Context) {
        if (!_auto.value) return
        if (System.currentTimeMillis() - _lastSyncAt.value < AUTO_INTERVAL_MS) return
        // A computer that just started answers "not yet" while it reads its library: try again a little later.
        repeat(AUTO_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                kotlinx.coroutines.delay(AUTO_RETRY_MS)
                if (PcRemote.connection.value != PcRemote.Connection.Connected) return
            }
            sync(context, quiet = true)
            if (_status.value != SyncStatus.Failed) return
        }
    }

    /**
     * Syncs now.
     * [quiet]: an automatic one, which doesn't report an old computer as a failure.
     */
    suspend fun sync(context: Context, quiet: Boolean = false) = lock.withLock {
        withContext(Dispatchers.IO) {
            val pc = PcRemote.pairedId() ?: return@withContext
            val api = PcRemote.apiNow()
            if (api == null || api < PcRemote.SYNC_API) {
                _status.value = if (api == null) SyncStatus.Failed else if (quiet) SyncStatus.Idle else SyncStatus.Outdated
                return@withContext
            }
            val songs = library() ?: return@withContext
            _status.value = SyncStatus.Running
            val history = PlayHistory.get(context)
            val user = UserLibrary.get(context.filesDir)
            val plays = withTimeoutOrNull(5_000) { history.plays.first { it != null } }
            val userData = withTimeoutOrNull(5_000) { user.data.first { it.loaded } }
            if (plays == null || userData == null) {
                _status.value = SyncStatus.Failed
                return@withContext
            }
            val samePc = prefs.getString(K_PC, null) == pc
            val base = if (samePc) readBase() else null
            // After the first time only the latest plays travel; a day's overlap for the ones in flight.
            val since = if (samePc && base != null) (prefs.getLong(K_LAST, 0L) - PLAYS_OVERLAP_MS).coerceAtLeast(0L) else 0L
            val remote = PcRemote.fetchSync(since)?.let { runCatching { LibrarySnapshot.fromJson(it) }.getOrNull() }
            if (remote == null) {
                _status.value = SyncStatus.Failed
                return@withContext
            }
            val local = LibrarySnapshot.of(songs, userData, plays, allTracks = true).let { s -> s.copy(plays = s.plays.filter { it.atMs >= since }) }
            val outcome = LibrarySync.merge(local, remote, base)
            if (!outcome.remote.isEmpty && !PcRemote.postSync(outcome.remote.toJson())) {
                _status.value = SyncStatus.Failed
                return@withContext
            }
            user.apply(outcome.local)
            history.merge(outcome.local.plays)
            writeAtomically(File(dir, BASE_FILE), outcome.base.toJson().toString())
            val now = System.currentTimeMillis()
            prefs.edit {
                putString(K_PC, pc)
                putLong(K_LAST, now)
            }
            _lastSyncAt.value = now
            _status.value = SyncStatus.Done(outcome.matched)
        }
    }

    /** Forgets the agreed state, as when pairing with another computer. */
    fun reset() {
        prefs.edit { remove(K_PC); remove(K_LAST) }
        File(dir, BASE_FILE).delete()
        _lastSyncAt.value = 0L
        _status.value = SyncStatus.Idle
    }

    private fun readBase(): SyncBase? =
        File(dir, BASE_FILE).takeIf(::recoverAtomicWrite)?.let { runCatching { SyncBase.fromJson(JSONObject(it.readText())) }.getOrNull() }

    private const val K_AUTO = "auto"
    private const val K_PC = "pc"
    private const val K_LAST = "last"
    private const val BASE_FILE = "sync-base.json"
    private const val AUTO_INTERVAL_MS = 60 * 60 * 1000L
    private const val AUTO_ATTEMPTS = 6
    private const val AUTO_RETRY_MS = 10_000L
    private const val PLAYS_OVERLAP_MS = 24 * 60 * 60 * 1000L
}
