package com.ku.pierdoon.data

import com.ku.pierdoon.platform.AppDirs
import java.io.File

// CoverStore itself is shared with the phone (ui module); this is where the computer keeps it.

/** Opens the store in the app's covers folder. */
fun CoverStore.init() = init(File(AppDirs.files, "covers"))
