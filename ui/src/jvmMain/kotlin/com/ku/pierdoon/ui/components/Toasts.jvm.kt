package com.ku.pierdoon.ui.components

import androidx.compose.runtime.Composable
import com.ku.pierdoon.ui.desktop.Toast

@Composable
actual fun rememberToast(): (String) -> Unit = { text -> Toast.show(text) }
