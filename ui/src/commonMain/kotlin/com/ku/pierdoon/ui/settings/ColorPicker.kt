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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

/**
 * Hand-picked seeds that each produce a balanced scheme: first the quiet ones (graphite, with no
 * hue at all, then slate, sage and sand, held back in colour), then the classic ones, and last
 * the loud ones, as strong as a screen can show.
 */
val PresetSeeds: List<Int> = listOf(
    SchemeGenerator.MONOCHROME_SEED, // graphite
    0xFF56677D.toInt(), // slate
    0xFF66785A.toInt(), // sage
    0xFF8A7656.toInt(), // sand
    0xFF6750A4.toInt(), // violet
    0xFF0061A4.toInt(), // blue
    0xFF006A6A.toInt(), // teal
    0xFF386A20.toInt(), // green
    0xFF7C5800.toInt(), // amber
    0xFFB4532A.toInt(), // coral
    0xFFB3261E.toInt(), // red
    0xFF984061.toInt(), // rose
    0xFF8E4585.toInt(), // plum
    SchemeGenerator.seed(125f, 200f), // lime
    SchemeGenerator.seed(215f, 200f), // aqua
    SchemeGenerator.seed(295f, 200f), // electric blue
    SchemeGenerator.seed(335f, 200f), // magenta
)

private val SwatchMorph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

@Composable
fun SwatchRow(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    // Wraps instead of scrolling: on a narrow window a sideways strip hid the last ones.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        PresetSeeds.forEach { seed -> Swatch(seed, seed == selected) { onSelect(seed) } }
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

/**
 * Picks a theme colour by its hue; keeps the strength [chroma] the user set with [ChromaPicker],
 * so sliding through hues doesn't wash it out. Ticks the haptic motor every 15° like detents.
 */
@Composable
fun HuePicker(seed: Int, chroma: Float, onSeed: (Int) -> Unit, modifier: Modifier = Modifier) {
    val hue = remember(seed) { SchemeGenerator.hueOf(seed) }
    val rainbow = remember(chroma) { List(13) { i -> Color(SchemeGenerator.seed(i * 30f, chroma)) } }
    GradientSlider(
        fraction = hue / 360f,
        track = rainbow,
        thumb = Color(seed),
        detents = 24,
        onChange = { f -> onSeed(SchemeGenerator.seed(f * 360f, chroma)) },
        modifier = modifier,
    )
}

/** How strong the theme colour is: from quiet, near grey, to as vivid as the screen can show. */
@Composable
fun ChromaPicker(seed: Int, chroma: Float, onChroma: (Float) -> Unit, modifier: Modifier = Modifier) {
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
) {
    val haptics = LocalHapticFeedback.current
    val ring = MaterialTheme.colorScheme.surface
    val change by rememberUpdatedState(onChange)

    fun pick(x: Float, width: Int): Float = (x / width).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    change(pick(o.x, size.width))
                }
            }
            .pointerInput(Unit) {
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
