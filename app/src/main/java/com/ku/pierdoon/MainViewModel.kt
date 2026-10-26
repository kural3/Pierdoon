package com.ku.pierdoon

import com.ku.pierdoon.data.ALBUM_ID
import com.ku.pierdoon.data.QUEUE_KEY
import com.ku.pierdoon.data.artworkUri
import com.ku.pierdoon.data.uri
import com.ku.pierdoon.data.toMediaItem
import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.database.ContentObserver
import android.content.IntentSender
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
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
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.TagOverride
import com.ku.pierdoon.data.TagOverrides
import com.ku.pierdoon.data.metadata.CoverEdit
import com.ku.pierdoon.data.update.AppRelease
import com.ku.pierdoon.data.update.UpdateState
import com.ku.pierdoon.data.update.Updater
import com.ku.pierdoon.data.metadata.MetadataSearch
import com.ku.pierdoon.data.metadata.TagWriter
import com.ku.pierdoon.data.metadata.TrackEdit
import com.ku.pierdoon.data.writeScaledJpeg
import com.ku.pierdoon.ui.components.ArtworkCache
import java.io.File
import com.ku.pierdoon.data.MusicRepository
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.UserData
import com.ku.pierdoon.data.UserLibrary
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.data.lyrics.LyricsOffsets
import com.ku.pierdoon.data.lyrics.LyricsQuery
import com.ku.pierdoon.data.lyrics.LyricsRepository
import com.ku.pierdoon.data.lyrics.NetworkNotAllowedException
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.playback.PlaybackService
import com.ku.pierdoon.playback.PlayerState
import com.ku.pierdoon.playback.QueueEntry
import com.ku.pierdoon.playback.QueueState
import com.ku.pierdoon.playback.nextRepeatMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
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

    private val userLibrary = UserLibrary.get(app)
    private val tagOverrides = TagOverrides.get(app)
    val userData: StateFlow<UserData> = userLibrary.data

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    /** Rebuilt only when the queue itself changes, not on every play/pause or seek. */
    private val _queue = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

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

    private val lyricsOffsets = LyricsOffsets(app)

    /** Hand-set sync correction of the current track's lyrics; positive = lines show later. */
    val lyricsOffset: StateFlow<Long> = combine(
        _player.map { it.nowPlaying?.songId }.distinctUntilChanged(),
        lyricsOffsets.version,
    ) { id, _ -> id?.let(lyricsOffsets::get) ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0L)

    fun shiftLyrics(deltaMs: Long) {
        _player.value.nowPlaying?.let { lyricsOffsets.shift(it.songId, deltaMs) }
    }

    fun resetLyricsOffset() {
        _player.value.nowPlaying?.let { lyricsOffsets.set(it.songId, 0L) }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState(player)
            if (events.containsAny(*QUEUE_EVENTS)) syncQueue(player)
            eventTick.update { it + 1 }
        }
    }

    init {
        controllerFuture.addListener({
            val c = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            syncState(c)
            syncQueue(c)
            eventTick.update { it + 1 }
        }, ContextCompat.getMainExecutor(app))
    }

    val update: StateFlow<UpdateState> = Updater.state
    private val _updatePrompt = MutableStateFlow(false)
    /** Whether the "new version" dialog is up. */
    val updatePrompt: StateFlow<Boolean> = _updatePrompt.asStateFlow()

    init {
        // Quietly on launch; a release the user put off with "Later" isn't offered again.
        if (SettingsStore.settings.value.checkUpdates && Updater.isDue()) {
            viewModelScope.launch {
                val s = Updater.check()
                if (s is UpdateState.Available && !Updater.isSkipped(s.release)) _updatePrompt.value = true
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            if (Updater.check() is UpdateState.Available) _updatePrompt.value = true
        }
    }

    fun showUpdate() { _updatePrompt.value = true }
    fun installUpdate(release: AppRelease) = Updater.install(release)

    fun dismissUpdate() {
        when (val s = Updater.state.value) {
            is UpdateState.Downloading -> Updater.cancel()
            is UpdateState.Available -> Updater.skip(s.release)
            else -> Unit
        }
        _updatePrompt.value = false
    }

    private var loadJob: Job? = null

    fun loadLibrary(force: Boolean = false) {
        if (!force && _library.value is LibraryState.Ready) return
        watchMediaStore()
        // A newer load supersedes one still running.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val songs = runCatching { repository.loadSongs() }.getOrElse { emptyList() }
            // Unrelated MediaStore changes (another app's ringtone, say) leave the UI alone.
            if ((_library.value as? LibraryState.Ready)?.songs == songs) return@launch
            songsById = songs.associateBy { it.id }
            _collections.value = withContext(Dispatchers.Default) { MusicCollections.from(songs) }
            _library.value = LibraryState.Ready(songs)
            controller?.let {
                refreshQueueItems(it)
                syncQueue(it)
            }
        }
    }

    /**
     * Files added, deleted or retagged show up without a manual rescan. The media scanner
     * reports a batch as a burst of changes, so they are coalesced into one reload; nothing
     * is registered once the screen's ViewModel is gone.
     */
    private val mediaStoreChanged =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            mediaStoreChanged.tryEmit(Unit)
        }
    }
    private var watching = false

    @OptIn(FlowPreview::class)
    private fun watchMediaStore() {
        if (watching) return
        watching = true
        getApplication<Application>().contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaStoreObserver,
        )
        viewModelScope.launch {
            mediaStoreChanged.debounce(MEDIA_STORE_DEBOUNCE_MS).collect { loadLibrary(force = true) }
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

    /** Several tracks right after the current one, in the order given. */
    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) return playSongs(songs, 0)
        c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
    }

    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) return playSongs(songs, 0)
        c.addMediaItems(songs.map { it.toMediaItem() })
    }

    fun addFavorites(songIds: List<Long>) = userLibrary.addFavorites(songIds)
    fun addToPlaylist(id: Long, songIds: List<Long>) = userLibrary.addToPlaylist(id, songIds)
    fun removeFromPlaylist(id: Long, songIds: Set<Long>) = userLibrary.removeFromPlaylist(id, songIds)

    fun toggleFavorite(songId: Long) = userLibrary.toggleFavorite(songId)
    fun createPlaylist(name: String, initialSongs: List<Long> = emptyList()) =
        userLibrary.createPlaylist(name, initialSongs)
    fun renamePlaylist(id: Long, name: String) = userLibrary.renamePlaylist(id, name)
    fun deletePlaylist(id: Long) {
        userLibrary.deletePlaylist(id)
        CoverStore.removePlaylistCover(id)
    }
    fun setPlaylistShape(id: Long, shape: String?) = userLibrary.setPlaylistShape(id, shape)
    fun setPlaylistCover(id: Long, image: Uri) {
        viewModelScope.launch {
            val file = importCover(image) ?: return@launch
            withContext(Dispatchers.IO) { CoverStore.setPlaylistCover(id, file) }
            file.delete()
        }
    }
    fun removePlaylistCover(id: Long) = CoverStore.removePlaylistCover(id)
    fun addToPlaylist(id: Long, songId: Long) = userLibrary.addToPlaylist(id, songId)
    fun removeFromPlaylist(id: Long, songId: Long) = userLibrary.removeFromPlaylist(id, songId)

    fun playQueueEntry(key: String) {
        val c = controller ?: return
        val index = c.indexOfQueueKey(key) ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun removeQueueEntry(key: String) {
        val c = controller ?: return
        c.indexOfQueueKey(key)?.let(c::removeMediaItem)
    }

    /** Moves an upcoming entry to [position] among the upcoming ones (0 = right after the current). */
    fun moveQueueEntry(key: String, position: Int) {
        val c = controller ?: return
        val from = c.indexOfQueueKey(key) ?: return
        val to = (c.currentMediaItemIndex + 1 + position).coerceIn(0, c.mediaItemCount - 1)
        if (from != to) c.moveMediaItem(from, to)
    }

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
        c.repeatMode = nextRepeatMode(c.repeatMode)
    }

    // Track editing ------------------------------------------------------------------------

    fun song(id: Long): Song? = songsById[id]

    /** The track as its file has it, to tell in-app edits apart from what's really there. */
    suspend fun fileTags(id: Long): Song? = repository.fileTags(id)

    fun hasInAppEdits(songId: Long): Boolean = tagOverrides.has(songId) || CoverStore.songVersion(songId) != null

    fun searchMetadata(query: String, durationMs: Long) = MetadataSearch.search(query, durationMs)

    /** A picture from the gallery as a cover-sized JPEG; null if it couldn't be read as an image. */
    suspend fun importCover(uri: Uri): File? = withContext(Dispatchers.IO) {
        val raw = coverPickFile("img")
        runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)
                ?.use { input -> raw.outputStream().use { input.copyTo(it) } }
        }
        toCoverJpeg(raw)
    }

    suspend fun downloadCover(url: String): File? {
        val bytes = MetadataSearch.download(url) ?: return null
        return withContext(Dispatchers.IO) {
            val raw = coverPickFile("img")
            raw.writeBytes(bytes)
            toCoverJpeg(raw)
        }
    }

    private fun toCoverJpeg(raw: File): File? {
        val out = coverPickFile("jpg")
        val ok = writeScaledJpeg(raw, out)
        raw.delete()
        return out.takeIf { ok }
    }

    private fun coverPickFile(ext: String): File {
        val dir = File(getApplication<Application>().cacheDir, COVER_PICKS).apply { mkdirs() }
        return File(dir, "pick_${SystemClock.elapsedRealtimeNanos()}.$ext")
    }

    /** Saves [edit] as app records: the file stays untouched. Empty fields fall back to the file's tags. */
    suspend fun saveInApp(song: Song, edit: TrackEdit) {
        val file = repository.fileTags(song.id)
        withContext(Dispatchers.IO) {
            tagOverrides.set(
                song.id,
                TagOverride(
                    title = edit.title.trim().takeIf { it.isNotEmpty() && it != file?.title },
                    artist = edit.artist.trim().takeIf { it.isNotEmpty() && it != file?.artist },
                    album = edit.album.trim().takeIf { it.isNotEmpty() && it != file?.album },
                ),
            )
            when (val cover = edit.cover) {
                CoverEdit.Keep -> Unit
                CoverEdit.Remove -> CoverStore.removeSongCover(song.id)
                is CoverEdit.Replace -> CoverStore.setSongCover(song.id, cover.file)
            }
        }
        refreshAfterEdit()
    }

    /** Android 11+ asks the user before an app may change someone else's file; null below that. */
    fun fileWriteRequest(song: Song): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createWriteRequest(getApplication<Application>().contentResolver, listOf(song.uri)).intentSender
        } else {
            null
        }

    /** Whether the file's format takes tags at all (raw AAC, for one, doesn't). */
    suspend fun canWriteTags(song: Song): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            TagWriter.canWrite(TagWriter.displayName(getApplication(), song))

    /**
     * Writes [edit] into the file's tags; call once the user granted [fileWriteRequest].
     * The app's own edits of this track are folded in and then dropped: the file is the truth now.
     */
    suspend fun saveToFile(song: Song, edit: TrackEdit): Boolean {
        val app = getApplication<Application>()
        val name = TagWriter.displayName(app, song) ?: return false
        val cover = withContext(Dispatchers.IO) {
            when (val c = edit.cover) {
                // A cover picked "in the app" earlier goes into the file rather than being lost.
                CoverEdit.Keep -> CoverStore.songFile(song.id)?.let { runCatching { it.readBytes() }.getOrNull() }
                CoverEdit.Remove -> null
                is CoverEdit.Replace -> runCatching { c.file.readBytes() }.getOrNull()
            }
        }
        // The player holds the file open while it plays; rewriting it underneath breaks playback.
        val c = controller?.takeIf { it.currentMediaItem?.mediaId == song.id.toString() }
        val resume = c?.let { Triple(it.currentMediaItemIndex, it.currentPosition, it.playWhenReady) }
        c?.stop()
        val ok = TagWriter.write(
            app, song, name,
            title = edit.title.trim(),
            artist = edit.artist.trim(),
            album = edit.album.trim(),
            cover = cover,
            removeCover = edit.cover == CoverEdit.Remove,
        )
        if (ok) {
            tagOverrides.set(song.id, null)
            CoverStore.removeSongCover(song.id)
            ArtworkCache.invalidateAlbum(song.albumId)
        }
        if (c != null && resume != null) {
            val (index, positionMs, playWhenReady) = resume
            c.seekTo(index, positionMs)
            c.prepare()
            c.playWhenReady = playWhenReady
        }
        if (ok) refreshAfterEdit()
        return ok
    }

    /**
     * Android 11+ asks the user before an app may delete someone else's file (the system dialog
     * is the confirmation); null below that, where deleting would need write access to storage.
     */
    /** One system consent for all of [songs]; null below Android 11, where apps can't ask for it. */
    fun deleteRequest(songs: List<Song>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && songs.isNotEmpty()) {
            MediaStore.createDeleteRequest(getApplication<Application>().contentResolver, songs.map { it.uri }).intentSender
        } else {
            null
        }

    /** The user agreed and the system deleted the file: forget everything the app kept about it. */
    fun onSongDeleted(songId: Long) {
        controller?.let { c ->
            val mediaId = songId.toString()
            for (i in c.mediaItemCount - 1 downTo 0) {
                if (c.getMediaItemAt(i).mediaId == mediaId) c.removeMediaItem(i)
            }
        }
        userLibrary.forgetSong(songId)
        lyricsOffsets.set(songId, 0L)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                tagOverrides.set(songId, null)
                CoverStore.removeSongCover(songId)
            }
            lyricsRepository.forget(songId)
        }
        loadLibrary(force = true)
    }

    /** Drops the app's edits of a track: tags and cover come from the file again. */
    suspend fun resetEdits(song: Song) {
        withContext(Dispatchers.IO) {
            tagOverrides.set(song.id, null)
            CoverStore.removeSongCover(song.id)
        }
        refreshAfterEdit()
    }

    private suspend fun refreshAfterEdit() {
        loadLibrary(force = true)
        loadJob?.join()
        // A cover-only change leaves the song list equal, so the load above may skip the queue.
        controller?.let(::refreshQueueItems)
    }

    /** Queued entries carry the tags they were queued with; edited tracks get their new ones. */
    private fun refreshQueueItems(p: Player) {
        for (i in 0 until p.mediaItemCount) {
            val item = p.getMediaItemAt(i)
            val song = songsById[item.mediaId.toLongOrNull()] ?: continue
            val md = item.mediaMetadata
            if (md.title?.toString() == song.title && md.artist?.toString() == song.artist &&
                md.albumTitle?.toString() == song.album && md.artworkUri == song.artworkUri
            ) continue
            val key = md.extras?.getLong(QUEUE_KEY, 0L)?.takeIf { it != 0L }
            // Same file, new metadata: Media3 swaps it in without interrupting playback.
            p.replaceMediaItem(i, if (key != null) song.toMediaItem(key) else song.toMediaItem())
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
                        albumId = md.albumId(),
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

    private fun syncQueue(p: Player) {
        val timeline = p.currentTimeline
        if (timeline.isEmpty) {
            _queue.value = QueueState()
            return
        }
        // Walk the play order (shuffle included) from the current track. A shuffled queue on
        // "repeat all" goes round, so it lists every other track; in list order it stops at the
        // end, which keeps drag positions meaning "right after the current track".
        val wrap = p.shuffleModeEnabled && p.repeatMode == Player.REPEAT_MODE_ALL
        val order = ArrayList<Int>()
        var i = p.currentMediaItemIndex
        while (i != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += i
            i = timeline.getNextWindowIndex(
                i, if (wrap) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF, p.shuffleModeEnabled,
            )
        }
        val seen = HashSet<String>()
        val entries = order.map { index ->
            val item = p.getMediaItemAt(index)
            // LazyColumn needs unique keys; an item without one (or a clash) falls back to its slot.
            val key = queueKeyOf(item, index).let { if (seen.add(it)) it else "slot:$index" }
            QueueEntry(key, songsById[item.mediaId.toLongOrNull()] ?: item.toSong())
        }
        _queue.value = QueueState(entries.first(), entries.drop(1), canReorder = !p.shuffleModeEnabled)
    }

    override fun onCleared() {
        File(getApplication<Application>().cacheDir, COVER_PICKS).listFiles()?.forEach { it.delete() }
        if (watching) getApplication<Application>().contentResolver.unregisterContentObserver(mediaStoreObserver)
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
        const val STOP_TIMEOUT_MS = 1_000L
        const val MEDIA_STORE_DEBOUNCE_MS = 1_500L
        const val COVER_PICKS = "cover-picks"
        val QUEUE_EVENTS = intArrayOf(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            Player.EVENT_REPEAT_MODE_CHANGED,
        )
    }
}

private fun queueKeyOf(item: MediaItem, index: Int): String =
    item.mediaMetadata.extras?.getLong(QUEUE_KEY, 0L)?.takeIf { it != 0L }?.let { "q:$it" } ?: "slot:$index"

private fun Player.indexOfQueueKey(key: String): Int? =
    (0 until mediaItemCount).firstOrNull { queueKeyOf(getMediaItemAt(it), it) == key }

/** A queued file that has left the library still shows with what its metadata knows. */
private fun MediaItem.toSong(): Song {
    val md = mediaMetadata
    return Song(
        id = mediaId.toLongOrNull() ?: -1L,
        title = md.title?.toString().orEmpty(),
        artist = md.artist?.toString().orEmpty(),
        album = md.albumTitle?.toString().orEmpty(),
        albumId = md.albumId(),
        durationMs = 0L,
    )
}

/** Album of a queued item: from the extra, or (items queued before it existed) the art URI. */
private fun MediaMetadata.albumId(): Long =
    extras?.getLong(ALBUM_ID, -1L)?.takeIf { it != -1L }
        ?: artworkUri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() } ?: -1L
