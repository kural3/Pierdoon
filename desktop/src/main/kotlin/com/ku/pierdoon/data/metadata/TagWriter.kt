package com.ku.pierdoon.data.metadata

import com.ku.pierdoon.data.Song
import com.ku.pierdoon.platform.AppDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        // Next to the original first, so the final step is a rename on the same drive.
        val staged = File(original.parentFile, "." + original.name + ".pierdoon")
        try {
            original.copyTo(temp, overwrite = true)
            FileTags.write(temp, title, artist, album, cover, removeCover)
            temp.copyTo(staged, overwrite = true)
            Files.move(staged.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        } finally {
            work.listFiles()?.forEach { it.delete() }
            // Left behind only when the move failed; it mustn't litter the music folder.
            staged.delete()
        }
    }
}
