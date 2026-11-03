package com.ku.pierdoon.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cassette mode's deck around [player]: the queue is a tape.
 *
 * - Side A running out turns the cassette over; side B running out into the start of the queue
 *   (repeat all) rewinds it. [player] is told to pause at the end of that track, the sound plays,
 *   and playing on takes it into the next one. Going elsewhere meanwhile (next, a seek, play) ends
 *   it right there. Owns [Player.setPauseAtEndOfMediaItems], which keeps the crossfade out of it.
 * - Next and previous ([buttons], which the session hands to controllers) spin the tape there: the
 *   new track is held silent until the spin is over, then starts from the top.
 * - Play and pause asked for by anyone (the app, the notification, headphones) press a key.
 */
@OptIn(UnstableApi::class)
class TapeDeck(private val player: ExoPlayer, private val scope: CoroutineScope) {

    var tapeMode: Boolean = false
        set(value) {
            field = value
            update()
        }

    /** The sleep timer's "end of track": the player really stops there, without a sound. */
    var stopAtEnd: Boolean = false
        set(value) {
            field = value
            update()
        }

    /** [player] as the session should see it: next and previous spin the tape on the way. */
    val buttons: Player = object : ForwardingPlayer(player) {
        override fun seekToNext() {
            super.seekToNext()
            wind()
        }

        override fun seekToNextMediaItem() {
            super.seekToNextMediaItem()
            wind()
        }

        override fun seekToPrevious() {
            super.seekToPrevious()
            wind()
        }

        override fun seekToPreviousMediaItem() {
            super.seekToPreviousMediaItem()
            wind()
        }
    }

    private val flipSound by lazy { cassetteChangeSound() }
    private val rewindSound by lazy { cassetteRewindSound() }
    private val windSound by lazy { cassetteWindSound() }
    private val playKey by lazy { cassetteKeySound(play = true) }
    private val stopKey by lazy { cassetteKeySound(play = false) }

    private var track: AudioTrack? = null
    private var keyTrack: AudioTrack? = null
    private var waiting: Job? = null
    /** Set while the tape spins to a track that plays muted until then; the volume to give back. */
    private var windingFrom: Float? = null
    /**
     * Set when the deck itself plays on after turning the tape, which nobody pressed a key for.
     * Cleared by the change it causes, which may be reported late (from inside another callback).
     */
    private var playingOn = false

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playingOn && playWhenReady) {
                playingOn = false
                return
            }
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST && tapeMode) key(playWhenReady)
            if (playWhenReady) {
                finish(resume = false)
            } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && !stopAtEnd) {
                val end = sideEnd()
                if (end != SideEnd.None) turn(end)
            } else {
                // Paused mid-spin: the track waits at its start.
                finish(resume = false, backToStart = true)
            }
        }

        // The deck's own seek back to the start lands here too, after it has finished: nothing to do then.
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = finish(resume = true)

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) finish(resume = true)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(*NEXT_TRACK_EVENTS)) update()
        }
    }

    fun start() {
        player.addListener(listener)
        update()
    }

    fun release() {
        player.removeListener(listener)
        finish(resume = false)
        keyTrack?.release()
        keyTrack = null
    }

    /** Silences whatever the deck is doing and leaves [player] paused: playback is being stopped. */
    fun cancel() = finish(resume = false)

    /** Only with a different track to follow: the last one ends as usual, one on repeat runs on. */
    private fun update() {
        player.pauseAtEndOfMediaItems = stopAtEnd || sideEnd() != SideEnd.None
    }

    /** What the tape does when the current track runs out into the next. */
    private fun sideEnd(): SideEnd {
        if (!tapeMode) return SideEnd.None
        val next = player.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return SideEnd.None
        val timeline = player.currentTimeline
        val shuffle = player.shuffleModeEnabled
        val order = ArrayList<Int>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(shuffle)
        while (i != C.INDEX_UNSET) {
            order += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
        }
        val durations = order.map { player.getMediaItemAt(it).mediaMetadata.durationMs ?: 0L }
        val current = order.indexOf(player.currentMediaItemIndex)
        return sideEnd(current, order.indexOf(next), order.size, tapeSides(durations, current).sideBStart)
    }

    /** At the end of a side: the cassette is turned over or rewound, then the next track plays. */
    private fun turn(end: SideEnd) {
        finish(resume = false)
        val rewinding = end == SideEnd.Rewind
        val samples = if (rewinding) rewindSound else flipSound
        track = playing(samples)
        TapeRewind.running.value = rewinding
        waiting = scope.launch {
            delay(cassetteSoundMs(samples))
            finish(resume = true)
        }
    }

    /** Next or previous pressed while playing a tape: the track just moved to plays once the spin is over. */
    private fun wind() {
        if (!tapeMode || !player.playWhenReady || player.mediaItemCount == 0) return
        finish(resume = false)
        windingFrom = player.volume
        player.volume = 0f
        track = playing(windSound)
        waiting = scope.launch {
            delay(cassetteSoundMs(windSound))
            finish(resume = true, backToStart = true)
        }
    }

    private fun key(play: Boolean) {
        keyTrack?.release()
        keyTrack = playing(if (play) playKey else stopKey)
    }

    /**
     * Ends whatever the deck is doing. [resume] plays on into the track it was going to;
     * [backToStart] puts a track that was spun to back at its start, as it ran silently meanwhile.
     */
    private fun finish(resume: Boolean, backToStart: Boolean = false) {
        val job = waiting ?: return
        waiting = null
        TapeRewind.running.value = false
        track?.release()
        track = null
        job.cancel()
        val volume = windingFrom
        windingFrom = null
        if (volume != null) {
            if (backToStart) player.seekTo(0)
            player.volume = volume
        } else if (resume && player.playbackState != Player.STATE_IDLE && !player.playWhenReady) {
            playingOn = true
            player.play()
        }
    }

    private fun playing(samples: ShortArray): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(CASSETTE_SOUND_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(samples.size * 2)
        .build()
        .apply {
            write(samples, 0, samples.size)
            play()
        }

    private companion object {
        val NEXT_TRACK_EVENTS = intArrayOf(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            Player.EVENT_REPEAT_MODE_CHANGED,
        )
    }
}
