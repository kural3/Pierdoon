package com.ku.pierdoon

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.ui.elapsedRealtime

sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState
    data class Ready(val lyrics: Lyrics) : LyricsState
    data object Error : LyricsState
    data object WifiOnly : LyricsState
}

/** Position sampled at [atRealtimeMs]; lets the UI extrapolate instead of polling faster. */
@Immutable
data class PositionSnapshot(
    val positionMs: Long = 0L,
    val atRealtimeMs: Long = 0L,
    val playing: Boolean = false,
    /** How much faster than recorded the track plays: a second of it passes in 1 / speed seconds. */
    val speed: Float = 1f,
) {
    fun estimate(nowRealtimeMs: Long = elapsedRealtime()): Long =
        if (playing) positionMs + ((nowRealtimeMs - atRealtimeMs) * speed).toLong() else positionMs
}
