package com.ku.pierdoon.ui.settings

import androidx.compose.runtime.Composable
import com.ku.pierdoon.ui.desktop.formatFileSize

@Composable
internal actual fun fileSize(bytes: Long): String = formatFileSize(bytes)
