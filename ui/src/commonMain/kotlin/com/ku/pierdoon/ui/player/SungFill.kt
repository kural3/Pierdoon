package com.ku.pierdoon.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.lyrics.Lyrics
import kotlin.math.min

/**
 * Draws the part of [layout] sung so far, [fraction] of it, in [color] over the dim text already
 * drawn: left to right and line after line when it wraps, with a soft edge [feather] wide so the
 * light seems to flow on rather than be cut. LRC times lines, not words, so the fill runs evenly
 * over the line's time; it stands in for following each word.
 */
fun DrawScope.drawSungFill(layout: TextLayoutResult, fraction: Float, color: Color, feather: Dp = 28.dp) {
    if (fraction <= 0f) return
    val l = layout
    val widths = (0 until l.lineCount).map { l.getLineRight(it) - l.getLineLeft(it) }
    var left = fraction * widths.sum()
    val featherPx = feather.toPx()
    for (line in 0 until l.lineCount) {
        if (left <= 0f) break
        val x0 = l.getLineLeft(line)
        val edge = x0 + min(left, widths[line])
        // Tails and accents reach past the line's box: the first line is open above, the last
        // below, and the ones between reach a little into their neighbours.
        val overhang = GLYPH_OVERHANG * (l.getLineBottom(line) - l.getLineTop(line))
        val top = if (line == 0) -size.height else l.getLineTop(line) - overhang
        val bottom = if (line == l.lineCount - 1) size.height * 2 else l.getLineBottom(line) + overhang
        val solid = edge - featherPx / 2
        clipRect(left = x0 - featherPx, top = top, right = solid, bottom = bottom) {
            drawText(l, color = color, shadow = Shadow.None)
        }
        // In slices: a gradient brush on text draws nothing on the desktop.
        val slice = featherPx / FEATHER_SLICES
        for (s in 0 until FEATHER_SLICES) {
            val from = solid + s * slice
            clipRect(left = from, top = top, right = from + slice, bottom = bottom) {
                drawText(l, color = color, alpha = 1f - (s + 0.5f) / FEATHER_SLICES, shadow = Shadow.None)
            }
        }
        left -= widths[line]
    }
}

/** When line [index] of [lyrics] stops being sung: at the next line, but not through a long instrumental after it. */
fun Lyrics.Synced.sungUntil(index: Int): Long {
    val line = lines[index]
    val longest = SING_BASE_MS + SING_PER_CHAR_MS * line.text.length
    return min(lines.getOrNull(index + 1)?.timeMs ?: Long.MAX_VALUE, line.timeMs + longest)
}

private const val FEATHER_SLICES = 8
private const val GLYPH_OVERHANG = 0.2f
private const val SING_BASE_MS = 1_500L
private const val SING_PER_CHAR_MS = 350L
