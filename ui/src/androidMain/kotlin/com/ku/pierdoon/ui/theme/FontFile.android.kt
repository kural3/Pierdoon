package com.ku.pierdoon.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File

internal actual fun fontFamilyFromFile(path: String): FontFamily? {
    val file = File(path)
    if (!file.isFile) return null
    // Android only finds out a file isn't a font when drawing with it; asked here, it falls back cleanly.
    runCatching { Typeface.createFromFile(file) }.getOrNull() ?: return null
    return FontFamily(Font(file))
}
