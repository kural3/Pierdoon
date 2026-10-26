package com.ku.pierdoon.ui.components

import androidx.compose.ui.Modifier

/**
 * Asks for the display's top refresh rate while the next layer animates. Android votes for it
 * (FrameRate.kt in androidMain); on Windows the window already runs at the monitor's rate.
 */
expect fun Modifier.highFrameRate(): Modifier
