package com.ku.pierdoon.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import androidx.graphics.shapes.Morph
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.morphClip
import com.ku.pierdoon.ui.theme.SchemeGenerator

/** Hand-picked seeds that each produce a balanced scheme. */
val PresetSeeds: List<Int> = listOf(
    0xFF6750A4, // violet
    0xFF0061A4, // blue
    0xFF006A6A, // teal
    0xFF386A20, // green
    0xFF7C5800, // amber
    0xFF9C4500, // orange
    0xFFB3261E, // red
    0xFF984061, // rose
).map { it.toInt() }

private val SwatchMorph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

@Composable
fun SwatchRow(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        items(PresetSeeds) { seed -> Swatch(seed, seed == selected) { onSelect(seed) } }
    }
}

/** Seed swatch shows its scheme's primary; the selected one morphs into a cookie. */
@Composable
private fun Swatch(seed: Int, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val dark = MaterialTheme.colorScheme.surface.luminanceApprox() < 0.5f
    val scheme = remember(seed, dark) { SchemeGenerator.scheme(seed, dark) }
    val morph = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "swatch",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .morphClip(SwatchMorph, morph)
            .background(scheme.primaryContainer)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(50))
                .background(scheme.primary),
        ) {
            if (selected) {
                Icon(PlayerIcons.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun Color.luminanceApprox() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private val Rainbow = List(13) { i -> Color(SchemeGenerator.seedFromHue(i * 30f)) }

/** Continuous hue picker; ticks the haptic motor every 15° like detents. */
@Composable
fun HuePicker(seed: Int, onSeed: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val hue = remember(seed) { SchemeGenerator.hueOf(seed) }
    val thumbColor = Color(seed)
    val ring = MaterialTheme.colorScheme.surface

    fun pick(x: Float, width: Int): Float = (x / width).coerceIn(0f, 1f) * 360f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSeed(SchemeGenerator.seedFromHue(pick(o.x, size.width)))
                }
            }
            .pointerInput(Unit) {
                var lastDetent = -1
                detectHorizontalDragGestures(
                    onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                    onDragEnd = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                ) { change, _ ->
                    change.consume()
                    val h = pick(change.position.x, size.width)
                    val detent = (h / 15f).toInt()
                    if (detent != lastDetent) {
                        lastDetent = detent
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    onSeed(SchemeGenerator.seedFromHue(h))
                }
            },
    ) {
        val trackH = 16.dp.toPx()
        val top = (size.height - trackH) / 2
        drawRoundRect(
            brush = Brush.horizontalGradient(Rainbow),
            topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2),
        )
        val cx = (hue / 360f) * size.width
        val r = size.height / 2 - 2.dp.toPx()
        drawCircle(thumbColor, radius = r, center = Offset(cx.coerceIn(r, size.width - r), size.height / 2))
        drawCircle(ring, radius = r, center = Offset(cx.coerceIn(r, size.width - r), size.height / 2), style = Stroke(3.dp.toPx()))
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
