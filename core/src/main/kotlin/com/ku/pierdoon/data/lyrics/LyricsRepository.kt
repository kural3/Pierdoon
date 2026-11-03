package com.ku.pierdoon.data.lyrics

import com.ku.pierdoon.data.LruCache
import com.ku.pierdoon.data.bareTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 *
 * @param cacheDir the app's cache folder; the lyrics go in a `lyrics` folder inside it.
 * @param userAgent who the app says it is to LRCLIB.
 * @param filesDir the app's own folder, kept when the cache is cleared; lyrics the user wrote go there.
 */
class LyricsRepository(cacheDir: File, private val userAgent: String, filesDir: File? = null) {

    private val dir = File(cacheDir, "lyrics").apply { mkdirs() }
    private val ownDir = filesDir?.let { File(it, "lyrics").apply { mkdirs() } }
    private val genius = Genius(userAgent)
    private val memory = LruCache<Long, Lyrics>(48)

    fun peek(songId: Long): Lyrics? = memory.get(songId)

    /** @throws IOException when the network is unavailable (or not allowed) and nothing is cached. */
    suspend fun get(query: LyricsQuery, allowNetwork: Boolean = true): Lyrics {
        memory.get(query.songId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val lyrics = readOwn(query.songId)
                ?: readDisk(query.songId)
                ?: if (!allowNetwork) throw NetworkNotAllowedException()
                else fetch(query).also { writeDisk(query.songId, it) }
            memory.put(query.songId, lyrics)
            lyrics
        }
    }

