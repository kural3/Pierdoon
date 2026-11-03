package com.ku.pierdoon.data.lyrics

import com.ku.pierdoon.data.bareTitle
import com.ku.pierdoon.data.splitArtists
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lyrics from Genius (genius.com), for when LRCLIB has none. Genius has no lyrics API: the song
 * is found through the site's own search and the text read off its page. Plain text only, no
 * timing; the section marks ([Chorus], [Verse 1]) are left out.
 */
class Genius(private val userAgent: String) {

    data class Hit(val title: String, val artist: String, val url: String)

    /** @throws IOException when the network is unavailable. */
    fun search(text: String, limit: Int = 5): List<Hit> {
        val body = get("$API/search/song?per_page=$limit&q=${URLEncoder.encode(text.trim(), "UTF-8")}") ?: return emptyList()
        val sections = JSONObject(body).optJSONObject("response")?.optJSONArray("sections") ?: return emptyList()
        val out = ArrayList<Hit>()
        for (s in 0 until sections.length()) {
            val hits = sections.getJSONObject(s).optJSONArray("hits") ?: continue
            for (h in 0 until hits.length()) {
                val r = hits.getJSONObject(h).optJSONObject("result") ?: continue
                val url = r.optString("url")
                if (url.isBlank() || r.optString("lyrics_state") == "unreleased") continue
                out += Hit(
                    title = r.optString("title"),
                    artist = r.optJSONObject("primary_artist")?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: r.optString("artist_names"),
                    url = url,
                )
            }
        }
        return out.distinctBy { it.url }.take(limit)
    }

    /** The song's lyrics, or null if the page has none. */
    fun lyrics(url: String): Lyrics.Plain? {
        val html = get(url) ?: return null
        val lines = parse(html)
        return if (lines.isEmpty()) null else Lyrics.Plain(lines, LyricsSource.Genius)
    }

    /** The best match for the track, if Genius has one that's plainly the same song. */
    fun find(title: String, artist: String): Lyrics.Plain? {
        val bare = bareTitle(title)
        val first = splitArtists(artist).firstOrNull() ?: artist
        val hit = search("$first $bare").firstOrNull { matches(it, bare, first) } ?: return null
        return lyrics(hit.url)
    }

    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", userAgent)
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val API = "https://genius.com/api"

        private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

        /** Same title and artist, give or take punctuation and "(Remastered)"-like tails. */
        fun matches(hit: Hit, title: String, artist: String): Boolean {
            val t = norm(bareTitle(hit.title)); val want = norm(title)
            val a = norm(hit.artist); val wantA = norm(artist)
            if (t.isEmpty() || want.isEmpty() || a.isEmpty() || wantA.isEmpty()) return false
            val titleOk = t == want || (t.length >= 4 && want.length >= 4 && (t.contains(want) || want.contains(t)))
            val artistOk = a.contains(wantA) || wantA.contains(a)
            return titleOk && artistOk
        }

        private val container = Regex("""<div[^>]*data-lyrics-container="true"[^>]*>""")
        private val excluded = Regex("""<div[^>]*data-exclude-from-selection="true"[^>]*>""")
        private val divTag = Regex("""<(/?)div\b[^>]*>""")
        private val br = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
        private val tag = Regex("""<[^>]+>""")
        private val section = Regex("""^\s*\[[^\]]*]\s*$""")

        /** The lyrics off a song page: every lyrics block, without Genius's own headers in them. */
        fun parse(html: String): List<String> {
            val text = StringBuilder()
            var from = 0
            while (true) {
                val open = container.find(html, from) ?: break
                val end = closingDiv(html, open.range.last + 1)
                var inner = html.substring(open.range.last + 1, end)
                // Headers, contributor counts and ads nested inside the block.
                while (true) {
                    val ex = excluded.find(inner) ?: break
                    val exEnd = closingDiv(inner, ex.range.last + 1)
                    inner = inner.removeRange(ex.range.first, minOf(inner.length, exEnd + "</div>".length))
                }
                if (text.isNotEmpty()) text.append('\n')
                text.append(inner.replace(br, "\n").replace(tag, ""))
                from = end
            }
            val lines = unescape(text.toString()).lines().map { it.trim() }.filterNot { section.matches(it) }
            // One blank line between verses at most, none at the ends.
            val out = ArrayList<String>()
            for (l in lines) {
                if (l.isEmpty() && (out.isEmpty() || out.last().isEmpty())) continue
                out += l
            }
            while (out.lastOrNull()?.isEmpty() == true) out.removeAt(out.lastIndex)
            return out
        }

        /** Index of the `</div>` that closes a div whose content starts at [start]. */
        private fun closingDiv(html: String, start: Int): Int {
            var depth = 1
            var from = start
            while (true) {
                val m = divTag.find(html, from) ?: return html.length
                if (m.groupValues[1] == "/") {
                    depth--
                    if (depth == 0) return m.range.first
                } else depth++
                from = m.range.last + 1
            }
        }

        private val entity = Regex("""&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos|nbsp);""")

        private fun unescape(s: String): String = entity.replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                e == "amp" -> "&"
                e == "lt" -> "<"
                e == "gt" -> ">"
                e == "quot" -> "\""
                e == "apos" -> "'"
                else -> " "
            }
        }
    }
}
