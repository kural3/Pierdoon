package com.ku.pierdoon.data.download

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.COVER_MAX_PX
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.TagOverride
import com.ku.pierdoon.data.TagOverrides
import com.ku.pierdoon.data.UserLibrary
import com.ku.pierdoon.data.USER_AGENT
import com.ku.pierdoon.data.metadata.MetadataSearch
import com.ku.pierdoon.data.metadata.TagWriter
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.platform.AppDirs
import com.ku.pierdoon.platform.Images
import com.ku.pierdoon.platform.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

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
 * yt-dlp.exe is fetched from its GitHub releases on first use and updates itself at most once
 * a day, without waiting for a new Pier.doon release. FFmpeg (bundled with the app) only
 * repackages the audio: WebM into .opus, YouTube's fragmented m4a into a plain one.
 */
object Downloader {
    val supported: Boolean = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engineLock = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var running: Pair<Long, Job>? = null
    private var nextId = 1L
    private var started = false

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _engineVersion = MutableStateFlow<String?>(null)
    val engineVersion: StateFlow<String?> = _engineVersion.asStateFlow()

    private val _engineUpdate = MutableStateFlow<EngineUpdate>(EngineUpdate.Idle)
    val engineUpdate: StateFlow<EngineUpdate> = _engineUpdate.asStateFlow()

    /** Set by a link dropped on the window; the UI opens the downloads page and clears it. */
    val openRequest = MutableStateFlow(false)

    /** A finished file landed in the music folder: the library should look again. */
    private val _added = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val added: SharedFlow<Long> = _added.asSharedFlow()

    /** Playlists made for finished groups, so a track retried later joins its playlist. */
    private val groupPlaylists = mutableMapOf<Long, Long>()

    private val ytDlp get() = File(AppDirs.tools, "yt-dlp.exe")
    private val prefs by lazy { Prefs("downloader") }

    /** Where downloads go: the folder chosen in the settings, else one of their own inside the user's Music. */
    val folder: File get() = SettingsStore.settings.value.downloadFolder?.let(::File) ?: defaultFolder

    val defaultFolder: File get() = File(AppDirs.music, FOLDER)

    @Synchronized
    fun init() {
        if (started) return
        started = true
        scope.launch { for (u in wake) drain() }
        if (ytDlp.exists()) scope.launch { refreshVersion() }
    }

    /** Fetches yt-dlp ahead of time so the first download starts quicker. */
    fun warmUp() {
        scope.launch { runCatching { ensureEngine(update = false) } }
    }

    fun enqueue(url: String, format: DownloadFormat, title: String? = null, artist: String? = null) {
        val task = synchronized(this) { DownloadTask(nextId++, url.trim(), format, title, artist) }
        _tasks.update { it + task }
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
        val out = execute(listOf("${source.prefix}$SEARCH_RESULTS:${query.trim()}", "--flat-playlist", "-J"))
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
        wake.trySend(Unit)
    }

    fun clearFinished() = _tasks.update { list -> list.filter { it.active } }

    fun updateEngine() {
        if (_engineUpdate.value == EngineUpdate.Running) return
        scope.launch {
            _engineUpdate.value = EngineUpdate.Running
            _engineUpdate.value = try {
                ensureEngine(update = false)
                val before = _engineVersion.value
                engineLock.withLock { selfUpdate() }
                if (_engineVersion.value != before) EngineUpdate.Updated else EngineUpdate.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EngineUpdate.Failed
            }
        }
    }

    private suspend fun drain() {
        while (true) {
            val next = _tasks.value.firstOrNull { it.status == DownloadStatus.Queued } ?: return
            val job = scope.launch { run(next) }
            synchronized(this) { running = next.id to job }
            job.join()
            synchronized(this) { running = null }
            next.group?.let { finishGroup(it) }
        }
    }

