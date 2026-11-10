package com.ku.pierdoon.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.theme.SchemeGenerator

/** How far, in degrees, a seed may stray from the hue picked and still count as it. */
private const val HUE_SLACK = 3f

/**
 * Picks a theme colour by its hue; keeps the strength [chroma] the user set with [ChromaPicker],
 * so sliding through hues doesn't wash it out. Ticks the haptic motor every 15° like detents.
 */
@Composable
fun HuePicker(seed: Int, chroma: Float, onSeed: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val hue = remember(seed) { SchemeGenerator.hueOf(seed) }
    // Where the thumb was let go. The seed only keeps its hue roughly, and the far end (360) comes
    // back as 0, which would throw the thumb to the start; while the seed is about the hue picked,
    // the thumb stays where the finger put it.
    var picked by remember { mutableFloatStateOf(-1f) }
    val shown = picked.takeIf { it >= 0f && abs(hue - it * 360f).let { d -> minOf(d, 360f - d) } < HUE_SLACK } ?: (hue / 360f)
    val rainbow = remember(chroma) { List(13) { i -> Color(SchemeGenerator.seed(i * 30f, chroma)) } }
    GradientSlider(
        fraction = shown,
        track = rainbow,
        thumb = Color(seed),
        detents = 24,
        onChange = { f -> picked = f; onSeed(SchemeGenerator.seed(f * 360f, chroma)) },
        modifier = modifier,
        enabled = enabled,
    )
}

/** How strong the theme colour is: from quiet, near grey, to as vivid as the screen can show. */
@Composable
fun ChromaPicker(seed: Int, chroma: Float, onChroma: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val hue = remember(seed) { SchemeGenerator.hueOf(seed) }
    val max = remember(hue) { SchemeGenerator.maxChroma(hue).coerceAtLeast(SchemeGenerator.MIN_CHROMA + 1f) }
    val track = remember(hue, max) {
        List(5) { i -> Color(SchemeGenerator.seed(hue, SchemeGenerator.MIN_CHROMA + (max - SchemeGenerator.MIN_CHROMA) * i / 4f)) }
    }
    GradientSlider(
        fraction = ((chroma - SchemeGenerator.MIN_CHROMA) / (max - SchemeGenerator.MIN_CHROMA)).coerceIn(0f, 1f),
        track = track,
        thumb = Color(seed),
        detents = 10,
        onChange = { f -> onChroma(SchemeGenerator.MIN_CHROMA + (max - SchemeGenerator.MIN_CHROMA) * f) },
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun GradientSlider(
    fraction: Float,
    track: List<Color>,
    thumb: Color,
    detents: Int,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Off: drawn as is, but ignores the pointer, so a finger can still scroll the page over it. */
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val ring = MaterialTheme.colorScheme.surface
    val change by rememberUpdatedState(onChange)

    fun pick(x: Float, width: Int): Float = (x / width).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o ->
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    change(pick(o.x, size.width))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var lastDetent = -1
                detectHorizontalDragGestures(
                    onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                    onDragEnd = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                ) { c, _ ->
                    c.consume()
                    val f = pick(c.position.x, size.width)
                    val detent = (f * detents).toInt()
                    if (detent != lastDetent) {
                        lastDetent = detent
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    change(f)
                }
            },
    ) {
        val trackH = 16.dp.toPx()
        val top = (size.height - trackH) / 2
        drawRoundRect(
            brush = Brush.horizontalGradient(track),
            topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2),
        )
        val r = size.height / 2 - 2.dp.toPx()
        val cx = (fraction * size.width).coerceIn(r, size.width - r)
        drawCircle(thumb, radius = r, center = Offset(cx, size.height / 2))
        drawCircle(ring, radius = r, center = Offset(cx, size.height / 2), style = Stroke(3.dp.toPx()))
    }
}

/** Strip of the scheme's key colours so the effect of a choice is visible at a glance. */
@Composable
fun PalettePreview(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        listOf(c.primary, c.primaryContainer, c.secondaryContainer, c.tertiary, c.tertiaryContainer).forEach { color ->
            Box(
                Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color)
            )
        }
    }
}
