package com.ku.pierdoon.ui.components

import androidx.compose.runtime.Composable
import com.ku.pierdoon.ui.desktop.BackHandler

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
