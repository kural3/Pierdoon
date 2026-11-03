package com.ku.pierdoon.platform

import java.io.File

/**
 * Where the app keeps its files. Roaming data (playlists, settings, covers) under %APPDATA%,
 * anything that can be rebuilt (lyrics, yt-dlp, thumbnails) under %LOCALAPPDATA%\Pier.doon Data.
 * Not %LOCALAPPDATA%\Pier.doon: the installer puts the app there and wipes that whole folder on
 * every upgrade and uninstall.
 * `-Dpierdoon.home=<dir>` keeps all of it in one folder instead (the screenshot tool runs on a copy).
 */
object AppDirs {
    private const val NAME = "Pier.doon"
    private const val LOCAL_NAME = "Pier.doon Data"
    private val home = System.getProperty("pierdoon.home")?.let(::File)

    val files: File by lazy {
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
        (home?.let { File(it, "files") } ?: File(base, NAME)).apply { mkdirs() }
    }

    val cache: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".cache")
        (home?.let { File(it, "cache") } ?: File(base, "$LOCAL_NAME/cache")).apply { mkdirs() }
    }

    /** Programs the app fetches for itself (yt-dlp); kept apart from the cache, which can be cleared. */
    val tools: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".local")
        (home?.let { File(it, "tools") } ?: File(base, "$LOCAL_NAME/tools")).apply { mkdirs() }
    }

    /** The user's Music folder, the default library and where downloads go. */
    val music: File by lazy {
        val home = File(System.getProperty("user.home"))
        // A Music folder moved elsewhere (OneDrive, another drive) is recorded by the shell.
        WinShell.knownFolder("{4BD8D571-6D19-48D3-BE97-422220080E43}")?.let(::File)?.takeIf { it.isDirectory }
            ?: File(home, "Music")
    }
}
