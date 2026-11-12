package com.ku.pierdoon.ui.settings

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun fileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)
