package com.ku.pierdoon.ui.components

import androidx.compose.runtime.Composable

/** System back while [enabled]: the gesture on the phone; Esc, Alt+Left or the mouse's back button on the computer. */
@Composable
expect fun SystemBackHandler(enabled: Boolean = true, onBack: () -> Unit)