    /**
     * LRCLIB's search for any text, for picking lyrics by hand. Synced ones first.
     * @throws IOException when the network is unavailable.
     */
    suspend fun search(text: String): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        // Genius alongside: its first few songs, each page read for its text. Its failing is no failure.
        val fromGenius = async {
            runCatching {
                genius.search(text, GENIUS_RESULTS).map { hit ->
                    async {
                        runCatching { genius.lyrics(hit.url) }.getOrNull()
                            ?.let { LyricsCandidate(hit.title, hit.artist, "", 0L, it) }
                    }
                }.awaitAll().filterNotNull()
            }.getOrDefault(emptyList())
        }
        val fromLrclib = runCatching { lrclibSearch(text) }
        val genius = fromGenius.await()
        if (fromLrclib.isFailure && genius.isEmpty()) throw fromLrclib.exceptionOrNull()!!
        fromLrclib.getOrDefault(emptyList()) + genius
    }

    private fun lrclibSearch(text: String): List<LyricsCandidate> {
        val results = httpGet("$API/search?q=${enc(text.trim())}")?.let(::JSONArray) ?: return emptyList()
        return List(results.length()) { results.getJSONObject(it) }
            .map { o ->
                LyricsCandidate(
                    title = o.optString("trackName"),
                    artist = o.optString("artistName"),
                    album = o.optString("albumName"),
                    durationMs = (o.optDouble("duration", 0.0) * 1000).toLong(),
                    lyrics = toLyrics(o),
                )
            }
            .filter { it.lyrics !is Lyrics.NotFound }
            .sortedBy { if (it.lyrics is Lyrics.Synced) 0 else 1 }
            .take(MAX_SEARCH_RESULTS)
    }

    /** Lyrics picked by hand: kept for the track for good, "none" included. */
    suspend fun choose(songId: Long, lyrics: Lyrics) = withContext(Dispatchers.IO) {
        memory.put(songId, lyrics)
        ownFile(songId)?.delete() // a pick replaces lyrics written by hand, or they'd win again
        writeDisk(songId, lyrics, manual = true)
    }

    /** Lyrics the user wrote for the track, as they wrote them; null if none. */
    suspend fun ownText(songId: Long): String? = withContext(Dispatchers.IO) {
        ownFile(songId)?.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
    }

    fun isOwn(songId: Long): Boolean = ownFile(songId)?.exists() == true

    /** Keeps the user's own lyrics for the track, over anything found; blank text removes them. */
    suspend fun saveOwn(songId: Long, text: String): Lyrics = withContext(Dispatchers.IO) {
        val lines = LyricsText.lines(text)
        val lyrics = LyricsText.toLyrics(lines)
        if (lyrics == Lyrics.NotFound) {
            removeOwnNow(songId)
            return@withContext lyrics
        }
        val f = ownFile(songId) ?: throw IOException("No folder for own lyrics")
        f.writeText(LyricsText.text(lines))
        memory.put(songId, lyrics)
        lyrics
    }

    /** Drops the user's lyrics and whatever was cached, so the track's are looked up afresh. */
    suspend fun removeOwn(songId: Long) = withContext(Dispatchers.IO) { removeOwnNow(songId) }

    private fun removeOwnNow(songId: Long) {
        ownFile(songId)?.delete()
        file(songId).delete()
        memory.remove(songId)
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
        val found = fetchLrclib(q)
        if (found != Lyrics.NotFound) return found
        // LRCLIB has nothing: Genius may have the words, without timing. Only a plain match counts.
        if (q.artist.isBlank()) return found
        return runCatching { genius.find(q.title, q.artist) }.getOrNull() ?: found
    }

    private fun fetchLrclib(q: LyricsQuery): Lyrics {
        val title = bareTitle(q.title)
        val durationSec = q.durationMs / 1000

        // 1) Exact signature match — the cheapest and most accurate request.
        if (q.artist.isNotBlank() && q.album.isNotBlank() && durationSec > 0) {
            val url = "$API/get?track_name=${enc(title)}&artist_name=${enc(q.artist)}" +
                "&album_name=${enc(q.album)}&duration=$durationSec"
            // LRCLIB answers an unknown track here with a 503 now and then; the search below still runs.
            runCatching { httpGet(url) }.getOrNull()?.let { body -> return toLyrics(JSONObject(body)) }
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

    private fun ownFile(songId: Long) = ownDir?.let { File(it, "$songId.lrc") }

    private fun readOwn(songId: Long): Lyrics? = ownFile(songId)?.takeIf { it.exists() }
        ?.let { f -> runCatching { LyricsText.toLyrics(f.readText()) }.getOrNull() }
        ?.takeIf { it != Lyrics.NotFound }

    private fun readDisk(songId: Long): Lyrics? {
        val f = file(songId)
        if (!f.exists()) return null
        return runCatching {
            val o = JSONObject(f.readText())
            when (o.optString("kind")) {
                "synced" -> Lyrics.Synced(LrcParser.parse(o.getString("text")))
                "plain" -> Lyrics.Plain(
                    o.getString("text").lines(),
                    if (o.optString("source") == "genius") LyricsSource.Genius else LyricsSource.Lrclib,
                )
                "instrumental" -> Lyrics.Instrumental
                else -> {
                    // Retry "not found" after a while: someone may have uploaded it since.
                    // And once more for those from before Genius was asked too ("g").
                    val stale = System.currentTimeMillis() - o.optLong("t") > NOT_FOUND_TTL_MS || !o.optBoolean("g")
                    if (!o.optBoolean("manual") && stale) null
                    else Lyrics.NotFound
                }
            }
        }.getOrNull()
    }

    private fun writeDisk(songId: Long, lyrics: Lyrics, manual: Boolean = false) {
        val o = JSONObject().put("t", System.currentTimeMillis())
        if (manual) o.put("manual", true)
        when (lyrics) {
            is Lyrics.Synced -> o.put("kind", "synced").put("text", LyricsText.lrc(lyrics))
            is Lyrics.Plain -> {
                o.put("kind", "plain").put("text", lyrics.lines.joinToString("\n"))
                if (lyrics.source == LyricsSource.Genius) o.put("source", "genius")
            }
            Lyrics.Instrumental -> o.put("kind", "instrumental")
            Lyrics.NotFound -> o.put("kind", "none").put("g", true)
        }
        runCatching { file(songId).writeText(o.toString()) }
    }

    // --- HTTP ---------------------------------------------------------------------------

    /** @return body, or null on 404. */
    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", userAgent)
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
        const val MAX_SEARCH_RESULTS = 30
        const val GENIUS_RESULTS = 3
    }
}
