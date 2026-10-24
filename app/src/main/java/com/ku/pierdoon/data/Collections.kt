package com.ku.pierdoon.data

import androidx.compose.runtime.Immutable
import java.text.Collator

@Immutable
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songs: List<Song>,
) {
    /** Any track of the album carries its art. */
    val coverSongId: Long get() = songs.first().id
}

@Immutable
data class Artist(
    val name: String,
    val songs: List<Song>,
    val albumCount: Int,
) {
    val coverSong: Song get() = songs.first()
}

@Immutable
data class MusicCollections(
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),
) {
    companion object {
        private const val RECENTLY_ADDED = 15

        /** One pass over the library; call off the main thread. */
        fun from(songs: List<Song>): MusicCollections {
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            val albums = songs.groupBy { it.albumId }.map { (id, tracks) ->
                Album(
                    id = id,
                    title = tracks.first().album,
                    artist = tracks.map { it.artist }.filter { it.isNotBlank() }
                        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty(),
                    songs = tracks,
                )
            }.sortedWith { a, b -> collator.compare(a.title, b.title) }

            val artists = songs.filter { it.artist.isNotBlank() }.groupBy { it.artist }.map { (name, tracks) ->
                Artist(name, tracks, tracks.distinctBy { it.albumId }.size)
            }.sortedWith { a, b -> collator.compare(a.name, b.name) }

            return MusicCollections(
                albums = albums,
                artists = artists,
                recentlyAdded = songs.sortedByDescending { it.dateAdded }.take(RECENTLY_ADDED),
            )
        }
    }
}
