package com.ku.pierdoon.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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
import com.ku.pierdoon.MainActivity
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
            .setCallback(SessionCallback)
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
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Controllers send only ids + metadata; the playable URI is resolved here. */
    private object SessionCallback : MediaSession.Callback {
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
    }
}