    /** Once nothing in [group] is left to download, its finished tracks become a playlist, in order. */
    private fun finishGroup(group: DownloadGroup) {
        val members = _tasks.value.filter { it.group?.id == group.id }
        val songIds = members.mapNotNull { (it.status as? DownloadStatus.Done)?.songId }
        val library = UserLibrary.get()
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
        val info = JSONObject(execute(listOf(task.url, "--flat-playlist", "--no-playlist", "-J")))
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
        val work = File(AppDirs.cache, "downloads/${task.id}").apply { deleteRecursively(); mkdirs() }
        try {
            set(task.id) { it.copy(status = DownloadStatus.Preparing) }
            ensureEngine(update = true)
            if (task.group == null && !looksLikeSingleTrack(task.url) && expand(task)) return
            val info = fetch(task, work)
            set(task.id) { it.copy(status = DownloadStatus.Saving) }
            val songId = save(info, work)
            set(task.id) { it.copy(status = DownloadStatus.Done(songId)) }
            _added.tryEmit(songId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadException) {
            set(task.id) { it.copy(status = DownloadStatus.Failed(e.reason, e.detail)) }
        } catch (e: Exception) {
            set(task.id) { it.copy(status = DownloadStatus.Failed(classify(e.message), errorLine(e.message))) }
        } finally {
            work.deleteRecursively()
        }
    }

    private suspend fun ensureEngine(update: Boolean) = engineLock.withLock {
        if (!ytDlp.exists()) {
            // A half-written exe would be run next time: not interrupted by a cancel.
            withContext(NonCancellable) { fetchYtDlp() }
            prefs.edit { putLong(K_CHECKED, System.currentTimeMillis()) }
            refreshVersion()
        }
        val checked = prefs.getLong(K_CHECKED, 0L)
        if (update && System.currentTimeMillis() - checked > UPDATE_EVERY_MS) {
            // A stale yt-dlp is the usual reason a site stops working; failing to update isn't fatal,
            // and the next download tries again.
            runCatching { selfUpdate() }
        }
    }

    private suspend fun selfUpdate() {
        runInterruptible { runYtDlp(listOf("-U")) }
        prefs.edit { putLong(K_CHECKED, System.currentTimeMillis()) }
        refreshVersion()
    }

    /**
     * Checked against the release's SHA2-256SUMS before it is ever run. Later updates are
     * yt-dlp's own (-U), which checks the same list.
     */
    private fun fetchYtDlp() {
        val tmp = File(ytDlp.parentFile, "yt-dlp.exe.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val expected = httpGet(YT_DLP_SUMS_URL) { it.readBytes().toString(Charsets.UTF_8) }
                .lineSequence()
                .map { it.trim().split(Regex("""\s+""")) }
                .firstOrNull { it.size == 2 && it[1].removePrefix("*") == "yt-dlp.exe" }
                ?.first()?.lowercase()
                ?: throw DownloadException(FailReason.Network, "No checksum for yt-dlp.exe")
            httpGet(YT_DLP_URL) { input ->
                DigestInputStream(input, digest).use { d -> tmp.outputStream().use { d.copyTo(it) } }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) throw DownloadException(FailReason.Network, "yt-dlp.exe checksum mismatch")
        } catch (e: Exception) {
            tmp.delete()
            throw if (e is DownloadException) e else DownloadException(FailReason.Network, e.message)
        }
        if (!tmp.renameTo(ytDlp)) {
            tmp.copyTo(ytDlp, overwrite = true)
            tmp.delete()
        }
    }

    private fun <T> httpGet(url: String, read: (java.io.InputStream) -> T): T {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode !in 200..299) throw DownloadException(FailReason.Network, "HTTP ${conn.responseCode}")
            return conn.inputStream.use(read)
        } finally {
            conn.disconnect()
        }
    }

    private fun refreshVersion() {
        _engineVersion.value = runCatching { runYtDlp(listOf("--version")).trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Runs yt-dlp: the audio file and its .info.json land in [work]. */
    private suspend fun fetch(task: DownloadTask, work: File): JSONObject {
        val args = listOf(
            task.url,
            "--no-playlist",
            "--no-mtime",
            "--newline",
            "--write-info-json",
            "-f", formatSelector(task.format),
            // Audio as served: "best" keeps the codec, FFmpeg only changes the container.
            "-x", "--audio-format", "best",
            "--ffmpeg-location", ffmpegPath(),
            "-o", File(work, "%(id)s.%(ext)s").path,
        )
        execute(args) { line ->
            val m = PROGRESS.find(line) ?: return@execute
            val percent = m.groupValues[1].toFloatOrNull()
            set(task.id) {
                it.copy(status = DownloadStatus.Downloading(percent?.let { p -> (p / 100f).coerceIn(0f, 1f) }))
            }
            // The .info.json is written before the audio: show the name as soon as it's known.
            if (tasks.value.firstOrNull { it.id == task.id }?.title == null) readInfo(work)?.let { showName(task.id, it) }
        }
        val info = readInfo(work) ?: throw DownloadException(FailReason.Other, null)
        showName(task.id, info)
        return info
    }

    /** Runs yt-dlp; a cancelled coroutine kills the process instead of waiting for it. */
    private suspend fun execute(args: List<String>, onLine: ((String) -> Unit)? = null): String =
        runInterruptible { runYtDlp(args, onLine) }

    /**
     * @return its standard output.
     * @throws IOException with the error lines when yt-dlp fails.
     */
    private fun runYtDlp(args: List<String>, onLine: ((String) -> Unit)? = null): String {
        val process = ProcessBuilder(listOf(ytDlp.absolutePath, "--encoding", "utf-8") + args)
            .redirectErrorStream(false)
            .apply { environment()["PYTHONIOENCODING"] = "utf-8" }
            .start()
        val err = StringBuffer()
        val out = StringBuffer()
        // Both streams are read on threads of their own: waitFor() is what a cancel interrupts.
        val readers = listOf(
            Thread {
                runCatching { process.errorStream.bufferedReader(Charsets.UTF_8).forEachLine { err.appendLine(it) } }
            },
            Thread {
                runCatching {
                    process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                        out.appendLine(line)
                        onLine?.invoke(line)
                    }
                }
            },
        )
        readers.forEach { it.isDaemon = true; it.start() }
        try {
            val code = process.waitFor()
            readers.forEach { it.join(5_000) }
            if (code != 0) throw IOException(err.toString().ifBlank { "yt-dlp exited with $code" })
            return out.toString()
        } catch (e: InterruptedException) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            throw e
        }
    }

    private fun ffmpegPath(): String = org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.ffmpeg::class.java)

    private fun readInfo(work: File): JSONObject? =
        work.listFiles { f -> f.name.endsWith(".info.json") }?.firstOrNull()
            ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }

    private fun showName(id: Long, info: JSONObject) {
        val name = info.trackName()
        set(id) { it.copy(title = name.title.ifBlank { null }, artist = name.artist.ifBlank { null }) }
    }

    /** Tags the file, gives it a cover and moves it into Music/Pier.doon. Returns its song id. */
    private suspend fun save(info: JSONObject, work: File): Long {
        var audio = work.listFiles { f -> !f.name.endsWith(".json") && !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") }
            ?.maxByOrNull { it.length() }
            ?: throw DownloadException(FailReason.Other, null)
        val ext = audio.extension.lowercase()
        if (ext !in AUDIO) throw DownloadException(FailReason.NoAudio, ext)

        val name = info.trackName()
        // A single is its own album; without one every download would share an untitled album.
        val album = info.str("album") ?: name.title
        val cover = info.str("thumbnail")?.let { MetadataSearch.download(it) }?.let(::squareJpeg)
        // Tagged on a copy: a writer that gives up half-way mustn't take the audio with it.
        val tagged = File(work, "tagged.$ext")
        val inFile = TagWriter.canWrite(audio.name) && runCatching {
            audio.copyTo(tagged, overwrite = true)
            TagWriter.tagFile(tagged, name.title, name.artist, album, cover)
        }.isSuccess
        if (inFile) audio = tagged

        val base = safeFileName(listOf(name.artist, name.title).filter { it.isNotBlank() }.joinToString(" - "))
        val dir = folder.apply { mkdirs() }
        if (!dir.isDirectory) throw DownloadException(FailReason.Save, dir.path)
        var target = File(dir, "$base.$ext")
        var n = 1
        while (target.exists()) target = File(dir, "$base (${n++}).$ext")
        val songId = Song.idFor(target)
        try {
            // Some files take no tags: the app keeps them instead, set before the file appears so
            // the library's first look already has them.
            if (!inFile) {
                TagOverrides.get().set(songId, TagOverride(name.title, name.artist.ifBlank { null }, album))
                cover?.let { bytes -> File(work, "cover.jpg").also { it.writeBytes(bytes) } }
                    ?.let { CoverStore.setSongCover(songId, it) }
            }
            audio.copyTo(target)
        } catch (e: Exception) {
            target.delete()
            TagOverrides.get().set(songId, null)
            CoverStore.removeSongCover(songId)
            throw if (e is DownloadException || e is CancellationException) e else DownloadException(FailReason.Save, e.message)
        }
        return songId
    }

    private const val FOLDER = "Pier.doon"
    private const val K_CHECKED = "ytdlp_checked"
    private const val UPDATE_EVERY_MS = 24 * 60 * 60 * 1000L
    private const val SEARCH_RESULTS = 15
    private const val YT_DLP_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
    private const val YT_DLP_SUMS_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS"
    private val PROGRESS = Regex("""^\[download]\s+([\d.]+)%""")
    private val AUDIO = setOf("m4a", "mp3", "opus", "ogg", "flac", "wav", "aac", "webm", "mka")
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
    val decoded = Images.decode(bytes) ?: return null
    Images.squareJpeg(decoded, COVER_MAX_PX)
}.getOrNull()
