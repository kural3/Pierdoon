package com.ku.pierdoon.data.download

import com.ku.pierdoon.data.DEFAULT_SAVE_FOLDER
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ku.pierdoon.data.COVER_MAX_PX
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.TagOverride
import com.ku.pierdoon.data.TagOverrides
import com.ku.pierdoon.data.UserLibrary
import com.ku.pierdoon.data.metadata.MetadataSearch
import com.ku.pierdoon.data.metadata.TagWriter
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/** What the user asked for. Neither is converted: both are the site's own audio, as is. */
enum class DownloadFormat {
    /** AAC in .m4a: plays and takes tags everywhere. */
    M4a,

    /** Opus, YouTube's best audio; repackaged from WebM into .opus without re-encoding. */
    Opus,
}

enum class FailReason { Unsupported, Unavailable, Drm, NoAudio, Network, Save, Other }

sealed interface DownloadStatus {
    data object Queued : DownloadStatus

    /** Starting yt-dlp and reading the page. */
    data object Preparing : DownloadStatus

    /** [progress] 0..1, or null while unknown. */
    data class Downloading(val progress: Float?) : DownloadStatus

    /** Tags, cover and the copy into the music folder. */
    data object Saving : DownloadStatus

    data class Done(val songId: Long) : DownloadStatus
    data class Failed(val reason: FailReason, val detail: String?) : DownloadStatus
}

@Immutable
data class DownloadTask(
    val id: Long,
    val url: String,
    val format: DownloadFormat,
    val title: String? = null,
    val artist: String? = null,
    val status: DownloadStatus = DownloadStatus.Queued,
    /** Set on the tracks of a playlist or album link; they end up in a Pier.doon playlist together. */
    val group: DownloadGroup? = null,
) {
    val active: Boolean get() = status !is DownloadStatus.Done && status !is DownloadStatus.Failed
}

@Immutable
data class DownloadGroup(val id: Long, val name: String)

enum class SearchSource(internal val prefix: String) { YouTube("ytsearch"), SoundCloud("scsearch") }

/** One search result: enough to recognise the track, and the link to download it. */
@Immutable
data class SearchHit(val url: String, val title: String, val artist: String, val durationSec: Int?, val thumbnail: String?)

sealed interface EngineUpdate {
    data object Idle : EngineUpdate
    data object Running : EngineUpdate
    data object UpToDate : EngineUpdate
    data object Updated : EngineUpdate
    data object Failed : EngineUpdate
}

/**
 * Downloads audio from YouTube, SoundCloud, Bandcamp and the many other sites yt-dlp knows,
 * one link at a time, into Music/Pier.doon, where the library picks it up like any other file.
 *
 * yt-dlp runs in its own Python process (youtubedl-android). Sites change often, so it updates
 * itself from GitHub at most once a day, without waiting for a new Pier.doon release.
 * A foreground [DownloadService] keeps the process alive while anything is in the queue.
 */
object Downloader {
    /** MediaStore's RELATIVE_PATH and IS_PENDING arrived in Android 10. */
    val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engineLock = Mutex()
    private var engineReady = false
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var running: Pair<Long, Job>? = null
    private var nextId = 1L

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _engineVersion = MutableStateFlow<String?>(null)
    val engineVersion: StateFlow<String?> = _engineVersion.asStateFlow()

    private val _engineUpdate = MutableStateFlow<EngineUpdate>(EngineUpdate.Idle)
    val engineUpdate: StateFlow<EngineUpdate> = _engineUpdate.asStateFlow()

    /** Set by a link shared into the app; the UI opens the downloads page and clears it. */
    val openRequest = MutableStateFlow(false)

    /** Playlists made for finished groups, so a track retried later joins its playlist. */
    private val groupPlaylists = mutableMapOf<Long, Long>()

