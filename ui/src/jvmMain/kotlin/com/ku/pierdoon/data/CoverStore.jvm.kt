package com.ku.pierdoon.data

import com.ku.pierdoon.platform.Images
import java.io.File

/** Decodes [source] scaled to about [maxPx] and saves it as JPEG. False if it isn't an image. */
actual fun writeScaledJpeg(source: File, target: File, maxPx: Int): Boolean = runCatching {
    val image = Images.decode(source.readBytes()) ?: return false
    val bytes = Images.scaledJpeg(image, maxPx) ?: return false
    val tmp = File(target.parentFile, target.name + ".tmp")
    tmp.writeBytes(bytes)
    tmp.renameTo(target)
}.getOrDefault(false)
