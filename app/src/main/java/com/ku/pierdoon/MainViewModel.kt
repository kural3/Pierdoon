package com.ku.pierdoon

import android.app.Application
import android.content.ComponentName
import android.content.IntentSender
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ku.pierdoon.data.Backup
import com.ku.pierdoon.data.CommonSettings
import com.ku.pierdoon.data.M3uMatch
import com.ku.pierdoon.data.MusicFolder
import com.ku.pierdoon.data.MusicRepository
import com.ku.pierdoon.data.PlayHistory
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.USER_AGENT
import com.ku.pierdoon.data.history.Play
import com.ku.pierdoon.data.lyrics.LyricsOffsets
import com.ku.pierdoon.data.metadata.FileTags
import com.ku.pierdoon.data.metadata.MetadataSearch
import com.ku.pierdoon.data.metadata.TagWriter
import com.ku.pierdoon.data.update.Updater
import com.ku.pierdoon.data.uri
import com.ku.pierdoon.playback.EvenVolume
import com.ku.pierdoon.playback.MediaQueuePlayer
import com.ku.pierdoon.playback.PlaybackService
import com.ku.pierdoon.playback.Radio
import com.ku.pierdoon.playback.TapeMotion
import com.ku.pierdoon.remote.PcRemote
import com.ku.pierdoon.remote.PcSync
import com.ku.pierdoon.ui.components.ArtworkCache
import com.ku.pierdoon.ui.components.PickedImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps the phone's [MainViewModel] across rotations, as long as the activity's screen lives;
 * the model itself is the one both apps share ([AppModel]).
 */
class MainViewModelHolder(app: Application) : AndroidViewModel(app) {
    val model = MainViewModel(app)
    override fun onCleared() = model.dispose()
}