    @Synchronized
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        CoverStore.init(app)
        scope.launch { for (u in wake) drain() }
    }

    /** Unpacks Python and yt-dlp ahead of time (a few seconds, once per install) so a download starts quicker. */
    fun warmUp() {
        if (supported) scope.launch { runCatching { ensureEngine(update = false) } }
    }

    fun enqueue(url: String, format: DownloadFormat, title: String? = null, artist: String? = null) {
        if (!supported) return
        val task = synchronized(this) { DownloadTask(nextId++, url.trim(), format, title, artist) }
        _tasks.update { it + task }
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        wake.trySend(Unit)
    }

    /** Stops [id] if it's running and takes it off the list. */
    fun remove(id: Long) {
        val group = _tasks.value.firstOrNull { it.id == id }?.group
        _tasks.update { list -> list.filterNot { it.id == id } }
        synchronized(this) { running?.takeIf { it.first == id }?.second?.cancel() }
        // The cancelled track may have been the last one the group was waiting for.
        group?.let { scope.launch { finishGroup(it) } }
    }

    /**
     * Up to [SEARCH_RESULTS] tracks for [query]. Throws when yt-dlp can't reach the site.
     * SoundCloud's 30-second previews of paid tracks are left out: that's all a download would get.
     */
    suspend fun search(query: String, source: SearchSource): List<SearchHit> = withContext(Dispatchers.IO) {
        ensureEngine(update = true)
        val request = YoutubeDLRequest("${source.prefix}$SEARCH_RESULTS:${query.trim()}").apply {
            addOption("--flat-playlist")
            addOption("-J")
        }
        val out = execute(request, "pierdoon-search-${System.nanoTime()}").out
        val entries = JSONObject(out).optJSONArray("entries") ?: return@withContext emptyList()
        List(entries.length()) { entries.optJSONObject(it) }.mapNotNull { e ->
            val url = e?.str("url") ?: return@mapNotNull null
            val duration = e.optDouble("duration").takeIf { !it.isNaN() }
            if (source == SearchSource.SoundCloud && duration == 30.0) return@mapNotNull null
            val name = trackName(e.str("title"), null, null, e.str("uploader") ?: e.str("channel"))
            SearchHit(url, name.title, name.artist, duration?.toInt(), e.thumbnail())
        }
    }

    fun retry(id: Long) {
        _tasks.update { list ->
            list.map { if (it.id == id && it.status is DownloadStatus.Failed) it.copy(status = DownloadStatus.Queued) else it }
        }
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        wake.trySend(Unit)
    }

    fun clearFinished() = _tasks.update { list -> list.filter { it.active } }

    fun updateEngine() {
        if (!supported || _engineUpdate.value == EngineUpdate.Running) return
        scope.launch {
            _engineUpdate.value = EngineUpdate.Running
            _engineUpdate.value = try {
                ensureEngine(update = false)
                val status = engineLock.withLock { YoutubeDL.updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE) }
                prefs().edit { putLong(K_CHECKED, System.currentTimeMillis()) }
                refreshVersion()
                if (status == YoutubeDL.UpdateStatus.DONE) EngineUpdate.Updated else EngineUpdate.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp update failed", e)
                EngineUpdate.Failed
            }
        }
    }

    private suspend fun drain() {
        while (true) {
            val next = _tasks.value.firstOrNull { it.status == DownloadStatus.Queued } ?: return
            // Registered before it starts, so a remove() right away still finds it to cancel.
            val job = scope.launch(start = CoroutineStart.LAZY) { run(next) }
            synchronized(this) { running = next.id to job }
            // Removed before it got here: it isn't run at all.
            if (_tasks.value.none { it.id == next.id }) job.cancel() else job.start()
            job.join()
            synchronized(this) { running = null }
            next.group?.let { finishGroup(it) }
        }
    }

    /** Once nothing in [group] is left to download, its finished tracks become a playlist, in order. */
    private fun finishGroup(group: DownloadGroup) {
        val members = _tasks.value.filter { it.group?.id == group.id }
        val songIds = members.mapNotNull { (it.status as? DownloadStatus.Done)?.songId }
        val library = UserLibrary.get(app)
        synchronized(groupPlaylists) {
            val existing = groupPlaylists[group.id]
            when {
                existing != null -> library.addToPlaylist(existing, songIds)
                members.none { it.active } && songIds.isNotEmpty() ->
                    groupPlaylists[group.id] = library.createPlaylist(group.name, songIds)
            }
        }
    }

    /** A playlist or album link: its tracks take [task]'s place in the queue. False if it's a single track. */
    private suspend fun expand(task: DownloadTask): Boolean {
        val request = YoutubeDLRequest(task.url).apply {
            addOption("--flat-playlist")
            addOption("--no-playlist")
            addOption("-J")
            // The link after "--", as in fetch().
            addCommands(listOf("--"))
        }
        val info = JSONObject(execute(request, "pierdoon-${task.id}").out)
        if (info.str("_type") != "playlist") return false
        val entries = info.optJSONArray("entries")
        // Channel pages list their tabs as playlists of their own; only tracks are taken.
        val tracks = List(entries?.length() ?: 0) { entries!!.optJSONObject(it) }
            .filter { it != null && it.str("url") != null && it.str("_type") != "playlist" }
        if (tracks.isEmpty()) throw DownloadException(FailReason.Unavailable, null)
        val group = DownloadGroup(task.id, playlistName(info.str("title")) ?: task.url)
        val expanded = synchronized(this) {
            tracks.map { e ->
                val name = trackName(e.str("title"), null, null, e.str("uploader") ?: e.str("channel"))
                DownloadTask(nextId++, e.str("url")!!, task.format, name.title.ifBlank { null }, name.artist.ifBlank { null }, group = group)
            }
        }
        _tasks.update { list -> list.flatMap { if (it.id == task.id) expanded else listOf(it) } }
        return true
    }

    private fun set(id: Long, transform: (DownloadTask) -> DownloadTask) =
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }

    private suspend fun run(task: DownloadTask) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val work = File(app.cacheDir, "downloads/${task.id}").apply { deleteRecursively(); mkdirs() }
        try {
            set(task.id) { it.copy(status = DownloadStatus.Preparing) }
            ensureEngine(update = true)
            if (task.group == null && !looksLikeSingleTrack(task.url) && expand(task)) return
            val info = fetch(task, work)
            set(task.id) { it.copy(status = DownloadStatus.Saving) }
            val songId = save(task, info, work)
            set(task.id) { it.copy(status = DownloadStatus.Done(songId)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadException) {
            set(task.id) { it.copy(status = DownloadStatus.Failed(e.reason, e.detail)) }
        } catch (e: Exception) {
            Log.w(TAG, "Download failed: ${task.url}", e)
            set(task.id) { it.copy(status = DownloadStatus.Failed(classify(e.message), errorLine(e.message))) }
        } finally {
            work.deleteRecursively()
        }
    }

    private suspend fun ensureEngine(update: Boolean) = engineLock.withLock {
        if (!engineReady) {
            // Unpacking Python half-way would leave a broken copy: not interrupted by a cancel.
            withContext(NonCancellable) { YoutubeDL.init(app) }
            engineReady = true
            refreshVersion()
        }
        val checked = prefs().getLong(K_CHECKED, 0L)
        if (update && System.currentTimeMillis() - checked > UPDATE_EVERY_MS) {
            // A stale yt-dlp is the usual reason a site stops working; failing to update isn't fatal,
            // and the next download tries again.
            try {
                runInterruptible { YoutubeDL.updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE) }
                prefs().edit { putLong(K_CHECKED, System.currentTimeMillis()) }
                refreshVersion()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp update failed", e)
            }
        }
    }

    private fun refreshVersion() {
        // Known only once it has updated itself; the bundled copy reports nothing.
        _engineVersion.value = runCatching { YoutubeDL.version(app) }.getOrNull()
    }

    /** Runs yt-dlp: the audio file and its .info.json land in [work]. */
    private suspend fun fetch(task: DownloadTask, work: File): JSONObject {
        val request = YoutubeDLRequest(task.url).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--newline")
            addOption("--write-info-json")
            addOption("-f", formatSelector(task.format))
            addOption("-o", File(work, "%(id)s.%(ext)s").path)
            // Links come from sites too (search results, playlist entries): after "--" one that
            // starts with "-" is still a link, never an option such as --exec. The library puts
            // the link last, right after these commands.
            addCommands(listOf("--"))
        }
        execute(request, "pierdoon-${task.id}") { progress, line ->
            if (!line.startsWith("[download]")) return@execute
            set(task.id) {
                it.copy(status = DownloadStatus.Downloading(if (progress > 0f) (progress / 100f).coerceIn(0f, 1f) else null))
            }
            // The .info.json is written before the audio: show the name as soon as it's known.
            if (tasks.value.firstOrNull { it.id == task.id }?.title == null) readInfo(work)?.let { showName(task.id, it) }
        }
        val info = readInfo(work) ?: throw DownloadException(FailReason.Other, null)
        showName(task.id, info)
        return info
    }

    /** Runs yt-dlp; a cancelled coroutine kills the Python process instead of waiting for it. */
    private suspend fun execute(
        request: YoutubeDLRequest,
        processId: String,
        onLine: ((Float, String) -> Unit)? = null,
    ): YoutubeDLResponse {
        val stop = coroutineContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) YoutubeDL.destroyProcessById(processId)
        }
        try {
            return runInterruptible {
                if (onLine == null) {
                    YoutubeDL.execute(request, processId)
                } else {
                    YoutubeDL.execute(request, processId) { progress, _, line -> onLine(progress, line) }
                }
            }
        } finally {
            stop.dispose()
        }
    }

    private fun readInfo(work: File): JSONObject? =
        work.listFiles { f -> f.name.endsWith(".info.json") }?.firstOrNull()
            ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }

    private fun showName(id: Long, info: JSONObject) {
        val name = info.trackName()
        set(id) { it.copy(title = name.title.ifBlank { null }, artist = name.artist.ifBlank { null }) }
    }

    /** Tags the file, gives it a cover and moves it into Music/Pier.doon. Returns its MediaStore id. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun save(task: DownloadTask, info: JSONObject, work: File): Long {
        var audio = work.listFiles { f -> !f.name.endsWith(".json") && !f.name.endsWith(".part") }
            ?.maxByOrNull { it.length() }
            ?: throw DownloadException(FailReason.Other, null)
        if (audio.extension.equals("webm", ignoreCase = true)) {
            val opus = File(work, audio.nameWithoutExtension + ".opus")
            if (!remux(audio, opus, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG, MediaFormat.MIMETYPE_AUDIO_OPUS)) {
                throw DownloadException(FailReason.NoAudio, null)
            }
            audio.delete()
            audio = opus
        } else if (audio.extension.equals("m4a", ignoreCase = true)) {
            // Without ffmpeg yt-dlp leaves YouTube's DASH m4a fragmented, and fragmented MP4 takes
            // no tags. Rewritten as a plain MP4 it does; if that fails the original is kept.
            val plain = File(work, audio.nameWithoutExtension + ".plain.m4a")
            if (remux(audio, plain, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, MediaFormat.MIMETYPE_AUDIO_AAC)) {
                runCatching { dropMuxerExtras(plain) }.onFailure { Log.w(TAG, "moov not cleaned", it) }
                audio.delete()
                audio = plain
            }
        }
        val ext = audio.extension.lowercase()
        val mime = MIME[ext] ?: throw DownloadException(FailReason.NoAudio, ext)

        val name = info.trackName()
        // A single is its own album; without one MediaStore would file every download under "Pier.doon".
        val album = info.str("album") ?: name.title
        val cover = info.str("thumbnail")?.let { MetadataSearch.download(it) }?.let(::squareJpeg)
        // Tagged on a copy: a writer that gives up half-way mustn't take the audio with it.
        val tagged = File(work, "tagged.$ext")
        val inFile = TagWriter.canWrite(audio.name) && runCatching {
            audio.copyTo(tagged, overwrite = true)
            TagWriter.tagFile(tagged, name.title, name.artist, album, cover)
        }.onFailure { Log.w(TAG, "Tags not written: ${audio.name}", it) }.isSuccess
        if (inFile) audio = tagged

        val display = safeFileName(listOf(name.artist, name.title).filter { it.isNotBlank() }.joinToString(" - ")) + ".$ext"
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, display)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "$DEFAULT_SAVE_FOLDER/")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw DownloadException(FailReason.Save, null)
        val songId = ContentUris.parseId(uri)
        try {
            resolver.openOutputStream(uri)?.use { out -> audio.inputStream().use { it.copyTo(out) } }
                ?: throw DownloadException(FailReason.Save, null)
            // Some files take no tags (fragmented MP4 from HLS, for one): the app keeps them instead,
            // set before publishing so the library's first look already has them.
            if (!inFile) {
                TagOverrides.get(app).set(songId, TagOverride(name.title, name.artist.ifBlank { null }, album))
                cover?.let { bytes -> File(work, "cover.jpg").also { it.writeBytes(bytes) } }
                    ?.let { CoverStore.setSongCover(songId, it) }
            }
            // Published: the media scanner reads the tags and the library refreshes on its own.
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            TagOverrides.get(app).set(songId, null)
            CoverStore.removeSongCover(songId)
            throw if (e is DownloadException || e is CancellationException) e else DownloadException(FailReason.Save, e.message)
        }
        return songId
    }

    private fun prefs() = app.getSharedPreferences("downloader", Context.MODE_PRIVATE)

    private const val TAG = "Downloader"
    private const val K_CHECKED = "ytdlp_checked"
    private const val UPDATE_EVERY_MS = 24 * 60 * 60 * 1000L
    private const val SEARCH_RESULTS = 15
    private val MIME = mapOf(
        "m4a" to "audio/mp4",
        "mp3" to "audio/mpeg",
        "opus" to "audio/ogg",
        "ogg" to "audio/ogg",
        "flac" to "audio/flac",
        "wav" to "audio/wav",
    )
}

private class DownloadException(val reason: FailReason, val detail: String?) : Exception(detail)

/** yt-dlp picks the stream; "/bestaudio" covers sites that have only one kind (SoundCloud's mp3, for one). */
private fun formatSelector(format: DownloadFormat) = when (format) {
    DownloadFormat.M4a -> "bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio"
    DownloadFormat.Opus -> "bestaudio[acodec=opus]/bestaudio"
}

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

