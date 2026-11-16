package com.ku.pierdoon

import com.ku.pierdoon.data.Backup
import com.ku.pierdoon.data.CommonSettings
import com.ku.pierdoon.data.LibraryChanges
import com.ku.pierdoon.data.LibrarySnapshot
import com.ku.pierdoon.data.M3uMatch
import com.ku.pierdoon.data.MusicRepository
import com.ku.pierdoon.data.PlayHistory
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.USER_AGENT
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.data.history.Play
import com.ku.pierdoon.data.lyrics.LyricsOffsets
import com.ku.pierdoon.data.metadata.FileTags
import com.ku.pierdoon.data.metadata.MetadataSearch
import com.ku.pierdoon.data.metadata.TagWriter
import com.ku.pierdoon.data.update.Updater
import com.ku.pierdoon.platform.AppDirs
import com.ku.pierdoon.platform.SystemClock
import com.ku.pierdoon.playback.DesktopQueuePlayer
import com.ku.pierdoon.playback.PlaybackController
import com.ku.pierdoon.playback.Player
import com.ku.pierdoon.playback.Radio
import com.ku.pierdoon.ui.components.ArtworkCache
import com.ku.pierdoon.ui.components.PickedImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

/**
 * The computer's side of [AppModel]: music folders for the library, its own player for playback.
 * One per process, like the window.
 */
class MainViewModel : AppModel(
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    files = AppDirs.files,
    cache = AppDirs.cache,
    userAgent = USER_AGENT,
    lyricsOffsets = LyricsOffsets(),
    updater = Updater,
    metadata = MetadataSearch,
    radioControl = Radio,
    settings = SettingsStore.settings,
    platform = "windows",
    appVersion = BuildConfig.VERSION_NAME,
) {

    private val repository = MusicRepository()
    override val queuePlayer = DesktopQueuePlayer(PlaybackController.player)
    private val controller: Player get() = queuePlayer.player

    private val listener = Player.Listener { _, events ->
        onPlayerEvents(queueChanged = events.any { it in QUEUE_EVENTS })
    }

    init {
        controller.addListener(listener)
        onPlayerEvents(queueChanged = true)
        scope.launch {
            Downloader.added.collect {
                scanDownloads()
                rescanAfterDownload()
            }
        }
    }

    override suspend fun loadSongs(): List<Song> = repository.loadSongs()
    override suspend fun readFileTags(song: Song): Song? = repository.fileTags(song)
    override suspend fun songPaths(): (Song) -> String = { it.path }
    override fun scanLoudness(songs: List<Song>) = PlaybackController.loudness.scanLibrary(songs)
    override fun networkAllowed(): Boolean = !SettingsStore.settings.value.lyricsWifiOnly
    override fun invalidateArtwork(song: Song) = ArtworkCache.invalidate(song.id, song.albumId)
    override fun commonSettings(): CommonSettings = SettingsStore.settings.value.common

    override suspend fun allPlays(): List<Play> {
        PlayHistory.load()
        return PlayHistory.plays.first { it != null }.orEmpty()
    }

    override suspend fun mergePlays(plays: List<Play>) = PlayHistory.merge(plays)

    private var lastLoadAt = 0L

    override fun onLoadStarted() {
        lastLoadAt = SystemClock.elapsedRealtime()
    }

    override fun onSongsLoaded(songs: List<Song>, first: Boolean) {
        ArtworkCache.registerFiles(songs)
        if (first) PlaybackController.restore(songsById)
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
        downloadRescan = scope.launch {
            delay(if (more) DOWNLOAD_RESCAN_BATCH_MS else DOWNLOAD_RESCAN_MS)
            loadLibrary(force = true)
        }
    }

    /**
     * Files added, deleted or retagged elsewhere show up when the window comes back to the front.
     * Cheap: only files whose size or date changed are read again.
     */
    fun onWindowFocused() {
        if (library.value !is LibraryState.Ready) return
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


    /** Makes a playlist of a file's tracks found in the library; null if the file can't be read. */
    suspend fun importPlaylist(source: File): M3uMatch? {
        val bytes = withContext(Dispatchers.IO) { runCatching { source.readBytes() }.getOrNull() } ?: return null
        return importM3u(bytes, source.absoluteFile.parent) { source.nameWithoutExtension }
    }

    /** Writes [playlist] to [target]; false if it couldn't be. */
    suspend fun exportPlaylist(playlist: Playlist, target: File): Boolean {
        val text = m3uText(playlist)
        return withContext(Dispatchers.IO) { runCatching { target.writeText(text) }.isSuccess }
    }

    // Sync with the phone and backups -------------------------------------------------------------

    /**
     * The library is scanned and the playlists read. Until then a sync would see no tracks and no
     * playlists here, and take that for everything having been removed.
     */
    fun syncReady(): Boolean = library.value is LibraryState.Ready && userData.value.loaded

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
    suspend fun exportBackup(target: File): Boolean {
        val bytes = backupBytes()
        return withContext(Dispatchers.IO) { runCatching { target.writeBytes(bytes) }.isSuccess }
    }

    /** The backup in [source], or null if it isn't one. */
    suspend fun readBackup(source: File): Backup? =
        decodeBackup(withContext(Dispatchers.IO) { runCatching { source.readBytes() }.getOrNull() })

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

    // Track editing ------------------------------------------------------------------------

    /** A picture file as a cover-sized JPEG; null if it couldn't be read as an image. */
    override suspend fun importCover(image: PickedImage): File? = importCover(image.file)

    suspend fun importCover(image: File): File? = coverFrom { raw -> image.copyTo(raw, overwrite = true) }

    /** Whether the file's format takes tags at all (raw AAC, for one, doesn't). */
    override suspend fun canWriteTags(song: Song): Boolean = FileTags.canWrite(song.file.name) && song.file.canWrite()

    override suspend fun writeTags(
        song: Song, title: String, artist: String, album: String, cover: ByteArray?, removeCover: Boolean,
    ): Boolean = TagWriter.write(song, title = title, artist = artist, album = album, cover = cover, removeCover = removeCover)

    /**
     * Sends [songs] to the Recycle Bin (where they can be restored from), then forgets them.
     * @return how many went.
     */
    suspend fun deleteSongs(songs: List<Song>): Int {
        // A playing file can't be moved while the decoder has it open.
        val playing = controller.currentMediaItem?.song?.id
        if (songs.any { it.id == playing }) queuePlayer.stopAndRelease()
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

    override fun dispose() {
        controller.removeListener(listener)
        super.dispose()
    }

    private companion object {
        const val FOCUS_RESCAN_MS = 30_000L
        const val DOWNLOAD_RESCAN_MS = 300L
        const val DOWNLOAD_RESCAN_BATCH_MS = 8_000L
        val QUEUE_EVENTS = setOf(
            Player.Event.Timeline,
            Player.Event.Transition,
            Player.Event.Shuffle,
            Player.Event.Repeat,
        )
    }
}
