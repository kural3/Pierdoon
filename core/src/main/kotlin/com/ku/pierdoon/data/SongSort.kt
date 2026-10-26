package com.ku.pierdoon.data

import java.text.Collator

/** Orders for the "all tracks" list. The library arrives sorted by title already. */
enum class SongSort { Title, Artist, Album, DateAdded, Duration }

/**
 * [songs] in [sort] order. Text compares by the device's locale (ё next to е, case ignored);
 * ties fall back to the title, so equal artists or albums still read in a sensible order.
 */
fun sortSongs(songs: List<Song>, sort: SongSort, descending: Boolean): List<Song> {
    val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
    val byTitle = Comparator<Song> { a, b -> collator.compare(a.title, b.title) }
    val primary: Comparator<Song> = when (sort) {
        SongSort.Title -> byTitle
        SongSort.Artist -> Comparator { a, b -> collator.compare(a.artist, b.artist) }
        SongSort.Album -> Comparator { a, b -> collator.compare(a.album, b.album) }
        SongSort.DateAdded -> compareBy { it.dateAdded }
        SongSort.Duration -> compareBy { it.durationMs }
    }
    val ordered = if (descending) primary.reversed() else primary
    return songs.sortedWith(if (sort == SongSort.Title) ordered else ordered.then(byTitle))
}
