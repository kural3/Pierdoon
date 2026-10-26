package com.ku.pierdoon.remote

import com.ku.pierdoon.data.DEFAULT_SAVE_FOLDER
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.TagOverride
import com.ku.pierdoon.data.TagOverrides
import com.ku.pierdoon.data.TrackKey
import com.ku.pierdoon.data.TrackMatcher
import com.ku.pierdoon.data.download.safeFileName
import com.ku.pierdoon.data.uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder

enum class TransferDirection { ToComputer, ToPhone }

enum class TransferFailure {
    /** The computer stopped answering. */
    Offline,
    /** Not enough room where the track was going. */
    NoSpace,
    /** The file is gone from where the library said it was. */
    Missing,
    Other,
}

sealed interface TransferStatus {
    data object Queued : TransferStatus
    /** [progress] is 0..1, or null while the size is unknown. */
    data class Running(val progress: Float?) : TransferStatus
    data object Done : TransferStatus
    data class Failed(val reason: TransferFailure) : TransferStatus
}

data class TransferTask(
    val id: Long,
    val direction: TransferDirection,
    val title: String,
    val artist: String,
    val status: TransferStatus,
) {
    val active: Boolean get() = status is TransferStatus.Queued || status is TransferStatus.Running
}

/** A track to move: one of the phone's, or one of the computer's. */
sealed interface TransferItem {
    val title: String
    val artist: String
    val durationMs: Long

    data class Local(val song: Song) : TransferItem {
        override val title get() = song.title
        override val artist get() = song.artist
        override val durationMs get() = song.durationMs
    }

    data class Remote(val song: PcSong) : TransferItem {
        override val title get() = song.title
        override val artist get() = song.artist
        override val durationMs get() = song.durationMs
    }
}

/** Waiting for the user: some of [items] look like they're already on the other side. */
data class PendingTransfer(
    val direction: TransferDirection,
    val items: List<TransferItem>,
    val duplicates: List<TransferItem>,
)

/** Something to tell the user right away, as a short message. */
sealed interface TransferMessage {
    data class Started(val direction: TransferDirection, val count: Int) : TransferMessage
    data object Offline : TransferMessage
    data object OutdatedComputer : TransferMessage
    /** Saving to the phone needs Android 10 (MediaStore's folders). */
    data object Unsupported : TransferMessage
}

/**
 * Copies tracks between the phone and the paired computer, on request: "Send to computer" on the
 * phone's tracks, "Save to phone" on the computer's. Files go byte for byte, nothing re-encoded;
 * the computer puts them in its downloads folder, the phone in Music/Pier.doon. Tags edited in
 * the app rather than the file go along.
 *
 * Before anything moves, the tracks are looked up on the other side by their tags and length
 * ([TrackMatcher]); if some seem to be there already, the user picks ([pending]). One file at a
 * time, kept alive by [TransferService] with a notification.
 */
object PcTransfers {
    val supportedToPhone: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val items = HashMap<Long, TransferItem>()
    private var nextId = 1L

    private val _tasks = MutableStateFlow<List<TransferTask>>(emptyList())
    val tasks: StateFlow<List<TransferTask>> = _tasks.asStateFlow()

    private val _pending = MutableStateFlow<PendingTransfer?>(null)
    val pending: StateFlow<PendingTransfer?> = _pending.asStateFlow()

    private val _messages = MutableSharedFlow<TransferMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<TransferMessage> = _messages.asSharedFlow()

