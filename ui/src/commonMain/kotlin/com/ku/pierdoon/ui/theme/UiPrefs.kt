package com.ku.pierdoon.ui.theme

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.ProgressStyle
import androidx.compose.runtime.staticCompositionLocalOf

/** UI switches that components read without threading them through every call. */
@Immutable
data class UiPrefs(
    val glass: Boolean = true,
    val progressStyle: ProgressStyle = ProgressStyle.Wavy,
    val lyricsSyncControls: Boolean = false,
    /** Nothing style: dot-matrix headings, dotted progress, flat player. */
    val nothing: Boolean = false,
    /** Covers lean towards a press and spring back, shapes turn while held ([com.ku.pierdoon.ui.components.lively]); off only where nothing should move. */
    val lively: Boolean = true,
)

val LocalUiPrefs = staticCompositionLocalOf { UiPrefs() }
