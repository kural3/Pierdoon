package com.ku.pierdoon.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

actual class PickedImage(val uri: Uri)

@Composable
actual fun rememberImagePicker(onPicked: (PickedImage) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) callback(PickedImage(uri))
    }
    return { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
