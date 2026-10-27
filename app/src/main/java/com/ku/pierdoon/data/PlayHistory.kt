package com.ku.pierdoon.data

import android.content.Context
import android.os.SystemClock
import com.ku.pierdoon.data.history.ListeningTracker
import com.ku.pierdoon.data.history.Play
import com.ku.pierdoon.data.history.PlayHistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Every play, for the statistics and the playlist of the day. Null until the log is read.
 * One per process, shared by the playback service (which writes) and the UI (which reads).
 */
class PlayHistory private constructor(context: Context) {
    private val _plays = MutableStateFlow<List<Play>?>(null)
    val plays: StateFlow<List<Play>?> = _plays.asStateFlow()

    private val store = PlayHistoryStore(File(context.filesDir, "plays.log")) { _plays.value = it }

    /** Fed by the player; writes each play as it ends. */
    val tracker = ListeningTracker { store.add(it) }

    /** The current track counts as far as it got, and lands on disk before the service goes. */
    fun flush() {
        tracker.flush(SystemClock.elapsedRealtime())
        store.awaitWrites()
    }

    fun clear() = store.clear()

    companion object {
        @Volatile private var instance: PlayHistory? = null

        fun get(context: Context): PlayHistory = instance ?: synchronized(this) {
            instance ?: PlayHistory(context.applicationContext).also { instance = it }
        }
    }
}
