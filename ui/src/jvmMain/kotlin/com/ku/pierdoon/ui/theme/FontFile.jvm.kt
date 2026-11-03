package com.ku.pierdoon.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import java.io.File

internal actual fun fontFamilyFromFile(path: String): FontFamily? {
    val file = File(path)
    if (!file.isFile) return null
    // Skia only finds out a file isn't a font when drawing with it; asked here, it falls back cleanly.
    val ok = runCatching { FontMgr.default.makeFromData(Data.makeFromBytes(file.readBytes())) != null }.getOrDefault(false)
    return if (ok) FontFamily(Font(file)) else null
}
