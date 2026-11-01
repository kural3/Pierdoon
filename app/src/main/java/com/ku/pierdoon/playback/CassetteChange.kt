package com.ku.pierdoon.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The sound of a cassette being changed where one track runs out and another follows.
 *
 * [player] is told to pause at the end of the track, the sound plays, and playing on takes it into
 * the next one. Going elsewhere meanwhile (next, a seek, play) ends the change right there. Owns
 * [Player.setPauseAtEndOfMediaItems], which keeps the crossfade out of it too.
 *
 * Cassette mode does the same at the end of each side, with the tape rewinding instead.
 */
@OptIn(UnstableApi::class)
class CassetteChange(private val player: ExoPlayer, private val scope: CoroutineScope) {

    var enabled: Boolean = false
        set(value) {
            field = value
            update()
        }

    /** Cassette mode: the queue is a tape, and running off a side rewinds it. */
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

    private val sound by lazy { cassetteChangeSound() }
    private val rewindSound by lazy { cassetteRewindSound() }
    private var track: AudioTrack? = null
    private var waiting: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                finish(resume = false)
            } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && !stopAtEnd) {
                val rewinding = atSideEnd()
                if (enabled || rewinding) begin(rewinding)
            }
        }

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
    }

    /** Silences a change under way and leaves [player] paused: playback is being stopped. */
    fun cancel() = finish(resume = false)

    /** Only with a different track to follow: the last one ends as usual, one on repeat runs on. */
    private fun update() {
        val next = player.nextMediaItemIndex
        player.pauseAtEndOfMediaItems = stopAtEnd ||
            ((enabled || atSideEnd()) && next != C.INDEX_UNSET && next != player.currentMediaItemIndex)
    }

    /** Whether what plays next is across the end of a side of the tape. */
    private fun atSideEnd(): Boolean {
        if (!tapeMode) return false
        val next = player.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return false
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
        return isSideEnd(current, order.indexOf(next), order.size, tapeSides(durations, current).sideBStart)
    }

    private fun begin(rewinding: Boolean) {
        finish(resume = false)
        val samples = if (rewinding) rewindSound else sound
        val t = newTrack(samples.size)
        t.write(samples, 0, samples.size)
        t.play()
        track = t
        TapeRewind.running.value = rewinding
        waiting = scope.launch {
            delay(if (rewinding) CASSETTE_REWIND_MS else CASSETTE_SOUND_MS)
            finish(resume = true)
        }
    }

    private fun finish(resume: Boolean) {
        val job = waiting ?: return
        waiting = null
        TapeRewind.running.value = false
        track?.run {
            stop()
            release()
        }
        track = null
        job.cancel()
        if (resume && player.playbackState != Player.STATE_IDLE) player.play()
    }

    private fun newTrack(samples: Int) = AudioTrack.Builder()
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
        .setBufferSizeInBytes(samples * 2)
        .build()

    private companion object {
        val NEXT_TRACK_EVENTS = intArrayOf(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            Player.EVENT_REPEAT_MODE_CHANGED,
        )
    }
}
