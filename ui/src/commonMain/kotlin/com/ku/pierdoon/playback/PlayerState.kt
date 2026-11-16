package com.ku.pierdoon.playback

import androidx.compose.runtime.Immutable

/** Repeat modes, with Media3's values (the phone hands them to Media3 as they are). */
object RepeatMode {
    const val OFF = 0
    const val ONE = 1
    const val ALL = 2
}

@Immutable
data class PlayerState(
    val connected: Boolean = false,
    val nowPlaying: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = RepeatMode.OFF,
)

/** Off → all → one → off, the order of the player's repeat button. */
fun nextRepeatMode(mode: Int): Int = when (mode) {
    RepeatMode.OFF -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    else -> RepeatMode.OFF
}
