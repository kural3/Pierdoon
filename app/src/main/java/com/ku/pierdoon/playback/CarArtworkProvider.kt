package com.ku.pierdoon.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.net.toUri
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.ArtworkCache
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

/**
 * Covers for Android Auto's lists. It draws them itself from the URIs it's given and has no right
 * to MediaStore's, so they come from here, to it alone.
 */
class CarArtworkProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = checkNotNull(context)
        val caller = callingPackage
        if (caller != context.packageName && caller !in CarLibrary.CAR_PACKAGES) throw SecurityException("Not for $caller")
        val songId = uri.pathSegments.getOrNull(0)?.toLongOrNull()
        val albumId = uri.pathSegments.getOrNull(1)?.toLongOrNull()
        if (songId == null || albumId == null) throw FileNotFoundException(uri.toString())
        CoverStore.init(context)
        val bitmap = runBlocking { ArtworkCache.load(context, songId, albumId, large = false) }
            ?: throw FileNotFoundException(uri.toString())
        val dir = File(context.cacheDir, "car-art").apply { mkdirs() }
        val file = File.createTempFile("art", ".jpg", dir)
        try {
            file.outputStream().use { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 85, it) }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            // The open descriptor keeps the bytes readable; nothing piles up in the cache.
            file.delete()
        }
    }

    override fun getType(uri: Uri) = "image/jpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        fun uri(context: Context, song: Song): Uri =
            "content://${context.packageName}.carart/${song.id}/${song.albumId}".toUri()
    }
}
