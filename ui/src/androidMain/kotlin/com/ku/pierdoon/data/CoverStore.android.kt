package com.ku.pierdoon.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Decodes [source] downsampled to about [maxPx] and saves it as JPEG. False if it isn't an image. */
actual fun writeScaledJpeg(source: File, target: File, maxPx: Int): Boolean = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return false
    val scale = maxPx.toFloat() / maxOf(decoded.width, decoded.height)
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    } else {
        decoded
    }
    val tmp = File(target.parentFile, target.name + ".tmp")
    tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    tmp.renameTo(target)
}.getOrDefault(false)
