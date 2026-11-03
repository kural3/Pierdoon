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
    /** The queue as a cassette, for cassette mode. */
    val tape: TapeSides? = null,
    /** Entries a side of the tape starts at, by key: "A" or "B". */
    val sideStarts: Map<String, String> = emptyMap(),
)
