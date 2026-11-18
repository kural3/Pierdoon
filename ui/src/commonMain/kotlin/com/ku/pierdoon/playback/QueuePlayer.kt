package com.ku.pierdoon.playback

import com.ku.pierdoon.data.Song
import com.ku.pierdoon.radio.RadioStart
import com.ku.pierdoon.radio.RadioState
import kotlinx.coroutines.flow.StateFlow

/**
 * The player as [com.ku.pierdoon.AppModel] sees it: a queue of songs, a current entry and the
 * transport. The phone puts Media3's controller behind it, the computer its own player; both
 * keep Media3's meaning of the calls (indices in list order, shuffle as a separate play order).
 */
interface QueuePlayer {
    val itemCount: Int

    /** The current entry's index in list order; -1 when the queue is empty. */
    val currentIndex: Int
    val isPlaying: Boolean
    val isBuffering: Boolean

    /** The current track's length; 0 while unknown. */
    val durationMs: Long
    val positionMs: Long
    val speed: Float
    var playWhenReady: Boolean
    var shuffle: Boolean
    var repeatMode: Int

    /** The song an entry carries (as it was queued, which may be older than the library's). */
    fun songAt(index: Int): Song

    /** A key for the entry that stays with it while it moves: "q:<n>", or "slot:<index>" without one. */
    fun keyAt(index: Int): String

    /** The entry played after [index] with [repeatMode] (shuffle included); -1 for none. */
    fun nextIndex(index: Int, repeatMode: Int): Int

    /** The entry the play order starts with; -1 when empty. */
    fun firstIndex(): Int

    /** Replaces the queue with [songs] and starts playing [startIndex]. */
    fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean)

    fun insert(index: Int, songs: List<Song>)
    fun append(songs: List<Song>)

    /** Gives the entry [song]'s newer tags, keeping its key and, if it's playing, its place. Nothing if equal. */
    fun refresh(index: Int, song: Song)

    fun remove(index: Int)
    fun move(from: Int, to: Int)

    /** Starts the entry at [index] from its beginning. */
    fun playAt(index: Int)

    fun seekTo(positionMs: Long)
    fun seekTo(index: Int, positionMs: Long)
    fun next()
    fun previous()
    fun togglePlay()
    fun prepare()

    /** Turns the music down over [durationMs], pauses, and puts the volume back for next time. */
    fun fadeOutAndPause(durationMs: Long)

    /** Stops and waits (a little) until the current file is let go, so it can be rewritten or moved. */
    suspend fun stopAndRelease()
}

/** The radio (beta) of each app, as the shared model drives it. */
interface RadioControl {
    val state: StateFlow<RadioState?>
    fun start(library: List<Song>, request: RadioStart)
    fun keep(songId: Long)
    fun dislike(songId: Long)
    fun stop()
}
