package com.ku.pierdoon.ui.components

import android.os.Build

// RenderEffect, which the blur needs, came with Android 12.
actual val GlassSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
