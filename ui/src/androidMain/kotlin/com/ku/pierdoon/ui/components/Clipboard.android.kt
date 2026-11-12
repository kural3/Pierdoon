package com.ku.pierdoon.ui.components

import android.content.ClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberClipboardText(): () -> String? {
    val context = LocalContext.current
    return remember(context) {
        {
            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
            clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        }
    }
}
