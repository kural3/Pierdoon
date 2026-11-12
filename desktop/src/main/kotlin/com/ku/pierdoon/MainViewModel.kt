package com.ku.pierdoon

import com.ku.pierdoon.data.lyrics.LyricsRepository
import com.ku.pierdoon.data.lyrics.LyricsOffsets
import com.ku.pierdoon.ui.components.PickedImage
import com.ku.pierdoon.ui.edit.TrackEditor
import kotlinx.coroutines.flow.Flow
import com.ku.pierdoon.data.metadata.MetadataCandidate
import com.ku.pierdoon.data.USER_AGENT
import com.ku.pierdoon.data.Backup
import com.ku.pierdoon.data.LibraryChanges
import com.ku.pierdoon.data.LibrarySnapshot
import com.ku.pierdoon.data.LibrarySync
import com.ku.pierdoon.data.PlayHistory
import com.ku.pierdoon.data.SmartPlaylist
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.M3uMatch
import com.ku.pierdoon.data.M3uMatcher
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.playback.tapeSides
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.decodeM3u
import com.ku.pierdoon.data.parseM3u
import com.ku.pierdoon.data.writeM3u
import com.ku.pierdoon.data.MusicRepository
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.TagOverride
import com.ku.pierdoon.data.TagOverrides
import com.ku.pierdoon.data.UserData
import com.ku.pierdoon.data.UserLibrary
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.data.lyrics.LyricsCandidate
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
import com.ku.pierdoon.playback.Radio
import com.ku.pierdoon.radio.RadioStart
import com.ku.pierdoon.radio.RadioState
import com.ku.pierdoon.radio.RadioTrack
import com.ku.pierdoon.playback.nextRepeatMode
import com.ku.pierdoon.ui.components.ArtworkCache
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface LibraryState {
    data object Loading : LibraryState
    data class Ready(val songs: List<Song>) : LibraryState
}

/** The app's state and actions, shared by every screen. One per process, like the window. */
class MainViewModel : TrackEditor {

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = MusicRepository()

    private val _library = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _collections = MutableStateFlow(MusicCollections())
    val collections: StateFlow<MusicCollections> = _collections.asStateFlow()

