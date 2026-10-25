package com.ku.pierdoon.platform

import java.io.File

/**
 * Where the app keeps its files. Roaming data (playlists, settings, covers) under %APPDATA%,
 * anything that can be rebuilt (lyrics, yt-dlp, thumbnails) under %LOCALAPPDATA%.
 */
object AppDirs {
    private const val NAME = "Pier.doon"

    val files: File by lazy {
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
        File(base, NAME).apply { mkdirs() }
    }

    val cache: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".cache")
        File(base, "$NAME/cache").apply { mkdirs() }
    }

    /** Programs the app fetches for itself (yt-dlp); kept apart from the cache, which can be cleared. */
    val tools: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".local")
        File(base, "$NAME/tools").apply { mkdirs() }
    }

    /** The user's Music folder, the default library and where downloads go. */
    val music: File by lazy {
        val home = File(System.getProperty("user.home"))
        // A Music folder moved elsewhere (OneDrive, another drive) is recorded by the shell.
        WinShell.knownFolder("{4BD8D571-6D19-48D3-BE97-422220080E43}")?.let(::File)?.takeIf { it.isDirectory }
            ?: File(home, "Music")
    }
}

/** Writes [text] to [file] through a temp file, so a crash mid-write leaves the old one intact. */
fun writeAtomically(file: File, text: String) {
    runCatching {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
