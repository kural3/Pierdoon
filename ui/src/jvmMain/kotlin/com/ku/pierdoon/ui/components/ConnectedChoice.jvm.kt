package com.ku.pierdoon.ui.components

import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal actual fun toggleColors(containerColor: Color): ToggleButtonColors =
    ToggleButtonDefaults.toggleButtonColors(containerColor = containerColor)
