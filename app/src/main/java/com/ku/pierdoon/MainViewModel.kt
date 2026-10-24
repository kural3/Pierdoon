package com.ku.pierdoon

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.net.ConnectivityManager
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.MusicRepository
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.UserData
import com.ku.pierdoon.data.UserLibrary
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.data.lyrics.LyricsQuery
import com.ku.pierdoon.data.lyrics.LyricsRepository
import com.ku.pierdoon.data.lyrics.NetworkNotAllowedException
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.playback.PlaybackService
import com.ku.pierdoon.playback.PlayerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState
    data class Ready(val lyrics: Lyrics) : LyricsState
    data object Error : LyricsState
    data object WifiOnly : LyricsState
}

/** Position sampled at [atRealtimeMs]; lets the UI extrapolate instead of polling faster. */
@Immutable
data class PositionSnapshot(val positionMs: Long = 0L, val atRealtimeMs: Long = 0L, val playing: Boolean = false) {
    fun estimate(nowRealtimeMs: Long = SystemClock.elapsedRealtime()): Long =
        if (playing) positionMs + (nowRealtimeMs - atRealtimeMs) else positionMs
}

sealed interface LibraryState {
    data object Loading : LibraryState
    data class Ready(val songs: List<Song>) : LibraryState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MusicRepository(app)

    private val _library = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _collections = MutableStateFlow(MusicCollections())
    val collections: StateFlow<MusicCollections> = _collections.asStateFlow()

    private val userLibrary = UserLibrary(app, viewModelScope)
    val userData: StateFlow<UserData> = userLibrary.data

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    private var controller: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .buildAsync()

    /** Bumped on every player event, so paused UI can wait instead of polling. */
    private val eventTick = MutableStateFlow(0)

    /**
     * Playback position, polled only while someone collects it (the UI is started)
     * and only while audio is actually playing. Paused or backgrounded = zero wakeups.
     */
    val position: StateFlow<PositionSnapshot> = flow {
        while (true) {
            val tick = eventTick.value
            val c = controller
            val playing = c?.isPlaying == true
            emit(PositionSnapshot(c?.currentPosition ?: 0L, SystemClock.elapsedRealtime(), playing))
            if (playing) delay(POSITION_TICK_MS) else eventTick.first { it != tick }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PositionSnapshot())

    private val lyricsRepository = LyricsRepository(app)
    private val lyricsVisible = MutableStateFlow(false)
    private val lyricsRetry = MutableStateFlow(0)
    private var songsById: Map<Long, Song> = emptyMap()

    /** Fetched lazily: only while the lyrics panel is open, once per track. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val lyrics: StateFlow<LyricsState> = combine(
        _player.map { it.nowPlaying }.distinctUntilChanged { a, b -> a?.songId == b?.songId },
        lyricsVisible,
        lyricsRetry,
    ) { np, visible, _ -> if (visible) np else null }
        .flatMapLatest { np ->
            if (np == null) return@flatMapLatest flowOf<LyricsState>(LyricsState.Idle)
            flow<LyricsState> {
                lyricsRepository.peek(np.songId)?.let { emit(LyricsState.Ready(it)); return@flow }
                emit(LyricsState.Loading)
                val song = songsById[np.songId]
                val query = LyricsQuery(
                    songId = np.songId,
                    title = song?.title ?: np.title,
                    artist = song?.artist ?: np.artist,
                    album = song?.album.orEmpty(),
                    durationMs = song?.durationMs ?: _player.value.durationMs,
                )
                emit(
                    try {
                        LyricsState.Ready(lyricsRepository.get(query, allowNetwork = networkAllowed()))
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LyricsState.Idle)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState(player)
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                player.currentMediaItem?.mediaId?.toLongOrNull()?.let(userLibrary::recordPlayed)
            }
            eventTick.update { it + 1 }
        }
    }

    init {
        controllerFuture.addListener({
            val c = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            syncState(c)
            eventTick.update { it + 1 }
        }, ContextCompat.getMainExecutor(app))
    }

    fun loadLibrary(force: Boolean = false) {
        if (!force && _library.value is LibraryState.Ready) return
        viewModelScope.launch {
            val songs = runCatching { repository.loadSongs() }.getOrElse { emptyList() }
            songsById = songs.associateBy { it.id }
            _collections.value = withContext(Dispatchers.Default) { MusicCollections.from(songs) }
            _library.value = LibraryState.Ready(songs)
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, C.TIME_UNSET)
        c.prepare()
        c.play()
    }

    fun shuffleAll() {
        val songs = (_library.value as? LibraryState.Ready)?.songs ?: return
        if (songs.isNotEmpty()) playSongs(songs, songs.indices.random(), shuffle = true)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    /** Resolves stored ids against the current library, silently skipping deleted files. */
    fun resolve(ids: Collection<Long>): List<Song> = ids.mapNotNull { songsById[it] }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSongs(listOf(song), 0)
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSongs(listOf(song), 0)
        c.addMediaItem(song.toMediaItem())
    }

    fun toggleFavorite(songId: Long) = userLibrary.toggleFavorite(songId)
    fun createPlaylist(name: String, initialSongs: List<Long> = emptyList()) =
        userLibrary.createPlaylist(name, initialSongs)
    fun renamePlaylist(id: Long, name: String) = userLibrary.renamePlaylist(id, name)
    fun deletePlaylist(id: Long) = userLibrary.deletePlaylist(id)
    fun addToPlaylist(id: Long, songId: Long) = userLibrary.addToPlaylist(id, songId)
    fun removeFromPlaylist(id: Long, songId: Long) = userLibrary.removeFromPlaylist(id, songId)

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    private fun networkAllowed(): Boolean {
        if (!SettingsStore.settings.value.lyricsWifiOnly) return true
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        return cm?.isActiveNetworkMetered == false
    }

    suspend fun lyricsCacheSize(): Long = lyricsRepository.cacheSizeBytes()
    fun clearLyricsCache() { viewModelScope.launch { lyricsRepository.clear() } }
    fun rescanLibrary() = loadLibrary(force = true)

    fun setLyricsVisible(visible: Boolean) { lyricsVisible.value = visible }
    fun retryLyrics() { lyricsRetry.update { it + 1 } }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun syncState(p: Player) {
        val item = p.currentMediaItem
        _player.update {
            PlayerState(
                connected = true,
                nowPlaying = item?.let { mi ->
                    val md = mi.mediaMetadata
                    NowPlaying(
                        songId = mi.mediaId.toLongOrNull() ?: -1L,
                        albumId = md.artworkUri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() } ?: -1L,
                        title = md.title?.toString().orEmpty(),
                        artist = md.artist?.toString().orEmpty(),
                    )
                },
                isPlaying = p.isPlaying,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
                shuffle = p.shuffleModeEnabled,
                repeatMode = p.repeatMode,
            )
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
        const val STOP_TIMEOUT_MS = 1_000L
    }
}

private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
    )
    .build()
