package com.ku.pierdoon.data

import java.text.Normalizer

/** What a library search found, best matches first in each group. */
data class LibraryMatches(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

/**
 * Every word of [query] has to appear somewhere in the item's title, artist or album; case, accents
 * and ё/е don't matter. Items whose title starts with the query come first.
 */
fun searchLibrary(query: String, songs: List<Song>, collections: MusicCollections): LibraryMatches {
    val q = fold(query)
    val words = q.split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return LibraryMatches()
    fun matches(vararg fields: String): Boolean {
        val text = fields.joinToString(" ") { fold(it) }
        return words.all { it in text }
    }
    fun <T> List<T>.ranked(title: (T) -> String) = sortedBy { if (fold(title(it)).startsWith(q)) 0 else 1 }
    return LibraryMatches(
        songs = songs.filter { matches(it.title, it.artist, it.album) }.ranked { it.title },
        albums = collections.albums.filter { matches(it.title, it.artist) }.ranked { it.title },
        artists = collections.artists.filter { matches(it.name) }.ranked { it.name },
    )
}

private val MARKS = Regex("\\p{Mn}+")
private val SPACES = Regex("\\s+")

private fun fold(s: String): String {
    // NFD splits "й" into "и" + a mark too; put it back so it isn't searched as "и".
    val decomposed = Normalizer.normalize(s.lowercase().replace('ё', 'е'), Normalizer.Form.NFD)
    return MARKS.replace(decomposed.replace("й", "й"), "").replace(SPACES, " ").trim()
}
