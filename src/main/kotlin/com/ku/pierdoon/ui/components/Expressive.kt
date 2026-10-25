package com.ku.pierdoon.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Release spring: underdamped, so a tap "pops" slightly past rest before settling. */
private val ReleaseSpring = spring<Float>(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)

/**
 * Press progress: 0 at rest, 1 fully pressed, briefly negative on release (overshoot).
 *
 * A quick tap lasts only a few milliseconds, so a plain "pressed ? a : b" animation barely
 * moves. Here a release waits until the squish is mostly visible, then springs back with
 * a bounce — taps feel as physical as long presses.
 */
@Composable
fun rememberPressProgress(interactionSource: InteractionSource): State<Float> {
    val progress = remember { Animatable(0f) }
    val pressSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(interactionSource) {
        var release: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    release?.cancel()
                    launch { progress.animateTo(1f, pressSpec) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    release = launch {
                        withTimeoutOrNull(MIN_SQUISH_TIMEOUT_MS) {
                            snapshotFlow { progress.value }.first { it >= MIN_SQUISH }
                        }
                        progress.animateTo(0f, ReleaseSpring)
                    }
                }
            }
        }
    }
    return progress.asState()
}

private const val MIN_SQUISH = 0.85f
private const val MIN_SQUISH_TIMEOUT_MS = 180L

/** Scale derived from press progress: 1 → [pressedScale], overshooting above 1 on release. */
@Composable
fun animatePressAsState(interactionSource: InteractionSource, pressedScale: Float = 0.9f): State<Float> {
    val press = rememberPressProgress(interactionSource)
    return remember(press, pressedScale) {
        derivedStateOf { 1f - (1f - pressedScale) * press.value }
    }
}

/** Scales in the draw phase only: pressing never triggers recomposition. */
fun Modifier.scaleBy(scale: State<Float>): Modifier = highFrameRate().graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
}

/** A [Shape] that renders [morph] at [progress]; normalized polygons are scaled to bounds. */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val startAngle: Int = 0,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path: Path = morph.toPath(progress = progress, startAngle = startAngle)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/** Clips to a morph whose progress is read in the layer phase: animating it skips recomposition. */
fun Modifier.morphClip(morph: Morph, progress: State<Float>, startAngle: Int = 0): Modifier =
    highFrameRate().graphicsLayer {
        shape = MorphShape(morph, progress.value, startAngle)
        clip = true
    }

/**
 * Clips to [target], morphing from the previous shape whenever it changes. A change mid-way
 * starts from the shape being morphed to, so quick taps never jump.
 */
@Composable
fun Modifier.animatedShapeClip(target: RoundedPolygon): Modifier {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = to
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
    }
    val morph = remember(from, to) { Morph(from, to) }
    return morphClip(morph, progress.asState())
}
