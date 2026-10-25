package com.ku.pierdoon.ui.theme

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ku.pierdoon.data.AppSettings
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.ThemeMode
import com.ku.pierdoon.ui.components.HighFrameRate
import kotlinx.coroutines.flow.MutableStateFlow

/** UI switches that components read without threading them through every call. */
@Immutable
data class UiPrefs(
    val glass: Boolean = true,
    val wavyProgress: Boolean = true,
    val lyricsSyncControls: Boolean = false,
    /** Nothing style: dot-matrix headings, dotted progress, flat player. */
    val nothing: Boolean = false,
)

val LocalUiPrefs = staticCompositionLocalOf { UiPrefs() }

/** Seed extracted from the current track's artwork; written by the app, read by the theme. */
object AlbumThemeSeed {
    val seed = MutableStateFlow<Int?>(null)
}

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

private const val SCHEME_FADE_MS = 650

@Composable
fun AppSettings.isDarkTheme(): Boolean = when (theme) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun PierdoonTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit
) {
    val darkTheme = settings.isDarkTheme()
    val context = LocalContext.current
    val albumSeed by AlbumThemeSeed.seed.collectAsStateWithLifecycle()
    val wallpaperAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Generated schemes are cheap (a few dozen colour conversions) and only rebuilt on change.
    val target = remember(settings.colorSource, settings.seedColor, albumSeed, darkTheme, settings.pureBlack, settings.nothingStyle) {
        val base = when {
            settings.nothingStyle -> return@remember if (darkTheme) NothingDark else NothingLight
            settings.colorSource == ColorSource.Wallpaper && wallpaperAvailable ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            settings.colorSource == ColorSource.AlbumArt ->
                SchemeGenerator.scheme(albumSeed ?: settings.seedColor, darkTheme)
            else -> SchemeGenerator.scheme(settings.seedColor, darkTheme)
        }
        if (darkTheme && settings.pureBlack) base.pureBlack() else base
    }
    val colorScheme = animateColorScheme(target)
    val haptics = if (settings.haptics) LocalHapticFeedback.current else NoHaptics
    HighFrameRate.enabled = settings.smoothAnimations

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = typographyFor(settings.font),
    ) {
        CompositionLocalProvider(
            LocalUiPrefs provides UiPrefs(
                glass = settings.glass && !settings.nothingStyle,
                wavyProgress = settings.wavyProgress,
                lyricsSyncControls = settings.lyricsSyncControls,
                nothing = settings.nothingStyle,
            ),
            LocalHapticFeedback provides haptics,
            // Text outside a Surface (or on a transparent one) would otherwise default to black.
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            content = content,
        )
    }
}

/**
 * Cross-fades between palettes with a single animated fraction instead of one animation per
 * colour. Runs once per change (album switch, theme toggle), idle otherwise.
 */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = lerpScheme(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(SCHEME_FADE_MS))
    }
    return lerpScheme(from, to, progress.value)
}

/** True-black backgrounds with containers stepped up just enough to stay distinguishable. */
private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF121215),
    surfaceContainerHigh = Color(0xFF1A1A1E),
    surfaceContainerHighest = Color(0xFF232328),
)

private val NothingRed = Color(0xFFD71921)

/** Black, white and greys; red marks what's active (progress, switches, the playing track). */
private val NothingDark = darkColorScheme(
    primary = NothingRed,
    onPrimary = Color.White,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    inversePrimary = NothingRed,
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color.White,
    tertiary = NothingRed,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3A0B0D),
    onTertiaryContainer = Color(0xFFFFB4AE),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFF9A9A9A),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF111111),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF262626),
    surfaceBright = Color(0xFF262626),
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF242424),
)

private val NothingLight = lightColorScheme(
    primary = NothingRed,
    onPrimary = Color.White,
    primaryContainer = Color.Black,
    onPrimaryContainer = Color.White,
    inversePrimary = Color(0xFFFF6B63),
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9D9D9),
    onSecondaryContainer = Color.Black,
    tertiary = NothingRed,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD6),
    onTertiaryContainer = Color(0xFF410003),
    background = Color(0xFFF5F5F5),
    onBackground = Color.Black,
    surface = Color(0xFFF5F5F5),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF5E5E5E),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color(0xFFF2F2F2),
    outline = Color(0xFF9E9E9E),
    outlineVariant = Color(0xFFD0D0D0),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE0E0E0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFEFEF),
    surfaceContainer = Color(0xFFE9E9E9),
    surfaceContainerHigh = Color(0xFFE2E2E2),
    surfaceContainerHighest = Color(0xFFDADADA),
)
