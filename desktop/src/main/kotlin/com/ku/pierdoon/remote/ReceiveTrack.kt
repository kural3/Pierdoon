package com.ku.pierdoon.remote

import com.ku.pierdoon.data.download.safeFileName
import java.io.File
import java.io.InputStream
import java.nio.file.Files

private val receiving = Any()

/**
 * Saves a track sent over the network into [dir] as [name] (only its last path part counts), or
 * "name (1)" and so on beside one already there. Written under a temporary name first, so a
 * half-arrived file never shows up in the library. Null, and nothing left behind, unless exactly
 * [length] bytes came.
 */
internal fun receiveTrack(input: InputStream, length: Long, name: String, dir: File): File? {
    val fileName = name.substringAfterLast('/').substringAfterLast('\\')
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val base = safeFileName(fileName.substringBeforeLast('.'))
    val part = File(dir, "$base.${System.nanoTime()}.part")
    try {
        val copied = part.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
        if (copied != length) return null
        return synchronized(receiving) {
            var target = File(dir, "$base.$ext")
            var n = 1
            while (target.exists()) target = File(dir, "$base (${n++}).$ext")
            Files.move(part.toPath(), target.toPath())
            target
        }
    } finally {
        part.delete()
    }
}
