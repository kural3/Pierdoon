package com.ku.pierdoon.playback

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.Song

@Immutable
data class NowPlaying(
    val songId: Long,
    val albumId: Long,
    val title: String,
    val artist: String,
)

/** One entry of the play queue; [key] stays the same while the entry moves. */
@Immutable
data class QueueEntry(val key: String, val song: Song)

/** The queue in play order (shuffle-aware) from the current track on. */
@Immutable
data class QueueState(
    val current: QueueEntry? = null,
    val upcoming: List<QueueEntry> = emptyList(),
    /** Under shuffle the play order isn't the list order, so dragging would land somewhere else. */
    val canReorder: Boolean = true,
)

@Immutable
data class PlayerState(
    val connected: Boolean = false,
    val nowPlaying: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/** Off → all → one → off, the order of the player's repeat button. */
fun nextRepeatMode(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}
