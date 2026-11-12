package com.ku.pierdoon.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.ku.pierdoon.data.ALBUM_FROM_FOLDER
import com.ku.pierdoon.data.ALBUM_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fades the end of one track into the start of the next on [player].
 *
 * ExoPlayer plays one item at a time, so the old track's last seconds are handed to a second,
 * silent-to-the-system player ([tail]): a little ahead it starts muted and gets in step with
 * [player]. When the fade is due it takes over, getting quieter, while [player] moves on to the
 * next item and comes up from silence. The session, the notification and the queue only ever see
 * [player], for which this is an ordinary skip to the next track.
 */
@OptIn(UnstableApi::class)
class Crossfader(
    private val context: Context,
    private val player: ExoPlayer,
    /** [player]'s volume; the fades set their share of it. */
    private val volume: PlayerVolume,
    private val scope: CoroutineScope,
) {

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
    private var tailVolume: PlayerVolume? = null
    /** The item [tail] holds and where in it it starts; null with nothing prepared. */
    private var prepared: Prepared? = null
    private var fade: Job? = null
    /** Set around this class's own skip to the next item, which isn't the user's. */
    private var switching = false
    /** Whether the running fade has got as far as [tail] being the one heard. */
    private var handedOver = false

    private class Prepared(val item: MediaItem, val index: Int, val startMs: Long)

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (fade?.isActive != true) return
            if (handedOver) {
                // Paused mid-fade (a call, unplugged headphones): the old track's tail waits too.
                tail?.playWhenReady = playWhenReady
            } else {
                // Not in step any more; it gets ready again once playing resumes.
                stopFade()
                dropTail()
            }
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
        when (tail?.playbackState) {
            Player.STATE_READY -> beginFade(p, duration)
            Player.STATE_BUFFERING -> if (position < p.startMs) return FINE_TICK_MS else dropTail()
            // Failed: another try further on, or the track just runs into the next.
            else -> dropTail()
        }
        return FINE_TICK_MS
    }

    /** Whether the current track should fade into the one after it rather than run straight in. */
    private fun fadesIntoNext(): Boolean {
        if (player.pauseAtEndOfMediaItems || player.repeatMode == Player.REPEAT_MODE_ONE) return false
        val next = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return false
        if (!albumsGapless || player.shuffleModeEnabled) return true
        val current = player.currentMediaItem?.mediaMetadata ?: return true
        val following = player.getMediaItemAt(next).mediaMetadata
        // Tracks without an album share an empty one; that doesn't make them a record.
        // Nor does the folder name MediaStore files untagged tracks under.
        val sameAlbum = !current.albumTitle.isNullOrBlank() &&
            current.extras?.getBoolean(ALBUM_FROM_FOLDER) != true &&
            current.extras?.getLong(ALBUM_ID) == following.extras?.getLong(ALBUM_ID)
        return !sameAlbum
    }

    private fun prepareTail(startMs: Long) {
        val item = player.currentMediaItem ?: return
        val t = tail ?: newTail().also { tail = it }
        t.setMediaItem(item, startMs)
        tailVolume?.fade = 1f
        // The old track goes on at the speed it was playing at.
        t.playbackParameters = player.playbackParameters
        t.playWhenReady = false
        t.prepare()
        prepared = Prepared(item, player.currentMediaItemIndex, startMs)
    }

    private fun beginFade(p: Prepared, duration: Long) {
        val t = tail ?: return
        prepared = null
        handedOver = false
        fade = scope.launch {
            tailVolume?.fade = 0f
            val inStep = getInStep(t)
            while (inStep && player.currentPosition < p.startMs) delay(FINE_TICK_MS)
            val from = t.currentPosition
            val lengthMs = duration - from
            if (!inStep || lengthMs < MIN_FADE_MS) {
                // No fade then, rather than a jump: [player] plays on into the next track.
                fade = null
                dropTail()
                return@launch
            }
            handedOver = true
            tailVolume?.fade = 1f
            volume.fade = 0f
            switching = true
            try {
                player.seekToNextMediaItem()
            } finally {
                switching = false
            }
            // Paced by the tail itself, so a pause holds the fade where it is.
            while (isActive && t.playbackState != Player.STATE_ENDED && t.playbackState != Player.STATE_IDLE) {
                val f = ((t.currentPosition - from).toDouble() / lengthMs).coerceIn(0.0, 1.0)
                // Equal power: the overlap is as loud as either track alone.
                volume.fade = sin(f * PI / 2).toFloat()
                tailVolume?.fade = cos(f * PI / 2).toFloat()
                if (f >= 1.0) break
                delay(FADE_STEP_MS)
            }
            fade = null
            volume.fade = 1f
            dropTail()
        }
    }

    /**
     * Plays [t] from where [player] is, however long the device takes to sound. Positions tell
     * what's being heard but a volume change applies to what's being mixed, a good part of a
     * second ahead of that on some phones: started level with [player], [t] would take over that
     * far back and repeat it. So it's started, how far ahead of [player] it ends up shows that delay, and it's
     * sent that much further on. Both players then read the same while mixing the same.
     */
    private suspend fun getInStep(t: ExoPlayer): Boolean {
        var lead = FIRST_LEAD_MS
        t.seekTo(player.currentPosition + lead)
        t.play()
        repeat(STEP_TRIES) {
            delay(STEP_SETTLE_MS)
            while (t.playbackState == Player.STATE_BUFFERING) delay(FINE_TICK_MS)
            if (t.playbackState != Player.STATE_READY) return false
            val off = t.currentPosition - player.currentPosition
            if (abs(off) <= STEP_TOLERANCE_MS) return true
            lead -= off
            t.seekTo(player.currentPosition + lead)
        }
        return false
    }

    private fun stopFade() {
        val running = fade ?: return
        fade = null
        running.cancel()
        volume.fade = 1f
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
        // Equalized like the main player, or the fading track would change sound as it goes.
        return ExoPlayer.Builder(context, audioRenderers(context))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // The main player holds the focus; asking for it here would pause that one.
                /* handleAudioFocus = */ false,
            )
            .build()
            .also {
                it.skipSilenceEnabled = skipSilence
                tailVolume = PlayerVolume(it)
            }
    }

    /** Both players at their tracks' levels again (even volume turned on or off, a track measured). */
    fun applyLevels() {
        volume.apply()
        tailVolume?.apply()
    }

    private companion object {
        const val IDLE_TICK_MS = 500L
        const val FINE_TICK_MS = 20L
        const val FADE_STEP_MS = 30L
        /** The tail is made ready this long before it's needed. */
        const val PREPARE_AHEAD_MS = 3_000L
        /** Least time a tail made ready on the spot gets before it has to play. */
        const val PREPARE_LEAD_MS = 600L
        /** A first guess at how far ahead of what's heard the mixer is. */
        const val FIRST_LEAD_MS = 300L
        /** How long a player just started or moved takes to report its position reliably. */
        const val STEP_SETTLE_MS = 500L
        const val STEP_TOLERANCE_MS = 25L
        const val STEP_TRIES = 4
        /** A fade shorter than this would sound like a click rather than a crossfade. */
        const val MIN_FADE_MS = 1_000L
    }
}
