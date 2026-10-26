package com.ku.pierdoon.data

import java.text.Normalizer

/** What a track is recognised by on another device: the files may differ, the tags usually don't. */
data class TrackKey(val title: String, val artist: String, val durationMs: Long)

/**
 * Finds the tracks that look like ones already in a library: the same title and artist, give or
 * take case, spacing, "ё" and the like, and a length within a few seconds (MP3 and M4A of one
 * recording report slightly different ones). Without an artist on either side the length has to
 * be known on both, or the title alone would match too much.
 *
 * Errs towards "not there": a missed match costs a duplicate the user was warned could happen,
 * a wrong one would leave a track behind.
 */
class TrackMatcher(library: Collection<TrackKey>) {
    private val byTitle: Map<String, List<TrackKey>> = library.groupBy { normalizeTag(it.title) }

    fun contains(track: TrackKey): Boolean {
        val title = normalizeTag(track.title)
        if (title.isEmpty()) return false
        val artist = normalizeArtist(track.artist)
        return byTitle[title].orEmpty().any { other ->
            val otherArtist = normalizeArtist(other.artist)
            val lengthsKnown = track.durationMs > 0 && other.durationMs > 0
            val closeLength = !lengthsKnown || kotlin.math.abs(track.durationMs - other.durationMs) <= DURATION_SLACK_MS
            when {
                artist.isNotEmpty() && otherArtist.isNotEmpty() -> artist == otherArtist && closeLength
                else -> lengthsKnown && closeLength
            }
        }
    }

    companion object {
        const val DURATION_SLACK_MS = 3_000L
    }
}

/** Lower case, "ё" as "е", unified quotes and dashes, punctuation at the ends and repeated spaces gone. */
fun normalizeTag(s: String): String {
    val folded = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
        .replace('ё', 'е')
        .replace(Regex("[‘’‚‛ʼ`´]"), "'")
        .replace(Regex("[“”„«»]"), "\"")
        .replace(Regex("[‐-―−]"), "-")
        .replace(Regex("\\s+"), " ")
    return folded.trim { it.isWhitespace() || it in ".,;:!?-_\"'" }
}

/** Like [normalizeTag], with the "unknown artist" placeholders of MediaStore and taggers as none. */
private fun normalizeArtist(s: String): String =
    normalizeTag(s).takeUnless { it in UNKNOWN_ARTISTS }.orEmpty()

private val UNKNOWN_ARTISTS = setOf("<unknown>", "unknown", "unknown artist", "неизвестный исполнитель", "various artists")
