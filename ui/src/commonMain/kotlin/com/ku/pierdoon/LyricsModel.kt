package com.ku.pierdoon

import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.data.lyrics.LyricsCandidate
import com.ku.pierdoon.data.lyrics.LyricsOffsets
import com.ku.pierdoon.data.lyrics.LyricsQuery
import com.ku.pierdoon.data.lyrics.LyricsRepository
import com.ku.pierdoon.data.lyrics.NetworkNotAllowedException
import com.ku.pierdoon.playback.NowPlaying
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
