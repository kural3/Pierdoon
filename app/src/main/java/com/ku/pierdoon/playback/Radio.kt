package com.ku.pierdoon.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.ku.pierdoon.data.QUEUE_KEY
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.data.toMediaItem
import com.ku.pierdoon.radio.RadioMemory
import com.ku.pierdoon.radio.RadioPlanner
import com.ku.pierdoon.radio.RadioSession
import com.ku.pierdoon.radio.RadioSource
import com.ku.pierdoon.radio.RadioStart
import com.ku.pierdoon.radio.RadioState
import com.ku.pierdoon.radio.RadioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The radio (beta) on the phone: keeps the player's queue one track ahead with what a
 * [RadioSession] picks. Lives beside the player in the playback service; the UI starts and steers
 * it from the same process.
 *
 * Does nothing at all between tracks: the next one is picked (and a new one downloaded) only when
 * a track starts and the queue has nothing after it.
 *
 * A downloaded track's file goes together with its place in the queue: never one without the
 * other. The one playing is left to finish and goes once the player has moved off it.
 */
object Radio {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: Player? = null
    private var session: RadioSession? = null
    private var stateJob: Job? = null
    private var startJob: Job? = null
    private var fillJob: Job? = null
    private var settingsJob: Job? = null
    /** Queue entries the radio added, by their queue key. */
    private val ours = HashSet<String>()
    /** Downloaded tracks, for a controller that sends back only their ids. */
    private val songs = ConcurrentHashMap<Long, Song>()
    /** Downloads let go of while playing: deleted once the player has moved off them. */
    private val orphans = HashMap<Long, RadioTrack>()
    /** New tracks kept in the library, by their radio id: outlive the session, for the saved queue. */
    private val keptIds = ConcurrentHashMap<Long, Long>()

