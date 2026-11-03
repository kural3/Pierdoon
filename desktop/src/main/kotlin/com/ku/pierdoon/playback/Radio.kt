package com.ku.pierdoon.playback

import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.platform.AppDirs
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The radio (beta) on the computer: keeps the [Player]'s queue one track ahead with what a
 * [RadioSession] picks. The same as the phone's, around the desktop player.
 *
 * A downloaded track's file goes together with its place in the queue: never one without the
 * other. The one playing is left to finish and goes once the player has moved off it.
 */
object Radio {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player get() = PlaybackController.player
    private var session: RadioSession? = null
    private var stateJob: Job? = null
    private var startJob: Job? = null
    private var fillJob: Job? = null
    private var started = false
    /** Queue entries the radio added, by their queue key. */
    private val ours = HashSet<Long>()
    /** Downloads let go of while playing: deleted once the player has moved off them. */
    private val orphans = HashMap<Long, RadioTrack>()
    /** New tracks kept in the library, by their radio id: outlive the session, for the saved queue. */
    private val keptIds = ConcurrentHashMap<Long, Long>()
    /** Above 0 while the radio itself takes entries out: not someone playing something else. */
    private var editing = 0
    /** Set by a "not this" that found nothing queued after it. */
    private var skipWhenReady = false

    private val _state = MutableStateFlow<RadioState?>(null)
    /** The radio playing now, or null. */
    val state: StateFlow<RadioState?> = _state.asStateFlow()

    /** Called once with the player's controller. */
    fun start() {
        if (started) return
        started = true
        Downloader.clearRadioCache()
        player.addListener { p, events ->
            if (Player.Event.Transition in events || Player.Event.Timeline in events) sweep()
            if (session != null) {
                // The radio picks its own order and never ends.
                if (Player.Event.Shuffle in events && p.shuffleModeEnabled) p.shuffleModeEnabled = false
                if (Player.Event.Repeat in events && p.repeatMode != Player.REPEAT_MODE_OFF) p.repeatMode = Player.REPEAT_MODE_OFF
            }
            if (Player.Event.Timeline in events && session != null && editing == 0) {
                // Something else was played in its place (before the first track came in, too): the radio is over.
                if ((0 until p.mediaItemCount).none { p.getMediaItemAt(it).queueKey in ours }) stop()
            }
            if (Player.Event.Transition in events) fill()
        }
        scope.launch {
            SettingsStore.settings.map { Triple(it.radioNovelty, it.radioBuffer, it.radioEnabled) }.distinctUntilChanged()
                .collect { (novelty, buffer, on) ->
                    session?.novelty = novelty
                    session?.buffer = buffer
                    if (!on) stop() else fill()
                }
        }
    }

    /** The library's id of a new track kept from the radio, or null. */
    fun libraryId(id: Long): Long? = keptIds[id]

    /** Starts a radio from [start], over whatever was playing. */
    fun start(library: List<Song>, start: RadioStart) {
        stop()
        val settings = SettingsStore.settings.value
        val memory = RadioMemory.get(AppDirs.files)
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
            val p = player
            p.shuffleModeEnabled = false
            p.repeatMode = Player.REPEAT_MODE_OFF
            p.setMediaItems(listOf(item(first)), 0, 0)
            p.prepare()
            p.play()
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
        val p = player
        if (p.currentMediaItem?.song?.id != songId) return
        if (p.nextIndexFor(p.currentMediaItemIndex, Player.REPEAT_MODE_OFF) >= 0) {
            p.seekToNext()
        } else {
            // The next one is still downloading: it starts as soon as it's in.
            p.pause()
            skipWhenReady = true
        }
    }

    private fun fill() {
        val s = session ?: return
        val p = player
        if (fillJob?.isActive == true) return
        if (p.currentMediaItemIndex < 0) return
        fillJob = scope.launch {
            // One at a time, so the first of them is ready to play before the rest are in.
            while (p.mediaItemCount - p.currentMediaItemIndex - 1 < s.buffer) {
                val next = s.next() ?: break
                if (session !== s) return@launch
                p.addMediaItem(item(next))
                if (skipWhenReady) {
                    skipWhenReady = false
                    p.seekToNext()
                    p.play()
                }
            }
            skipWhenReady = false
        }
    }

    private fun item(song: Song): MediaItem = MediaItem(song).also { ours += it.queueKey }

    /**
     * The session is done with [track]'s file. Its entries leave the queue and the file goes, or,
     * while it plays, it's left to finish.
     */
    private fun release(track: RadioTrack) {
        if (player.currentMediaItem?.song?.id == track.songId) {
            orphans[track.songId] = track
            return
        }
        removeEntries(track.songId)
        // Windows won't delete a file still open (the engine's prepared next): whatever stays is
        // cleared when the app next starts.
        Downloader.radioDiscard(track)
    }

    /** Lets go of the orphans the player has moved off. Posted: called from inside the player's events. */
    private fun sweep() {
        if (orphans.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            // A crossfade still plays the old track for a while, holding its file (and Windows its delete).
            delay(RELEASE_DELAY_MS)
            val current = player.currentMediaItem?.song?.id
            orphans.values.filter { it.songId != current }.forEach { track ->
                orphans.remove(track.songId)
                removeEntries(track.songId)
                Downloader.radioDiscard(track)
            }
        }
    }

    private fun removeEntries(songId: Long) {
        val p = player
        editing++
        try {
            for (i in p.mediaItemCount - 1 downTo 0) {
                if (i != p.currentMediaItemIndex && p.getMediaItemAt(i).song.id == songId) p.removeMediaItem(i)
            }
        } finally {
            editing--
        }
    }

    /** Past the longest crossfade (12 s). */
    private const val RELEASE_DELAY_MS = 14_000L

    private class Source(private val format: DownloadFormat) : RadioSource {
        override suspend fun find(query: String) = Downloader.radioFind(query)
        override suspend fun mix(videoId: String) = Downloader.radioMix(videoId)
        override suspend fun fetch(track: RadioTrack) = Downloader.radioFetch(track, format)
        override fun discard(track: RadioTrack) = release(track)
        override suspend fun keep(track: RadioTrack) = Downloader.radioKeep(track)
    }
}
