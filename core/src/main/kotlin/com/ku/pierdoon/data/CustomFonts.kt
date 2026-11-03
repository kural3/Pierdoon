package com.ku.pierdoon.data

import java.io.File
import java.io.InputStream

/**
 * The user's own font: copied into the app's fonts folder when picked, so it keeps working if
 * the original is moved or deleted. One at a time; picking another replaces it.
 */
object CustomFonts {
    /** What a picked font became: the file in the fonts folder and the name it goes by. */
    data class Imported(val file: String, val name: String)

    /** The extensions a font file may have; anything else is turned away before it's copied. */
    val EXTENSIONS = listOf("ttf", "otf", "ttc")

    /**
     * Copies [source] (a file called [fileName]) into [dir] under a new name, so a font already
     * on screen is never overwritten underneath it. Null if it isn't a TrueType or OpenType font;
     * then nothing is left behind. Earlier fonts in [dir] are removed once the new one is in.
     */
    fun import(source: InputStream, fileName: String, dir: File): Imported? {
        dir.mkdirs()
        val ext = fileName.substringAfterLast('.', "").lowercase().takeIf { it in EXTENSIONS } ?: "ttf"
        val target = File(dir, "font-${System.currentTimeMillis()}.$ext")
        source.use { input -> target.outputStream().use { input.copyTo(it) } }
        // Not readNBytes: this runs on Android 7 too, which doesn't have it.
        val header = ByteArray(4)
        val read = target.inputStream().use { input ->
            var n = 0
            while (n < header.size) {
                val r = input.read(header, n, header.size - n)
                if (r < 0) break
                n += r
            }
            n
        }
        if (read < header.size) {
            target.delete()
            return null
        }
        if (!looksLikeFont(header)) {
            target.delete()
            return null
        }
        dir.listFiles()?.filter { it.name != target.name && it.name.startsWith("font-") }?.forEach { it.delete() }
        val name = fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').ifBlank { target.name }
        return Imported(target.name, name)
    }

    /** The first four bytes of a TrueType, OpenType or TrueType collection file. */
    fun looksLikeFont(header: ByteArray): Boolean {
        if (header.size < 4) return false
        val tag = String(header, 0, 4, Charsets.ISO_8859_1)
        return tag == "OTTO" || tag == "true" || tag == "ttcf" ||
            (header[0] == 0.toByte() && header[1] == 1.toByte() && header[2] == 0.toByte() && header[3] == 0.toByte())
    }
}
