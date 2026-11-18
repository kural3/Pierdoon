package com.ku.pierdoon

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.Backup
import com.ku.pierdoon.data.CommonSettings
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.LibrarySnapshot
import com.ku.pierdoon.data.LibrarySync
import com.ku.pierdoon.data.M3uMatch
import com.ku.pierdoon.data.M3uMatcher
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.SharedSettings
import com.ku.pierdoon.data.SharedSettingsStore
import com.ku.pierdoon.data.SmartPlaylist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.TagOverride
import com.ku.pierdoon.data.TagOverrides
import com.ku.pierdoon.data.UserData
import com.ku.pierdoon.data.UserLibrary
import com.ku.pierdoon.data.decodeM3u
import com.ku.pierdoon.data.history.Play
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.data.lyrics.LyricsCandidate
import com.ku.pierdoon.data.lyrics.LyricsOffsets
import com.ku.pierdoon.data.lyrics.LyricsRepository
import com.ku.pierdoon.data.metadata.CoverEdit
import com.ku.pierdoon.data.metadata.MetadataCandidate
import com.ku.pierdoon.data.metadata.MetadataSearchEngine
import com.ku.pierdoon.data.metadata.SaveTarget
import com.ku.pierdoon.data.metadata.TrackEdit
import com.ku.pierdoon.data.parseM3u
import com.ku.pierdoon.data.update.AppRelease
import com.ku.pierdoon.data.update.AppUpdater
import com.ku.pierdoon.data.update.UpdateState
import com.ku.pierdoon.data.writeM3u
import com.ku.pierdoon.data.writeScaledJpeg
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.playback.PlayerState
import com.ku.pierdoon.playback.QueueEntry
import com.ku.pierdoon.playback.QueuePlayer
import com.ku.pierdoon.playback.QuizPlayback
import com.ku.pierdoon.playback.QueueState
import com.ku.pierdoon.playback.RadioControl
import com.ku.pierdoon.playback.RepeatMode
import com.ku.pierdoon.playback.nextRepeatMode
import com.ku.pierdoon.playback.tapeSides
import com.ku.pierdoon.radio.RadioStart
import com.ku.pierdoon.radio.RadioState
import com.ku.pierdoon.radio.RadioTrack
import com.ku.pierdoon.ui.components.PickedImage
import com.ku.pierdoon.ui.edit.TrackEditor
import com.ku.pierdoon.ui.elapsedRealtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface LibraryState {
    data object Loading : LibraryState
    data class Ready(val songs: List<Song>) : LibraryState
}

/**
 * The app's state and actions, the same on the phone and the computer: the library, playlists,
 * the queue, lyrics, radio, backups, tag edits and updates. Each app's MainViewModel adds what
 * only it does (reading its library, its player, its files) through the members left abstract.
 *
 * Nothing abstract is touched while this class is being constructed, so subclasses may keep
 * what they hand over in their own fields.
 */
