package com.ku.pierdoon.data

import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** A line of a playlist file: where the track was, and what `#EXTINF` says it is, if anything. */
data class M3uEntry(
    val location: String,
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0L,
)

data class M3uFile(
    /** From `#PLAYLIST`; null when the file doesn't name itself. */
    val name: String?,
    val entries: List<M3uEntry>,
)

/** What a playlist file comes to in a library: its tracks in order, and how many weren't there. */
data class M3uMatch(val songIds: List<Long>, val missed: Int)

/** Extension exports get: M3U in UTF-8, so players don't read the names in the system's code page. */
const val M3U_EXTENSION = "m3u8"

/** Text of a playlist file: UTF-8, with or without a BOM; anything else is taken for an old Windows one. */
fun decodeM3u(bytes: ByteArray): String {
    val utf8 = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val text = try {
        utf8.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1251"))
    }
    return text.removePrefix("\uFEFF")
}

fun parseM3u(text: String): M3uFile {
    var name: String? = null
    var info: M3uEntry? = null
    val entries = ArrayList<M3uEntry>()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> Unit
            line.startsWith("#PLAYLIST:") -> name = line.substringAfter(':').trim().ifEmpty { null }
            line.startsWith("#EXTINF:") -> info = parseExtInf(line)
            line.startsWith("#") -> Unit
            else -> {
                entries += (info ?: M3uEntry("")).copy(location = line)
                info = null
            }
        }
    }
    return M3uFile(name, entries)
}

/** `#EXTINF:215,Artist - Title`; the length may be -1 (unknown) and carry attributes after it. */
private fun parseExtInf(line: String): M3uEntry {
    val body = line.substringAfter(':')
    val seconds = body.substringBefore(',').trim().substringBefore(' ').toDoubleOrNull() ?: 0.0
    val display = if (',' in body) body.substringAfter(',').trim() else ""
    val (artist, title) = splitArtistTitle(display)
    return M3uEntry("", title, artist, if (seconds > 0) (seconds * 1000).toLong() else 0L)
}

private fun splitArtistTitle(display: String): Pair<String, String> {
    val at = display.indexOf(" - ")
    return if (at < 0) "" to display else display.take(at).trim() to display.substring(at + 3).trim()
}

/** A playlist file for [songs]; [locationOf] says where each one is (empty leaves the track out). */
fun writeM3u(name: String, songs: List<Song>, locationOf: (Song) -> String): String = buildString {
    append("#EXTM3U\n")
    // A line break in a name would make the rest of it a track.
    append("#PLAYLIST:").append(oneLine(name)).append('\n')
    for (song in songs) {
        val location = locationOf(song)
        if (location.isEmpty()) continue
        val title = oneLine(song.title)
        val display = if (song.artist.isBlank()) title else "${oneLine(song.artist)} - $title"
        append("#EXTINF:").append(song.durationMs / 1000).append(',').append(display).append('\n')
        append(location).append('\n')
    }
}

private fun oneLine(s: String) = s.replace(Regex("\\s*[\\r\\n]+\\s*"), " ").trim()

/**
 * Finds a playlist file's tracks in a library. Files move between devices, so it goes from the
 * surest to the loosest: the exact path, the file's name with as much of its folders as can be
 * told apart, then the tags. A file name two tracks share with nothing to choose between them
 * falls through to the tags, as [TrackMatcher] errs towards "not there".
 *
 * @param pathOf where a song's file is; may be empty.
 * @param baseDir the folder of the playlist file, for paths relative to it.
 */
class M3uMatcher(songs: List<Song>, pathOf: (Song) -> String, private val baseDir: String? = null) {
    private val byPath = HashMap<String, Song>()
    private val byFileName = HashMap<String, MutableList<Pair<List<String>, Song>>>()
    private val byTags = songs.associateBy { TrackKey(it.title, it.artist, it.durationMs) }
    private val tags = TrackMatcher(byTags.keys)

    init {
        for (song in songs) {
            val path = comparable(pathOf(song))
            if (path.isEmpty()) continue
            byPath[path] = song
            val segments = path.split('/')
            byFileName.getOrPut(segments.last()) { ArrayList() } += segments to song
        }
    }

    fun match(file: M3uFile): M3uMatch {
        val found = file.entries.map(::find)
        return M3uMatch(found.mapNotNull { it?.id }, found.count { it == null })
    }

    private fun find(entry: M3uEntry): Song? {
        val path = comparable(entry.location)
        if (isUrl(path)) return null
        byPath[path]?.let { return it }
        baseDir?.let { base -> byPath[comparable(joined(base, entry.location))]?.let { return it } }
        return byName(path)?.takeIf { titleAgrees(entry, it) } ?: byTag(entry, path)
    }

    private fun byName(path: String): Song? {
        val segments = path.split('/')
        val same = byFileName[segments.last()] ?: return null
        same.singleOrNull()?.let { return it.second }
        val shared = same.map { (other, _) -> sharedTail(segments, other) }
        val best = shared.max()
        return if (best > 1 && shared.count { it == best } == 1) same[shared.indexOf(best)].second else null
    }

    /** "01.mp3" on two devices can be two songs; when the list says what it holds, that has to fit. */
    private fun titleAgrees(entry: M3uEntry, song: Song) =
        entry.title.isEmpty() || normalizeTag(entry.title) == normalizeTag(song.title)

    private fun byTag(entry: M3uEntry, path: String): Song? {
        val (artist, title) =
            if (entry.title.isNotEmpty()) entry.artist to entry.title
            else splitArtistTitle(path.substringAfterLast('/').substringBeforeLast('.'))
        return tags.find(TrackKey(title, artist, entry.durationMs))?.let(byTags::get)
    }

    private fun sharedTail(a: List<String>, b: List<String>): Int {
        var n = 0
        while (n < a.size && n < b.size && a[a.size - 1 - n] == b[b.size - 1 - n]) n++
        return n
    }

    /** Above all, slashes one way and one case: a list from Windows names paths any way it likes. */
    private fun comparable(location: String): String {
        val plain = if (location.startsWith("file:", ignoreCase = true)) fromFileUrl(location) else location
        return plain.replace('\\', '/').lowercase()
    }

    private fun fromFileUrl(url: String): String {
        val path = url.substringAfter(':').trimStart('/')
        // "file:///C:/Music/a.mp3" is a Windows path, "file:///storage/a.mp3" a rooted one.
        val rooted = if (path.length > 1 && path[1] == ':') path else "/$path"
        // A plain "+" is a plus in a URL's path, which URLDecoder would turn into a space.
        return runCatching { URLDecoder.decode(rooted.replace("+", "%2B"), "UTF-8") }.getOrDefault(rooted)
    }

    private fun joined(base: String, location: String) = base.trimEnd('/', '\\') + "/" + location

    private fun isUrl(path: String) = Regex("^[a-z][a-z0-9+.-]+://").containsMatchIn(path)
}
