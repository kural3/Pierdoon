package com.ku.pierdoon.data.metadata

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/** Writes tags into an audio file in place, the same way on both apps. */
object FileTags {
    // jaudiotagger logs every frame it parses at INFO. Held here: JUL keeps loggers only weakly,
    // and a collected one comes back with its chatter on.
    private val quiet: Logger = Logger.getLogger("org.jaudiotagger").apply { level = Level.OFF }

    /** Containers jaudiotagger can write tags into. Raw .aac (ADTS) has no tag format at all. */
    private val WRITABLE = setOf("mp3", "m4a", "m4b", "mp4", "flac", "ogg", "opus", "wav", "aif", "aiff", "wma", "dsf")

    fun canWrite(fileName: String?): Boolean =
        fileName?.substringAfterLast('.', "")?.lowercase() in WRITABLE

    /** Sets the tags of a private [file] in place; jaudiotagger picks the format by its extension. */
    fun write(
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
