package com.ku.pierdoon.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File

// CoverStore itself is shared with the computer (ui module); this is where the phone keeps it.

/** Opens the store in files/covers. */
fun CoverStore.init(context: Context) = init(File(context.applicationContext.filesDir, "covers"))

/** The track's own cover as a file:// Uri, for Media3 and the system's media controls. */
fun CoverStore.songUri(songId: Long): Uri? = songFile(songId)?.toUri()
