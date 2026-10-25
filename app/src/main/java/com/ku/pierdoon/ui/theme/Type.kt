package com.ku.pierdoon.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ku.pierdoon.R
import com.ku.pierdoon.data.AppFont

// Material defaults already include the expressive "Emphasized" styles.
val Typography = Typography()

@OptIn(ExperimentalTextApi::class)
private val JetBrainsMono: FontFamily by lazy {
    // One variable file covers every weight; before Android 8 it shows as regular.
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Font(R.font.jetbrains_mono, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
            } else {
                Font(R.font.jetbrains_mono, w)
            }
        }
    )
}

private val IbmPlexMono: FontFamily by lazy {
    FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_mono_semibold, FontWeight.Medium),
        Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
        Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
    )
}

private fun AppFont.family(): FontFamily? = when (this) {
    AppFont.System -> null
    AppFont.SystemMono -> FontFamily.Monospace
    AppFont.JetBrainsMono -> JetBrainsMono
    AppFont.IbmPlexMono -> IbmPlexMono
}

private val typographies = mutableMapOf<AppFont, Typography>()

/** [Typography] with every style set in [font]. Built once per font. */
fun typographyFor(font: AppFont): Typography = typographies.getOrPut(font) {
    val family = font.family() ?: return@getOrPut Typography
    fun TextStyle.f() = copy(fontFamily = family)
    with(Typography) {
        Typography(
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
}
