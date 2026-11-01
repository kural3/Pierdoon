package com.ku.pierdoon.playback

import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import com.ku.pierdoon.data.Song
import kotlinx.coroutines.flow.MutableStateFlow

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
    /** The queue as a cassette, for cassette mode. */
    val tape: TapeSides? = null,
    /** Entries a side of the tape starts at, by key: "A" or "B". */
    val sideStarts: Map<String, String> = emptyMap(),
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

/** The tape running back between two sides, for the reels on screen. */
object TapeRewind {
    val running = MutableStateFlow(false)
}

/** Off → all → one → off, the order of the player's repeat button. */
@Player.RepeatMode
fun nextRepeatMode(@Player.RepeatMode mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}
