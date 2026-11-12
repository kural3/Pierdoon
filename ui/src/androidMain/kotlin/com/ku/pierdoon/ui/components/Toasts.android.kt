package com.ku.pierdoon.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberToast(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() } }
}
