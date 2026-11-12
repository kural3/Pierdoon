package com.ku.pierdoon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.ku.pierdoon.data.AppSettings
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.ThemeMode
import com.ku.pierdoon.platform.WinShell
import com.ku.pierdoon.ui.components.DesktopArtwork
import kotlinx.coroutines.flow.MutableStateFlow

/** Windows' accent colour, read at start and whenever the window comes back to the front. */
object SystemAccent {
    val seed = MutableStateFlow(WinShell.accentColor())
    val dark = MutableStateFlow(WinShell.appsUseDarkTheme())

    fun refresh() {
        seed.value = WinShell.accentColor()
        dark.value = WinShell.appsUseDarkTheme()
    }
}

@Composable
fun AppSettings.isDarkTheme(): Boolean = when (theme) {
    ThemeMode.System -> SystemAccent.dark.collectAsState().value ?: isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** [AppTheme] on Windows: its dark mode, its accent colour for "wallpaper", no haptics. */
@Composable
fun PierdoonTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit
) {
    val darkTheme = settings.isDarkTheme()
    val accent by SystemAccent.seed.collectAsState()
    val wallpaper = remember(accent, settings.seedColor, darkTheme) {
        SchemeGenerator.scheme(accent ?: settings.seedColor, darkTheme)
    }
    AppTheme(
        settings = settings,
        darkTheme = darkTheme,
        wallpaperScheme = wallpaper.takeIf { settings.colorSource == ColorSource.Wallpaper },
        fontPath = settings.customFontPath,
        haptics = null,
        artwork = DesktopArtwork,
        content = content,
    )
}
