package com.ku.pierdoon.data.metadata

import com.ku.pierdoon.platform.MediaProbe
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.platform.AppDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes title, artist, album and cover into the audio file itself.
 *
 * The edit happens on a private copy; only a fully written result replaces the original,
 * so a writer that gives up half-way never damages the user's file.
 */
object TagWriter {
    init {
        MediaProbe.quietTags // jaudiotagger's chatter off, as for reading.
    }

    /** Containers jaudiotagger can write tags into. Raw .aac (ADTS) has no tag format at all. */
    private val WRITABLE = setOf("mp3", "m4a", "m4b", "mp4", "flac", "ogg", "opus", "wav", "aif", "aiff", "wma", "dsf")

    fun canWrite(fileName: String?): Boolean =
        fileName?.substringAfterLast('.', "")?.lowercase() in WRITABLE

    /** @param cover new cover as JPEG bytes, or null to keep the file's own (unless [removeCover]). */
    suspend fun write(
        song: Song,
        title: String,
        artist: String,
        album: String,
        cover: ByteArray?,
        removeCover: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        val original = song.file
        val ext = original.extension.lowercase()
        val work = File(AppDirs.cache, "tag-edit").apply { mkdirs() }
        val temp = File(work, "edit.$ext")
        try {
            original.copyTo(temp, overwrite = true)
            tagFile(temp, title, artist, album, cover, removeCover)
            // Next to the original first, so the final step is a rename on the same drive.
            val staged = File(original.parentFile, "." + original.name + ".pierdoon")
            temp.copyTo(staged, overwrite = true)
            Files.move(staged.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        } finally {
            work.listFiles()?.forEach { it.delete() }
        }
    }

    /** Sets the tags of a private [file] in place; jaudiotagger picks the format by its extension. */
    fun tagFile(
        file: File,
        title: String,
        artist: String,
        album: String,
        cover: ByteArray?,
        removeCover: Boolean = false,
    ) {
        if (file.extension.equals("opus", ignoreCase = true)) {
            OpusTags.write(file, title, artist, album, cover, removeCover)
            return
        }
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault
        tag.setField(FieldKey.TITLE, title)
        tag.setField(FieldKey.ARTIST, artist)
        // jaudiotagger refuses empty values.
        if (album.isBlank()) tag.deleteField(FieldKey.ALBUM) else tag.setField(FieldKey.ALBUM, album)
        if (cover != null || removeCover) tag.deleteArtworkField()
        if (cover != null) {
            tag.setField(ArtworkFactory.getNew().apply {
                binaryData = cover
                mimeType = "image/jpeg"
                pictureType = FRONT_COVER
            })
        }
        audio.commit()
    }

    private const val FRONT_COVER = 3
}
