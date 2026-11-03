package com.ku.pierdoon.data.lyrics

import androidx.compose.runtime.Immutable

/** A line in the lyrics editor: its text and, once it has one, the moment it starts. */
@Immutable
data class EditLine(val timeMs: Long?, val text: String)

/**
 * Lyrics as text the user writes: one line per line, an optional `[mm:ss.xx]` stamp in front.
 * All lines stamped, the lyrics are synced; none, plain; some, synced with the gaps filled in.
 */
object LyricsText {
    private val stamp = Regex("""^\s*\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val tag = Regex("""^\s*\[[a-zA-Z]+:[^]]*]\s*$""")

    /** How long an unstamped line after the last stamped one is given, in the filled-in timeline. */
    private const val GAP_MS = 3_000L

    fun lines(text: String): List<EditLine> {
        val out = ArrayList<EditLine>()
        text.replace("\r", "").lines().forEach { raw ->
            if (tag.matches(raw)) return@forEach // [ar:], [ti:], [offset:]: nothing to show or edit
            var rest = raw
            val times = ArrayList<Long>()
            while (true) {
                val m = stamp.find(rest) ?: break
                times += toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                rest = rest.substring(m.range.last + 1)
            }
            val line = rest.trim()
            if (times.isEmpty()) out += EditLine(null, line)
            else times.forEach { out += EditLine(it, line) } // a chorus stamped twice: two lines
        }
        // Blank lines at the ends are only the text field's; in between they part the verses.
        while (out.lastOrNull()?.let { it.timeMs == null && it.text.isEmpty() } == true) out.removeAt(out.lastIndex)
        while (out.firstOrNull()?.let { it.timeMs == null && it.text.isEmpty() } == true) out.removeAt(0)
        return out
    }

    fun text(lines: List<EditLine>): String = lines.joinToString("\n") { l ->
        if (l.timeMs == null) l.text else "[${time(l.timeMs)}]${l.text}"
    }

    /** What the editor starts from for lyrics already on the track. */
    fun from(lyrics: Lyrics?): List<EditLine> = when (lyrics) {
        is Lyrics.Synced -> lyrics.lines.map { EditLine(it.timeMs, it.text) }
        is Lyrics.Plain -> lines(lyrics.lines.joinToString("\n"))
        else -> emptyList()
    }

    /** Nothing written means no lyrics: [Lyrics.NotFound]. */
    fun toLyrics(lines: List<EditLine>): Lyrics {
        if (lines.all { it.text.isBlank() && it.timeMs == null }) return Lyrics.NotFound
        if (lines.none { it.timeMs != null }) return Lyrics.Plain(lines.map { it.text })
        val times = LongArray(lines.size) { lines[it].timeMs ?: -1L }
        var i = 0
        while (i < times.size) {
            if (times[i] >= 0) { i++; continue }
            // A run of unstamped lines: spread them evenly up to the next stamp.
            var j = i
            while (j < times.size && times[j] < 0) j++
            val start = if (i > 0) times[i - 1] else 0L
            val end = if (j < times.size) times[j] else start + GAP_MS * (j - i + 1)
            val step = (end - start).coerceAtLeast(0) / (j - i + 1)
            for (k in i until j) times[k] = start + step * (k - i + 1)
            i = j
        }
        return Lyrics.Synced(
            lines.indices.map { LyricLine(times[it], lines[it].text) }.sortedBy { it.timeMs },
        )
    }

    fun toLyrics(text: String): Lyrics = toLyrics(lines(text))

    /** `mm:ss.cc`, the LRC way. */
    fun time(ms: Long): String {
        val m = ms / 60_000
        val s = ms / 1000 % 60
        val cs = ms % 1000 / 10
        return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}.${cs.toString().padStart(2, '0')}"
    }

    fun lrc(lyrics: Lyrics.Synced): String = lyrics.lines.joinToString("") { "[${time(it.timeMs)}]${it.text}\n" }

    private fun toMs(min: String, sec: String, frac: String): Long {
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.toLong()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs
    }
}
