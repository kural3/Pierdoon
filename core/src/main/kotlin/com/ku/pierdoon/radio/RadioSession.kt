package com.ku.pierdoon.radio

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What each app does its own way for the radio: yt-dlp, its cache and its library. */
interface RadioSource {
    /** The first video a search finds. */
    suspend fun find(query: String): RadioTrack?

    /** The video's mix, the video itself among them. */
    suspend fun mix(videoId: String): List<RadioTrack>

    /** Downloads [track] into the radio's cache, as a song to queue (its id [RadioTrack.songId]). */
    suspend fun fetch(track: RadioTrack): Song?

    /** Deletes what [fetch] downloaded. */
    fun discard(track: RadioTrack)

    /** Moves the downloaded [track] into the library for good; its id there. */
    suspend fun keep(track: RadioTrack): Long?
}

@Immutable
data class RadioState(
    /** What it started from: a track's or an artist's name, or empty for "my wave". */
    val seed: String,
    /** The new tracks it has queued so far, by their queue id. */
    val fresh: Map<Long, RadioTrack> = emptyMap(),
    /** Those of [fresh] now in the library, with their id there. */
    val kept: Map<Long, Long> = emptyMap(),
    /** Couldn't reach the internet (or yt-dlp) last time: playing from the library. */
    val offline: Boolean = false,
)

/**
 * One radio from start to stop. The player asks [next] whenever its queue runs short (one track
 * ahead is enough, so it is downloading the next while this one plays and doing nothing otherwise);
 * a new track is fetched into a cache and deleted once it's a few tracks behind, unless kept.
 */
class RadioSession(
    private val source: RadioSource,
    private val planner: RadioPlanner,
    private val memory: RadioMemory?,
    seed: String,
    /** A video to take the first mix from, when the seed has one already. */
    private var mixFrom: String? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var retryAt = 0L

    private fun failed() {
        failures++
        if (failures >= MAX_FAILURES) {
            retryAt = now() + RETRY_MS
            _state.update { it.copy(offline = true) }
        }
    }

    private val _state = MutableStateFlow(RadioState(seed))
    val state: StateFlow<RadioState> = _state.asStateFlow()

    private val lock = Mutex()
    private val fetched = ArrayDeque<RadioTrack>()
    private val mixed = HashSet<String>()
    private var failures = 0
    private var stopped = false

    var novelty: Int
        get() = planner.novelty
        set(value) { planner.novelty = value }

    /** New tracks queued ahead of the one playing: their files are kept as well as the few behind it. */
    var buffer: Int = 1

    fun isFresh(songId: Long): Boolean = songId in _state.value.fresh

    /** The next track to queue, or null when there's nothing left to play. */
    suspend fun next(): Song? = lock.withLock {
        repeat(MAX_TRIES) {
            if (stopped) return null
            if (failures >= MAX_FAILURES && now() >= retryAt) failures = MAX_FAILURES - 1
            val wantsNew = planner.novelty > 0 || !planner.hasLibrary
            if (wantsNew && planner.waiting < LOW_WATER && failures < MAX_FAILURES) refill()
            when (val pick = planner.next() ?: return null) {
                is RadioPlanner.Pick.Known -> return pick.song
                is RadioPlanner.Pick.New -> {
                    val song = runSafely { source.fetch(pick.track) }
                    if (song == null) {
                        planner.skipped(pick.track)
                        failed()
                        return@repeat
                    }
                    failures = 0
                    mixFrom = pick.track.videoId
                    remember(pick.track)
                    _state.update { it.copy(offline = false, fresh = it.fresh + (song.id to pick.track)) }
                    return song
                }
            }
        }
        null
    }

    /** Keeps the new track [songId] in the library; its id there, or null if it couldn't. */
    suspend fun keep(songId: Long): Long? {
        _state.value.kept[songId]?.let { return it }
        val track = _state.value.fresh[songId] ?: return null
        val id = runSafely { source.keep(track) } ?: return null
        lock.withLock { fetched.remove(track) }
        _state.update { it.copy(kept = it.kept + (songId to id)) }
        return id
    }

    /** "Not this": never again, and fewer like it. The player skips it. */
    fun dislike(songId: Long) {
        val track = _state.value.fresh[songId]
        if (track != null) memory?.dislike(track) else memory?.dislike(songId)
    }

    /** Deletes every downloaded track not kept. */
    suspend fun stop() {
        stopped = true
        lock.withLock {
            fetched.forEach { source.discard(it) }
            fetched.clear()
        }
    }

    private suspend fun refill() {
        val from = mixFrom ?: planner.seedSong()?.let { song ->
            runSafely { source.find("${song.artist} ${song.title}") }?.videoId?.also(planner::owns)
        }
        if (from == null || !mixed.add(from)) {
            // Every mix around here tried: start over from another of the library's tracks.
            mixFrom = null
            if (from == null) failed()
            return
        }
        val mix = runSafely { source.mix(from) }
        if (mix == null) failed() else planner.add(mix)
        mixFrom = null
    }

    private fun remember(track: RadioTrack) {
        fetched += track
        while (fetched.size > buffer + KEEP_BEHIND) source.discard(fetched.removeFirst())
    }

    private suspend fun <T> runSafely(block: suspend () -> T?): T? =
        try { block() } catch (e: CancellationException) { throw e } catch (_: Throwable) { null }

    companion object {
        /** Fewer new tracks waiting than this, and the next mix is fetched. */
        private const val LOW_WATER = 3
        /** Downloaded tracks kept for going back, the one playing among them, past the [buffer]. */
        private const val KEEP_BEHIND = 3
        private const val MAX_TRIES = 4
        /** This many failures in a row, and it stops trying the internet until one works again. */
        private const val MAX_FAILURES = 3
        /** Then it tries again after this long. */
        private const val RETRY_MS = 3 * 60_000L
    }
}
