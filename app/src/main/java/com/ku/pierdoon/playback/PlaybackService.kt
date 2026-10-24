package com.ku.pierdoon.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.ku.pierdoon.MainActivity
import com.ku.pierdoon.data.MusicRepository
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.UserLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var queueStore: QueueStore
    private var saveJob: Job? = null
    /** Nothing is saved until the previous queue is back, or an empty player would overwrite it. */
    private var restoring = true
    /** The queue found on disk at start (null if none); shared by restore and resumption. */
    private val savedQueue = SettableFuture.create<MediaSession.MediaItemsWithStartPosition?>()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)

        // Audio-only renderers: no video/text/metadata pipelines are created,
        // which keeps memory low and lets R8 drop the unused code.
        val audioOnly = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf(
                MediaCodecAudioRenderer(this, MediaCodecSelector.DEFAULT, handler, audioListener)
            )
        }

        val player = ExoPlayer.Builder(this, audioOnly)
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
        val library = UserLibrary.get(this)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.toLongOrNull()?.let(library::recordPlayed)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(*QUEUE_EVENTS)) scheduleSave(player)
            }
        })

        queueStore = QueueStore(this)
        restoreQueue(player)

        // Settings apply live; distinctUntilChanged keeps the player untouched by unrelated toggles.
        scope.launch {
            SettingsStore.settings
                .map { it.skipSilence to it.audioOffload }
                .distinctUntilChanged()
                .collect { (skipSilence, offload) ->
                    player.skipSilenceEnabled = skipSilence
                    // Hardware offload lets the DSP decode while the CPU sleeps —
                    // the single biggest battery win for long listening sessions.
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setAudioOffloadPreferences(
                            AudioOffloadPreferences.Builder()
                                .setAudioOffloadMode(
                                    if (offload) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                                    else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                                )
                                .setIsGaplessSupportRequired(true)
                                .build()
                        )
                        .build()
                }
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        UserLibrary.get(this).flush()
        scope.cancel()
        session?.run {
            // A few hundred bytes; written in place so it lands before the process can go.
            if (!restoring) queueStore.write(snapshot(player))
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun restoreQueue(player: Player) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) { loadSavedQueue() }
            // A controller may have started something new while the file was read; that wins.
            if (saved != null && player.mediaItemCount == 0) {
                player.shuffleModeEnabled = saved.shuffle
                player.repeatMode = saved.repeatMode
                player.setMediaItems(saved.items.mediaItems, saved.items.startIndex, saved.items.startPositionMs)
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
    )

    /** Saved ids resolved against MediaStore; deleted files are dropped. */
    private fun loadSavedQueue(): Restored? {
        val q = queueStore.read() ?: return null
        val byId = MusicRepository(this).loadSongs(q.songIds).associateBy { it.id }
        val items = ArrayList<MediaItem>(q.songIds.size)
        var index = 0
        var current = false
        q.songIds.forEachIndexed { i, id ->
            val song = byId[id] ?: return@forEachIndexed
            if (i < q.index) index++ else if (i == q.index) current = true
            items += song.toMediaItem()
        }
        if (items.isEmpty()) return null
        // The current track is gone: start the one after it from the top.
        val position = if (current) q.positionMs else 0L
        return Restored(
            MediaSession.MediaItemsWithStartPosition(items, index.coerceAtMost(items.lastIndex), position),
            q.shuffle,
            q.repeatMode,
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
        songIds = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L },
        index = player.currentMediaItemIndex,
        positionMs = player.currentPosition,
        shuffle = player.shuffleModeEnabled,
        repeatMode = player.repeatMode,
    )

    /** Controllers send only ids + metadata; the playable URI is resolved here. */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.mapTo(ArrayList(mediaItems.size)) { item ->
                if (item.localConfiguration != null) item
                else item.buildUpon().setUri(Song.uriFor(item.mediaId)).build()
            }
        )

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
