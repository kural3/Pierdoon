package com.ku.pierdoon.playback

import com.ku.pierdoon.data.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The computer's own [Player] as the shared model's [QueuePlayer]. */
class DesktopQueuePlayer(val player: Player) : QueuePlayer {
    override val itemCount: Int get() = player.mediaItemCount
    override val currentIndex: Int get() = player.currentMediaItemIndex
    override val isPlaying: Boolean get() = player.isPlaying
    override val isBuffering: Boolean get() = player.playbackState == Player.STATE_BUFFERING
    override val durationMs: Long get() = player.duration.takeIf { it != Player.TIME_UNSET } ?: 0L
    override val positionMs: Long get() = player.currentPosition
    override val speed: Float get() = player.speed

    override var playWhenReady: Boolean
        get() = player.playWhenReady
        set(value) { player.playWhenReady = value }

    override var shuffle: Boolean
        get() = player.shuffleModeEnabled
        set(value) { player.shuffleModeEnabled = value }

    override var repeatMode: Int
        get() = player.repeatMode
        set(value) { player.repeatMode = value }

    override fun songAt(index: Int): Song = player.getMediaItemAt(index).song
    override fun keyAt(index: Int): String = "q:${player.getMediaItemAt(index).queueKey}"
    override fun nextIndex(index: Int, repeatMode: Int): Int = player.nextIndexFor(index, repeatMode)
    override fun firstIndex(): Int = player.playOrderSnapshot().firstOrNull() ?: -1

    override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        // Shuffled after the list is in, so the shuffled order starts from the track picked.
        player.shuffleModeEnabled = false
        player.setMediaItems(songs.map { MediaItem(it) }, startIndex, 0L)
        player.shuffleModeEnabled = shuffle
        player.prepare()
        player.play()
    }

    override fun insert(index: Int, songs: List<Song>) = player.addMediaItems(index, songs.map { MediaItem(it) })
    override fun append(songs: List<Song>) = player.addMediaItems(songs.map { MediaItem(it) })

    override fun refresh(index: Int, song: Song) {
        val item = player.getMediaItemAt(index)
        if (item.song != song) player.replaceMediaItem(index, item.copy(song = song))
    }

    override fun remove(index: Int) = player.removeMediaItem(index)
    override fun move(from: Int, to: Int) = player.moveMediaItem(from, to)

    override fun playAt(index: Int) {
        player.seekToDefaultPosition(index)
        player.play()
    }

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun seekTo(index: Int, positionMs: Long) = player.seekTo(index, positionMs)
    override fun next() = player.seekToNext()
    override fun previous() = player.seekToPrevious()
    override fun togglePlay() = PlaybackController.togglePlay()
    override fun prepare() = player.prepare()

    private var fade: Job? = null

    override fun fadeOutAndPause(durationMs: Long) {
        if (fade?.isActive == true) return
        fade = fadeScope.launch {
            val start = player.volume
            val steps = (durationMs / FADE_STEP_MS).toInt().coerceAtLeast(1)
            try {
                // The engine cubes the volume, so an even slide down sounds like an even fade.
                for (i in steps downTo 1) {
                    if (!player.playWhenReady) return@launch
                    player.volume = start * i / steps
                    delay(FADE_STEP_MS)
                }
                player.playWhenReady = false
            } finally {
                player.volume = start
            }
        }
    }

    /** Windows won't replace or move a file the decoder still has open: wait for it to let go. */
    override suspend fun stopAndRelease() {
        val closed = player.stop()
        withTimeoutOrNull(CLOSE_TIMEOUT_MS) { closed.await() }
    }

    private companion object {
        const val CLOSE_TIMEOUT_MS = 2_000L
        const val FADE_STEP_MS = 50L
        val fadeScope = MainScope()
    }
}
