package com.ku.pierdoon.playback

import android.content.ContentUris
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.ku.pierdoon.data.ALBUM_ID
import com.ku.pierdoon.data.QUEUE_KEY
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.artworkUri
import com.ku.pierdoon.data.toMediaItem

/** Media3's player (the phone's MediaController) as the shared model's [QueuePlayer]. */
class MediaQueuePlayer(private val p: Player) : QueuePlayer {
    override val itemCount: Int get() = p.mediaItemCount
    override val currentIndex: Int get() = if (p.mediaItemCount == 0) -1 else p.currentMediaItemIndex
    override val isPlaying: Boolean get() = p.isPlaying
    override val isBuffering: Boolean get() = p.playbackState == Player.STATE_BUFFERING
    override val durationMs: Long get() = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
    override val positionMs: Long get() = p.currentPosition
    override val speed: Float get() = p.playbackParameters.speed

    override var playWhenReady: Boolean
        get() = p.playWhenReady
        set(value) { p.playWhenReady = value }

    override var shuffle: Boolean
        get() = p.shuffleModeEnabled
        set(value) { p.shuffleModeEnabled = value }

    override var repeatMode: Int
        get() = p.repeatMode
        set(value) { p.repeatMode = value }

    override fun songAt(index: Int): Song = p.getMediaItemAt(index).toSong()

    override fun keyAt(index: Int): String = queueKeyOf(p.getMediaItemAt(index), index)

    override fun nextIndex(index: Int, repeatMode: Int): Int =
        p.currentTimeline.takeUnless { it.isEmpty }
            ?.getNextWindowIndex(index, repeatMode, p.shuffleModeEnabled)
            ?.takeIf { it != C.INDEX_UNSET } ?: -1

    override fun firstIndex(): Int =
        p.currentTimeline.takeUnless { it.isEmpty }
            ?.getFirstWindowIndex(p.shuffleModeEnabled)
            ?.takeIf { it != C.INDEX_UNSET } ?: -1

    override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        p.shuffleModeEnabled = shuffle
        p.setMediaItems(songs.map { it.toMediaItem() }, startIndex, C.TIME_UNSET)
        p.prepare()
        p.play()
    }

    override fun insert(index: Int, songs: List<Song>) = p.addMediaItems(index, songs.map { it.toMediaItem() })
    override fun append(songs: List<Song>) = p.addMediaItems(songs.map { it.toMediaItem() })

    override fun refresh(index: Int, song: Song) {
        val item = p.getMediaItemAt(index)
        val md = item.mediaMetadata
        if (md.title?.toString() == song.title && md.artist?.toString() == song.artist &&
            md.albumTitle?.toString() == song.album && md.artworkUri == song.artworkUri
        ) return
        val key = md.extras?.getLong(QUEUE_KEY, 0L)?.takeIf { it != 0L }
        // Same file, new metadata: Media3 swaps it in without interrupting playback.
        p.replaceMediaItem(index, if (key != null) song.toMediaItem(key) else song.toMediaItem())
    }

    override fun remove(index: Int) = p.removeMediaItem(index)
    override fun move(from: Int, to: Int) = p.moveMediaItem(from, to)

    override fun playAt(index: Int) {
        p.seekToDefaultPosition(index)
        p.play()
    }

    override fun seekTo(positionMs: Long) = p.seekTo(positionMs)
    override fun seekTo(index: Int, positionMs: Long) = p.seekTo(index, positionMs)
    override fun next() = p.seekToNext()
    override fun previous() = p.seekToPrevious()

    /** Swiping back: always the track before, never a restart of this one as the button does past 3 s. */
    fun previousTrack() = p.seekToPreviousMediaItem()

    override fun togglePlay() {
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            if (p.playbackState == Player.STATE_ENDED) p.seekToDefaultPosition()
            p.play()
        }
    }

    override fun prepare() = p.prepare()

    override suspend fun stopAndRelease() = p.stop()
}

private fun queueKeyOf(item: MediaItem, index: Int): String =
    item.mediaMetadata.extras?.getLong(QUEUE_KEY, 0L)?.takeIf { it != 0L }?.let { "q:$it" } ?: "slot:$index"

/** A queued entry as a song: what its metadata knows (a file that has left the library still shows). */
private fun MediaItem.toSong(): Song {
    val md = mediaMetadata
    return Song(
        id = mediaId.toLongOrNull() ?: -1L,
        title = md.title?.toString().orEmpty(),
        artist = md.artist?.toString().orEmpty(),
        album = md.albumTitle?.toString().orEmpty(),
        albumId = md.albumId(),
        durationMs = md.durationMs ?: 0L,
    )
}

/** Album of a queued item: from the extra, or (items queued before it existed) the art URI. */
private fun MediaMetadata.albumId(): Long =
    extras?.getLong(ALBUM_ID, -1L)?.takeIf { it != -1L }
        ?: artworkUri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() } ?: -1L
