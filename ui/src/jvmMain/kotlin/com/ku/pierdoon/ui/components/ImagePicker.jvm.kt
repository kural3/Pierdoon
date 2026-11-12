package com.ku.pierdoon.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.ku.pierdoon.ui.desktop.Files
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.choose_image
import org.jetbrains.compose.resources.stringResource
import java.io.File

actual class PickedImage(val file: File)

@Composable
actual fun rememberImagePicker(onPicked: (PickedImage) -> Unit): () -> Unit {
    val title = stringResource(Res.string.choose_image)
    val callback by rememberUpdatedState(onPicked)
    return { Files.pickImage(title)?.let { callback(PickedImage(it)) } }
}
