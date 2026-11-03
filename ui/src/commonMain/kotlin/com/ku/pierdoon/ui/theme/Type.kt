package com.ku.pierdoon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ku.pierdoon.data.AppFont
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.ibm_plex_mono_bold
import com.ku.pierdoon.ui.resources.ibm_plex_mono_regular
import com.ku.pierdoon.ui.resources.ibm_plex_mono_semibold
import com.ku.pierdoon.ui.resources.jetbrains_mono
import org.jetbrains.compose.resources.Font

// Material defaults already include the expressive "Emphasized" styles.
val Typography = Typography()

/** A font file from disk, every weight from it; null if it can't be read as a font. */
internal expect fun fontFamilyFromFile(path: String): FontFamily?

/** One variable file covers every weight (shown as regular where variable fonts aren't supported). */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun jetBrainsMono(): FontFamily {
    val fonts = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(Res.font.jetbrains_mono, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    }
    return remember(fonts) { FontFamily(fonts) }
}

@Composable
private fun ibmPlexMono(): FontFamily {
    val fonts = listOf(
        Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(Res.font.ibm_plex_mono_semibold, FontWeight.Medium),
        Font(Res.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
        Font(Res.font.ibm_plex_mono_bold, FontWeight.Bold),
    )
    return remember(fonts) { FontFamily(fonts) }
}

/** The family [font] stands for; null for the platform's own (Roboto or the phone maker's, Segoe UI). */
@Composable
fun fontFamilyFor(font: AppFont, customFontPath: String?): FontFamily? = when (font) {
    AppFont.System -> null
    AppFont.SystemMono -> FontFamily.Monospace
    AppFont.JetBrainsMono -> jetBrainsMono()
    AppFont.IbmPlexMono -> ibmPlexMono()
    // A font file that went missing or broke falls back to the system one.
    AppFont.Custom -> customFontPath?.let { path -> remember(path) { fontFamilyFromFile(path) } }
}

/** [Typography] with every style set in [font]; [customFontPath] is the user's own font file, if any. */
@Composable
fun typographyFor(font: AppFont, customFontPath: String?): Typography {
    val family = fontFamilyFor(font, customFontPath) ?: return Typography
    return remember(family) { Typography.withFamily(family) }
}

private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
        displayLargeEmphasized = displayLargeEmphasized.f(),
        displayMediumEmphasized = displayMediumEmphasized.f(),
        displaySmallEmphasized = displaySmallEmphasized.f(),
        headlineLargeEmphasized = headlineLargeEmphasized.f(),
        headlineMediumEmphasized = headlineMediumEmphasized.f(),
        headlineSmallEmphasized = headlineSmallEmphasized.f(),
        titleLargeEmphasized = titleLargeEmphasized.f(),
        titleMediumEmphasized = titleMediumEmphasized.f(),
        titleSmallEmphasized = titleSmallEmphasized.f(),
        bodyLargeEmphasized = bodyLargeEmphasized.f(),
        bodyMediumEmphasized = bodyMediumEmphasized.f(),
        bodySmallEmphasized = bodySmallEmphasized.f(),
        labelLargeEmphasized = labelLargeEmphasized.f(),
        labelMediumEmphasized = labelMediumEmphasized.f(),
        labelSmallEmphasized = labelSmallEmphasized.f(),
    )
}
