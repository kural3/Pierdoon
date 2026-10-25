package com.ku.pierdoon.data

import androidx.compose.runtime.mutableStateMapOf
import com.ku.pierdoon.platform.AppDirs
import com.ku.pierdoon.platform.Images
import java.io.File

/**
 * Covers the user picked: for tracks ("save in app") and for playlists. One JPEG per owner in
 * the app's covers folder, named with a version so every cache sees a changed cover as a new image.
 *
 * Versions live in snapshot state: artwork on screen updates the moment a cover changes.
 */
object CoverStore {
    private var dir: File? = null
    private val songs = mutableStateMapOf<Long, Long>()
    private val playlists = mutableStateMapOf<Long, Long>()

    @Synchronized
    fun init() {
        if (dir != null) return
        val d = File(AppDirs.files, "covers").apply { mkdirs() }
        dir = d
        d.listFiles()?.forEach { f ->
            val m = NAME.matchEntire(f.name) ?: return@forEach
            val id = m.groupValues[2].toLongOrNull() ?: return@forEach
            val version = m.groupValues[3].toLongOrNull() ?: return@forEach
            when (m.groupValues[1]) {
                SONG -> songs[id] = version
                PLAYLIST -> playlists[id] = version
            }
        }
    }

    fun songVersion(songId: Long): Long? = songs[songId]
    fun playlistVersion(playlistId: Long): Long? = playlists[playlistId]

    fun songFile(songId: Long): File? = songs[songId]?.let { file(SONG, songId, it) }
    fun playlistFile(playlistId: Long): File? = playlists[playlistId]?.let { file(PLAYLIST, playlistId, it) }

    /** Copies [image] (any decodable picture) in as the track's cover, scaled to a sane size. */
    fun setSongCover(songId: Long, image: File): Boolean = put(SONG, songId, image, songs)
    fun setPlaylistCover(playlistId: Long, image: File): Boolean = put(PLAYLIST, playlistId, image, playlists)

    fun removeSongCover(songId: Long) = remove(SONG, songId, songs)
    fun removePlaylistCover(playlistId: Long) = remove(PLAYLIST, playlistId, playlists)

    private fun put(kind: String, id: Long, image: File, versions: MutableMap<Long, Long>): Boolean {
        val d = dir ?: return false
        val version = System.currentTimeMillis()
        val target = File(d, "${kind}_${id}_$version.jpg")
        if (!writeScaledJpeg(image, target)) return false
        val old = versions[id]
        versions[id] = version
        old?.let { file(kind, id, it).delete() }
        return true
    }

    private fun remove(kind: String, id: Long, versions: MutableMap<Long, Long>) {
        val old = versions.remove(id) ?: return
        file(kind, id, old).delete()
    }

    private fun file(kind: String, id: Long, version: Long) = File(dir, "${kind}_${id}_$version.jpg")

    private const val SONG = "song"
    private const val PLAYLIST = "playlist"
    private val NAME = Regex("""(song|playlist)_(-?\d+)_(\d+)\.jpg""")
}

/** Longest side of a stored cover; plenty for a full-screen player, small on disk. */
const val COVER_MAX_PX = 1200

/** Decodes [source] scaled to about [maxPx] and saves it as JPEG. False if it isn't an image. */
fun writeScaledJpeg(source: File, target: File, maxPx: Int = COVER_MAX_PX): Boolean = runCatching {
    val image = Images.decode(source.readBytes()) ?: return false
    val bytes = Images.jpeg(Images.scaled(image, maxPx)) ?: return false
    val tmp = File(target.parentFile, target.name + ".tmp")
    tmp.writeBytes(bytes)
    tmp.renameTo(target)
}.getOrDefault(false)
