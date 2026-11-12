package com.ku.pierdoon.ui.components

import androidx.compose.runtime.Composable
import com.ku.pierdoon.ui.desktop.Files

@Composable
actual fun rememberClipboardText(): () -> String? = Files::clipboardText
