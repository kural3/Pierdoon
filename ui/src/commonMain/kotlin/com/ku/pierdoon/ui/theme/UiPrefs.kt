package com.ku.pierdoon.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

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
