package com.ku.pierdoon.playback

import androidx.compose.runtime.Immutable
import androidx.media3.common.Player

@Immutable
data class NowPlaying(
    val songId: Long,
    val albumId: Long,
    val title: String,
    val artist: String,
)

@Immutable
data class PlayerState(
    val connected: Boolean = false,
    val nowPlaying: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    @param:Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/** Off → all → one → off, the order of the player's repeat button. */
@Player.RepeatMode
fun nextRepeatMode(@Player.RepeatMode mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}
