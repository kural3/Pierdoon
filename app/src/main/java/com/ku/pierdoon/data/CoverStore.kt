package com.ku.pierdoon.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.net.toUri
import java.io.File

/**
 * Covers the user picked: for tracks ("save in app") and for playlists. One JPEG per owner in
 * files/covers, named with a version so every cache (ours, Media3's, the system's) sees a
 * changed cover as a new image.
 *
 * Versions live in snapshot state: artwork on screen updates the moment a cover changes.
 * Shared by the UI and the playback service (same process), like [SettingsStore].
 */
object CoverStore {
    private var dir: File? = null
    private val songs = mutableStateMapOf<Long, Long>()
    private val playlists = mutableStateMapOf<Long, Long>()

    @Synchronized
    fun init(context: Context) {
        if (dir != null) return
        val d = File(context.applicationContext.filesDir, "covers").apply { mkdirs() }
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

    fun songUri(songId: Long): Uri? = songFile(songId)?.toUri()

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

/** Decodes [source] downsampled to about [maxPx] and saves it as JPEG. False if it isn't an image. */
fun writeScaledJpeg(source: File, target: File, maxPx: Int = COVER_MAX_PX): Boolean = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return false
    val scale = maxPx.toFloat() / maxOf(decoded.width, decoded.height)
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    } else {
        decoded
    }
    val tmp = File(target.parentFile, target.name + ".tmp")
    tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    tmp.renameTo(target)
}.getOrDefault(false)
