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
 *   (repeat all) rewinds it, for longer the longer the side. [player] is told to pause at the end
 *   of that track, the sound plays, and playing on takes it into the next one. Going elsewhere
 *   meanwhile (next, a seek, play) ends it right there. Owns [Player.setPauseAtEndOfMediaItems],
 *   which keeps the crossfade out of it.
 * - Next and previous ([buttons], which the session hands to controllers) move along the tape,
 *   playing or paused: across the middle the cassette is turned over, round to the start it's
 *   rewound, anywhere else it's spun. Playing, the track it lands on starts from the top once the
 *   deck is done (what plays meanwhile is muted); paused, it stays paused.
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

    /** [player] as the session should see it: next and previous go along the tape. */
    val buttons: Player = object : ForwardingPlayer(player) {
        override fun seekToNext() = next { super.seekToNext() }

        override fun seekToNextMediaItem() = next { super.seekToNextMediaItem() }

        override fun seekToPrevious() {
            val restart = player.currentPosition > player.maxSeekToPreviousPosition || !player.hasPreviousMediaItem()
            previous(if (restart) C.INDEX_UNSET else player.previousMediaItemIndex) { super.seekToPrevious() }
        }

        override fun seekToPreviousMediaItem() = previous(player.previousMediaItemIndex) { super.seekToPreviousMediaItem() }
    }

    private val flipSound by lazy { cassetteChangeSound() }
    // The keys and spins come out a little different every time.
    private val winds by lazy { cassetteWindVariety() }
    private val playKeys by lazy { cassetteKeyVariety(play = true) }
    private val stopKeys by lazy { cassetteKeyVariety(play = false) }

    private var track: AudioTrack? = null
    private var keyTrack: AudioTrack? = null
    private var waiting: Job? = null
    /** Set while what plays is muted until the deck is done; the volume to give back. */
    private var windingFrom: Float? = null
    /** Set while the deck does what a button asked (a spin, a turn), playing or not. */
    private var spinning = false
    /** The track a button's turn of the tape lands on. */
    private var turnTarget: Int? = null

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
            when {
                playWhenReady && spinning && windingFrom == null -> {
                    // Play pressed while a paused tape moves: it starts once the deck is done, silent till then.
                    windingFrom = player.volume
                    player.volume = 0f
                }
                playWhenReady -> finish()
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> {
                    // A muted track that ran out while a button's turn goes on: the turn sees to what's next.
                    if (waiting == null && !stopAtEnd) sideEnd().takeIf { it != SideEnd.None }?.let(::turn)
                }
                // Paused while the deck works: it lands where it was going, and waits there.
                else -> finish(arrived = true)
            }
        }

        // The deck's own seeks land here too, after it has finished: nothing to do then.
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = finish(playOn = !spinning)

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) finish(playOn = !spinning)
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
        finish()
        keyTrack?.release()
        keyTrack = null
    }

    /** Silences whatever the deck is doing and leaves [player] paused: playback is being stopped. */
    fun cancel() = finish()

    /** Only with a different track to follow: the last one ends as usual, one on repeat runs on. */
    private fun update() {
        player.pauseAtEndOfMediaItems = stopAtEnd || sideEnd() != SideEnd.None
    }

    /** The queue as a tape: play order (indices), where the current track is in it, and the sides. */
    private class TapeView(val order: List<Int>, val current: Int, val sides: TapeSides)

    private fun tapeView(): TapeView? {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return null
        val shuffle = player.shuffleModeEnabled
        val order = ArrayList<Int>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(shuffle)
        while (i != C.INDEX_UNSET) {
            order += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
        }
        val current = order.indexOf(player.currentMediaItemIndex)
        if (current < 0) return null
        val durations = order.map { player.getMediaItemAt(it).mediaMetadata.durationMs ?: 0L }
        return TapeView(order, current, tapeSides(durations, current))
    }

    /** What the tape does when the current track runs out into the next. */
    private fun sideEnd(): SideEnd {
        if (!tapeMode) return SideEnd.None
        val next = player.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return SideEnd.None
        val t = tapeView() ?: return SideEnd.None
        return sideEnd(t.current, t.order.indexOf(next), t.order.size, t.sides.sideBStart)
    }

    private fun soundFor(motion: TapeMotion, t: TapeView?): ShortArray =
        if (motion == TapeMotion.Rewind) cassetteRewindSound(cassetteRewindMs(t?.sides?.sideLengthMs ?: 0L)) else flipSound

    /** At the end of a side: the cassette is turned over or rewound, then the next track plays. */
    private fun turn(end: SideEnd) {
        finish()
        val motion = if (end == SideEnd.Rewind) TapeMotion.Rewind else TapeMotion.Flip
        run(soundFor(motion, tapeView()), motion) { finish(playOn = true) }
    }

    private fun next(move: () -> Unit) {
        // Mid-turn at a side's end: no waiting for the rest of it, straight on.
        if (waiting != null && !spinning) return move()
        if (!turnTo(player.nextMediaItemIndex, forward = true)) {
            move()
            wind(TapeMotion.Forward)
        }
    }

    private fun previous(target: Int, move: () -> Unit) {
        if (!turnTo(target, forward = false)) {
            move()
            wind(TapeMotion.Back)
        }
    }

    /**
     * Next or previous to [target] across the middle of the tape, or round to its start: the
     * cassette is turned over or rewound first. The track it's on stops where it is (muted, if
     * playing), and [target] starts from the top after. False if it's a spin instead.
     */
    private fun turnTo(target: Int, forward: Boolean): Boolean {
        if (!tapeMode || target == C.INDEX_UNSET) return false
        val t = tapeView() ?: return false
        val motion = tapeMove(t.current, t.order.indexOf(target), t.order.size, t.sides.sideBStart, forward)
        if (motion != TapeMotion.Flip && motion != TapeMotion.Rewind) return false
        finish()
        muteWhilePlaying()
        spinning = true
        turnTarget = target
        run(soundFor(motion, t), motion) { finish(arrived = true, playOn = windingFrom != null) }
        return true
    }

    /**
     * Next or previous on a tape, after [player] has moved: it spins to the track. Playing, the
     * track plays once the spin is over; paused, the tape spins all the same and stays paused.
     */
    private fun wind(motion: TapeMotion) {
        if (!tapeMode || player.mediaItemCount == 0) return
        finish()
        muteWhilePlaying()
        spinning = true
        run(winds.next(), motion) { finish(arrived = true, playOn = windingFrom != null) }
    }

    private fun muteWhilePlaying() {
        if (!player.playWhenReady) return
        windingFrom = player.volume
        player.volume = 0f
    }

    private fun run(sound: ShortArray, motion: TapeMotion, done: () -> Unit) {
        track = playing(sound)
        TapeMotion.current.value = motion
        waiting = scope.launch {
            delay(cassetteSoundMs(sound))
            done()
        }
    }

    private fun key(play: Boolean) {
        keyTrack?.release()
        keyTrack = playing((if (play) playKeys else stopKeys).next())
    }

    /**
     * Ends whatever the deck is doing. [arrived]: it got where it was going (or was paused on the
     * way), so a turn lands on its track and a spun-to track, which ran silently, goes back to its
     * start. [playOn]: playback goes on after, as it did before the deck started.
     */
    private fun finish(arrived: Boolean = false, playOn: Boolean = false) {
        val job = waiting ?: return
        waiting = null
        spinning = false
        val target = turnTarget
        turnTarget = null
        TapeMotion.current.value = TapeMotion.Still
        track?.release()
        track = null
        job.cancel()
        val volume = windingFrom
        windingFrom = null
        if (arrived) {
            when {
                target != null && target < player.mediaItemCount -> player.seekTo(target, 0)
                volume != null -> player.seekTo(0)
            }
        }
        volume?.let { player.volume = it }
        // A muted track may have run out and paused itself meanwhile: playing on takes the next one.
        if (playOn && player.playbackState != Player.STATE_IDLE && !player.playWhenReady) {
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
