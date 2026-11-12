package com.ku.pierdoon.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ku.pierdoon.data.AppSettings
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.ThemeMode
import com.ku.pierdoon.ui.components.HighFrameRate
import com.ku.pierdoon.ui.components.PhoneArtwork

@Composable
fun AppSettings.isDarkTheme(): Boolean = when (theme) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** [AppTheme] on Android: the wallpaper's palette from Android 12, haptics unless switched off. */
@Composable
fun PierdoonTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit
) {
    val darkTheme = settings.isDarkTheme()
    val context = LocalContext.current
    val wallpaper =
        if (settings.colorSource == ColorSource.Wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            remember(context, darkTheme) { if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
        } else {
            null
        }
    HighFrameRate.enabled = settings.smoothAnimations
    AppTheme(
        settings = settings,
        darkTheme = darkTheme,
        wallpaperScheme = wallpaper,
        fontPath = settings.customFontPath,
        haptics = if (settings.haptics) LocalHapticFeedback.current else null,
        artwork = PhoneArtwork,
        content = content,
    )
}
