package com.ku.pierdoon.ui.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.ku.pierdoon.data.Song

actual interface PlatformTrackEditor

@Composable
internal actual fun rememberWriteConsent(editor: TrackEditor, onResult: (granted: Boolean) -> Unit): (Song) -> Unit {
    val callback by rememberUpdatedState(onResult)
    return { callback(true) }
}