    private val _checking = MutableStateFlow(false)
    /** Looking the tracks up on the other side, before anything is queued. */
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        scope.launch { for (u in wake) drain() }
    }

    /** The phone's [songs] to the computer, after checking which it seems to have. */
    fun sendToComputer(songs: List<Song>) {
        if (songs.isEmpty()) return
        prepare(TransferDirection.ToComputer, songs.map(TransferItem::Local)) {
            val library = PcRemote.libraryNow() ?: return@prepare null
            TrackMatcher(library.songs.map { TrackKey(it.title, it.artist, it.durationMs) })
        }
    }

    /** The computer's [songs] to the phone, checked against [phoneLibrary]. */
    fun saveToPhone(songs: List<PcSong>, phoneLibrary: List<Song>) {
        if (songs.isEmpty()) return
        if (!supportedToPhone) {
            _messages.tryEmit(TransferMessage.Unsupported)
            return
        }
        prepare(TransferDirection.ToPhone, songs.map(TransferItem::Remote)) {
            TrackMatcher(phoneLibrary.map { TrackKey(it.title, it.artist, it.durationMs) })
        }
    }

    private fun prepare(direction: TransferDirection, list: List<TransferItem>, matcher: () -> TrackMatcher?) {
        if (_checking.value) return
        _checking.value = true
        scope.launch {
            try {
                val api = PcRemote.apiNow()
                when {
                    api == null -> _messages.tryEmit(TransferMessage.Offline)
                    api < PcRemote.TRANSFER_API -> _messages.tryEmit(TransferMessage.OutdatedComputer)
                    else -> {
                        val there = matcher()
                        if (there == null) {
                            _messages.tryEmit(TransferMessage.Offline)
                            return@launch
                        }
                        val duplicates = list.filter { there.contains(TrackKey(it.title, it.artist, it.durationMs)) }
                        if (duplicates.isEmpty()) enqueue(direction, list)
                        else _pending.value = PendingTransfer(direction, list, duplicates)
                    }
                }
            } finally {
                _checking.value = false
            }
        }
    }

    /** The user's answer about [pending]: everything, or only what isn't there yet. */
    fun confirm(includeDuplicates: Boolean) {
        val p = _pending.value ?: return
        _pending.value = null
        val chosen = if (includeDuplicates) p.items else p.items - p.duplicates.toSet()
        if (chosen.isNotEmpty()) enqueue(p.direction, chosen)
    }

    fun dismiss() {
        _pending.value = null
    }

    private fun enqueue(direction: TransferDirection, list: List<TransferItem>) {
        val added = synchronized(this) {
            list.map { item ->
                val id = nextId++
                items[id] = item
                TransferTask(id, direction, item.title, item.artist, TransferStatus.Queued)
            }
        }
        // Finished ones were summed up when the last run ended.
        _tasks.update { old -> old.filter { it.active } + added }
        _messages.tryEmit(TransferMessage.Started(direction, list.size))
        ContextCompat.startForegroundService(app, Intent(app, TransferService::class.java))
        wake.trySend(Unit)
    }

    private suspend fun drain() {
        while (true) {
            val task = _tasks.value.firstOrNull { it.status == TransferStatus.Queued } ?: return
            val item = synchronized(this) { items[task.id] } ?: return
            set(task.id, TransferStatus.Running(null))
            val result = try {
                when (item) {
                    is TransferItem.Local -> upload(task.id, item.song)
                    is TransferItem.Remote -> if (supportedToPhone) download(task.id, item.song) else TransferFailure.Other
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                TransferFailure.Offline
            } catch (_: Exception) {
                TransferFailure.Other
            }
            set(task.id, if (result == null) TransferStatus.Done else TransferStatus.Failed(result))
            synchronized(this) { items.remove(task.id) }
        }
    }

    private fun set(id: Long, status: TransferStatus) =
        _tasks.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }

    /** One of the phone's tracks to the computer; null when it got there. */
    private suspend fun upload(id: Long, song: Song): TransferFailure? {
        val resolver = app.contentResolver
        val uri = song.uri
        val name = displayName(uri) ?: return TransferFailure.Missing
        val length = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull()
            ?.takeIf { it > 0 } ?: return TransferFailure.Missing
        val tags = TagOverrides.get(app).get(song.id)?.let(::tagsJson)
        val conn = PcRemote.openUpload(name, tags, length) ?: return TransferFailure.Offline
        try {
            val input = resolver.openInputStream(uri) ?: return TransferFailure.Missing
            input.use { source -> conn.outputStream.use { copy(id, source, it, length) } }
            return when (conn.responseCode) {
                200 -> null
                507 -> TransferFailure.NoSpace
                401, 404 -> TransferFailure.Offline
                else -> TransferFailure.Other
            }
        } finally {
            conn.disconnect()
        }
    }

    /** One of the computer's tracks into Music/Pier.doon; null when it's there. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun download(id: Long, song: PcSong): TransferFailure? {
        val conn = PcRemote.openFile(song.id) ?: return TransferFailure.Offline
        try {
            when (conn.responseCode) {
                200 -> Unit
                404 -> return TransferFailure.Missing
                401 -> return TransferFailure.Offline
                else -> return TransferFailure.Other
            }
            val length = conn.contentLengthLong
            val free = runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(Long.MAX_VALUE)
            if (length > 0 && length + FREE_SPACE_MARGIN > free) return TransferFailure.NoSpace

            val sent = conn.getHeaderField("X-Pierdoon-Name")?.let { URLDecoder.decode(it, "UTF-8") }
            val ext = sent?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() } ?: return TransferFailure.Other
            val base = safeFileName(sent.substringBeforeLast('.'))
            val tags = conn.getHeaderField("X-Pierdoon-Tags")?.let { runCatching { JSONObject(URLDecoder.decode(it, "UTF-8")) }.getOrNull() }

            val resolver = app.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$base.$ext")
                put(MediaStore.Audio.Media.MIME_TYPE, MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "audio/*")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "$DEFAULT_SAVE_FOLDER/")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: return TransferFailure.Other
            try {
                val out = resolver.openOutputStream(uri) ?: throw IOException("no output")
                out.use { target -> conn.inputStream.use { copy(id, it, target, length) } }
                // Before publishing, so the library's first look already has them.
                tags?.let { t ->
                    val songId = android.content.ContentUris.parseId(uri)
                    TagOverrides.get(app).set(songId, TagOverride(t.optStringOrNull("title"), t.optStringOrNull("artist"), t.optStringOrNull("album")))
                }
                // Published: the media scanner reads the tags and the library refreshes on its own.
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** Copies [input] to [output], reporting progress about once a percent. */
    private suspend fun copy(id: Long, input: InputStream, output: OutputStream, length: Long) {
        val buf = ByteArray(64 * 1024)
        var done = 0L
        var reported = -1
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            done += n
            if (length > 0) {
                val percent = (done * 100 / length).toInt()
                if (percent != reported) {
                    reported = percent
                    set(id, TransferStatus.Running(done.toFloat() / length))
                }
            }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.takeIf { '.' in it }

    private fun tagsJson(t: TagOverride) = JSONObject().apply {
        t.title?.let { put("title", it) }
        t.artist?.let { put("artist", it) }
        t.album?.let { put("album", it) }
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    /** Left free on the phone after a track from the computer is saved. */
    private const val FREE_SPACE_MARGIN = 200L * 1024 * 1024
}