/** The smallest picture still sharp in a list row; sites without sizes get their last (usually biggest) one. */
private fun JSONObject.thumbnail(): String? {
    val all = optJSONArray("thumbnails")?.let { a -> List(a.length()) { a.optJSONObject(it) }.filterNotNull() }.orEmpty()
        .filter { it.str("url") != null }
    val sized = all.filter { it.optInt("width") > 0 }.sortedBy { it.optInt("width") }
    return (sized.firstOrNull { it.optInt("width") >= 160 } ?: sized.lastOrNull() ?: all.lastOrNull())?.str("url")
        ?: str("thumbnail")
}

private fun JSONObject.trackName(): TrackName {
    val artists = optJSONArray("artists")?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } }
    return trackName(
        title = str("title"),
        track = str("track"),
        artist = artists?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: str("artist") ?: str("creator"),
        uploader = str("uploader") ?: str("channel"),
    )
}

/** The reason yt-dlp gives, roughly sorted into what the user can do about it. */
private fun classify(message: String?): FailReason {
    val m = message.orEmpty()
    return when {
        "Unsupported URL" in m || "is not a valid URL" in m -> FailReason.Unsupported
        "DRM" in m -> FailReason.Drm
        "Requested format is not available" in m -> FailReason.NoAudio
        "Unable to download" in m || "Errno" in m || "timed out" in m || "getaddrinfo" in m -> FailReason.Network
        "unavailable" in m || "Private video" in m || "removed" in m || "Sign in" in m -> FailReason.Unavailable
        else -> FailReason.Other
    }
}

