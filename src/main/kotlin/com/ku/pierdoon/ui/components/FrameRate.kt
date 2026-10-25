package com.ku.pierdoon.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android votes for the display's top refresh rate while animating. A desktop window is drawn
 * at the monitor's rate anyway, so these stay as no-ops that keep the shared UI code unchanged.
 */
object HighFrameRate {
    @Volatile
    var enabled: Boolean = true

    @Volatile
    var suppressed: Boolean = false
}

fun Modifier.highFrameRate(): Modifier = this

@Composable
fun FrameRateBoost(active: Boolean) = Unit
