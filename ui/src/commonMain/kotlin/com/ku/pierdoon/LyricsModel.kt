package com.ku.pierdoon

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.data.lyrics.LyricsCandidate
import com.ku.pierdoon.data.lyrics.LyricsOffsets
import com.ku.pierdoon.data.lyrics.LyricsQuery
import com.ku.pierdoon.data.lyrics.LyricsRepository
import com.ku.pierdoon.data.lyrics.NetworkNotAllowedException
import com.ku.pierdoon.data.lyrics.quotableLines
import com.ku.pierdoon.playback.NowPlaying
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The lyrics side of both apps' view models: the playing track's lyrics, fetched only while the
 * panel is open and once per track; their hand-set sync offset; searching for others, picking
 * one, and lyrics the user wrote.
 *
 * @param nowPlaying the track that plays, as it changes.
 * @param current the track that plays now, and how long it is.
 * @param song a track of the library by id, for its full tags; null for one that isn't loaded.
 * @param networkAllowed false while lyrics may only come from the cache (Wi-Fi only, on mobile data).
 */
class LyricsModel(
    private val scope: CoroutineScope,
    private val repository: LyricsRepository,
    private val offsets: LyricsOffsets,
    nowPlaying: Flow<NowPlaying?>,
    private val current: () -> Pair<NowPlaying?, Long>,
    private val song: (Long) -> Song?,
    private val networkAllowed: () -> Boolean,
    stopTimeoutMs: Long,
) {
    private val visible = MutableStateFlow(false)
    private val retries = MutableStateFlow(0)

    /** Fetched lazily: only while the lyrics panel is open, once per track. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val lyrics: StateFlow<LyricsState> = combine(
        nowPlaying.distinctUntilChanged { a, b -> a?.songId == b?.songId },
        visible,
        retries,
    ) { np, shown, _ -> if (shown) np else null }
        .flatMapLatest { np ->
            if (np == null) return@flatMapLatest flowOf<LyricsState>(LyricsState.Idle)
            flow<LyricsState> {
                repository.peek(np.songId)?.let { emit(LyricsState.Ready(it, repository.isOwn(np.songId))); return@flow }
                emit(LyricsState.Loading)
                val s = song(np.songId)
                val query = LyricsQuery(
                    songId = np.songId,
                    title = s?.title ?: np.title,
                    artist = s?.artist ?: np.artist,
                    album = s?.album.orEmpty(),
                    durationMs = s?.durationMs ?: current().second,
                )
                emit(
                    try {
                        LyricsState.Ready(
                            repository.get(query, allowNetwork = networkAllowed()),
                            repository.isOwn(np.songId),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: NetworkNotAllowedException) {
                        LyricsState.WifiOnly
                    } catch (_: Exception) {
                        LyricsState.Error
                    }
                )
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMs), LyricsState.Idle)

    /** Hand-set sync correction of the current track's lyrics; positive = lines show later. */
    val offset: StateFlow<Long> = combine(
        nowPlaying.map { it?.songId }.distinctUntilChanged(),
        offsets.version,
    ) { id, _ -> id?.let(offsets::get) ?: 0L }
        .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMs), 0L)

    /** Changes whenever any track's lyrics offset does. */
    val offsetsVersion: StateFlow<Int> get() = offsets.version

    fun offsetFor(songId: Long): Long = offsets.get(songId)

    fun shift(deltaMs: Long) {
        current().first?.let { offsets.shift(it.songId, deltaMs) }
    }

    fun resetOffset() {
        current().first?.let { offsets.set(it.songId, 0L) }
    }

    fun setVisible(shown: Boolean) { visible.value = shown }
    fun retry() { retries.update { it + 1 } }

    /** Lyrics of any track, not only the one on screen (the phone asks the computer). Null if unknown or failed. */
    suspend fun lyricsFor(songId: Long): Lyrics? {
        repository.peek(songId)?.let { return it }
        val s = song(songId) ?: return null
        return try {
            repository.get(
                LyricsQuery(s.id, s.title, s.artist, s.album, s.durationMs),
                allowNetwork = networkAllowed(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** The tracks the last few quotes came from, newest last: the next one comes from another. */
    private val recentQuotes = ArrayDeque<Long>()
    private var prefetch: Job? = null

    /**
     * A line of the lyrics of a random track of [library]. It comes from lyrics already on this
     * device when there are any from a track not quoted lately; otherwise a few tracks' lyrics are
     * fetched for it (where the network may be used). Each call also fetches a few more in the
     * background, so the lines keep coming from new songs. Null when no lyrics with words are found.
     */
    suspend fun randomQuote(library: Collection<Long>): LyricQuote? {
        val pool = library as? Set<Long> ?: library.toSet()
        val stored = repository.storedIds()
        val unfetched = pool.filter { it !in stored }
        if (networkAllowed() && prefetch?.isActive != true && unfetched.isNotEmpty()) {
            prefetch = scope.launch { unfetched.shuffled().take(QUOTE_PREFETCH).forEach { lyricsFor(it) } }
        }
        // Only [library]'s tracks: the quiz asks about one playlist or album.
        val fresh = stored.filter { it in pool && it !in recentQuotes && song(it) != null }.shuffled()
        val quote = quoteFrom(fresh.take(QUOTE_TRIES), allowNetwork = false)
            ?: (if (networkAllowed()) quoteFrom(unfetched.shuffled().take(QUOTE_FETCHES), allowNetwork = true) else null)
            // Nothing new anywhere: an older one again rather than nothing.
            ?: quoteFrom(recentQuotes.filter { it in pool }.shuffled(), allowNetwork = false)
            ?: return null
        recentQuotes.remove(quote.songId)
        recentQuotes.addLast(quote.songId)
        while (recentQuotes.size > QUOTE_RECENT) recentQuotes.removeFirst()
        return quote
    }

    private suspend fun quoteFrom(ids: List<Long>, allowNetwork: Boolean): LyricQuote? {
        for (id in ids) {
            val s = song(id) ?: continue
            val lyrics = if (allowNetwork) {
                withTimeoutOrNull(QUOTE_FETCH_TIMEOUT_MS) { lyricsFor(id) }
            } else {
                try {
                    repository.get(LyricsQuery(s.id, s.title, s.artist, s.album, s.durationMs), allowNetwork = false)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            } ?: continue
            val line = lyrics.quotableLines().randomOrNull() ?: continue
            // Synced lyrics say where the line is sung; quotableLines trims, so compare trimmed.
            val at = (lyrics as? Lyrics.Synced)?.lines?.firstOrNull { it.text.trim() == line }?.timeMs
            return LyricQuote(s.id, line, s.title, s.artist, at)
        }
        return null
    }

    /** Where a lyrics search starts: the current track's artist and title. */
    fun searchText(): String {
        val np = current().first ?: return ""
        val s = song(np.songId)
        return listOf(s?.artist ?: np.artist, s?.title ?: np.title).filter { it.isNotBlank() }.joinToString(" ")
    }

    suspend fun search(text: String): List<LyricsCandidate> = repository.search(text)

    /** Puts lyrics picked by hand on the current track, for good. */
    fun pick(lyrics: Lyrics) {
        val id = current().first?.songId ?: return
        scope.launch {
            repository.choose(id, lyrics)
            retry()
        }
    }

    suspend fun own(songId: Long): String? = repository.ownText(songId)

    /** Lyrics the user wrote in the editor: they stay over anything found, until removed. */
    fun saveOwn(songId: Long, text: String) {
        scope.launch {
            runCatching { repository.saveOwn(songId, text) }
            retry()
        }
    }

    fun removeOwn(songId: Long) {
        scope.launch {
            repository.removeOwn(songId)
            retry()
        }
    }

    suspend fun cacheSize(): Long = repository.cacheSizeBytes()
    fun clearCache() { scope.launch { repository.clear() } }

    /** A track that's gone: its offset now, its lyrics on [forget]. */
    fun dropOffset(songId: Long) = offsets.set(songId, 0L)

    suspend fun forget(songId: Long) = repository.forget(songId)
}

/** How many tracks with stored lyrics a quote is looked for in, and how many are fetched for one. */
private const val QUOTE_TRIES = 40
private const val QUOTE_FETCHES = 3
private const val QUOTE_FETCH_TIMEOUT_MS = 4_000L

/** Tracks whose lyrics are fetched in the background on each quote, and how many quoted ones are skipped. */
private const val QUOTE_PREFETCH = 3
private const val QUOTE_RECENT = 5

/**
 * A line of a song, for the home screen's greeting and the lyrics quiz. [atMs] is where in the
 * track it's sung (by the lyrics, before the user's offset); null for unsynced lyrics.
 */
@Immutable
data class LyricQuote(val songId: Long, val line: String, val title: String, val artist: String, val atMs: Long? = null)