private fun errorLine(message: String?): String? =
    message?.lineSequence()?.lastOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()

/** Thumbnails are mostly 16:9 video frames; the centre square is what a cover looks like. */
private fun squareJpeg(bytes: ByteArray): ByteArray? = runCatching {
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val side = minOf(decoded.width, decoded.height)
    var square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
    if (side > COVER_MAX_PX) square = Bitmap.createScaledBitmap(square, COVER_MAX_PX, COVER_MAX_PX, true)
    ByteArrayOutputStream().also { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
}.getOrNull()

/**
 * Some phones' muxers (Samsung's, for one) put vendor boxes into moov, a udta of their own and a
 * bare meta, that the tag writer can't read. With moov written after mdat, as the muxer does,
 * dropping them moves no sample offsets.
 */
private fun dropMuxerExtras(file: File) = RandomAccessFile(file, "rw").use { f ->
    var pos = 0L
    var moovAt = -1L
    var moovSize = 0L
    var mdatBefore = false
    while (pos + 8 <= f.length()) {
        f.seek(pos)
        var size = f.readInt().toLong() and 0xffffffffL
        val type = ByteArray(4).also { f.readFully(it) }.toString(Charsets.ISO_8859_1)
        if (size == 1L) size = f.readLong() else if (size == 0L) size = f.length() - pos
        if (size < 8) return
        when (type) {
            "mdat" -> mdatBefore = moovAt < 0
            "moov" -> if (size <= Int.MAX_VALUE) { moovAt = pos; moovSize = size }
        }
        pos += size
    }
    if (moovAt < 0 || !mdatBefore || moovAt + moovSize != f.length()) return
    val moov = ByteArray(moovSize.toInt()).also { f.seek(moovAt); f.readFully(it) }
    val kept = ByteArrayOutputStream()
    var o = 8
    while (o + 8 <= moov.size) {
        val size = ByteBuffer.wrap(moov, o, 4).int
        if (size < 8 || o + size > moov.size) return
        val type = String(moov, o + 4, 4, Charsets.ISO_8859_1)
        if (type != "udta" && type != "meta") kept.write(moov, o, size)
        o += size
    }
    val body = kept.toByteArray()
    if (body.size + 8 == moov.size) return
    f.seek(moovAt)
    f.writeInt(body.size + 8)
    f.write("moov".toByteArray(Charsets.ISO_8859_1))
    f.write(body)
    f.setLength(moovAt + 8 + body.size)
}

/**
 * Copies the [mime] audio track of [source] into a new [format] container, packets as they are:
 * WebM → Ogg Opus, fragmented → plain MP4. ffmpeg would do it too, but costs 35 MB;
 * the platform muxer writes Ogg since Android 10.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun remux(source: File, target: File, format: Int, mime: String): Boolean {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    return try {
        extractor.setDataSource(source.path)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == mime
        } ?: return false
        extractor.selectTrack(track)
        val trackFormat = extractor.getTrackFormat(track)
        val m = MediaMuxer(target.path, format).also { muxer = it }
        val out = m.addTrack(trackFormat)
        m.start()
        val buffer = ByteBuffer.allocate(256 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            info.set(0, size, extractor.sampleTime, flags)
            m.writeSampleData(out, buffer, info)
            extractor.advance()
        }
        m.stop()
        true
    } catch (_: Exception) {
        false
    } finally {
        runCatching { muxer?.release() }
        extractor.release()
    }
}
