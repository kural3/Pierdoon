package com.ku.pierdoon.data.lyrics

import com.ku.pierdoon.platform.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Per-track sync correction for synced lyrics, set by hand when LRCLIB's timing is off.
 * Positive = lines show later. Kept apart from the lyrics cache, so clearing it keeps them.
 */
class LyricsOffsets {

    private val prefs = Prefs("lyrics_offsets")
    private val _version = MutableStateFlow(0)
    /** Bumped on every change, so readers of [get] know to read again. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun get(songId: Long): Long = prefs.getLong(songId.toString(), 0L)

    fun set(songId: Long, offsetMs: Long) {
        prefs.edit {
            if (offsetMs == 0L) remove(songId.toString()) else putLong(songId.toString(), offsetMs)
        }
        _version.update { it + 1 }
    }

    fun shift(songId: Long, deltaMs: Long) = set(songId, (get(songId) + deltaMs).coerceIn(-MAX_MS, MAX_MS))

    companion object {
        const val STEP_MS = 250L
        private const val MAX_MS = 30_000L
    }
}
