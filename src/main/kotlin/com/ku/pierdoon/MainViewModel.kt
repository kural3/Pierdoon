package com.ku.pierdoon

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.MusicRepository
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.TagOverride
import com.ku.pierdoon.data.TagOverrides
import com.ku.pierdoon.data.UserData
import com.ku.pierdoon.data.UserLibrary
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.data.lyrics.LyricsOffsets
import com.ku.pierdoon.data.lyrics.LyricsQuery
import com.ku.pierdoon.data.lyrics.LyricsRepository
import com.ku.pierdoon.data.lyrics.NetworkNotAllowedException
import com.ku.pierdoon.data.metadata.CoverEdit
import com.ku.pierdoon.data.metadata.MetadataSearch
import com.ku.pierdoon.data.metadata.TagWriter
import com.ku.pierdoon.data.metadata.TrackEdit
import com.ku.pierdoon.data.update.AppRelease
import com.ku.pierdoon.data.update.UpdateState
import com.ku.pierdoon.data.update.Updater
import com.ku.pierdoon.data.writeScaledJpeg
import com.ku.pierdoon.platform.AppDirs
import com.ku.pierdoon.platform.SystemClock
import com.ku.pierdoon.playback.MediaItem
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.playback.PlaybackController
import com.ku.pierdoon.playback.Player
import com.ku.pierdoon.playback.PlayerState
import com.ku.pierdoon.playback.QueueEntry
import com.ku.pierdoon.playback.QueueState
import com.ku.pierdoon.playback.nextRepeatMode
import com.ku.pierdoon.ui.components.ArtworkCache
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

/** The app's state and actions, shared by every screen. One per process, like the window. */
class MainViewModel {

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = MusicRepository()

    private val _library = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _collections = MutableStateFlow(MusicCollections())
    val collections: StateFlow<MusicCollections> = _collections.asStateFlow()

    private val userLibrary = UserLibrary.get()
    private val tagOverrides = TagOverrides.get()
    val userData: StateFlow<UserData> = userLibrary.data

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    /** Rebuilt only when the queue itself changes, not on every play/pause or seek. */
    private val _queue = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    private val controller: Player = PlaybackController.player

    /** Bumped on every player event, so paused UI can wait instead of polling. */
    private val eventTick = MutableStateFlow(0)

