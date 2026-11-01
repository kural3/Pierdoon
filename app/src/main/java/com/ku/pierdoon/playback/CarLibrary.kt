package com.ku.pierdoon.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.ku.pierdoon.R
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.MusicRepository
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.UserLibrary
import com.ku.pierdoon.data.searchLibrary
import kotlinx.coroutines.flow.first

/**
 * The library as Android Auto walks it: four tabs (the most it shows), folders under them, tracks
 * at the end.
 *
 * A track here has the id "<its list>/<song id>", so picking one can queue the list around it. In
 * the player it's a plain queue item again, with the song id alone like every other.
 */
class CarLibrary(private val context: Context) {
    private val repository = MusicRepository(context)
    private var library: Library? = null
    private var loadedAtMs = 0L

    private class Library(val songs: List<Song>, val collections: MusicCollections)

    val root: MediaItem
        get() = folder(ROOT, context.getString(R.string.app_name))

    /** A walk through the tree asks for many lists in a row; the file scan needn't run for each. */
    private suspend fun library(): Library {
        library?.takeIf { SystemClock.uptimeMillis() - loadedAtMs < LIBRARY_FRESH_MS }?.let { return it }
        val songs = runCatching { repository.loadSongs() }.getOrDefault(emptyList())
        return Library(songs, MusicCollections.from(songs)).also {
            library = it
            loadedAtMs = SystemClock.uptimeMillis()
        }
    }

    private suspend fun userData() = UserLibrary.get(context).data.first { it.loaded }

    suspend fun children(id: String): List<MediaItem> = when (id) {
        ROOT -> listOf(
            folder(PLAYLISTS, context.getString(R.string.nav_playlists)),
            folder(ALBUMS, context.getString(R.string.nav_albums)),
            folder(ARTISTS, context.getString(R.string.nav_artists)),
            folder(TRACKS, context.getString(R.string.car_tracks)),
        )
        PLAYLISTS -> playlists()
        ALBUMS -> library().collections.albums.map {
            folder("$ALBUM${it.id}", it.title, it.artist, it.songs.first())
        }
        ARTISTS -> library().collections.artists.map {
            folder("$ARTIST${Uri.encode(it.name)}", it.name, cover = it.coverSong)
        }
        else -> songsOf(id).map { track(id, it) }
    }

    private suspend fun playlists(): List<MediaItem> {
        val data = userData()
        val byId = library().songs.associateBy { it.id }
        fun first(ids: Collection<Long>) = ids.firstNotNullOfOrNull { byId[it] }
        return listOf(folder(FAVORITES, context.getString(R.string.favorites), cover = first(data.favorites))) +
            data.playlists.map { folder("$PLAYLIST${it.id}", it.name, cover = first(it.songIds)) }
    }

    suspend fun item(id: String): MediaItem? {
        if (id == ROOT) return root
        if (!isTrack(id)) return null
        val (songs, index) = queueFor(id) ?: return null
        return track(id.substringBeforeLast('/'), songs[index])
    }

    suspend fun search(query: String): List<MediaItem> {
        val list = "$SEARCH${Uri.encode(query)}"
        return songsOf(list).map { track(list, it) }
    }

    /** The list a track was picked from, and where in it the track is. */
    suspend fun queueFor(id: String): Pair<List<Song>, Int>? {
        val songId = id.substringAfterLast('/').toLongOrNull() ?: return null
        val songs = songsOf(id.substringBeforeLast('/'))
        val index = songs.indexOfFirst { it.id == songId }
        return if (index >= 0) songs to index else null
    }

    /** What "play ..." said aloud comes to; nothing named means the whole library. */
    suspend fun songsForVoice(query: String): List<Song> =
        if (query.isBlank()) library().songs else songsOf("$SEARCH${Uri.encode(query)}")

    private suspend fun songsOf(list: String): List<Song> {
        val library = library()
        return when {
            list == TRACKS -> library.songs
            list == FAVORITES -> inOrder(userData().favorites, library)
            list.startsWith(PLAYLIST) -> {
                val id = list.removePrefix(PLAYLIST).toLongOrNull()
                inOrder(userData().playlists.firstOrNull { it.id == id }?.songIds.orEmpty(), library)
            }
            list.startsWith(ALBUM) -> {
                val id = list.removePrefix(ALBUM).toLongOrNull()
                library.collections.albums.firstOrNull { it.id == id }?.songs.orEmpty()
            }
            list.startsWith(ARTIST) -> {
                val name = Uri.decode(list.removePrefix(ARTIST))
                library.collections.artists.firstOrNull { it.name == name }?.songs.orEmpty()
            }
            list.startsWith(SEARCH) ->
                searchLibrary(Uri.decode(list.removePrefix(SEARCH)), library.songs, library.collections).songs
            else -> emptyList()
        }
    }

    private fun inOrder(ids: Collection<Long>, library: Library): List<Song> {
        val byId = library.songs.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    private fun folder(id: String, title: String, subtitle: String = "", cover: Song? = null) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(subtitle)
                .setArtworkUri(cover?.let { CarArtworkProvider.uri(context, it) })
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()

    private fun track(list: String, song: Song) = MediaItem.Builder()
        .setMediaId("$list/${song.id}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(CarArtworkProvider.uri(context, song))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()

    companion object {
        /** Only these read the library: it's what the audio permission guards, and the service is open to every app. */
        val CAR_PACKAGES = setOf(
            "com.google.android.projection.gearhead",
            "com.google.android.carassistant",
            "com.google.android.googlequicksearchbox",
            "com.google.android.autosimulator",
        )

        const val ROOT = "car:root"
        private const val PLAYLISTS = "car:playlists"
        private const val ALBUMS = "car:albums"
        private const val ARTISTS = "car:artists"
        private const val TRACKS = "car:tracks"
        private const val FAVORITES = "car:favorites"
        private const val PLAYLIST = "car:playlist:"
        private const val ALBUM = "car:album:"
        private const val ARTIST = "car:artist:"
        private const val SEARCH = "car:search:"
        private const val LIBRARY_FRESH_MS = 30_000L

        /** Encoded names can't hold a slash, so this tells the car's tracks from the app's plain ids. */
        fun isTrack(mediaId: String) = mediaId.startsWith("car:") && '/' in mediaId
    }
}
