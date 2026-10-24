package com.ku.pierdoon.data.lyrics

import android.content.Context
import android.util.LruCache
import com.ku.pierdoon.data.USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** Thrown when lyrics aren't cached and the network may not be used (metered + Wi-Fi only). */
class NetworkNotAllowedException : IOException()

data class LyricsQuery(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)

/**
 * Lyrics from LRCLIB (https://lrclib.net), an open, key-less lyrics database.
 *
 * Energy: the network is touched at most once per track. Results — including "not found" —
 * are cached on disk, so re-opening lyrics or replaying a song costs a single file read.
 */
class LyricsRepository(context: Context) {

    private val dir = File(context.cacheDir, "lyrics").apply { mkdirs() }
    private val memory = LruCache<Long, Lyrics>(48)

    fun peek(songId: Long): Lyrics? = memory.get(songId)

    /** @throws IOException when the network is unavailable (or not allowed) and nothing is cached. */
    suspend fun get(query: LyricsQuery, allowNetwork: Boolean = true): Lyrics {
        memory.get(query.songId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val lyrics = readDisk(query.songId)
                ?: if (!allowNetwork) throw NetworkNotAllowedException()
                else fetch(query).also { writeDisk(query.songId, it) }
            memory.put(query.songId, lyrics)
            lyrics
        }
    }

    /** The track's file is gone: its lyrics won't be asked for again. */
    suspend fun forget(songId: Long) = withContext(Dispatchers.IO) {
        memory.remove(songId)
        file(songId).delete()
    }

    suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        memory.evictAll()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fetch(q: LyricsQuery): Lyrics {
        val title = cleanTitle(q.title)
        val durationSec = q.durationMs / 1000

        // 1) Exact signature match — the cheapest and most accurate request.
        if (q.artist.isNotBlank() && q.album.isNotBlank() && durationSec > 0) {
            val url = "$API/get?track_name=${enc(title)}&artist_name=${enc(q.artist)}" +
                "&album_name=${enc(q.album)}&duration=$durationSec"
            httpGet(url)?.let { body -> return toLyrics(JSONObject(body)) }
        }

        // 2) Fuzzy search, then pick the closest duration, preferring synced results.
        val url = if (q.artist.isNotBlank()) {
            "$API/search?track_name=${enc(title)}&artist_name=${enc(q.artist)}"
        } else {
            "$API/search?q=${enc(title)}"
        }
        val results = httpGet(url)?.let(::JSONArray) ?: return Lyrics.NotFound
        var best: JSONObject? = null
        var bestScore = Double.MAX_VALUE
        for (i in 0 until results.length()) {
            val o = results.getJSONObject(i)
            val diff = if (durationSec > 0) abs(o.optDouble("duration", 0.0) - durationSec) else 0.0
            if (durationSec > 0 && diff > MAX_DURATION_DIFF_SEC) continue
            val synced = !o.isNull("syncedLyrics") && o.optString("syncedLyrics").isNotBlank()
            val score = diff + if (synced) 0.0 else UNSYNCED_PENALTY
            if (score < bestScore) {
                bestScore = score
                best = o
            }
        }
        return best?.let(::toLyrics) ?: Lyrics.NotFound
    }

    private fun toLyrics(o: JSONObject): Lyrics {
        if (o.optBoolean("instrumental")) return Lyrics.Instrumental
        val synced = o.optStringOrNull("syncedLyrics")
        if (synced != null) {
            val lines = LrcParser.parse(synced)
            if (lines.isNotEmpty()) return Lyrics.Synced(lines)
        }
        val plain = o.optStringOrNull("plainLyrics") ?: return Lyrics.NotFound
        return Lyrics.Plain(plain.lines())
    }

    // --- Disk cache: tiny JSON per song id ----------------------------------------------

    private fun file(songId: Long) = File(dir, "$songId.json")

    private fun readDisk(songId: Long): Lyrics? {
        val f = file(songId)
        if (!f.exists()) return null
        return runCatching {
            val o = JSONObject(f.readText())
            when (o.optString("kind")) {
                "synced" -> Lyrics.Synced(LrcParser.parse(o.getString("text")))
                "plain" -> Lyrics.Plain(o.getString("text").lines())
                "instrumental" -> Lyrics.Instrumental
                else -> {
                    // Retry "not found" after a while: someone may have uploaded it since.
                    if (System.currentTimeMillis() - o.optLong("t") > NOT_FOUND_TTL_MS) null
                    else Lyrics.NotFound
                }
            }
        }.getOrNull()
    }

    private fun writeDisk(songId: Long, lyrics: Lyrics) {
        val o = JSONObject().put("t", System.currentTimeMillis())
        when (lyrics) {
            is Lyrics.Synced -> o.put("kind", "synced").put("text", lyrics.toLrc())
            is Lyrics.Plain -> o.put("kind", "plain").put("text", lyrics.lines.joinToString("\n"))
            Lyrics.Instrumental -> o.put("kind", "instrumental")
            Lyrics.NotFound -> o.put("kind", "none")
        }
        runCatching { file(songId).writeText(o.toString()) }
    }

    private fun Lyrics.Synced.toLrc(): String = buildString {
        lines.forEach { l ->
            val m = l.timeMs / 60_000
            val s = (l.timeMs / 1000) % 60
            val cs = (l.timeMs % 1000) / 10
            append('[').append(m.toString().padStart(2, '0')).append(':')
                .append(s.toString().padStart(2, '0')).append('.')
                .append(cs.toString().padStart(2, '0')).append(']').append(l.text).append('\n')
        }
    }

    // --- HTTP ---------------------------------------------------------------------------

    /** @return body, or null on 404. */
    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private companion object {
        const val API = "https://lrclib.net/api"
        const val MAX_DURATION_DIFF_SEC = 5.0
        const val UNSYNCED_PENALTY = 3.0
        const val NOT_FOUND_TTL_MS = 3L * 24 * 60 * 60 * 1000

        private val noise = Regex(
            """\s*[(\[](?:feat\.?|ft\.?|prod\.?|remaster(?:ed)?|official|lyrics?|audio|video)[^)\]]*[)\]]""",
            RegexOption.IGNORE_CASE,
        )

        fun cleanTitle(title: String) = title.replace(noise, "").trim().ifEmpty { title }
    }
}