    /** Playback position, polled only while someone collects it and only while audio is playing. */
    val position: StateFlow<PositionSnapshot> = flow {
        while (true) {
            val tick = eventTick.value
            val c = controller
            val playing = c.isPlaying
            emit(PositionSnapshot(c.currentPosition, SystemClock.elapsedRealtime(), playing))
            if (playing) delay(POSITION_TICK_MS) else eventTick.first { it != tick }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PositionSnapshot())

    private val lyricsRepository = LyricsRepository()
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

    private val lyricsOffsets = LyricsOffsets()

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

    private val listener = Player.Listener { p, events ->
        syncState(p)
        if (events.any { it in QUEUE_EVENTS }) syncQueue(p)
        eventTick.update { it + 1 }
    }

    init {
        controller.addListener(listener)
        syncState(controller)
        syncQueue(controller)
        viewModelScope.launch {
            Downloader.added.collect {
                // Downloads land in Music/Pier.doon; a library without that folder would never show them.
                val folder = Downloader.folder.absolutePath
                val covered = SettingsStore.settings.value.musicFolders.any { root ->
                    folder.startsWith(root.trimEnd('\\', '/') + File.separator, ignoreCase = true) || folder.equals(root, ignoreCase = true)
                }
                if (!covered) SettingsStore.update { it.copy(musicFolders = it.musicFolders + folder) }
                loadLibrary(force = true)
            }
        }
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
    private var lastLoadAt = 0L

    fun loadLibrary(force: Boolean = false) {
        if (!force && _library.value is LibraryState.Ready) return
        // A newer load supersedes one still running.
        loadJob?.cancel()
        lastLoadAt = SystemClock.elapsedRealtime()
        loadJob = viewModelScope.launch {
            val songs = runCatching { repository.loadSongs() }.getOrElse { emptyList() }
            val first = _library.value !is LibraryState.Ready
            if (!first && (_library.value as? LibraryState.Ready)?.songs == songs) return@launch
            songsById = songs.associateBy { it.id }
            ArtworkCache.registerAlbumFiles(songs.associate { it.albumId to it.path })
            _collections.value = withContext(Dispatchers.Default) { MusicCollections.from(songs) }
            _library.value = LibraryState.Ready(songs)
            if (first) PlaybackController.restore(songsById)
            refreshQueueItems(controller)
            syncQueue(controller)
        }
    }

    /**
     * Files added, deleted or retagged elsewhere show up when the window comes back to the front.
     * Cheap: only files whose size or date changed are read again.
     */
    fun onWindowFocused() {
        if (_library.value !is LibraryState.Ready) return
        if (SystemClock.elapsedRealtime() - lastLoadAt < FOCUS_RESCAN_MS) return
        loadLibrary(force = true)
    }

    fun addMusicFolder(folder: File) {
        val path = folder.absolutePath
        SettingsStore.update { s -> if (s.musicFolders.any { it.equals(path, ignoreCase = true) }) s else s.copy(musicFolders = s.musicFolders + path) }
        loadLibrary(force = true)
    }

    fun removeMusicFolder(path: String) {
        SettingsStore.update { s -> s.copy(musicFolders = s.musicFolders.filterNot { it == path }) }
        loadLibrary(force = true)
    }

    fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean = false) {
        val c = controller
        c.shuffleModeEnabled = false
        c.setMediaItems(songs.map { MediaItem(it) }, startIndex, 0L)
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.play()
    }

    fun shuffleAll() {
        val songs = (_library.value as? LibraryState.Ready)?.songs ?: return
        if (songs.isNotEmpty()) playSongs(songs, songs.indices.random(), shuffle = true)
    }

    fun togglePlay() = PlaybackController.togglePlay()

    /** Resolves stored ids against the current library, silently skipping deleted files. */
    fun resolve(ids: Collection<Long>): List<Song> = ids.mapNotNull { songsById[it] }

    fun playNext(song: Song) = playNext(listOf(song))
    fun addToQueue(song: Song) = addToQueue(listOf(song))

    /** Several tracks right after the current one, in the order given. */
    fun playNext(songs: List<Song>) {
        val c = controller
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) return playSongs(songs, 0)
        c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { MediaItem(it) })
    }

    fun addToQueue(songs: List<Song>) {
        val c = controller
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) return playSongs(songs, 0)
        c.addMediaItems(songs.map { MediaItem(it) })
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
    fun setPlaylistCover(id: Long, image: File) {
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
        val c = controller
        val index = c.indexOfQueueKey(key) ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun removeQueueEntry(key: String) {
        val c = controller
        c.indexOfQueueKey(key)?.let(c::removeMediaItem)
    }

    /** Moves an upcoming entry to [position] among the upcoming ones (0 = right after the current). */
    fun moveQueueEntry(key: String, position: Int) {
        val c = controller
        val from = c.indexOfQueueKey(key) ?: return
        val to = (c.currentMediaItemIndex + 1 + position).coerceIn(0, c.mediaItemCount - 1)
        if (from != to) c.moveMediaItem(from, to)
    }

    fun next() = controller.seekToNext()
    fun previous() = controller.seekToPrevious()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    /** Arrow keys in the player: a few seconds either way. */
    fun seekBy(deltaMs: Long) {
        val c = controller
        if (c.currentMediaItem == null) return
        val target = (c.currentPosition + deltaMs).coerceAtLeast(0)
        val duration = c.duration.takeIf { it > 0 }
        c.seekTo(if (duration != null) target.coerceAtMost(duration - 500) else target)
    }

    fun setVolume(volume: Float) = SettingsStore.update { it.copy(volume = volume.coerceIn(0f, 1f), muted = false) }
    fun toggleMute() = SettingsStore.update { it.copy(muted = !it.muted) }

    private fun networkAllowed(): Boolean = !SettingsStore.settings.value.lyricsWifiOnly

    suspend fun lyricsCacheSize(): Long = lyricsRepository.cacheSizeBytes()
    fun clearLyricsCache() { viewModelScope.launch { lyricsRepository.clear() } }
    fun rescanLibrary() = loadLibrary(force = true)

    fun setLyricsVisible(visible: Boolean) { lyricsVisible.value = visible }
    fun retryLyrics() { lyricsRetry.update { it + 1 } }

    fun toggleShuffle() {
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeat() {
        controller.repeatMode = nextRepeatMode(controller.repeatMode)
    }

    // Track editing ------------------------------------------------------------------------

    fun song(id: Long): Song? = songsById[id]

    /** The track as its file has it, to tell in-app edits apart from what's really there. */
    suspend fun fileTags(id: Long): Song? = songsById[id]?.let { repository.fileTags(it) }

    fun hasInAppEdits(songId: Long): Boolean = tagOverrides.has(songId) || CoverStore.songVersion(songId) != null

    fun searchMetadata(query: String, durationMs: Long) = MetadataSearch.search(query, durationMs)

    /** A picture file as a cover-sized JPEG; null if it couldn't be read as an image. */
    suspend fun importCover(image: File): File? = withContext(Dispatchers.IO) {
        val raw = coverPickFile("img")
        runCatching { image.copyTo(raw, overwrite = true) }
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
        val dir = File(AppDirs.cache, COVER_PICKS).apply { mkdirs() }
        return File(dir, "pick_${SystemClock.elapsedRealtimeNanos()}.$ext")
    }

    /** Saves [edit] as app records: the file stays untouched. Empty fields fall back to the file's tags. */
    suspend fun saveInApp(song: Song, edit: TrackEdit) {
        val file = repository.fileTags(song)
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

    /** Whether the file's format takes tags at all (raw AAC, for one, doesn't). */
    suspend fun canWriteTags(song: Song): Boolean = TagWriter.canWrite(song.file.name) && song.file.canWrite()

    /**
     * Writes [edit] into the file's tags.
     * The app's own edits of this track are folded in and then dropped: the file is the truth now.
     */
    suspend fun saveToFile(song: Song, edit: TrackEdit): Boolean {
        val cover = withContext(Dispatchers.IO) {
            when (val c = edit.cover) {
                // A cover picked "in the app" earlier goes into the file rather than being lost.
                CoverEdit.Keep -> CoverStore.songFile(song.id)?.let { runCatching { it.readBytes() }.getOrNull() }
                CoverEdit.Remove -> null
                is CoverEdit.Replace -> runCatching { c.file.readBytes() }.getOrNull()
            }
        }
        // The decoder holds the file open while it plays; rewriting it underneath breaks playback.
        val c = controller.takeIf { it.currentMediaItem?.song?.id == song.id }
        val resume = c?.let { Triple(it.currentMediaItemIndex, it.currentPosition, it.playWhenReady) }
        c?.stop()?.let { awaitClosed(it) }
        val ok = TagWriter.write(
            song,
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
     * Sends [songs] to the Recycle Bin (where they can be restored from), then forgets them.
     * @return how many went.
     */
    suspend fun deleteSongs(songs: List<Song>): Int {
        // A playing file can't be moved while the decoder has it open.
        val playing = controller.currentMediaItem?.song?.id
        if (songs.any { it.id == playing }) awaitClosed(controller.stop())
        val deleted = withContext(Dispatchers.IO) {
            songs.filter { s ->
                runCatching {
                    val desktop = Desktop.getDesktop()
                    if (desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) desktop.moveToTrash(s.file) else s.file.delete()
                }.getOrDefault(false)
            }
        }
        deleted.forEach { onSongDeleted(it.id) }
        return deleted.size
    }

    /** Windows won't replace or move a file the decoder still has open: wait for it to let go. */
    private suspend fun awaitClosed(closed: java.util.concurrent.CompletableFuture<Unit>) {
        withTimeoutOrNull(CLOSE_TIMEOUT_MS) { closed.await() }
    }

    /** The file is gone: forget everything the app kept about it. */
    private fun onSongDeleted(songId: Long) {
        val c = controller
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).song.id == songId) c.removeMediaItem(i)
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
        refreshQueueItems(controller)
    }

    /** Queued entries carry the tags they were queued with; edited tracks get their new ones. */
    private fun refreshQueueItems(p: Player) {
        for (i in 0 until p.mediaItemCount) {
            val item = p.getMediaItemAt(i)
            val song = songsById[item.song.id] ?: continue
            if (song == item.song) continue
            p.replaceMediaItem(i, item.copy(song = song))
        }
    }

    private fun syncState(p: Player) {
        val item = p.currentMediaItem
        _player.update {
            PlayerState(
                connected = true,
                nowPlaying = item?.let { mi ->
                    NowPlaying(
                        songId = mi.song.id,
                        albumId = mi.song.albumId,
                        title = mi.song.title,
                        artist = mi.song.artist,
                    )
                },
                isPlaying = p.isPlaying,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                durationMs = p.duration.takeIf { it != Player.TIME_UNSET } ?: 0L,
                shuffle = p.shuffleModeEnabled,
                repeatMode = p.repeatMode,
            )
        }
    }

    private fun syncQueue(p: Player) {
        if (p.mediaItemCount == 0 || p.currentMediaItemIndex < 0) {
            _queue.value = QueueState()
            return
        }
        // Walk the play order (shuffle included) from the current track. A shuffled queue on
        // "repeat all" goes round, so it lists every other track; in list order it stops at the
        // end, which keeps drag positions meaning "right after the current track".
        val wrap = p.shuffleModeEnabled && p.repeatMode == Player.REPEAT_MODE_ALL
        val order = ArrayList<Int>()
        var i = p.currentMediaItemIndex
        while (i >= 0 && order.size < p.mediaItemCount) {
            order += i
            i = p.nextIndexFor(i, if (wrap) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF)
            if (i in order) break
        }
        val seen = HashSet<String>()
        val entries = order.map { index ->
            val item = p.getMediaItemAt(index)
            // LazyColumn needs unique keys; a clash falls back to the slot.
            val key = "q:${item.queueKey}".let { if (seen.add(it)) it else "slot:$index" }
            QueueEntry(key, songsById[item.song.id] ?: item.song)
        }
        _queue.value = QueueState(entries.first(), entries.drop(1), canReorder = !p.shuffleModeEnabled)
    }

    fun dispose() {
        File(AppDirs.cache, COVER_PICKS).listFiles()?.forEach { it.delete() }
        controller.removeListener(listener)
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
        const val STOP_TIMEOUT_MS = 1_000L
        const val FOCUS_RESCAN_MS = 30_000L
        const val CLOSE_TIMEOUT_MS = 2_000L
        const val COVER_PICKS = "cover-picks"
        val QUEUE_EVENTS = setOf(
            Player.Event.Timeline,
            Player.Event.Transition,
            Player.Event.Shuffle,
            Player.Event.Repeat,
        )
    }
}

private fun Player.indexOfQueueKey(key: String): Int? =
    (0 until mediaItemCount).firstOrNull { i ->
        val item = getMediaItemAt(i)
        "q:${item.queueKey}" == key || "slot:$i" == key
    }
