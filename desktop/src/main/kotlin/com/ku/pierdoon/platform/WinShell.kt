package com.ku.pierdoon.platform

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Shell32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/** The bits of Windows the app reads: known folders, the accent colour, light or dark apps. */
object WinShell {
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun knownFolder(guid: String): String? = if (!isWindows) null else runCatching {
        Shell32Util.getKnownFolderPath(Guid.GUID.fromString(guid))
    }.getOrNull()

    /** Windows' accent colour as ARGB, or null (not Windows, or never set). */
    fun accentColor(): Int? = if (!isWindows) null else runCatching {
        // Stored as ABGR.
        val abgr = Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, """Software\Microsoft\Windows\DWM""", "AccentColor")
        val r = abgr and 0xFF
        val g = (abgr shr 8) and 0xFF
        val b = (abgr shr 16) and 0xFF
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }.getOrNull()

    /** Whether apps are set to dark ("Choose your mode" in Personalisation). */
    fun appsUseDarkTheme(): Boolean? = if (!isWindows) null else runCatching {
        Advapi32Util.registryGetIntValue(
            WinReg.HKEY_CURRENT_USER,
            """Software\Microsoft\Windows\CurrentVersion\Themes\Personalize""",
            "AppsUseLightTheme",
        ) == 0
    }.getOrNull()

    /** Opens Explorer with [file] selected. */
    fun reveal(file: File) {
        runCatching {
            if (isWindows) ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
            else java.awt.Desktop.getDesktop().open(file.parentFile)
        }
    }
}
