package com.ku.pierdoon.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberFontFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current.applicationContext
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        callback(PickedFile(displayName(context, uri) ?: "font.ttf") { context.contentResolver.openInputStream(uri) })
    }
    // File managers label fonts every which way (font/ttf, application/x-font-ttf, octet-stream),
    // so any file is offered and checked once picked.
    return { launcher.launch(arrayOf("*/*")) }
}

private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
