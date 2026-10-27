package com.ku.pierdoon.data

import com.ku.pierdoon.data.history.ListeningTracker
import com.ku.pierdoon.data.history.Play
import com.ku.pierdoon.data.history.PlayHistoryStore
import com.ku.pierdoon.platform.AppDirs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Every play, for the statistics and the playlist of the day. Null until the log is read. */
object PlayHistory {
    private val _plays = MutableStateFlow<List<Play>?>(null)
    val plays: StateFlow<List<Play>?> = _plays.asStateFlow()

    private val store by lazy { PlayHistoryStore(File(AppDirs.files, "plays.log")) { _plays.value = it } }

    /** Fed by the player; writes each play as it ends. */
    val tracker = ListeningTracker { store.add(it) }

    /** Reads the log; the first look at [plays] needs it. */
    fun load() {
        store
    }

    /** The current track counts as far as it got, and lands on disk before the app quits. */
    fun flush() {
        tracker.flush(System.nanoTime() / 1_000_000)
        store.awaitWrites()
    }

    fun clear() = store.clear()
}
