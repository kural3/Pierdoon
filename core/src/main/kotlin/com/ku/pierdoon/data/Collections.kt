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

    /** MediaStore's album of that track, for its art; [id] may be the app's own. */
    val coverAlbumId: Long get() = songs.first().albumId
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
    /** The artist [name] names, or the first one of it when it's a whole tag like "A, B". */
    fun artist(name: String): Artist? =
        artists.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: leadArtist(name).let { lead -> artists.firstOrNull { it.name.equals(lead, ignoreCase = true) } }

    companion object {
        private const val RECENTLY_ADDED = 15

        /** One pass over the library; call off the main thread. */
        fun from(songs: List<Song>): MusicCollections {
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            // MediaStore files untagged tracks of a folder under one album; tags the app keeps
            // for them (downloads that took none) still tell their albums apart.
            val byName = songs.groupBy { it.albumId to it.album }
            val namesPerId = byName.keys.groupingBy { it.first }.eachCount()
            val albums = byName.map { (key, tracks) ->
                val (mediaStoreId, name) = key
                Album(
                    id = if (namesPerId[mediaStoreId] == 1) mediaStoreId else mediaStoreId * 31 + name.hashCode(),
                    title = name,
                    artist = tracks.map { it.artist }.filter { it.isNotBlank() }
                        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty(),
                    songs = tracks,
                )
            }.sortedWith { a, b -> collator.compare(a.title, b.title) }

            // A track of "A, B" is both A's and B's; names that differ only in case are one artist,
            // under the spelling met first.
            val byArtist = LinkedHashMap<String, Pair<String, MutableList<Song>>>()
            for (song in songs) for (name in splitArtists(song.artist)) {
                byArtist.getOrPut(name.lowercase()) { name to ArrayList() }.second += song
            }
            val artists = byArtist.values.map { (name, tracks) ->
                Artist(name, tracks, tracks.distinctBy { it.albumId to it.album }.size)
            }.sortedWith { a, b -> collator.compare(a.name, b.name) }

            return MusicCollections(
                albums = albums,
                artists = artists,
                recentlyAdded = songs.sortedByDescending { it.dateAdded }.take(RECENTLY_ADDED),
            )
        }
    }
}
