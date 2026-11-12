package com.ku.pierdoon.ui.edit

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.ku.pierdoon.data.Song

actual interface PlatformTrackEditor {
    /** The system's "allow Pier.doon to modify this file?" request; null when it can't be asked. */
    fun fileWriteRequest(song: Song): IntentSender?
}

@Composable
internal actual fun rememberWriteConsent(editor: TrackEditor, onResult: (granted: Boolean) -> Unit): (Song) -> Unit {
    val callback by rememberUpdatedState(onResult)
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        callback(r.resultCode == Activity.RESULT_OK)
    }
    return { song ->
        val request = editor.fileWriteRequest(song)
        if (request != null) consent.launch(IntentSenderRequest.Builder(request).build()) else callback(false)
    }
}
