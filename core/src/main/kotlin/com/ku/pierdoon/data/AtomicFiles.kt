package com.ku.pierdoon.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes [text] to [file] through a temp file, so a crash mid-write leaves the old one intact.
 * A plain rename replaces the old file at once on Android; on Windows it won't replace an
 * existing one, and deleting it first would leave a moment where neither exists, so there the
 * move goes through java.nio (which Android has only from 8.0, and never needs here).
 */
fun writeAtomically(file: File, text: String) {
    runCatching {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (tmp.renameTo(file)) return
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/**
 * Older versions could stop a save between deleting [file] and renaming its temp file over it,
 * leaving only the complete temp file behind. Puts it back; true if [file] is there to read.
 */
fun recoverAtomicWrite(file: File): Boolean {
    if (file.exists()) return true
    val tmp = File(file.parentFile, file.name + ".tmp")
    return tmp.exists() && tmp.renameTo(file)
}
