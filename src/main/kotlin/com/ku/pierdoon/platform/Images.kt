package com.ku.pierdoon.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/** Picture decoding and scaling through Skia, which Compose already ships. */
object Images {

    fun decode(bytes: ByteArray): Image? = runCatching { Image.makeFromEncoded(bytes) }.getOrNull()

    /** [image] scaled so its longer side is at most [maxPx] (never enlarged). */
    fun scaled(image: Image, maxPx: Int): Image {
        val scale = maxPx.toFloat() / maxOf(image.width, image.height)
        if (scale >= 1f) return image
        return resized(image, (image.width * scale).toInt().coerceAtLeast(1), (image.height * scale).toInt().coerceAtLeast(1))
    }

    /** The centre square of [image], at most [maxPx] on a side: video thumbnails as covers. */
    fun centreSquare(image: Image, maxPx: Int): Image {
        val side = minOf(image.width, image.height)
        val out = minOf(side, maxPx)
        val surface = Surface.makeRasterN32Premul(out, out)
        val src = Rect.makeXYWH((image.width - side) / 2f, (image.height - side) / 2f, side.toFloat(), side.toFloat())
        surface.canvas.drawImageRect(image, src, Rect.makeWH(out.toFloat(), out.toFloat()), SAMPLING, null, true)
        return surface.makeImageSnapshot()
    }

    fun resized(image: Image, w: Int, h: Int): Image {
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            Rect.makeWH(w.toFloat(), h.toFloat()),
            SAMPLING, null, true,
        )
        return surface.makeImageSnapshot()
    }

    fun jpeg(image: Image, quality: Int = 90): ByteArray? = image.encodeToData(EncodedImageFormat.JPEG, quality)?.bytes

    fun toBitmap(image: Image): ImageBitmap = image.toComposeImageBitmap()

    /** Pixels as ARGB ints, row by row. */
    fun pixels(image: Image): IntArray {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(image.width, image.height, ColorAlphaType.UNPREMUL))
        image.readPixels(bitmap)
        val bytes = bitmap.readPixels() ?: return IntArray(0)
        // N32 on little-endian machines is BGRA in memory.
        return IntArray(image.width * image.height) { i ->
            val o = i * 4
            val b = bytes[o].toInt() and 0xFF
            val g = bytes[o + 1].toInt() and 0xFF
            val r = bytes[o + 2].toInt() and 0xFF
            val a = bytes[o + 3].toInt() and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    fun fromPixels(pixels: IntArray, w: Int, h: Int): Image {
        val bytes = ByteArray(w * h * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            val o = i * 4
            bytes[o] = (p and 0xFF).toByte()
            bytes[o + 1] = ((p shr 8) and 0xFF).toByte()
            bytes[o + 2] = ((p shr 16) and 0xFF).toByte()
            bytes[o + 3] = ((p ushr 24) and 0xFF).toByte()
        }
        return Image.makeRaster(ImageInfo.makeN32(w, h, ColorAlphaType.UNPREMUL), bytes, w * 4)
    }

    private val SAMPLING = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
}
