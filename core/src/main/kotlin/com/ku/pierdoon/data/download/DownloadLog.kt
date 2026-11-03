package com.ku.pierdoon.data.download

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.recoverAtomicWrite
import com.ku.pierdoon.data.writeAtomically
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** How one download ended. [reason] is the app's FailReason name; [message] is what yt-dlp (or the save) said. */
@Immutable
data class DownloadLogEntry(
    val time: Long,
    val ok: Boolean,
    val url: String,
    val title: String?,
    val artist: String?,
    val reason: String? = null,
    val message: String? = null,
)

/**
 * The downloads' journal, newest first, kept in [file] between runs: so a track that didn't
 * come can be looked up afterwards, with the error as it was, not just its one-line summary.
 */
class DownloadLog(private val file: File, private val limit: Int = 200) {
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<DownloadLogEntry>> = _entries.asStateFlow()

    fun add(entry: DownloadLogEntry) = synchronized(this) {
        val trimmed = entry.copy(message = entry.message?.trim()?.takeLast(MESSAGE_MAX)?.ifBlank { null })
        _entries.value = (listOf(trimmed) + _entries.value).take(limit)
        save()
    }

    fun clear() = synchronized(this) {
        _entries.value = emptyList()
        save()
    }

    private fun save() {
        val array = JSONArray()
        _entries.value.forEach { e ->
            array.put(
                JSONObject()
                    .put("time", e.time)
                    .put("ok", e.ok)
                    .put("url", e.url)
                    .putOpt("title", e.title)
                    .putOpt("artist", e.artist)
                    .putOpt("reason", e.reason)
                    .putOpt("message", e.message),
            )
        }
        runCatching { writeAtomically(file, array.toString()) }
    }

    private fun load(): List<DownloadLogEntry> = runCatching {
        recoverAtomicWrite(file)
        if (!file.isFile) return emptyList()
        val array = JSONArray(file.readText())
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            DownloadLogEntry(
                time = o.optLong("time"),
                ok = o.optBoolean("ok"),
                url = o.optString("url"),
                title = o.optString("title").ifBlank { null },
                artist = o.optString("artist").ifBlank { null },
                reason = o.optString("reason").ifBlank { null },
                message = o.optString("message").ifBlank { null },
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        /** yt-dlp's output can run long; the end is where the error is. */
        const val MESSAGE_MAX = 4000
    }
}
