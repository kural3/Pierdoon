package com.ku.pierdoon.data.metadata

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** One match found online; [coverUrl] is full size, [thumbUrl] small enough for a list row. */
@Immutable
data class MetadataCandidate(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String?,
    val thumbUrl: String?,
    val source: String,
)

/**
 * Track metadata from open, key-less catalogues: Deezer (wide coverage, CIS music included,
 * 1000 px covers) and MusicBrainz with Cover Art Archive (precise for official releases).
 * Both are asked at once; one failing doesn't spoil the other's results.
 *
 * Runs when the user taps "search", and for [coverUrl] only while the Discord status the user
 * switched on needs a picture.
 */
object MetadataSearch {

    /**
     * Emits the merged results each time a source answers: Deezer is usually there in a second,
     * MusicBrainz can take several, and nobody should wait for the slower one.
     * @throws IOException when no source could be reached at all.
     */
    fun search(query: String, durationMs: Long): Flow<List<MetadataCandidate>> = channelFlow {
        val lock = Mutex()
        val found = ArrayList<MetadataCandidate>()
        var failures = 0
        val sources = listOf(::deezer, ::musicBrainz)
        sources.forEach { source ->
            launch(Dispatchers.IO) {
                val result = runCatching { source(query) }
                lock.withLock {
                    result.onSuccess { found += it }.onFailure { failures++ }
                    if (failures == sources.size) throw IOException("No metadata source reachable")
                    if (result.isSuccess) send(rank(found, durationMs))
                }
            }
        }
    }

    private fun rank(all: List<MetadataCandidate>, durationMs: Long): List<MetadataCandidate> {
        val unique = all.distinctBy { "${it.title.lowercase()}|${it.artist.lowercase()}|${it.album.lowercase()}" }
        if (durationMs <= 0) return unique
        // Same length as the file is the strongest hint it's the same recording.
        return unique.sortedBy { c -> if (c.durationMs > 0) abs(c.durationMs - durationMs) else UNKNOWN_LENGTH_RANK }
    }

    /** The picture behind [url], or null. Covers are small JPEGs; read straight into memory. */
    suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { httpGet(url, accept = "image/*")?.let { it.inputStream.use { s -> s.readBytes() } } }.getOrNull()
    }

    /**
     * A public link to [title]'s cover, for places that can only show pictures from the web
     * (the Discord status). Only a confident match: the same title by the same artist. Blocking.
     */
    fun coverUrl(title: String, artist: String): String? = runCatching {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val t = norm(title)
        val a = norm(artist)
        deezer("$artist $title".trim()).firstOrNull { c ->
            val ct = norm(c.title)
            val ca = norm(c.artist)
            val sameArtist = a.isNotEmpty() && ca.isNotEmpty() && (ca.contains(a) || a.contains(ca))
            val sameTitle = t.isNotEmpty() && (ct.contains(t) || t.contains(ct))
            sameTitle && (sameArtist || a.isEmpty())
        }?.thumbUrl
    }.getOrNull()

    private fun deezer(query: String): List<MetadataCandidate> {
        val body = httpGetText("https://api.deezer.com/search?q=${enc(query)}&limit=$LIMIT") ?: return emptyList()
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return List(data.length()) { i ->
            val o = data.getJSONObject(i)
            val album = o.optJSONObject("album")
            MetadataCandidate(
                title = o.optString("title"),
                artist = o.optJSONObject("artist")?.optString("name").orEmpty(),
                album = album?.optString("title").orEmpty(),
                durationMs = o.optLong("duration") * 1000,
                coverUrl = album?.optString("cover_xl")?.takeIf { it.isNotBlank() },
                thumbUrl = album?.optString("cover_medium")?.takeIf { it.isNotBlank() },
                source = "Deezer",
            )
        }
    }

    private fun musicBrainz(query: String): List<MetadataCandidate> {
        val body = httpGetText("https://musicbrainz.org/ws/2/recording?query=${enc(query)}&fmt=json&limit=$LIMIT")
            ?: return emptyList()
        val recordings: JSONArray = JSONObject(body).optJSONArray("recordings") ?: return emptyList()
        return List(recordings.length()) { i ->
            val o = recordings.getJSONObject(i)
            val release = o.optJSONArray("releases")?.optJSONObject(0)
            val releaseId = release?.optString("id")?.takeIf { it.isNotBlank() }
            MetadataCandidate(
                title = o.optString("title"),
                artist = o.optJSONArray("artist-credit")?.let { credits ->
                    (0 until credits.length()).joinToString("") { j ->
                        val c = credits.getJSONObject(j)
                        c.optString("name") + c.optString("joinphrase")
                    }
                }.orEmpty(),
                album = release?.optString("title").orEmpty(),
                durationMs = o.optLong("length"),
                // Cover Art Archive redirects to the image; a release may simply have none.
                coverUrl = releaseId?.let { "https://coverartarchive.org/release/$it/front-1200" },
                thumbUrl = releaseId?.let { "https://coverartarchive.org/release/$it/front-250" },
                source = "MusicBrainz",
            )
        }
    }

    private fun httpGetText(url: String): String? =
        httpGet(url, accept = "application/json")?.let { c -> c.inputStream.bufferedReader().use { it.readText() } }

    /** @return the open connection, or null on 404. Follows redirects (Cover Art Archive uses them). */
    private fun httpGet(url: String, accept: String): HttpURLConnection? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 10_000
        conn.instanceFollowRedirects = true
        // MusicBrainz asks every client to identify itself.
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", accept)
        val code = conn.responseCode
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            conn.disconnect()
            return null
        }
        if (code !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP $code")
        }
        return conn
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private const val LIMIT = 12
    private const val UNKNOWN_LENGTH_RANK = Long.MAX_VALUE / 2
}
