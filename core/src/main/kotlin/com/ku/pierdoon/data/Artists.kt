package com.ku.pierdoon.data

/**
 * The artists of an artist tag: "A, B", "A; B" and "A feat. B" name several, each of whom the
 * track counts for. A part that starts with "The" stays with the one before ("Tyler, The Creator").
 */
fun splitArtists(artist: String): List<String> {
    if (artist.isBlank()) return emptyList()
    val out = ArrayList<String>()
    for (part in artist.split(separators)) {
        val name = part.trim()
        if (name.isEmpty()) continue
        if (out.isNotEmpty() && name.startsWith("the ", ignoreCase = true)) out[out.lastIndex] = out.last() + ", " + name
        else out += name
    }
    return out.distinctBy { it.lowercase() }
}

/** The first artist of the tag, the one a track's own line points to. */
fun leadArtist(artist: String): String = splitArtists(artist).firstOrNull() ?: artist

private val separators = Regex("""\s*[,;]\s*|\s+(?:feat\.?|ft\.?|featuring)\s+""", RegexOption.IGNORE_CASE)
