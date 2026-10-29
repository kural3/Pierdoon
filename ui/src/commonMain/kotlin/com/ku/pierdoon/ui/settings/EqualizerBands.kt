package com.ku.pierdoon.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The equalizer's bands side by side: a vertical slider each, the gain above it and the
 * frequency below. Bars grow from the middle line (0 dB) up for a boost and down for a cut.
 * Dragging or tapping a band sets it in half-decibel steps; [onChange] gets band and gain.
 */
@Composable
fun EqualizerBands(
    frequencies: List<Int>,
    gains: List<Float>,
    maxDb: Float,
    enabled: Boolean,
    onChange: (band: Int, db: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        frequencies.forEachIndexed { band, freq ->
            BandSlider(
                freq = freq,
                db = gains.getOrElse(band) { 0f },
                maxDb = maxDb,
                enabled = enabled,
                onChange = { onChange(band, it) },
            )
        }
    }
}

@Composable
private fun BandSlider(freq: Int, db: Float, maxDb: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(db)
    val shown by animateFloatAsState(db, MaterialTheme.motionScheme.fastSpatialSpec(), label = "eqBand")
    val alpha = if (enabled) 1f else 0.38f

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(30.dp)) {
        Text(
            text = (if (db > 0) "+" else "") + formatDb(db),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = alpha),
            maxLines = 1,
        )
        Canvas(
            Modifier
                .width(30.dp)
                .height(176.dp)
                .pointerInput(enabled, maxDb) {
                    if (!enabled) return@pointerInput
                    fun dbAt(y: Float) = snap((0.5f - y / size.height) * 2 * maxDb, maxDb)
                    detectTapGestures { onChange(dbAt(it.y)) }
                }
                .pointerInput(enabled, maxDb) {
                    if (!enabled) return@pointerInput
                    fun dbAt(y: Float) = snap((0.5f - y / size.height) * 2 * maxDb, maxDb)
                    detectVerticalDragGestures(
                        onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                        onDragEnd = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                    ) { change, _ ->
                        change.consume()
                        val next = dbAt(change.position.y)
                        if (next != current) {
                            // A notch at the middle, where the band leaves the sound alone.
                            if (next == 0f) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onChange(next)
                        }
                    }
                },
        ) {
            val track = 6.dp.toPx()
            val x = (size.width - track) / 2
            val mid = size.height / 2
            val y = mid - shown / maxDb * mid
            val corner = CornerRadius(track / 2)
            drawRoundRect(colors.surfaceContainerHighest.copy(alpha = alpha), Offset(x, 0f), Size(track, size.height), corner)
            drawLine(colors.outline.copy(alpha = alpha), Offset(x - 4.dp.toPx(), mid), Offset(x + track + 4.dp.toPx(), mid), 1.dp.toPx())
            drawRoundRect(colors.primary.copy(alpha = alpha), Offset(x, minOf(y, mid)), Size(track, abs(y - mid)), corner)
            drawCircle(colors.primary.copy(alpha = alpha), radius = 9.dp.toPx(), center = Offset(size.width / 2, y.coerceIn(9.dp.toPx(), size.height - 9.dp.toPx())))
        }
        Text(
            text = if (freq >= 1000) "${freq / 1000}k" else "$freq",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = alpha),
            maxLines = 1,
        )
    }
}

private fun snap(db: Float, maxDb: Float): Float = ((db * 2).roundToInt() / 2f).coerceIn(-maxDb, maxDb)

private fun formatDb(db: Float): String =
    if (db == db.roundToInt().toFloat()) db.roundToInt().toString() else db.toString()
