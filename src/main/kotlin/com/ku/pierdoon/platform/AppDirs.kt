package com.ku.pierdoon.platform

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Where the app keeps its files. Roaming data (playlists, settings, covers) under %APPDATA%,
 * anything that can be rebuilt (lyrics, yt-dlp, thumbnails) under %LOCALAPPDATA%.
 * `-Dpierdoon.home=<dir>` keeps all of it in one folder instead (the screenshot tool runs on a copy).
 */
object AppDirs {
    private const val NAME = "Pier.doon"
    private val home = System.getProperty("pierdoon.home")?.let(::File)

    val files: File by lazy {
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
        (home?.let { File(it, "files") } ?: File(base, NAME)).apply { mkdirs() }
    }

    val cache: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".cache")
        (home?.let { File(it, "cache") } ?: File(base, "$NAME/cache")).apply { mkdirs() }
    }

    /** Programs the app fetches for itself (yt-dlp); kept apart from the cache, which can be cleared. */
    val tools: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".local")
        (home?.let { File(it, "tools") } ?: File(base, "$NAME/tools")).apply { mkdirs() }
    }

    /** The user's Music folder, the default library and where downloads go. */
    val music: File by lazy {
        val home = File(System.getProperty("user.home"))
        // A Music folder moved elsewhere (OneDrive, another drive) is recorded by the shell.
        WinShell.knownFolder("{4BD8D571-6D19-48D3-BE97-422220080E43}")?.let(::File)?.takeIf { it.isDirectory }
            ?: File(home, "Music")
    }
}

/**
 * Writes [text] to [file] through a temp file, so a crash mid-write leaves the old one intact.
 * Not File.renameTo: on Windows it won't replace an existing file, and deleting the old one first
 * leaves a moment where neither exists.
 */
fun writeAtomically(file: File, text: String) {
    runCatching {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/**
 * Before 1.0.3 a save could stop between deleting [file] and renaming its temp file over it,
 * leaving only the complete temp file behind. Puts it back; true if [file] is there to read.
 */
fun recoverAtomicWrite(file: File): Boolean {
    if (file.exists()) return true
    val tmp = File(file.parentFile, file.name + ".tmp")
    return tmp.exists() && runCatching { Files.move(tmp.toPath(), file.toPath()) }.isSuccess
}
