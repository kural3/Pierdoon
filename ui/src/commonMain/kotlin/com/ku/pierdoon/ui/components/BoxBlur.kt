package com.ku.pierdoon.ui.components

/** Separable box blur, in place. Three passes approximate a gaussian. */
fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int) {
    val tmp = IntArray(px.size)
    blurPass(px, tmp, w, h, radius, horizontal = true)
    blurPass(tmp, px, w, h, radius, horizontal = false)
}

private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val n = 2 * r + 1
    for (line in 0 until lines) {
        for (i in 0 until len) {
            var a = 0
            var red = 0
            var g = 0
            var b = 0
            for (k in -r..r) {
                val j = (i + k).coerceIn(0, len - 1)
                val c = if (horizontal) src[line * w + j] else src[j * w + line]
                a += c ushr 24
                red += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            val idx = if (horizontal) line * w + i else i * w + line
            dst[idx] = ((a / n) shl 24) or ((red / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
    }
}