abstract class AppModel(
    protected val scope: CoroutineScope,
    files: File,
    private val cache: File,
    userAgent: String,
    lyricsOffsets: LyricsOffsets,
    updater: AppUpdater,
    private val metadata: MetadataSearchEngine,
    private val radioControl: RadioControl,
    private val settings: StateFlow<SharedSettings>,
    /** "android" or "windows", for backups. */
    private val platform: String,
    private val appVersion: String,
) : TrackEditor {

    private val _library = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _collections = MutableStateFlow(MusicCollections())
    val collections: StateFlow<MusicCollections> = _collections.asStateFlow()

    protected val userLibrary = UserLibrary.get(files)
    private val tagOverrides = TagOverrides.get(files)
    val userData: StateFlow<UserData> = userLibrary.data

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    /** Rebuilt only when the queue itself changes, not on every play/pause or seek. */
    private val _queue = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    /** Every track of the library by id; empty until it's read. */
    protected var songsById: Map<Long, Song> = emptyMap()
        private set

    // What each app does its own way ----------------------------------------------------------------

    /** The player, once there is one (the phone's connects a moment after start). */
    protected abstract val queuePlayer: QueuePlayer?

    /** Reads the library's tracks. */
    protected abstract suspend fun loadSongs(): List<Song>

    /** The track as its file has it, without the app's edits. */
    protected abstract suspend fun readFileTags(song: Song): Song?

    /** Where each track's file is, for playlists in and out as .m3u. */
    protected abstract suspend fun songPaths(): (Song) -> String

    protected abstract suspend fun writeTags(
        song: Song, title: String, artist: String, album: String, cover: ByteArray?, removeCover: Boolean,
    ): Boolean

    protected abstract fun invalidateArtwork(song: Song)

    /** Measures how loud each track is, for even volume. */
    protected abstract fun scanLoudness(songs: List<Song>)

    /** Lyrics may be fetched now (not on a metered network when that's switched off). */
    protected abstract fun networkAllowed(): Boolean

    /** Every play in the history, once it's read. */
    protected abstract suspend fun allPlays(): List<Play>

    protected abstract suspend fun mergePlays(plays: List<Play>)

    protected abstract fun commonSettings(): CommonSettings

    /** Called with the library just read, before the screens see it; [first] on the first read. */
    protected open fun onSongsLoaded(songs: List<Song>, first: Boolean) = Unit

    /** Called as a load starts. */
    protected open fun onLoadStarted() = Unit

    /**
     * Where the progress bar stands, given where the player is ([actual]) and where the bar stood
     * ([shown]); and whether it runs on from there. The phone holds it still while its tape deck spins.
     */
    protected open fun shownPosition(actual: Long, shown: Long): Pair<Long, Boolean> = actual to true

    // Playback position ------------------------------------------------------------------------------

    /** Bumped on every player event, so paused UI can wait instead of polling. */
    private val eventTick = MutableStateFlow(0)

    /** For the subclass's player listener: the player changed; [queueChanged] if its queue did. */
    protected fun onPlayerEvents(queueChanged: Boolean) {
        val p = queuePlayer ?: return
        syncState(p)
        if (queueChanged) syncQueue(p)
        eventTick.update { it + 1 }
    }

    /**
     * Playback position, polled only while someone collects it (the UI is started) and only
     * while audio is actually playing. Paused or backgrounded = zero wakeups.
     */
    val position: StateFlow<PositionSnapshot> = flow {
        var shown = 0L
        while (true) {
            val tick = eventTick.value
            val p = queuePlayer
            val playing = p?.isPlaying == true
            val (at, running) = shownPosition(p?.positionMs ?: 0L, shown)
            shown = at
            emit(PositionSnapshot(shown, elapsedRealtime(), playing && running, p?.speed ?: 1f))
            if (playing) delay(POSITION_TICK_MS) else eventTick.first { it != tick }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PositionSnapshot())

    // Lyrics ------------------------------------------------------------------------------------------

    private val lyricsModel = LyricsModel(
        scope = scope,
        repository = LyricsRepository(cache, userAgent, files),
        offsets = lyricsOffsets,
        nowPlaying = _player.map { it.nowPlaying },
        current = { _player.value.let { it.nowPlaying to it.durationMs } },
        song = { songsById[it] },
        networkAllowed = { networkAllowed() },
        stopTimeoutMs = STOP_TIMEOUT_MS,
    )

    /** Fetched lazily: only while the lyrics panel is open, once per track. */
    val lyrics: StateFlow<LyricsState> get() = lyricsModel.lyrics

    /** Hand-set sync correction of the current track's lyrics; positive = lines show later. */
    val lyricsOffset: StateFlow<Long> get() = lyricsModel.offset

    /** Changes whenever any track's lyrics offset does. */
    val lyricsOffsetsVersion: StateFlow<Int> get() = lyricsModel.offsetsVersion

    fun shiftLyrics(deltaMs: Long) = lyricsModel.shift(deltaMs)
    fun resetLyricsOffset() = lyricsModel.resetOffset()

    /** Lyrics of any track, not only the one on screen. Null if unknown or failed. */
    suspend fun lyricsFor(songId: Long): Lyrics? = lyricsModel.lyricsFor(songId)

    fun lyricsOffsetFor(songId: Long): Long = lyricsModel.offsetFor(songId)

    /** A random line from the lyrics already on this device, for the home screen's greeting. */
    suspend fun randomLyricQuote(): LyricQuote? = lyricsModel.randomQuote(songsById.keys)

    /**
     * Plays [quote]'s track on its own, from just before the line when the lyrics say where it's
     * sung (with the track's offset), else from the start.
     */
    fun playQuote(quote: LyricQuote) {
        val p = queuePlayer ?: return
        val song = songsById[quote.songId] ?: return
        quitQuizAudio()
        p.play(listOf(song), 0, p.shuffle)
        val at = quote.atMs?.let { it + lyricsModel.offsetFor(song.id) - QUOTE_LEAD_MS }
        if (at != null && at > 0) p.seekTo(0, at)
    }

    // The quiz ----------------------------------------------------------------------------------------

    /**
     * A round of the quiz over [pool]: the track to guess (one not in [used], while there are
     * any) and [QUIZ_OPTIONS] tracks to pick from. By lyrics it comes with a line of it; by ear,
     * with where its snippet starts. The other options have titles unlike it and each other, from
     * [pool] while it has enough, else from the whole library. Null when no line is found, or
     * there aren't enough titles.
     */
    suspend fun quizRound(mode: QuizMode, pool: List<Song>, used: Set<Long>): QuizRound? {
        val fresh = pool.filter { it.id !in used }.ifEmpty { pool }
        val quote: LyricQuote?
        val answer: Song
        when (mode) {
            QuizMode.Lyrics -> {
                quote = lyricsModel.randomQuote(fresh.mapTo(HashSet()) { it.id })
                    ?: (if (fresh.size < pool.size) lyricsModel.randomQuote(pool.mapTo(HashSet()) { it.id }) else null)
                    ?: return null
                answer = songsById[quote.songId] ?: return null
            }
            QuizMode.Listen -> {
                quote = null
                answer = fresh.random()
            }
        }
        val others = (pool.shuffled().asSequence() + librarySongs().shuffled().asSequence())
            .filter { quizKey(it) != quizKey(answer) }
            .distinctBy(::quizKey)
            .take(QUIZ_OPTIONS - 1)
            .toList()
        if (others.size < QUIZ_OPTIONS - 1) return null
        // By ear, from somewhere in the middle: the start is often silence or a long intro.
        val fromMs = if (answer.durationMs > QUIZ_SNIPPET_MS * 3) {
            (answer.durationMs * (0.25 + Math.random() * 0.4)).toLong()
        } else {
            0L
        }
        return QuizRound(answer, (others + answer).shuffled(), quote, fromMs)
    }

    private fun quizKey(song: Song) = song.title.trim().lowercase()

    private val _quizHidesPlayer = MutableStateFlow(false)

    /** True while a snippet plays unanswered: the players don't show what it is, not to give it away. */
    val quizHidesPlayer: StateFlow<Boolean> = _quizHidesPlayer.asStateFlow()

    /** What played before the quiz took the player, to come back to after it. */
    private class SavedQueue(val songs: List<Song>, val index: Int, val positionMs: Long, val playing: Boolean, val shuffle: Boolean)
    private var beforeQuiz: SavedQueue? = null

    /** Plays [round]'s snippet from its start, hiding the track in the players until [quizReveal]. */
    fun quizSnippet(round: QuizRound) {
        val p = queuePlayer ?: return
        if (beforeQuiz == null) {
            beforeQuiz = SavedQueue((0 until p.itemCount).map(p::songAt), p.currentIndex, p.positionMs, p.isPlaying, p.shuffle)
        }
        QuizPlayback.active = true
        _quizHidesPlayer.value = true
        p.play(listOf(round.answer), 0, false)
        if (round.fromMs > 0) p.seekTo(0, round.fromMs)
    }

    fun quizPause() {
        queuePlayer?.playWhenReady = false
    }

    fun quizReveal() {
        _quizHidesPlayer.value = false
    }

    /** Plays [song] for real, from its start: what played before the quiz no longer comes back. */
    fun quizPlay(song: Song) {
        quitQuizAudio()
        playSongs(listOf(song), 0)
    }

    /** The quiz is left: what played before it comes back where it was, paused if it was. */
    fun quizEnd() {
        _quizHidesPlayer.value = false
        QuizPlayback.active = false
        val saved = beforeQuiz ?: return
        beforeQuiz = null
        val p = queuePlayer ?: return
        if (saved.songs.isEmpty() || saved.index !in saved.songs.indices) {
            p.playWhenReady = false
            return
        }
        p.play(saved.songs, saved.index, saved.shuffle)
        p.seekTo(saved.index, saved.positionMs)
        if (!saved.playing) p.playWhenReady = false
    }

    private fun quitQuizAudio() {
        beforeQuiz = null
        QuizPlayback.active = false
        _quizHidesPlayer.value = false
    }

    suspend fun lyricsCacheSize(): Long = lyricsModel.cacheSize()
    fun clearLyricsCache() = lyricsModel.clearCache()

    fun setLyricsVisible(visible: Boolean) = lyricsModel.setVisible(visible)
    fun retryLyrics() = lyricsModel.retry()

    /** Where a lyrics search starts: the current track's artist and title. */
    fun lyricsSearchText(): String = lyricsModel.searchText()

    suspend fun searchLyrics(text: String): List<LyricsCandidate> = lyricsModel.search(text)

    /** Puts lyrics picked by hand on the current track, for good. */
    fun pickLyrics(lyrics: Lyrics) = lyricsModel.pick(lyrics)

    suspend fun ownLyrics(songId: Long): String? = lyricsModel.own(songId)

    /** Lyrics the user wrote in the editor: they stay over anything found, until removed. */
    fun saveOwnLyrics(songId: Long, text: String) = lyricsModel.saveOwn(songId, text)

    fun removeOwnLyrics(songId: Long) = lyricsModel.removeOwn(songId)

    // Updates -----------------------------------------------------------------------------------------

    // Quietly on launch, unless switched off.
    private val updates = UpdatePrompt(scope, updater, autoCheck = settings.value.checkUpdates)
    val update: StateFlow<UpdateState> get() = updates.state
    /** Whether the "new version" dialog is up. */
    val updatePrompt: StateFlow<Boolean> get() = updates.shown

    fun checkForUpdates() = updates.check()
    fun showUpdate() = updates.show()
    fun installUpdate(release: AppRelease) = updates.install(release)
    fun dismissUpdate() = updates.dismiss()

    init {
        // With even volume on, the whole library gets measured, a track at a time.
        scope.launch {
            combine(settings.map { it.normalizeVolume }.distinctUntilChanged(), _library) { on, lib -> on to lib }
                .collect { (on, lib) -> if (on && lib is LibraryState.Ready) scanLoudness(lib.songs) }
        }
    }

    // The library -------------------------------------------------------------------------------------

    private var loadJob: Job? = null

    fun loadLibrary(force: Boolean = false) {
        if (!force && _library.value is LibraryState.Ready) return
        onLoadStarted()
        // A newer load supersedes one still running.
        loadJob?.cancel()
        loadJob = scope.launch {
            val songs = runCatching { loadSongs() }.getOrElse { emptyList() }
            val first = _library.value !is LibraryState.Ready
            // Nothing that matters changed (another app's ringtone, say): the screens are left alone.
            if (!first && (_library.value as? LibraryState.Ready)?.songs == songs) return@launch
            songsById = songs.associateBy { it.id }
            onSongsLoaded(songs, first)
            _collections.value = withContext(Dispatchers.Default) { MusicCollections.from(songs) }
            _library.value = LibraryState.Ready(songs)
            queuePlayer?.let {
                refreshQueueItems(it)
                syncQueue(it)
            }
        }
    }

    fun rescanLibrary() = loadLibrary(force = true)

    fun librarySongs(): List<Song> = (_library.value as? LibraryState.Ready)?.songs.orEmpty()

    /** Resolves stored ids against the current library, silently skipping deleted files. */
    fun resolve(ids: Collection<Long>): List<Song> = ids.mapNotNull { songsById[it] }

    fun song(id: Long): Song? = songsById[id]

    /** The track as its file has it, to tell in-app edits apart from what's really there. */
    suspend fun fileTags(id: Long): Song? = songsById[id]?.let { readFileTags(it) }

    // Playback ----------------------------------------------------------------------------------------

    /**
     * Replaces the queue with [songs] from [startIndex]. [shuffle] null keeps shuffle as it is:
     * picking a track doesn't switch it off (issue #6); a shuffled queue then starts from that track.
     */
    fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean? = null) {
        val p = queuePlayer ?: return
        p.play(songs, startIndex, shuffle ?: p.shuffle)
    }

    fun shuffleAll() {
        val songs = librarySongs()
        if (songs.isNotEmpty()) playSongs(songs, songs.indices.random(), shuffle = true)
    }

    fun togglePlay() {
        queuePlayer?.togglePlay()
    }

    /** The song playing slowly fades away and stops, like at the end of a film. */
    fun fadeOutAndPause(durationMs: Long) {
        queuePlayer?.takeIf { it.isPlaying }?.fadeOutAndPause(durationMs)
    }

    fun playNext(song: Song) = playNext(listOf(song))
    fun addToQueue(song: Song) = addToQueue(listOf(song))

    /** Several tracks right after the current one, in the order given. */
    fun playNext(songs: List<Song>) {
        val p = queuePlayer ?: return
        if (songs.isEmpty()) return
        if (p.itemCount == 0) return playSongs(songs, 0)
        p.insert(p.currentIndex + 1, songs)
    }

    fun addToQueue(songs: List<Song>) {
        val p = queuePlayer ?: return
        if (songs.isEmpty()) return
        if (p.itemCount == 0) return playSongs(songs, 0)
        p.append(songs)
    }

    fun playQueueEntry(key: String) {
        val p = queuePlayer ?: return
        p.indexOfKey(key)?.let(p::playAt)
    }

    fun removeQueueEntry(key: String) {
        val p = queuePlayer ?: return
        p.indexOfKey(key)?.let(p::remove)
    }

    /** Moves an upcoming entry to [position] among the upcoming ones (0 = right after the current). */
    fun moveQueueEntry(key: String, position: Int) {
        val p = queuePlayer ?: return
        val from = p.indexOfKey(key) ?: return
        val to = (p.currentIndex + 1 + position).coerceIn(0, p.itemCount - 1)
        if (from != to) p.move(from, to)
    }

    fun next() {
        queuePlayer?.next()
    }

    fun previous() {
        queuePlayer?.previous()
    }

    fun seekTo(positionMs: Long) {
        queuePlayer?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        queuePlayer?.let { it.shuffle = !it.shuffle }
    }

    fun cycleRepeat() {
        queuePlayer?.let { it.repeatMode = nextRepeatMode(it.repeatMode) }
    }

    fun currentSongId(): Long? = _player.value.nowPlaying?.songId

    /** An entry by its key, or by its slot where [syncQueue] gave it that over a clash. */
    private fun QueuePlayer.indexOfKey(key: String): Int? =
        (0 until itemCount).firstOrNull { keyAt(it) == key || "slot:$it" == key }

    protected fun syncState(p: QueuePlayer) {
        val index = p.currentIndex
        val song = if (index >= 0 && index < p.itemCount) p.songAt(index) else null
        _player.value = PlayerState(
            connected = true,
            nowPlaying = song?.let { NowPlaying(songId = it.id, albumId = it.albumId, title = it.title, artist = it.artist) },
            isPlaying = p.isPlaying,
            isBuffering = p.isBuffering,
            durationMs = p.durationMs,
            shuffle = p.shuffle,
            repeatMode = p.repeatMode,
        )
    }

    protected fun syncQueue(p: QueuePlayer) {
        val count = p.itemCount
        if (count == 0 || p.currentIndex < 0) {
            _queue.value = QueueState()
            return
        }
        // Walk the play order (shuffle included) from the current track. A shuffled queue on
        // "repeat all" goes round, so it lists every other track; in list order it stops at the
        // end, which keeps drag positions meaning "right after the current track".
        val wrap = p.shuffle && p.repeatMode == RepeatMode.ALL
        val order = ArrayList<Int>()
        val visited = HashSet<Int>()
        var i = p.currentIndex
        while (i >= 0 && order.size < count && visited.add(i)) {
            order += i
            i = p.nextIndex(i, if (wrap) RepeatMode.ALL else RepeatMode.OFF)
        }
        val seen = HashSet<String>()
        val entries = order.map { index ->
            val carried = p.songAt(index)
            // LazyColumn needs unique keys; a clash falls back to the slot.
            val key = p.keyAt(index).let { if (seen.add(it)) it else "slot:$index" }
            QueueEntry(key, songsById[carried.id] ?: carried)
        }
        // The whole play order from its start, for the cassette's two sides.
        val tapeOrder = ArrayList<Int>(count)
        val inTape = HashSet<Int>()
        var t = p.firstIndex()
        while (t >= 0 && inTape.add(t)) {
            tapeOrder += t
            t = p.nextIndex(t, RepeatMode.OFF)
        }
        val tape = tapeSides(tapeOrder.map { p.songAt(it).durationMs }, tapeOrder.indexOf(p.currentIndex))
        val sideStarts = buildMap {
            tapeOrder.firstOrNull()?.let { put(p.keyAt(it), "A") }
            tapeOrder.getOrNull(tape.sideBStart)?.let { put(p.keyAt(it), "B") }
        }
        _queue.value = QueueState(entries.first(), entries.drop(1), !p.shuffle, tape, sideStarts)
    }

    /** Queued entries carry the tags they were queued with; edited tracks get their new ones. */
    private fun refreshQueueItems(p: QueuePlayer) {
        for (i in 0 until p.itemCount) {
            val song = songsById[p.songAt(i).id] ?: continue
            p.refresh(i, song)
        }
    }

    // Favourites, playlists, radio ----------------------------------------------------------------------

    fun addFavorites(songIds: List<Long>) = userLibrary.addFavorites(songIds)
    fun addToPlaylist(id: Long, songIds: List<Long>) = userLibrary.addToPlaylist(id, songIds)
    fun removeFromPlaylist(id: Long, songIds: Set<Long>) = userLibrary.removeFromPlaylist(id, songIds)
    fun addToPlaylist(id: Long, songId: Long) = userLibrary.addToPlaylist(id, songId)
    fun removeFromPlaylist(id: Long, songId: Long) = userLibrary.removeFromPlaylist(id, songId)

    /** For a radio track not in the library yet, the heart keeps it: downloaded into it, as a favourite. */
    fun toggleFavorite(songId: Long) {
        if (RadioTrack.isRadio(songId)) radioControl.keep(songId) else userLibrary.toggleFavorite(songId)
    }

    /** The radio playing now (beta), or null. */
    val radio: StateFlow<RadioState?> get() = radioControl.state

    fun startRadio(start: RadioStart) = radioControl.start(librarySongs(), start)

    fun startArtistRadio(name: String) {
        val songs = librarySongs()
        radioControl.start(songs, RadioStart.artist(name, songs))
    }

    fun dislikeRadio(songId: Long) = radioControl.dislike(songId)
    fun stopRadio() = radioControl.stop()

    fun createPlaylist(name: String, initialSongs: List<Long> = emptyList()) =
        userLibrary.createPlaylist(name, initialSongs)
    fun renamePlaylist(id: Long, name: String) = userLibrary.renamePlaylist(id, name)
    fun deletePlaylist(id: Long) {
        userLibrary.deletePlaylist(id)
        CoverStore.removePlaylistCover(id)
    }
    fun setPlaylistShape(id: Long, shape: String?) = userLibrary.setPlaylistShape(id, shape)
    fun removePlaylistCover(id: Long) = CoverStore.removePlaylistCover(id)

    /** Makes the picked picture the playlist's cover. */
    fun setPlaylistCover(id: Long, image: PickedImage) {
        scope.launch {
            val file = importCover(image) ?: return@launch
            withContext(Dispatchers.IO) { CoverStore.setPlaylistCover(id, file) }
            file.delete()
        }
    }

    fun putSmartPlaylist(playlist: SmartPlaylist) = userLibrary.putSmartPlaylist(playlist)
    fun deleteSmartPlaylist(id: Long) = userLibrary.deleteSmartPlaylist(id)

    /**
     * Makes a playlist of an .m3u file's tracks found in the library, named as the file says or
     * else [name]. [baseDir] is the file's folder, for relative paths.
     */
    protected suspend fun importM3u(bytes: ByteArray, baseDir: String?, name: () -> String): M3uMatch =
        withContext(Dispatchers.IO) {
            val file = parseM3u(decodeM3u(bytes))
            val match = M3uMatcher(librarySongs(), songPaths(), baseDir).match(file)
            if (match.songIds.isNotEmpty()) userLibrary.createPlaylist(file.name ?: name(), match.songIds)
            match
        }

    /** [playlist] as the text of an .m3u8 file. */
    protected suspend fun m3uText(playlist: Playlist): String = withContext(Dispatchers.IO) {
        writeM3u(playlist.name, resolve(playlist.songIds), songPaths())
    }

    // Backups -----------------------------------------------------------------------------------------

    /** A backup of the settings, playlists, favourites and history, as the bytes of its file. */
    protected suspend fun backupBytes(): ByteArray {
        val plays = allPlays()
        return withContext(Dispatchers.IO) {
            Backup(
                createdAtMs = System.currentTimeMillis(),
                platform = platform,
                appVersion = appVersion,
                settings = commonSettings(),
                library = LibrarySnapshot.of(librarySongs(), userData.value, plays),
            ).encode()
        }
    }

    /** The backup in [bytes], or null if it isn't one. */
    protected fun decodeBackup(bytes: ByteArray?): Backup? = bytes?.let { Backup.decode(it, commonSettings()) }

    /**
     * Takes [backup]'s settings and adds what's missing of its playlists, favourites and history.
     * @return how many of the tracks it names were found here, and how many it names.
     */
    suspend fun restoreBackup(backup: Backup): Pair<Int, Int> {
        val songs = librarySongs()
        val plays = allPlays()
        val outcome = withContext(Dispatchers.Default) {
            LibrarySync.merge(LibrarySnapshot.of(songs, userData.value, plays, allTracks = true), backup.library, base = null)
        }
        userLibrary.apply(outcome.local)
        mergePlays(outcome.local.plays)
        backup.settings?.let { s -> SharedSettingsStore.update { s } }
        return outcome.matched to backup.library.tracks.size
    }

    // Track editing -----------------------------------------------------------------------------------

    override fun hasInAppEdits(songId: Long): Boolean = tagOverrides.has(songId) || CoverStore.songVersion(songId) != null

    override fun searchMetadata(query: String, durationMs: Long): Flow<List<MetadataCandidate>> = metadata.search(query, durationMs)

    /** A picture [write] puts into a file, made into a cover-sized JPEG; null if it isn't one. */
    protected suspend fun coverFrom(write: (File) -> Unit): File? = withContext(Dispatchers.IO) {
        val raw = coverPickFile("img")
        runCatching { write(raw) }
        val out = coverPickFile("jpg")
        val ok = writeScaledJpeg(raw, out)
        raw.delete()
        out.takeIf { ok }
    }

    override suspend fun downloadCover(url: String): File? {
        val bytes = metadata.download(url) ?: return null
        return coverFrom { it.writeBytes(bytes) }
    }

    private fun coverPickFile(ext: String): File {
        val dir = File(cache, COVER_PICKS).apply { mkdirs() }
        return File(dir, "pick_${System.nanoTime()}.$ext")
    }

    /** Saves [edit] as app records: the file stays untouched. Empty fields fall back to the file's tags. */
    override suspend fun saveInApp(song: Song, edit: TrackEdit) {
        storeInApp(song, edit)
        refreshAfterEdit()
    }

    override suspend fun saveAll(edits: List<Pair<Song, TrackEdit>>, target: SaveTarget, onProgress: (Int) -> Unit): Int {
        var saved = 0
        edits.forEachIndexed { i, (song, edit) ->
            if (target == SaveTarget.App) {
                storeInApp(song, edit)
                saved++
            } else if (writeToFile(song, edit)) {
                saved++
            }
            onProgress(i + 1)
        }
        // The library is read again once, not after every track.
        if (saved > 0) refreshAfterEdit()
        return saved
    }

    private suspend fun storeInApp(song: Song, edit: TrackEdit) {
        val file = readFileTags(song)
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
    }

    /**
     * Writes [edit] into the file's tags.
     * The app's own edits of this track are folded in and then dropped: the file is the truth now.
     */
    override suspend fun saveToFile(song: Song, edit: TrackEdit): Boolean =
        writeToFile(song, edit).also { if (it) refreshAfterEdit() }

    private suspend fun writeToFile(song: Song, edit: TrackEdit): Boolean {
        val cover = withContext(Dispatchers.IO) {
            when (val c = edit.cover) {
                // A cover picked "in the app" earlier goes into the file rather than being lost.
                CoverEdit.Keep -> CoverStore.songFile(song.id)?.let { runCatching { it.readBytes() }.getOrNull() }
                CoverEdit.Remove -> null
                is CoverEdit.Replace -> runCatching { c.file.readBytes() }.getOrNull()
            }
        }
        // The player holds the file open while it plays; rewriting it underneath breaks playback.
        val p = queuePlayer?.takeIf { it.currentIndex in 0 until it.itemCount && it.songAt(it.currentIndex).id == song.id }
        val resume = p?.let { Triple(it.currentIndex, it.positionMs, it.playWhenReady) }
        p?.stopAndRelease()
        val ok = writeTags(
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
            invalidateArtwork(song)
        }
        if (p != null && resume != null) {
            val (index, positionMs, playWhenReady) = resume
            p.seekTo(index, positionMs)
            p.prepare()
            p.playWhenReady = playWhenReady
        }
        return ok
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
        queuePlayer?.let(::refreshQueueItems)
    }

    /** The file is gone: forget everything the app kept about it. */
    fun onSongDeleted(songId: Long) {
        queuePlayer?.let { p ->
            for (i in p.itemCount - 1 downTo 0) {
                if (p.songAt(i).id == songId) p.remove(i)
            }
        }
        userLibrary.forgetSong(songId)
        lyricsModel.dropOffset(songId)
        scope.launch {
            withContext(Dispatchers.IO) {
                tagOverrides.set(songId, null)
                CoverStore.removeSongCover(songId)
            }
            lyricsModel.forget(songId)
        }
        loadLibrary(force = true)
    }

    open fun dispose() {
        File(cache, COVER_PICKS).listFiles()?.forEach { it.delete() }
        scope.cancel()
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
        const val STOP_TIMEOUT_MS = 1_000L
        const val COVER_PICKS = "cover-picks"
    }
}

/** How a quiz guesses: by a line of the lyrics, or by ear. */
enum class QuizMode { Lyrics, Listen }

/**
 * A round of the quiz: the track to guess, the tracks to pick from, and, by lyrics, a line of it;
 * by ear, where in it the snippet starts.
 */
@Immutable
data class QuizRound(val answer: Song, val options: List<Song>, val quote: LyricQuote?, val fromMs: Long)

/** How long a quiz snippet plays. */
const val QUIZ_SNIPPET_MS = 10_000L

/** How many tracks a quiz round offers. */
const val QUIZ_OPTIONS = 4

/** A quoted line plays from this long before it's sung, to come in on it. */
private const val QUOTE_LEAD_MS = 1_500L
