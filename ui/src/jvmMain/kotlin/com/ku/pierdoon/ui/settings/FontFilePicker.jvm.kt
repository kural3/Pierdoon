package com.ku.pierdoon.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.ku.pierdoon.ui.desktop.Files
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.font_custom_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun rememberFontFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val title = stringResource(Res.string.font_custom_title)
    val callback by rememberUpdatedState(onPicked)
    return { Files.pickFont(title)?.let { file -> callback(PickedFile(file.name) { file.inputStream() }) } }
}
