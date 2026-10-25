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
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.resources.ibm_plex_mono_bold
import com.ku.pierdoon.resources.ibm_plex_mono_regular
import com.ku.pierdoon.resources.ibm_plex_mono_semibold
import com.ku.pierdoon.resources.jetbrains_mono
import org.jetbrains.compose.resources.Font

// Material defaults already include the expressive "Emphasized" styles.
val Typography = Typography()

/** One variable file covers every weight. */
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

@Composable
private fun AppFont.family(): FontFamily? = when (this) {
    // The platform default: Segoe UI on Windows.
    AppFont.System -> null
    AppFont.SystemMono -> FontFamily.Monospace
    AppFont.JetBrainsMono -> jetBrainsMono()
    AppFont.IbmPlexMono -> ibmPlexMono()
}

/** [Typography] with every style set in [font]. */
@Composable
fun typographyFor(font: AppFont): Typography {
    val family = font.family() ?: return Typography
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
