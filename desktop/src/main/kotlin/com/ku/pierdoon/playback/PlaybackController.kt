package com.ku.pierdoon.playback

import com.ku.pierdoon.platform.AppDirs
import com.ku.pierdoon.data.PlayHistory
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.UserLibrary
import com.ku.pierdoon.radio.RadioTrack
import com.ku.pierdoon.ui.components.ArtworkCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the [Player] for the whole app, the desktop counterpart of the Android PlaybackService:
 * listening history, the saved queue and settings that apply to playback.
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

    /** How loud each track is, for the even-volume setting; measured in the background. */
    val loudness = Loudness(java.io.File(AppDirs.cache, "loudness.json"), scope) { LoudnessScan.measure(it.file) }
    private var normalizing = false

    private val _outputUnavailable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Playback stopped for want of an audio output; the UI says so. */
    val outputUnavailable: SharedFlow<Unit> = _outputUnavailable.asSharedFlow()

    fun start() {
        if (started) return
        started = true
        val library = UserLibrary.get(AppDirs.files)
        var lastRecorded = -1L
        PlayHistory.load()
        player.addListener { p, events ->
            // Radio tracks not kept aren't in the library: no history, no stats.
            val song = p.currentMediaItem?.song?.takeUnless { RadioTrack.isRadio(it.id) }
            PlayHistory.tracker.update(
                song?.id, song?.durationMs ?: 0, p.isPlaying, System.nanoTime() / 1_000_000, System.currentTimeMillis(),
                newItem = Player.Event.Transition in events,
            )
            if (Player.Event.Transition in events || Player.Event.IsPlaying in events) {
                // History counts what was actually played, not what was skipped past while paused.
                val id = song?.id
                if (id != null && p.playWhenReady && id != lastRecorded) {
                    lastRecorded = id
                    library.recordPlayed(id)
                }
            }
            if (events.any { it in QUEUE_EVENTS }) scheduleSave()
            if (normalizing && Player.Event.Transition in events) measureAhead()
            syncMediaControls()
        }
        player.onOutputUnavailable = { _outputUnavailable.tryEmit(Unit) }
        Radio.start()

        scope.launch {
            SettingsStore.settings
                .map { Triple(it.skipSilence, if (it.muted) 0f else it.volume, it.outputDevice) }
                .distinctUntilChanged()
                .collect { (skipSilence, volume, device) ->
                    player.skipSilenceEnabled = skipSilence
                    player.volume = volume
                    player.setOutputDevice(device)
                }
        }

        scope.launch {
            SettingsStore.settings
                .map { it.crossfadeSeconds to it.crossfadeAlbums }
                .distinctUntilChanged()
                .collect { (seconds, albums) ->
                    player.crossfadeMs = seconds * 1000L
                    player.albumsGapless = !albums
                }
        }

        scope.launch {
            SettingsStore.settings.map { it.playbackSpeed }.distinctUntilChanged().collect { player.speed = it }
        }

        scope.launch {
            SettingsStore.settings.map { it.normalizeVolume }.distinctUntilChanged().collect { on ->
                normalizing = on
                // Radio tracks aren't in the library, nor measured: they play as they are.
                player.trackGain = if (on) { file -> loudness.gain(Song.idFor(file)) } else { _ -> 1f }
                if (on) measureAhead() else loudness.stop()
            }
        }

        scope.launch {
            // No tape on the radio: an endless queue has no sides to turn or rewind.
            combine(SettingsStore.settings.map { it.cassetteMode }, Radio.state.map { it != null }) { tape, radio -> tape && !radio }
                .distinctUntilChanged()
                .collect { player.tapeMode = it }
        }

        scope.launch {
            SettingsStore.settings
                .map { if (it.eqEnabled) it.eqGains else null }
                .distinctUntilChanged()
                .collect { player.setEqualizer(it) }
        }

        // The screenshot tool runs beside the real app and mustn't take its keys.
        if (System.getProperty("pierdoon.shots") == null) {
            MediaKeys.install(
                onPlayPause = ::togglePlay,
                onNext = { player.seekToNext() },
                onPrevious = { player.seekToPrevious() },
                onStop = { player.pause() },
            )
        }
    }

    /** The playing track and the next couple are measured before the rest of the library. */
    private fun measureAhead() {
        val i = player.currentMediaItemIndex.takeIf { it >= 0 } ?: return
        loudness.prioritize((i..i + 2).mapNotNull { n -> if (n < player.mediaItemCount) player.getMediaItemAt(n).song else null })
    }

    /**
     * Puts the app in the Windows media overlay. Once that works, Windows routes the media keys
     * to it as well, so the app's own key catching steps aside and other players keep theirs.
     */
    fun attachWindow(window: java.awt.Window) {
        if (System.getProperty("pierdoon.shots") != null) return
        val ok = SystemMediaControls.install(window, object : SystemMediaControls.Buttons {
            override fun play() { if (!player.isPlaying) togglePlay() }
            override fun pause() = player.pause()
            override fun next() = player.seekToNext()
            override fun previous() = player.seekToPrevious()
            override fun stop() = player.pause()
        })
        if (!ok) return
        MediaKeys.uninstall()
        shownInOverlay = null
        syncMediaControls()
    }

    private var shownInOverlay: Pair<NowPlaying?, Boolean>? = null

    private fun syncMediaControls() {
        val song = player.currentMediaItem?.song?.let { NowPlaying(it.id, it.albumId, it.title, it.artist) }
        val state = song to player.isPlaying
        if (state == shownInOverlay) return
        shownInOverlay = state
        SystemMediaControls.update(song, player.isPlaying) { ArtworkCache.coverBytes(it.songId, it.albumId) }
    }

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
                    // The same shuffle as before, not a new one: what was coming up still is.
                    plan.shuffleOrder?.let(player::restoreShuffleOrder)
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
        PlayHistory.flush()
        UserLibrary.get(AppDirs.files).flush()
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
        // A radio's download is gone after a restart; one kept in the library is saved as that.
        songIds = List(player.mediaItemCount) { i ->
            val id = player.getMediaItemAt(i).song.id
            if (RadioTrack.isRadio(id)) Radio.libraryId(id) ?: -1L else id
        },
        index = player.currentMediaItemIndex,
        positionMs = player.currentPosition,
        shuffle = player.shuffleModeEnabled,
        repeatMode = player.repeatMode,
        shuffleOrder = player.shuffleOrderSnapshot,
    )

    private const val SAVE_DELAY_MS = 1_000L
    private val QUEUE_EVENTS = setOf(
        Player.Event.Timeline,
        Player.Event.Transition,
        Player.Event.IsPlaying,
        Player.Event.Position,
        Player.Event.Shuffle,
        Player.Event.Repeat,
    )
}
