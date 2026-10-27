package com.ku.pierdoon.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.ku.pierdoon.data.ALBUM_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fades the end of one track into the start of the next on [player].
 *
 * ExoPlayer plays one item at a time, so the old track's last seconds are handed to a second,
 * silent-to-the-system player ([tail]): it's made ready on that spot a little ahead, and when the
 * fade starts it plays them getting quieter while [player] moves on to the next item and comes up
 * from silence. The session, the notification and the queue only ever see [player], for which
 * this is an ordinary skip to the next track.
 */
@OptIn(UnstableApi::class)
class Crossfader(private val context: Context, private val player: ExoPlayer, private val scope: CoroutineScope) {

    /** How long the fade lasts; 0 for none. */
    var fadeMs: Long = 0

    /** Tracks of one album played in order run into each other without a fade, as on the record. */
    var albumsGapless: Boolean = true

    var skipSilence: Boolean = false
        set(value) {
            field = value
            tail?.skipSilenceEnabled = value
        }

    private var tail: ExoPlayer? = null
    /** The item [tail] holds and where in it it starts; null with nothing prepared. */
    private var prepared: Prepared? = null
    private var fade: Job? = null
    /** Set around this class's own skip to the next item, which isn't the user's. */
    private var switching = false

    private class Prepared(val item: MediaItem, val index: Int, val startMs: Long)

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Paused mid-fade (a call, unplugged headphones): the old track's tail waits too.
            if (fade?.isActive == true) tail?.playWhenReady = playWhenReady
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (switching) return
            // The user went elsewhere: the fade, or the tail made ready for this track, is moot.
            stopFade()
            dropTail()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (switching || reason != Player.DISCONTINUITY_REASON_SEEK) return
            stopFade()
            dropTail()
        }
    }

    fun start() {
        player.addListener(listener)
        scope.launch {
            while (isActive) delay(tick())
        }
    }

    fun release() {
        player.removeListener(listener)
        stopFade()
        tail?.release()
        tail = null
    }

    /** Looks at where [player] is and acts on it; returns how long until it's worth looking again. */
    private fun tick(): Long {
        if (fade?.isActive == true) return IDLE_TICK_MS
        if (fadeMs <= 0 || !player.isPlaying || !fadesIntoNext()) {
            dropTail()
            return IDLE_TICK_MS
        }
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return IDLE_TICK_MS
        val position = player.currentPosition
        val left = duration - position
        // Waits are real time; the track runs faster or slower than that at another speed.
        val speed = player.playbackParameters.speed
        if (left > fadeMs + PREPARE_AHEAD_MS) return ((left - fadeMs - PREPARE_AHEAD_MS) / speed).toLong().coerceAtMost(IDLE_TICK_MS)

        val p = prepared?.takeIf { it.index == player.currentMediaItemIndex && it.item == player.currentMediaItem }
        if (p == null) {
            // Not from the very spot if that's too close to make ready in time (a seek near the end).
            val startMs = maxOf(duration - fadeMs, position + PREPARE_LEAD_MS)
            if (duration - startMs < MIN_FADE_MS) return IDLE_TICK_MS
            prepareTail(startMs)
            return FINE_TICK_MS
        }
        if (position < p.startMs) return ((p.startMs - position) / speed).toLong().coerceIn(1, FINE_TICK_MS)
        beginFade(p, duration - p.startMs)
        return IDLE_TICK_MS
    }

    /** Whether the current track should fade into the one after it rather than run straight in. */
    private fun fadesIntoNext(): Boolean {
        if (player.pauseAtEndOfMediaItems || player.repeatMode == Player.REPEAT_MODE_ONE) return false
        val next = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return false
        if (!albumsGapless || player.shuffleModeEnabled) return true
        val current = player.currentMediaItem?.mediaMetadata ?: return true
        val following = player.getMediaItemAt(next).mediaMetadata
        // Tracks without an album share an empty one; that doesn't make them a record.
        val sameAlbum = !current.albumTitle.isNullOrBlank() &&
            current.extras?.getLong(ALBUM_ID) == following.extras?.getLong(ALBUM_ID)
        return !sameAlbum
    }

    private fun prepareTail(startMs: Long) {
        val item = player.currentMediaItem ?: return
        val t = tail ?: newTail().also { tail = it }
        t.setMediaItem(item, startMs)
        t.volume = 1f
        // The old track goes on at the speed it was playing at.
        t.playbackParameters = player.playbackParameters
        t.playWhenReady = false
        t.prepare()
        prepared = Prepared(item, player.currentMediaItemIndex, startMs)
    }

    private fun beginFade(p: Prepared, lengthMs: Long) {
        val t = tail ?: return
        prepared = null
        t.play()
        player.volume = 0f
        switching = true
        try {
            player.seekToNextMediaItem()
        } finally {
            switching = false
        }
        fade = scope.launch {
            // Paced by the tail itself, so a pause holds the fade where it is.
            while (isActive && t.playbackState != Player.STATE_ENDED && t.playbackState != Player.STATE_IDLE) {
                val f = ((t.currentPosition - p.startMs).toDouble() / lengthMs).coerceIn(0.0, 1.0)
                // Equal power: the overlap is as loud as either track alone.
                player.volume = sin(f * PI / 2).toFloat()
                t.volume = cos(f * PI / 2).toFloat()
                if (f >= 1.0) break
                delay(FADE_STEP_MS)
            }
            fade = null
            player.volume = 1f
            dropTail()
        }
    }

    private fun stopFade() {
        val running = fade ?: return
        fade = null
        running.cancel()
        player.volume = 1f
    }

    /** Lets go of the tail's file and decoder; the player itself is kept for the next fade. */
    private fun dropTail() {
        if (prepared == null && (tail?.mediaItemCount ?: 0) == 0) return
        prepared = null
        tail?.run {
            stop()
            clearMediaItems()
        }
    }

    private fun newTail(): ExoPlayer {
        val audioOnly = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf(MediaCodecAudioRenderer(context, MediaCodecSelector.DEFAULT, handler, audioListener))
        }
        return ExoPlayer.Builder(context, audioOnly)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // The main player holds the focus; asking for it here would pause that one.
                /* handleAudioFocus = */ false,
            )
            .build()
            .also { it.skipSilenceEnabled = skipSilence }
    }

    private companion object {
        const val IDLE_TICK_MS = 500L
        const val FINE_TICK_MS = 20L
        const val FADE_STEP_MS = 30L
        /** The tail is made ready this long before it's needed. */
        const val PREPARE_AHEAD_MS = 3_000L
        /** Least time a tail made ready on the spot gets before it has to play. */
        const val PREPARE_LEAD_MS = 600L
        /** A fade shorter than this would sound like a click rather than a crossfade. */
        const val MIN_FADE_MS = 1_000L
    }
}
