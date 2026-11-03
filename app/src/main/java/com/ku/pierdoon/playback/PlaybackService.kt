package com.ku.pierdoon.playback

import com.ku.pierdoon.data.InterfaceLanguage
import android.content.Context
import com.ku.pierdoon.data.uriFor
import com.ku.pierdoon.data.toMediaItem
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.ku.pierdoon.MainActivity
import com.ku.pierdoon.R
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.MusicRepository
import com.ku.pierdoon.data.PlayHistory
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.UserLibrary
import com.ku.pierdoon.data.uri
import com.ku.pierdoon.radio.RadioTrack
import com.ku.pierdoon.widget.PlayerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Media3 marks much of what a player service needs (custom renderers, shuffle orders,
// notification provider) as unstable API.
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    // Its notification buttons are labelled in the chosen language (Android 12 and older).
    override fun attachBaseContext(base: Context) = super.attachBaseContext(InterfaceLanguage.wrap(base))


    private var session: MediaLibrarySession? = null
    private var widget: PlayerWidget.Updater? = null
    private lateinit var carLibrary: CarLibrary
    private var crossfader: Crossfader? = null
    private var tapeDeck: TapeDeck? = null
    /** The player itself; the session gets it wrapped in [TapeDeck.buttons]. */
    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var queueStore: QueueStore
    private var saveJob: Job? = null
    /** Nothing is saved until the previous queue is back, or an empty player would overwrite it. */
    private var restoring = true
    /** The queue found on disk at start (null if none); shared by restore and resumption. */
    private val savedQueue = SettableFuture.create<MediaSession.MediaItemsWithStartPosition?>()

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        CoverStore.init(this)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) }
        )

        val player = ExoPlayer.Builder(this, audioRenderers(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // History lives here, not in the UI: tracks that play with the app closed count too.
        val library = UserLibrary.get(this.filesDir)
        val history = PlayHistory.get(this)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Radio tracks not kept aren't in the library: nothing to remember them by.
                mediaItem?.mediaId?.toLongOrNull()?.takeUnless { RadioTrack.isRadio(it) }?.let(library::recordPlayed)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                    SleepTimer.state.value == SleepTimerState.EndOfTrack
                ) {
                    SleepTimer.fired()
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED)) {
                    history.tracker.update(
                        player.currentMediaItem?.mediaId?.toLongOrNull()?.takeUnless { RadioTrack.isRadio(it) },
                        player.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
                        player.isPlaying,
                        SystemClock.elapsedRealtime(),
                        System.currentTimeMillis(),
                        newItem = events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION),
                    )
                }
                if (events.containsAny(*QUEUE_EVENTS)) scheduleSave(player)
                if (events.containsAny(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) {
                    session?.setMediaButtonPreferences(modeButtons(player))
                }
            }
        })

        queueStore = QueueStore(this)
        restoreQueue(player)
        carLibrary = CarLibrary(this)
        widget = PlayerWidget.Updater(this, player, scope).also { it.start() }

        val fader = Crossfader(this, player, scope).also { crossfader = it }
        fader.start()
        exoPlayer = player
        Radio.attach(this, player)
        val cassette = TapeDeck(player, scope).also { tapeDeck = it }
        cassette.start()
        scope.launch {
            // No tape on the radio: an endless queue has no sides to turn or rewind.
            combine(SettingsStore.settings.map { it.cassetteMode }, Radio.state.map { it != null }) { tape, radio -> tape && !radio }
                .distinctUntilChanged()
                .collect {
                    cassette.tapeMode = it
                    EqualizerSettings.tape = it
                }
        }
        scope.launch {
            SettingsStore.settings
                .map { Triple(it.crossfadeSeconds, it.crossfadeAlbums, it.skipSilence) }
                .distinctUntilChanged()
                .collect { (seconds, albums, skipSilence) ->
                    fader.fadeMs = seconds * 1000L
                    fader.albumsGapless = !albums
                    fader.skipSilence = skipSilence
                }
        }

        // Settings apply live; distinctUntilChanged keeps the player untouched by unrelated toggles.
        scope.launch {
            SettingsStore.settings
                .map { Triple(it.skipSilence, it.playbackSpeed, if (it.eqEnabled) it.eqGains else null) }
                .distinctUntilChanged()
                .collect { (skipSilence, speed, eqGains) ->
                    player.skipSilenceEnabled = skipSilence
                    // Pitch moves with the speed, as on a turntable: slowed and nightcore.
                    player.playbackParameters = PlaybackParameters(speed, speed)
                    // Read by the processors in this player and the crossfade's: both sound the same.
                    EqualizerSettings.gains = eqGains
                }
        }

        scope.launch {
            SleepTimer.state.collectLatest { timer ->
                cassette.stopAtEnd = timer == SleepTimerState.EndOfTrack
                if (timer !is SleepTimerState.At) return@collectLatest
                // The last seconds fade out instead of cutting off mid-phrase.
                delay((timer.remainingMs - SLEEP_FADE_MS).coerceAtLeast(0))
                val steps = 20
                try {
                    for (i in steps downTo 1) {
                        player.volume = i / steps.toFloat()
                        delay(SLEEP_FADE_MS / steps)
                    }
                    cassette.cancel()
                    player.pause()
                    SleepTimer.fired()
                } finally {
                    player.volume = 1f
                }
            }
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Next and previous from controllers (the app, the notification, headphones, the car)
        // go through the deck; the player's own moves (crossfade, end of track) don't.
        session = MediaLibrarySession.Builder(this, cassette.buttons, SessionCallback())
            .setSessionActivity(openApp)
            .setMediaButtonPreferences(modeButtons(player))
            .build()
        // Shuffle and repeat leave the notification while the radio plays, and come back after.
        scope.launch {
            Radio.state.map { it != null }.distinctUntilChanged().collect {
                session?.setMediaButtonPreferences(modeButtons(player))
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        PlayHistory.get(this).flush()
        UserLibrary.get(this.filesDir).flush()
        widget?.release()
        widget = null
        scope.cancel()
        crossfader?.release()
        crossfader = null
        tapeDeck?.release()
        tapeDeck = null
        exoPlayer?.let(Radio::detach)
        session?.run {
            val player = exoPlayer ?: player
            // A few hundred bytes; written in place so it lands before the process can go.
            if (!restoring) queueStore.write(snapshot(player))
            player.release()
            release()
        }
        session = null
        exoPlayer = null
        super.onDestroy()
    }

    private fun restoreQueue(player: ExoPlayer) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) { loadSavedQueue() }
            // A controller may have started something new while the file was read; that wins.
            if (saved != null && player.mediaItemCount == 0) {
                player.shuffleModeEnabled = saved.shuffle
                player.repeatMode = saved.repeatMode
                player.setMediaItems(saved.items.mediaItems, saved.items.startIndex, saved.items.startPositionMs)
                // The same shuffle as before, not a new one: what was coming up still is.
                saved.shuffleOrder?.let { player.setShuffleOrder(DefaultShuffleOrder(it.toIntArray(), System.nanoTime())) }
                player.prepare()
            }
            restoring = false
            savedQueue.set(saved?.items)
        }
    }

    private class Restored(
        val items: MediaSession.MediaItemsWithStartPosition,
        val shuffle: Boolean,
        val repeatMode: Int,
        val shuffleOrder: List<Int>?,
    )

    /** Saved ids resolved against MediaStore; deleted files are dropped. */
    private fun loadSavedQueue(): Restored? {
        val q = queueStore.read() ?: return null
        val byId = MusicRepository(this).loadSongs(q.songIds).associateBy { it.id }
        val plan = q.restorePlan { it in byId } ?: return null
        return Restored(
            MediaSession.MediaItemsWithStartPosition(
                plan.songIds.map { byId.getValue(it).toMediaItem() },
                plan.index,
                plan.positionMs,
            ),
            q.shuffle,
            q.repeatMode,
            plan.shuffleOrder,
        )
    }

    /** Coalesces bursts (skipping, queue edits) into one tiny write. */
    private fun scheduleSave(player: Player) {
        if (restoring) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            val q = snapshot(player)
            withContext(Dispatchers.IO) { queueStore.write(q) }
        }
    }

    private fun snapshot(player: Player) = SavedQueue(
        // A radio's download is gone after a restart; one kept in the library is saved as that.
        songIds = List(player.mediaItemCount) { i ->
            val id = player.getMediaItemAt(i).mediaId.toLongOrNull() ?: -1L
            if (RadioTrack.isRadio(id)) Radio.libraryId(id) ?: -1L else id
        },
        index = player.currentMediaItemIndex,
        positionMs = player.currentPosition,
        shuffle = player.shuffleModeEnabled,
        repeatMode = player.repeatMode,
        shuffleOrder = shuffleOrderOf(player),
    )

    /** The shuffled play order as indices, for saving; null with shuffle off. */
    private fun shuffleOrderOf(player: Player): List<Int>? {
        if (!player.shuffleModeEnabled) return null
        val order = (player as? ExoPlayer)?.shuffleOrder ?: return null
        if (order.length != player.mediaItemCount) return null
        return generateSequence(order.firstIndex.takeIf { it != C.INDEX_UNSET }) { i ->
            order.getNextIndex(i).takeIf { it != C.INDEX_UNSET }
        }.take(order.length).toList()
    }

    /** Shuffle and repeat for the notification / lock screen, showing the current mode. */
    private fun modeButtons(player: Player) = if (Radio.state.value != null) emptyList() else listOf(
        CommandButton.Builder(if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName(getString(R.string.shuffle))
            .setSessionCommand(TOGGLE_SHUFFLE)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
        CommandButton.Builder(
            when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else -> CommandButton.ICON_REPEAT_OFF
            }
        )
            .setDisplayName(getString(R.string.repeat))
            .setSessionCommand(CYCLE_REPEAT)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
    )

    /** Controllers send only ids + metadata; the playable URI is resolved here. */
    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(TOGGLE_SHUFFLE)
                    .add(CYCLE_REPEAT)
                    .build()
            )
            .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val player = session.player
            // The radio keeps its own order and never ends: a stale button does nothing.
            if (Radio.state.value != null && customCommand.customAction in setOf(TOGGLE_SHUFFLE.customAction, CYCLE_REPEAT.customAction)) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            when (customCommand.customAction) {
                TOGGLE_SHUFFLE.customAction -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                CYCLE_REPEAT.customAction -> player.repeatMode = nextRepeatMode(player.repeatMode)
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.mapTo(ArrayList(mediaItems.size)) { item ->
                if (item.localConfiguration != null) item
                else item.mediaId.substringAfterLast('/').let { id ->
                    val uri = id.toLongOrNull()?.let(Radio::song)?.uri ?: Song.uriFor(id)
                    item.buildUpon().setMediaId(id).setUri(uri).build()
                }
            }
        )

        /** The service is open to every app; the library is what the audio permission guards. */
        private fun canBrowse(controller: MediaSession.ControllerInfo) =
            controller.isTrusted || controller.packageName == packageName || controller.packageName in CarLibrary.CAR_PACKAGES

        private fun <T : Any> browsing(controller: MediaSession.ControllerInfo, read: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> {
            if (!canBrowse(controller)) return Futures.immediateFuture(LibraryResult.ofError<T>(SessionError.ERROR_PERMISSION_DENIED))
            val result = SettableFuture.create<LibraryResult<T>>()
            scope.launch {
                runCatching { read() }
                    .onSuccess { result.set(it) }
                    .onFailure { result.set(LibraryResult.ofError<T>(SessionError.ERROR_UNKNOWN)) }
            }
            return result
        }

        private fun page(items: List<MediaItem>, page: Int, pageSize: Int): ImmutableList<MediaItem> {
            val from = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
            val to = (from.toLong() + pageSize).coerceAtMost(items.size.toLong()).toInt()
            return ImmutableList.copyOf(items.subList(from, to))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ) = browsing(browser) {
            val extras = Bundle().apply { putBoolean(SEARCH_SUPPORTED, true) }
            LibraryResult.ofItem(carLibrary.root, LibraryParams.Builder().setExtras(extras).build())
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ) = browsing(browser) {
            carLibrary.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ) = browsing(browser) {
            LibraryResult.ofItemList(page(carLibrary.children(parentId), page, pageSize), params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ) = browsing<Void>(browser) {
            session.notifySearchResultChanged(browser, query, carLibrary.search(query).size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ) = browsing(browser) {
            LibraryResult.ofItemList(page(carLibrary.search(query), page, pageSize), params)
        }

        /** A track picked in the car brings its list along; "play ..." said aloud finds what to play. */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val item = mediaItems.singleOrNull()
            val query = item?.requestMetadata?.searchQuery
            val fromCar = item != null && canBrowse(controller) && (query != null || CarLibrary.isTrack(item.mediaId))
            if (!fromCar) return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                runCatching {
                    val (songs, index) =
                        if (query != null) carLibrary.songsForVoice(query) to 0
                        else carLibrary.queueFor(item.mediaId) ?: (emptyList<Song>() to 0)
                    MediaSession.MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, index, C.TIME_UNSET)
                }.onSuccess { result.set(it) }.onFailure { result.setException(it) }
            }
            return result
        }

        /** Play pressed on an empty player (headset button, system media controls): resume the saved queue. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = Futures.transform(
            savedQueue,
            { it ?: throw UnsupportedOperationException("Nothing to resume") },
            MoreExecutors.directExecutor(),
        )
    }

    private companion object {
        const val SAVE_DELAY_MS = 1_000L
        const val SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
        const val SLEEP_FADE_MS = 10_000L
        val TOGGLE_SHUFFLE = SessionCommand("com.ku.pierdoon.TOGGLE_SHUFFLE", Bundle.EMPTY)
        val CYCLE_REPEAT = SessionCommand("com.ku.pierdoon.CYCLE_REPEAT", Bundle.EMPTY)
        val QUEUE_EVENTS = intArrayOf(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_IS_PLAYING_CHANGED,
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            Player.EVENT_REPEAT_MODE_CHANGED,
        )
    }
}