    private val _state = MutableStateFlow<RadioState?>(null)
    /** The radio playing now, or null. */
    val state: StateFlow<RadioState?> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            sweep()
            fill()
        }

        // Any controller (the car, a watch) may still ask: the radio picks its own order and never ends.
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled && session != null) player?.shuffleModeEnabled = false
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            if (repeatMode != Player.REPEAT_MODE_OFF && session != null) player?.repeatMode = Player.REPEAT_MODE_OFF
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
            sweep()
            val p = player ?: return
            if (session == null || editing > 0) return
            // Something else was played in its place (before the first track came in, too): the radio is over.
            if ((0 until p.mediaItemCount).none { keyOf(p.getMediaItemAt(it)) in ours }) stop()
        }
    }

    /** Called by the playback service once its player exists. */
    fun attach(context: Context, player: Player) {
        app = context.applicationContext
        this.player = player
        player.addListener(listener)
        // Settings move the slider while it plays.
        settingsJob?.cancel()
        settingsJob = scope.launch {
            SettingsStore.settings.map { Triple(it.radioNovelty, it.radioBuffer, it.radioEnabled) }.distinctUntilChanged()
                .collect { (novelty, buffer, on) ->
                    session?.novelty = novelty
                    session?.buffer = buffer
                    if (!on) stop() else fill()
                }
        }
        Downloader.init(app)
        if (session == null && orphans.isEmpty()) Downloader.clearRadioCache()
    }

    fun detach(player: Player) {
        if (this.player !== player) return
        player.removeListener(listener)
        // The player goes with the service: nothing is reading the files any more.
        this.player = null
        stop()
        settingsJob?.cancel()
        orphans.values.forEach(::delete)
        orphans.clear()
    }

    fun song(id: Long): Song? = songs[id]

    /** The library's id of a new track kept from the radio, or null. */
    fun libraryId(id: Long): Long? = keptIds[id]

    /** Starts a radio from [start], over whatever was playing. */
    fun start(library: List<Song>, request: RadioStart) {
        val p = player ?: return
        stop()
        val playing = p.currentMediaItem?.mediaId?.takeIf { p.isPlaying }
        val start = request.over(playing?.let { id -> library.firstOrNull { it.id.toString() == id } })
        val settings = SettingsStore.settings.value
        val memory = RadioMemory.get(app.filesDir)
        val planner = RadioPlanner(library, start.seeds, start.openWith, memory, settings.radioNovelty)
        val s = RadioSession(Source(settings.downloadFormat), planner, memory, start.title)
        s.buffer = settings.radioBuffer
        session = s
        stateJob = scope.launch { s.state.collect { _state.value = it } }
        // Not the fill's job: the first track's arrival starts the fill, which must not find it busy.
        startJob = scope.launch {
            val first = s.next()
            if (session !== s) return@launch
            if (first == null) return@launch stop()
            p.shuffleModeEnabled = false
            p.repeatMode = Player.REPEAT_MODE_OFF
            val current = p.currentMediaItem
            if (current != null && current.mediaId == first.id.toString()) {
                // It's playing already: it goes on from where it is, the radio after it.
                ours += keyOf(current)
                editing++
                try {
                    val at = p.currentMediaItemIndex
                    if (at + 1 < p.mediaItemCount) p.removeMediaItems(at + 1, p.mediaItemCount)
                    if (at > 0) p.removeMediaItems(0, at)
                } finally {
                    editing--
                }
                p.play()
                fill()
            } else {
                p.setMediaItem(item(first))
                p.prepare()
                p.play()
            }
        }
    }

    fun stop() {
        val s = session ?: return
        session = null
        startJob?.cancel()
        fillJob?.cancel()
        stateJob?.cancel()
        _state.value = null
        ours.clear()
        // The new tracks still ahead go from the queue with their files; the one playing finishes first.
        scope.launch { s.stop() }
    }

    /** Saves a new track into the library and the favourites. */
    fun keep(songId: Long) {
        val s = session ?: return
        scope.launch { s.keep(songId)?.let { keptIds[songId] = it } }
    }

    /** Never this one again, and less like it; skips it if it's playing. */
    fun dislike(songId: Long) {
        val s = session ?: return
        s.dislike(songId)
        val p = player ?: return
        if (p.currentMediaItem?.mediaId != songId.toString()) return
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
        } else {
            // The next one is still downloading: it starts as soon as it's in.
            p.pause()
            skipWhenReady = true
        }
    }

    /** Set by a "not this" that found nothing queued after it. */
    private var skipWhenReady = false

    private fun fill() {
        val p = player ?: return
        val s = session ?: return
        if (fillJob?.isActive == true) return
        if (p.currentMediaItemIndex == C.INDEX_UNSET) return
        fillJob = scope.launch {
            // One at a time, so the first of them is ready to play before the rest are in.
            while (p.mediaItemCount - p.currentMediaItemIndex - 1 < s.buffer) {
                // Nothing left; it ends when the queue does.
                val next = s.next() ?: break
                if (session !== s) return@launch
                p.addMediaItem(item(next))
                if (skipWhenReady) {
                    skipWhenReady = false
                    p.seekToNextMediaItem()
                    p.play()
                }
            }
            skipWhenReady = false
        }
    }

    private fun item(song: Song): MediaItem {
        if (RadioTrack.isRadio(song.id)) songs[song.id] = song
        return song.toMediaItem().also { ours += keyOf(it) }
    }

    private fun keyOf(item: MediaItem) = item.mediaMetadata.extras?.getLong(QUEUE_KEY).toString()

    /**
     * The session is done with [track]'s file. Its entries leave the queue and the file goes, or,
     * while it plays, it's left to finish.
     */
    private fun release(track: RadioTrack) {
        val p = player
        if (p != null && p.currentMediaItem?.mediaId == track.songId.toString()) {
            orphans[track.songId] = track
            return
        }
        p?.let { removeEntries(it, track.songId) }
        delete(track)
    }

    /** Lets go of the orphans the player has moved off. Posted: called from inside the player's callbacks. */
    private fun sweep() {
        if (orphans.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            val current = player?.currentMediaItem?.mediaId
            orphans.values.filter { it.songId.toString() != current }.forEach { track ->
                orphans.remove(track.songId)
                player?.let { removeEntries(it, track.songId) }
                delete(track)
            }
        }
    }

    private fun removeEntries(p: Player, songId: Long) {
        val id = songId.toString()
        editing++
        try {
            for (i in p.mediaItemCount - 1 downTo 0) {
                if (i != p.currentMediaItemIndex && p.getMediaItemAt(i).mediaId == id) p.removeMediaItem(i)
            }
        } finally {
            editing--
        }
    }

    /** Above 0 while the radio itself takes entries out: not someone playing something else. */
    private var editing = 0

    private fun delete(track: RadioTrack) {
        Downloader.radioDiscard(track)
        songs.remove(track.songId)
    }

    private class Source(private val format: DownloadFormat) : RadioSource {
        override suspend fun find(query: String) = Downloader.radioFind(query)
        override suspend fun mix(videoId: String) = Downloader.radioMix(videoId)
        override suspend fun fetch(track: RadioTrack) = Downloader.radioFetch(track, format)
        override fun discard(track: RadioTrack) = release(track)
        override suspend fun keep(track: RadioTrack) = Downloader.radioKeep(track)
    }
}
