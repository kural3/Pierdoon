package com.ku.pierdoon.playback

import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.UserLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the [Player] for the whole app, the desktop counterpart of the Android PlaybackService:
 * listening history, the saved queue, the sleep timer and settings that apply to playback.
 * Lives as long as the process, so music keeps going with the window hidden in the tray.
 */
object PlaybackController {

    val player = Player()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queueStore = QueueStore()
    private var saveJob: Job? = null
    /** Nothing is saved until the previous queue is back, or an empty player would overwrite it. */
    private var restoring = true
    private var started = false

    fun start() {
        if (started) return
        started = true
        val library = UserLibrary.get()
        var lastRecorded = -1L
        player.addListener { p, events ->
            if (Player.Event.Transition in events || Player.Event.IsPlaying in events) {
                // History counts what was actually played, not what was skipped past while paused.
                val id = p.currentMediaItem?.song?.id
                if (id != null && p.playWhenReady && id != lastRecorded) {
                    lastRecorded = id
                    library.recordPlayed(id)
                }
            }
            if (events.any { it in QUEUE_EVENTS }) scheduleSave()
        }
        player.onPausedAtEnd = {
            if (SleepTimer.state.value == SleepTimerState.EndOfTrack) SleepTimer.fired()
        }

        scope.launch {
            SettingsStore.settings
                .map { Triple(it.skipSilence, if (it.muted) 0f else it.volume, it.outputDevice) }
                .distinctUntilChanged()
                .collect { (skipSilence, volume, device) ->
                    player.skipSilenceEnabled = skipSilence
                    if (!fading) player.volume = volume
                    player.setOutputDevice(device)
                }
        }

        scope.launch {
            SleepTimer.state.collectLatest { timer ->
                player.pauseAtEndOfMediaItems = timer == SleepTimerState.EndOfTrack
                if (timer !is SleepTimerState.At) return@collectLatest
                // The last seconds fade out instead of cutting off mid-phrase.
                delay((timer.remainingMs - SLEEP_FADE_MS).coerceAtLeast(0))
                val steps = 20
                val full = player.volume
                fading = true
                try {
                    for (i in steps downTo 1) {
                        player.volume = full * i / steps.toFloat()
                        delay(SLEEP_FADE_MS / steps)
                    }
                    player.pause()
                    SleepTimer.fired()
                } finally {
                    fading = false
                    val s = SettingsStore.settings.value
                    player.volume = if (s.muted) 0f else s.volume
                }
            }
        }

        MediaKeys.install(
            onPlayPause = ::togglePlay,
            onNext = { player.seekToNext() },
            onPrevious = { player.seekToPrevious() },
            onStop = { player.pause() },
        )
    }

    @Volatile private var fading = false

    /**
     * Puts the saved queue back, resolved against the library; tracks deleted since are dropped.
     * Called once the library has loaded.
     */
    fun restore(byId: Map<Long, Song>) {
        if (!restoring) return
        scope.launch {
            val q = withContext(Dispatchers.IO) { queueStore.read() }
            // Something may have started playing while the file was read; that wins.
            if (q != null && player.mediaItemCount == 0) {
                q.restorePlan { it in byId }?.let { plan ->
                    player.shuffleModeEnabled = false
                    player.setMediaItems(plan.songIds.map { MediaItem(byId.getValue(it)) }, plan.index, plan.positionMs)
                    player.shuffleModeEnabled = q.shuffle
                    player.repeatMode = q.repeatMode
                }
            }
            restoring = false
        }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        }
    }

    /** Writes the queue now; for quitting. */
    fun shutdown() {
        UserLibrary.get().flush()
        if (!restoring) queueStore.write(snapshot())
        MediaKeys.uninstall()
        player.release()
    }

    /** Coalesces bursts (skipping, queue edits) into one tiny write. */
    private fun scheduleSave() {
        if (restoring) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            val q = snapshot()
            withContext(Dispatchers.IO) { queueStore.write(q) }
        }
    }

    private fun snapshot() = SavedQueue(
        songIds = List(player.mediaItemCount) { player.getMediaItemAt(it).song.id },
        index = player.currentMediaItemIndex,
        positionMs = player.currentPosition,
        shuffle = player.shuffleModeEnabled,
        repeatMode = player.repeatMode,
    )

    private const val SAVE_DELAY_MS = 1_000L
    private const val SLEEP_FADE_MS = 10_000L
    private val QUEUE_EVENTS = setOf(
        Player.Event.Timeline,
        Player.Event.Transition,
        Player.Event.IsPlaying,
        Player.Event.Position,
        Player.Event.Shuffle,
        Player.Event.Repeat,
    )
}