    private val userLibrary = UserLibrary.get(AppDirs.files)
    private val tagOverrides = TagOverrides.get(AppDirs.files)
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
            emit(PositionSnapshot(c.currentPosition, SystemClock.elapsedRealtime(), playing, c.speed))
            if (playing) delay(POSITION_TICK_MS) else eventTick.first { it != tick }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PositionSnapshot())

    private var songsById: Map<Long, Song> = emptyMap()

    private val lyricsModel = LyricsModel(
        scope = viewModelScope,
        repository = LyricsRepository(AppDirs.cache, USER_AGENT, AppDirs.files),
        offsets = LyricsOffsets(),
        nowPlaying = _player.map { it.nowPlaying },
        current = { _player.value.let { it.nowPlaying to it.durationMs } },
        song = { songsById[it] },
        networkAllowed = ::networkAllowed,
        stopTimeoutMs = STOP_TIMEOUT_MS,
    )

    /** Fetched lazily: only while the lyrics panel is open, once per track. */
    val lyrics: StateFlow<LyricsState> get() = lyricsModel.lyrics

    /** Hand-set sync correction of the current track's lyrics; positive = lines show later. */
    val lyricsOffset: StateFlow<Long> get() = lyricsModel.offset

    fun shiftLyrics(deltaMs: Long) = lyricsModel.shift(deltaMs)
    fun resetLyricsOffset() = lyricsModel.resetOffset()

    /** Lyrics of any track, not only the one on screen; for the phone. Null if unknown or failed. */
    suspend fun lyricsFor(songId: Long): Lyrics? = lyricsModel.lyricsFor(songId)

    fun lyricsOffsetFor(songId: Long): Long = lyricsModel.offsetFor(songId)

    /** Changes whenever any track's lyrics offset does. */
    val lyricsOffsetsVersion: StateFlow<Int> get() = lyricsModel.offsetsVersion

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
                scanDownloads()
                rescanAfterDownload()
            }
        }
    }

    private var downloadRescan: Job? = null

    /**
     * One rescan for a run of downloads: a playlist link brings tracks one after another, and
     * the library needn't be walked again for each. The last one shows up right away.
     */
    private fun rescanAfterDownload() {
        val more = Downloader.tasks.value.any { it.active }
        // Still waiting to batch, but that was the last one: no need to wait any longer.
        if (downloadRescan?.isActive == true && more) return
        downloadRescan?.cancel()
        downloadRescan = viewModelScope.launch {
            delay(if (more) DOWNLOAD_RESCAN_BATCH_MS else DOWNLOAD_RESCAN_MS)
            loadLibrary(force = true)
        }
    }

    // Quietly on launch, unless switched off.
    private val updates = UpdatePrompt(viewModelScope, Updater, autoCheck = SettingsStore.settings.value.checkUpdates)
    val update: StateFlow<UpdateState> get() = updates.state
    /** Whether the "new version" dialog is up. */
    val updatePrompt: StateFlow<Boolean> get() = updates.shown

    fun checkForUpdates() = updates.check()
    fun showUpdate() = updates.show()
    fun installUpdate(release: AppRelease) = updates.install(release)
    fun dismissUpdate() = updates.dismiss()

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
            ArtworkCache.registerFiles(songs)
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

    fun addExcludedFolder(folder: File) {
        val path = folder.absolutePath
        SettingsStore.update { s ->
            if (s.excludedFolders.any { it.equals(path, ignoreCase = true) }) s else s.copy(excludedFolders = s.excludedFolders + path)
        }
        loadLibrary(force = true)
    }

    fun removeExcludedFolder(path: String) {
        SettingsStore.update { s -> s.copy(excludedFolders = s.excludedFolders.filterNot { it == path }) }
        loadLibrary(force = true)
    }

    /** Null goes back to the default folder. */
    fun setDownloadFolder(folder: File?) {
        SettingsStore.update { it.copy(downloadFolder = folder?.absolutePath) }
        if (scanDownloads()) loadLibrary(force = true)
    }

    /**
     * Downloads should show up in the library: adds their folder to the scanned ones unless one
     * already holds it, and takes it off the skipped ones. True when something changed.
     */
    private fun scanDownloads(): Boolean {
        val folder = Downloader.folder.absolutePath
        val before = SettingsStore.settings.value
        SettingsStore.update { s ->
            val covered = s.musicFolders.any { it.encloses(folder) }
            s.copy(
                musicFolders = if (covered) s.musicFolders else s.musicFolders + folder,
                excludedFolders = s.excludedFolders.filterNot { it.encloses(folder) },
            )
        }
        return SettingsStore.settings.value != before
    }

    private fun String.encloses(path: String): Boolean {
        val root = trimEnd('\\', '/')
        return path.equals(root, ignoreCase = true) || path.startsWith(root + File.separator, ignoreCase = true)
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

    /** For a radio track not in the library yet, the heart keeps it: downloaded into it, as a favourite. */
    fun toggleFavorite(songId: Long) {
        if (RadioTrack.isRadio(songId)) Radio.keep(songId) else userLibrary.toggleFavorite(songId)
    }

    /** The radio playing now (beta), or null. */
    val radio: StateFlow<RadioState?> = Radio.state

    fun startRadio(start: RadioStart) {
        val songs = (_library.value as? LibraryState.Ready)?.songs.orEmpty()
        Radio.start(songs, start)
    }

    fun dislikeRadio(songId: Long) = Radio.dislike(songId)

    fun stopRadio() = Radio.stop()
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

    /** Makes a playlist of a file's tracks found in the library; null if the file can't be read. */
    suspend fun importPlaylist(source: File): M3uMatch? = withContext(Dispatchers.IO) {
        val bytes = runCatching { source.readBytes() }.getOrNull() ?: return@withContext null
        val songs = (_library.value as? LibraryState.Ready)?.songs.orEmpty()
        val file = parseM3u(decodeM3u(bytes))
        val match = M3uMatcher(songs, { it.path }, source.absoluteFile.parent).match(file)
        if (match.songIds.isNotEmpty()) {
            userLibrary.createPlaylist(file.name ?: source.nameWithoutExtension, match.songIds)
        }
        match
    }

    /** Writes [playlist] to [target]; false if it couldn't be. */
    suspend fun exportPlaylist(playlist: Playlist, target: File): Boolean = withContext(Dispatchers.IO) {
        val text = writeM3u(playlist.name, resolve(playlist.songIds)) { it.path }
        runCatching { target.writeText(text) }.isSuccess
    }

    fun putSmartPlaylist(playlist: SmartPlaylist) = userLibrary.putSmartPlaylist(playlist)
    fun deleteSmartPlaylist(id: Long) = userLibrary.deleteSmartPlaylist(id)

    private fun librarySongs(): List<Song> = (_library.value as? LibraryState.Ready)?.songs.orEmpty()

    // Sync with the phone and backups -------------------------------------------------------------

    /**
     * The library is scanned and the playlists read. Until then a sync would see no tracks and no
     * playlists here, and take that for everything having been removed.
     */
    fun syncReady(): Boolean = _library.value is LibraryState.Ready && userData.value.loaded

    /** What the phone merges with its own: plays from [playsSince] on, everything else whole. */
    fun syncSnapshot(playsSince: Long): LibrarySnapshot {
        val plays = PlayHistory.plays.value.orEmpty()
        return LibrarySnapshot.of(librarySongs(), userData.value, plays, allTracks = true).let { s ->
            s.copy(plays = s.plays.filter { it.atMs >= playsSince })
        }
    }

    /** Changes the phone worked out, in this library's ids. */
    fun applySync(changes: LibraryChanges) {
        val known = songsById.keys
        userLibrary.apply(
            changes.copy(
                favoritesAdd = changes.favoritesAdd.filterTo(HashSet()) { it in known },
                playlistsPut = changes.playlistsPut.map { p -> p.copy(songIds = p.songIds.filter { it in known }) },
            )
        )
        PlayHistory.merge(changes.plays.filter { it.songId in known })
    }

    /** Writes a backup of the settings, playlists, favourites and history to [target]. */
    suspend fun exportBackup(target: File): Boolean = withContext(Dispatchers.IO) {
        PlayHistory.load()
        val plays = PlayHistory.plays.first { it != null }.orEmpty()
        val backup = Backup(
            createdAtMs = System.currentTimeMillis(),
            platform = "windows",
            appVersion = BuildConfig.VERSION_NAME,
            settings = SettingsStore.settings.value.common,
            library = LibrarySnapshot.of(librarySongs(), userData.value, plays),
        )
        runCatching { target.writeBytes(backup.encode()) }.isSuccess
    }

    /** The backup in [source], or null if it isn't one. */
    suspend fun readBackup(source: File): Backup? = withContext(Dispatchers.IO) {
        runCatching { source.readBytes() }.getOrNull()?.let { Backup.decode(it, SettingsStore.settings.value.common) }
    }

    /**
     * Takes [backup]'s settings and adds what's missing of its playlists, favourites and history.
     * @return how many of the tracks it names were found here, and how many it names.
     */
    suspend fun restoreBackup(backup: Backup): Pair<Int, Int> {
        PlayHistory.load()
        val plays = PlayHistory.plays.first { it != null }.orEmpty()
        val outcome = withContext(Dispatchers.Default) {
            LibrarySync.merge(LibrarySnapshot.of(librarySongs(), userData.value, plays, allTracks = true), backup.library, base = null)
        }
        userLibrary.apply(outcome.local)
        PlayHistory.merge(outcome.local.plays)
        backup.settings?.let { s -> SettingsStore.update { it.copy(common = s) } }
        return outcome.matched to backup.library.tracks.size
    }

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

    suspend fun lyricsCacheSize(): Long = lyricsModel.cacheSize()
    fun clearLyricsCache() = lyricsModel.clearCache()
    fun rescanLibrary() = loadLibrary(force = true)

    fun setLyricsVisible(visible: Boolean) = lyricsModel.setVisible(visible)
    fun retryLyrics() = lyricsModel.retry()

    /** Where a lyrics search starts: the current track's artist and title. */
    fun lyricsSearchText(): String = lyricsModel.searchText()

    suspend fun searchLyrics(text: String): List<LyricsCandidate> = lyricsModel.search(text)

    /** Puts lyrics picked by hand on the current track, for good. */
    fun pickLyrics(lyrics: Lyrics) = lyricsModel.pick(lyrics)

    fun currentSongId(): Long? = _player.value.nowPlaying?.songId

    suspend fun ownLyrics(songId: Long): String? = lyricsModel.own(songId)

    /** Lyrics the user wrote in the editor: they stay over anything found, until removed. */
    fun saveOwnLyrics(songId: Long, text: String) = lyricsModel.saveOwn(songId, text)

    fun removeOwnLyrics(songId: Long) = lyricsModel.removeOwn(songId)

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

    override fun hasInAppEdits(songId: Long): Boolean = tagOverrides.has(songId) || CoverStore.songVersion(songId) != null

    override fun searchMetadata(query: String, durationMs: Long): Flow<List<MetadataCandidate>> = MetadataSearch.search(query, durationMs)

    /** A picture file as a cover-sized JPEG; null if it couldn't be read as an image. */
    override suspend fun importCover(image: PickedImage): File? = importCover(image.file)

    suspend fun importCover(image: File): File? = withContext(Dispatchers.IO) {
        val raw = coverPickFile("img")
        runCatching { image.copyTo(raw, overwrite = true) }
        toCoverJpeg(raw)
    }

    override suspend fun downloadCover(url: String): File? {
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
    override suspend fun saveInApp(song: Song, edit: TrackEdit) {
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
    override suspend fun canWriteTags(song: Song): Boolean = TagWriter.canWrite(song.file.name) && song.file.canWrite()

    /**
     * Writes [edit] into the file's tags.
     * The app's own edits of this track are folded in and then dropped: the file is the truth now.
     */
    override suspend fun saveToFile(song: Song, edit: TrackEdit): Boolean {
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
            ArtworkCache.invalidate(song.id, song.albumId)
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
        lyricsModel.dropOffset(songId)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                tagOverrides.set(songId, null)
                CoverStore.removeSongCover(songId)
            }
            lyricsModel.forget(songId)
        }
        loadLibrary(force = true)
    }

    /** Drops the app's edits of a track: tags and cover come from the file again. */
    override suspend fun resetEdits(song: Song) {
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
        val tapeOrder = p.playOrderSnapshot()
        val tape = tapeSides(tapeOrder.map { p.getMediaItemAt(it).song.durationMs }, tapeOrder.indexOf(p.currentMediaItemIndex))
        val sideStarts = buildMap {
            tapeOrder.firstOrNull()?.let { put("q:${p.getMediaItemAt(it).queueKey}", "A") }
            tapeOrder.getOrNull(tape.sideBStart)?.let { put("q:${p.getMediaItemAt(it).queueKey}", "B") }
        }
        _queue.value = QueueState(entries.first(), entries.drop(1), !p.shuffleModeEnabled, tape, sideStarts)
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
        const val DOWNLOAD_RESCAN_MS = 300L
        const val DOWNLOAD_RESCAN_BATCH_MS = 8_000L
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
