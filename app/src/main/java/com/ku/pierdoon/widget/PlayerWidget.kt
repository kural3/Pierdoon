package com.ku.pierdoon.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ku.pierdoon.MainActivity
import com.ku.pierdoon.R
import com.ku.pierdoon.data.ALBUM_ID
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.InterfaceLanguage
import com.ku.pierdoon.playback.PlaybackService
import com.ku.pierdoon.ui.components.ArtworkCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the widget shows. Saved, as the launcher can ask for it with the player long gone. */
private data class Shown(
    val title: String = "",
    val artist: String = "",
    val songId: Long = -1L,
    val albumId: Long = -1L,
    val playing: Boolean = false,
)

/** The home screen player. Its buttons go to the playback service like a headset's do. */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                // A new process means nothing plays any more, whatever was last saved.
                val shown = load(context).let { it.copy(playing = it.playing && serviceRunning) }
                draw(context, shown, cover(context, shown))
            } finally {
                pending.finish()
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action !in BUTTONS) return
        val pending = goAsync()
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            if (controller == null) {
                pending.finish()
            } else {
                // Woken from nothing, the service is still reading its saved queue.
                whenQueueIsBack(controller, action == ACTION_PLAY_PAUSE) {
                    when (action) {
                        ACTION_PLAY_PAUSE -> Util.handlePlayPauseButtonAction(controller)
                        ACTION_NEXT -> controller.seekToNextMediaItem()
                        ACTION_PREVIOUS -> controller.seekToPrevious()
                    }
                    controller.release()
                    pending.finish()
                }
            }
        }, ContextCompat.getMainExecutor(app))
    }

    /** Play needs no wait: on an empty player the service resumes the saved queue itself. */
    private fun whenQueueIsBack(controller: MediaController, canPlayNow: Boolean, run: () -> Unit) {
        if (canPlayNow || controller.mediaItemCount > 0) return run()
        val handler = Handler(Looper.getMainLooper())
        var done = false
        lateinit var listener: Player.Listener
        fun finish() {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            controller.removeListener(listener)
            run()
        }
        listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (!timeline.isEmpty) finish()
            }
        }
        controller.addListener(listener)
        handler.postDelayed(::finish, QUEUE_WAIT_MS)
    }

    /**
     * Keeps the widgets up to date for as long as the service lives. Draws only when widgets are
     * placed; what's shown is saved either way, for one placed later.
     */
    class Updater(private val context: Context, private val player: Player, private val scope: CoroutineScope) {
        private var job: Job? = null
        private var last: Shown? = null

        private val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(*EVENTS)) update()
            }
        }

        fun start() {
            serviceRunning = true
            player.addListener(listener)
            update()
        }

        /** Nothing plays from here on. */
        fun release() {
            player.removeListener(listener)
            job?.cancel()
            serviceRunning = false
            val shown = load(context).copy(playing = false)
            save(context, shown)
            // Nothing can wait for the cover now, so it's the plain one if it isn't in memory.
            draw(context, shown, ArtworkCache.peek(shown.songId, shown.albumId, large = false)?.let(::software))
        }

        private fun update() {
            val item = player.currentMediaItem ?: return
            val metadata = item.mediaMetadata
            val shown = Shown(
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString().orEmpty(),
                songId = item.mediaId.toLongOrNull() ?: -1L,
                albumId = metadata.extras?.getLong(ALBUM_ID, -1L) ?: -1L,
                playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
            )
            if (shown == last) return
            last = shown
            save(context, shown)
            if (!anyPlaced(context)) return
            job?.cancel()
            job = scope.launch { draw(context, shown, cover(context, shown)) }
        }
    }

    companion object {
        private const val PREFS = "player_widget"
        private const val ACTION_PLAY_PAUSE = "com.ku.pierdoon.widget.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.ku.pierdoon.widget.NEXT"
        private const val ACTION_PREVIOUS = "com.ku.pierdoon.widget.PREVIOUS"
        private val BUTTONS = setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS)
        private const val QUEUE_WAIT_MS = 3_000L
        private val EVENTS = intArrayOf(
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_MEDIA_METADATA_CHANGED,
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
        )

        @Volatile private var serviceRunning = false

        private fun anyPlaced(context: Context) = placed(context).isNotEmpty()

        private fun placed(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))

        private fun load(context: Context): Shown {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Shown(
                title = p.getString("title", "").orEmpty(),
                artist = p.getString("artist", "").orEmpty(),
                songId = p.getLong("song", -1L),
                albumId = p.getLong("album", -1L),
                playing = p.getBoolean("playing", false),
            )
        }

        private fun save(context: Context, s: Shown) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("title", s.title)
            putString("artist", s.artist)
            putLong("song", s.songId)
            putLong("album", s.albumId)
            putBoolean("playing", s.playing)
        }

        private suspend fun cover(context: Context, s: Shown): Bitmap? {
            if (s.songId < 0) return null
            CoverStore.init(context)
            return ArtworkCache.load(context.applicationContext, s.songId, s.albumId, large = false)?.let(::software)
        }

        /** A hardware bitmap can't be sent to the launcher. */
        private fun software(image: androidx.compose.ui.graphics.ImageBitmap): Bitmap {
            val bitmap = image.asAndroidBitmap()
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
        }

        private fun draw(context: Context, s: Shown, cover: Bitmap?) {
            val ids = placed(context)
            if (ids.isEmpty()) return
            val text = InterfaceLanguage.wrap(context)
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            views.setTextViewText(R.id.widget_title, s.title.ifEmpty { text.getString(R.string.app_name) })
            views.setTextViewText(R.id.widget_artist, s.artist)
            // An empty line would push the title off centre.
            views.setViewVisibility(R.id.widget_artist, if (s.artist.isEmpty()) View.GONE else View.VISIBLE)
            if (cover != null) views.setImageViewBitmap(R.id.widget_cover, cover)
            else views.setImageViewResource(R.id.widget_cover, R.drawable.ic_widget_music)
            views.setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            views.setContentDescription(R.id.widget_play, text.getString(if (s.playing) R.string.pause else R.string.play))
            views.setContentDescription(R.id.widget_previous, text.getString(R.string.previous))
            views.setContentDescription(R.id.widget_next, text.getString(R.string.next))
            views.setOnClickPendingIntent(R.id.widget_play, button(context, ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_next, button(context, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widget_previous, button(context, ACTION_PREVIOUS))
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_cover, open)
            views.setOnClickPendingIntent(R.id.widget_text, open)
            AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
        }

        private fun button(context: Context, action: String) = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, PlayerWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
