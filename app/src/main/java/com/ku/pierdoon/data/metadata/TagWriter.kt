package com.ku.pierdoon.data.metadata

import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import com.ku.pierdoon.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.resume

/**
 * Writes title, artist, album and cover into the audio file itself.
 *
 * The file belongs to whoever downloaded it, so the caller must first get the user's consent
 * (MediaStore write request). The edit happens on a private copy; only a fully written result
 * replaces the original, through the content URI the user granted.
 */
object TagWriter {

    /** Containers jaudiotagger can write tags into. Raw .aac (ADTS) has no tag format at all. */
    private val WRITABLE = setOf("mp3", "m4a", "m4b", "mp4", "flac", "ogg", "opus", "wav", "aif", "aiff", "wma", "dsf")

    init {
        // It logs every frame it parses at INFO; nobody reads that on a phone.
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    suspend fun displayName(context: Context, song: Song): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(song.uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    fun canWrite(displayName: String?): Boolean =
        displayName?.substringAfterLast('.', "")?.lowercase() in WRITABLE

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

            val audio = AudioFileIO.read(temp)
            val tag = audio.tagOrCreateAndSetDefault
            tag.setField(FieldKey.TITLE, title)
            tag.setField(FieldKey.ARTIST, artist)
            tag.setField(FieldKey.ALBUM, album)
            if (cover != null || removeCover) tag.deleteArtworkField()
            if (cover != null) {
                tag.setField(ArtworkFactory.getNew().apply {
                    binaryData = cover
                    mimeType = "image/jpeg"
                    pictureType = FRONT_COVER
                })
            }
            audio.commit()

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

    private const val FRONT_COVER = 3
}
