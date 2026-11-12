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

/** A folder of the device holding tracks; only the tracks right in it, not in folders below. */
@Immutable
data class Folder(
    /** As the tracks give it ([Song.folder]); the folder's key. */
    val path: String,
    /** Its own name, the last part of [path]. */
    val name: String,
    /** Where it lies, past what all the folders share; empty when that is all of it. */
    val location: String,
    val songs: List<Song>,
)

@Immutable
data class MusicCollections(
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),
    val folders: List<Folder> = emptyList(),
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
                folders = folders(songs, collator),
            )
        }

        /**
         * The folders of [songs], by name. A folder's location leaves out the start all the
         * folders share (C:\Users\me\Music on a PC), so what is shown is what tells them apart.
         */
        internal fun folders(songs: List<Song>, collator: Collator = Collator.getInstance()): List<Folder> {
            val groups = songs.filter { it.folder.isNotEmpty() }.groupBy { it.folder }
            if (groups.isEmpty()) return emptyList()
            val parts = groups.keys.associateWith { path -> path.split('/', '\\').filter { it.isNotEmpty() } }
            val parents = parts.values.map { it.dropLast(1) }
            val shared = parents.reduce { acc, p -> acc.zip(p).takeWhile { (a, b) -> a == b }.map { it.first } }.size
            return groups.map { (path, tracks) ->
                val segments = parts.getValue(path)
                Folder(
                    path = path,
                    name = segments.lastOrNull() ?: path,
                    location = segments.dropLast(1).drop(shared).joinToString(if ('\\' in path) "\\" else "/"),
                    songs = tracks,
                )
            }.sortedWith { a, b -> collator.compare(a.name, b.name).takeIf { it != 0 } ?: a.path.compareTo(b.path) }
        }
    }
}
