package com.ku.pierdoon.data

import androidx.compose.runtime.Immutable

/** Tracks that look like one recording more than once; [songs] has the one most worth keeping first. */
@Immutable
data class DuplicateGroup(val songs: List<Song>)

/**
 * Finds the library's doubles: the same title (less "(Official Video)" and the like) and artist,
 * and lengths within a few seconds of each other, as [TrackMatcher] tells tracks apart between
 * devices. Untagged tracks need a known length to match.
 *
 * In each group the track to keep comes first: a favourite, then the one in more playlists,
 * then the one played more, then the oldest in the library.
 */
object Duplicates {

    fun find(
        songs: List<Song>,
        favorites: Set<Long> = emptySet(),
        playlistCounts: Map<Long, Int> = emptyMap(),
        playCounts: Map<Long, Int> = emptyMap(),
    ): List<DuplicateGroup> {
        val byTitle = songs.groupBy { normalizeTag(bareTitle(it.title)) }
        val groups = ArrayList<DuplicateGroup>()
        for ((title, tracks) in byTitle) {
            if (title.isEmpty() || tracks.size < 2) continue
            // Within a title, tracks join a group when one of its members looks like them.
            val left = tracks.toMutableList()
            while (left.isNotEmpty()) {
                val group = mutableListOf(left.removeAt(0))
                var i = 0
                while (i < left.size) {
                    val candidate = left[i]
                    if (group.any { same(it, candidate) }) {
                        group += left.removeAt(i)
                        i = 0
                    } else {
                        i++
                    }
                }
                if (group.size > 1) {
                    groups += DuplicateGroup(group.sortedWith(
                        compareByDescending<Song> { it.id in favorites }
                            .thenByDescending { playlistCounts[it.id] ?: 0 }
                            .thenByDescending { playCounts[it.id] ?: 0 }
                            .thenBy { it.dateAdded },
                    ))
                }
            }
        }
        return groups.sortedBy { it.songs.first().title.lowercase() }
    }

    private fun same(a: Song, b: Song): Boolean {
        val keyA = TrackKey(bareTitle(a.title), a.artist, a.durationMs)
        val keyB = TrackKey(bareTitle(b.title), b.artist, b.durationMs)
        return TrackMatcher(listOf(keyA)).contains(keyB)
    }
}