/** The phone's side of [AppModel]: MediaStore for the library, Media3 for playback. */
class MainViewModel(private val app: Application) : AppModel(
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    files = app.filesDir,
    cache = app.cacheDir,
    userAgent = USER_AGENT,
    lyricsOffsets = LyricsOffsets(app),
    updater = Updater,
    metadata = MetadataSearch,
    radioControl = Radio,
    settings = SettingsStore.settings,
    platform = "android",
    appVersion = BuildConfig.VERSION_NAME,
) {

    private val repository = MusicRepository(app)

    private var mediaPlayer: MediaQueuePlayer? = null
    override val queuePlayer: MediaQueuePlayer? get() = mediaPlayer

    private var controller: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .buildAsync()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            onPlayerEvents(queueChanged = events.containsAny(*QUEUE_EVENTS))
        }
    }

    init {
        controllerFuture.addListener({
            val c = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
            controller = c
            mediaPlayer = MediaQueuePlayer(c)
            c.addListener(listener)
            onPlayerEvents(queueChanged = true)
        }, ContextCompat.getMainExecutor(app))

        PcSync.library = { (library.value as? LibraryState.Ready)?.songs }
        // Each time the phone connects to the computer, a sync if it's been a while.
        scope.launch {
            combine(PcRemote.connection, library) { c, l ->
                c == PcRemote.Connection.Connected && l is LibraryState.Ready
            }.distinctUntilChanged().collect { ready -> if (ready) PcSync.onConnected(app) }
        }
    }

    override suspend fun loadSongs(): List<Song> = repository.loadSongs()
    override suspend fun readFileTags(song: Song): Song? = repository.fileTags(song.id)

    override suspend fun songPaths(): (Song) -> String {
        val paths = repository.paths()
        return { paths[it.id].orEmpty() }
    }

    override fun scanLoudness(songs: List<Song>) = EvenVolume.loudness(app).scanLibrary(songs)

    override fun networkAllowed(): Boolean {
        if (!SettingsStore.settings.value.lyricsWifiOnly) return true
        val cm = app.getSystemService(ConnectivityManager::class.java)
        return cm?.isActiveNetworkMetered == false
    }

    override suspend fun allPlays(): List<Play> = PlayHistory.get(app).plays.first { it != null }.orEmpty()
    override suspend fun mergePlays(plays: List<Play>) = PlayHistory.get(app).merge(plays)
    override fun commonSettings(): CommonSettings = SettingsStore.settings.value.common
    override fun invalidateArtwork(song: Song) = ArtworkCache.invalidate(song.id, song.albumId)
    override fun onLoadStarted() = watchMediaStore()

    // While the deck works, what plays runs silently and is put where it belongs after.
    // Meanwhile a track spun to stands at its start, and one the cassette is being turned
    // over or rewound from stands where it stopped: the bar doesn't run and jump back.
    override fun shownPosition(actual: Long, shown: Long): Pair<Long, Boolean> = when (TapeMotion.current.value) {
        TapeMotion.Still -> actual to true
        TapeMotion.Forward, TapeMotion.Back -> 0L to false
        else -> shown to false
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
        app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaStoreObserver)
        scope.launch {
            mediaStoreChanged.debounce(MEDIA_STORE_DEBOUNCE_MS).collect { loadLibrary(force = true) }
        }
        // Leaving a folder out, or taking it back, changes which tracks there are.
        scope.launch {
            SettingsStore.settings.map { it.excludedFolders }.distinctUntilChanged().drop(1).collect { loadLibrary(force = true) }
        }
    }

    /** Every folder with music in it, left-out ones too, for Settings → Библиотека. */
    suspend fun musicFolders(): List<MusicFolder> = repository.folders()

    /** Swiping back: always the track before, never a restart of this one as the button does past 3 s. */
    fun previousTrack() {
        mediaPlayer?.previousTrack()
    }


    /** Makes a playlist of a file's tracks found in the library; null if the file can't be read. */
    suspend fun importPlaylist(source: Uri): M3uMatch? {
        val bytes = readUri(source) ?: return null
        return importM3u(bytes, baseDir = null) { displayName(source) ?: app.getString(R.string.nav_playlists) }
    }

    /** Writes [playlist] to a file the user picked; false if it couldn't be. */
    suspend fun exportPlaylist(playlist: Playlist, target: Uri): Boolean = writeUri(target, m3uText(playlist).toByteArray())

    /** Writes a backup of the settings, playlists, favourites and history to a file the user picked. */
    suspend fun exportBackup(target: Uri): Boolean = writeUri(target, backupBytes())

    /** The backup in a file the user picked, or null if it isn't one. */
    suspend fun readBackup(source: Uri): Backup? = decodeBackup(readUri(source))

    /** Favourites, playlists and history, both ways with the paired computer. */
    fun syncWithComputer() {
        scope.launch { PcSync.sync(app) }
    }

    private suspend fun readUri(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    }

    private suspend fun writeUri(uri: Uri, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching { app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null }.getOrDefault(false)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.')?.ifBlank { null } else null
        }
    }.getOrNull()

    // Track editing ------------------------------------------------------------------------

    /** A picture from the gallery as a cover-sized JPEG; null if it couldn't be read as an image. */
    override suspend fun importCover(image: PickedImage): File? = importCover(image.uri)

    suspend fun importCover(uri: Uri): File? = coverFrom { raw ->
        app.contentResolver.openInputStream(uri)?.use { input -> raw.outputStream().use { input.copyTo(it) } }
    }

    /** Android 11+ asks the user before an app may change someone else's file; null below that. */
    override fun fileWriteRequest(songs: List<Song>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && songs.isNotEmpty()) {
            MediaStore.createWriteRequest(app.contentResolver, songs.map { it.uri }).intentSender
        } else {
            null
        }

    /** Whether the file's format takes tags at all (raw AAC, for one, doesn't). */
    override suspend fun canWriteTags(song: Song): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && FileTags.canWrite(TagWriter.displayName(app, song))

    /** Call once the user granted [fileWriteRequest]. */
    override suspend fun writeTags(
        song: Song, title: String, artist: String, album: String, cover: ByteArray?, removeCover: Boolean,
    ): Boolean {
        val name = TagWriter.displayName(app, song) ?: return false
        return TagWriter.write(app, song, name, title = title, artist = artist, album = album, cover = cover, removeCover = removeCover)
    }

    /** One system consent for all of [songs], which is the confirmation; null below Android 11, where apps can't ask for it. */
    fun deleteRequest(songs: List<Song>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && songs.isNotEmpty()) {
            MediaStore.createDeleteRequest(app.contentResolver, songs.map { it.uri }).intentSender
        } else {
            null
        }

    override fun dispose() {
        super.dispose()
        if (watching) app.contentResolver.unregisterContentObserver(mediaStoreObserver)
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
        mediaPlayer = null
    }

    private companion object {
        const val MEDIA_STORE_DEBOUNCE_MS = 1_500L
        val QUEUE_EVENTS = intArrayOf(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            Player.EVENT_REPEAT_MODE_CHANGED,
        )
    }
}
