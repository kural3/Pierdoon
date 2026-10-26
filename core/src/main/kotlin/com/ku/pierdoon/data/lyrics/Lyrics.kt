package com.ku.pierdoon.data.lyrics

import androidx.compose.runtime.Immutable

@Immutable
data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    @Immutable
    data class Synced(val lines: List<LyricLine>) : Lyrics {
        /** Index of the line active at [positionMs], or -1 before the first line. Binary search. */
        fun indexAt(positionMs: Long): Int {
            var lo = 0
            var hi = lines.lastIndex
            var result = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (lines[mid].timeMs <= positionMs) {
                    result = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            return result
        }
    }

    @Immutable
    data class Plain(val lines: List<String>) : Lyrics

    data object Instrumental : Lyrics
    data object NotFound : Lyrics
}

/** Minimal LRC parser: multiple stamps per line, 1–3 digit fractions and the [offset:] tag. */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(lrc: String): List<LyricLine> {
        val offset = offsetTag.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val out = ArrayList<LyricLine>()
        lrc.lineSequence().forEach { raw ->
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) return@forEach
            // Text follows the last leading timestamp.
            val text = raw.substring(stamps.last().range.last + 1).trim()
            stamps.forEach { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.toLong()
                }
                // Positive offset means lyrics should appear earlier.
                val t = (min * 60_000 + sec * 1000 + fracMs - offset).coerceAtLeast(0)
                out += LyricLine(t, text)
            }
        }
        out.sortBy { it.timeMs }
        return out
    }
}
