package com.ku.pierdoon.data.metadata

import com.ku.pierdoon.data.uri
import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import com.ku.pierdoon.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Writes title, artist, album and cover into the audio file itself.
 *
 * The file belongs to whoever downloaded it, so the caller must first get the user's consent
 * (MediaStore write request). The edit happens on a private copy; only a fully written result
 * replaces the original, through the content URI the user granted.
 */
object TagWriter {
    suspend fun displayName(context: Context, song: Song): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(song.uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    /** @param cover new cover as JPEG bytes, or null to keep the file's own (unless [removeCover]). */
    suspend fun write(
        context: Context,
        song: Song,
        displayName: String,
        title: String,
        artist: String,
        album: String,
        cover: ByteArray?,
        removeCover: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val work = File(context.cacheDir, "tag-edit").apply { mkdirs() }
        val temp = File(work, "edit.$ext")
        try {
            val resolver = context.contentResolver
            resolver.openInputStream(song.uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                ?: return@withContext false

            FileTags.write(temp, title, artist, album, cover, removeCover)

            // "wt" truncates: the file becomes exactly the edited copy.
            resolver.openOutputStream(song.uri, "wt")?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                ?: return@withContext false
            rescan(context, song)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        } finally {
            work.listFiles()?.forEach { it.delete() }
        }
    }

    /** Lets MediaStore re-read the tags (and regenerate the thumbnail) right away. */
    private suspend fun rescan(context: Context, song: Song) {
        val path = runCatching {
            context.contentResolver.query(song.uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: return
        suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> if (cont.isActive) cont.resume(Unit) }
        }
    }
}
