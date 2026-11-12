package com.ku.pierdoon.ui.components

import androidx.compose.runtime.Composable

/** Reads the text on the system clipboard, if there is any. */
@Composable
expect fun rememberClipboardText(): () -> String?
