package com.ku.pierdoon.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How far the open player shrinks under a held predictive back gesture. */
private const val BACK_PREVIEW = 0.25f

/** Fling faster than this (px/s) and the sheet follows the fling direction regardless of position. */
private const val FLING_VELOCITY = 1200f

/** Slight bounce when the sheet lands, like a physical card settling. */
private val SheetSpring = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/**
 * State of the expanding player: [progress] 0 = mini player, 1 = full screen.
 * Every visual is derived from it in the layer phase, so dragging never relayouts.
 */
@Stable
class ExpandingPlayerState(private val scope: CoroutineScope) {
    val progress = Animatable(0f)

    /** Distance the finger must travel for a full expand: mini player top → screen top. */
    private var rangePx = 1f

    /** Mini player bounds in root coordinates; the sheet grows out of this rectangle. */
    var miniBounds by mutableStateOf(Rect.Zero)
        private set

    /** Called from layout (not draw) whenever the mini player moves or resizes. */
    fun onMiniBounds(bounds: Rect) {
        miniBounds = bounds
        if (bounds.top > 0f) rangePx = bounds.top
    }
    internal var dragging by mutableStateOf(false)

    /** Velocity handed from a fling to the next settle animation. */
    private var pendingVelocity = 0f

    val isActive: Boolean get() = progress.value > 0f || progress.isRunning || dragging
    val isMoving: Boolean get() = progress.isRunning || dragging

    internal fun dragBy(deltaPx: Float) {
        scope.launch { progress.snapTo((progress.value - deltaPx / rangePx).coerceIn(0f, 1f)) }
    }

    /** @return the target the sheet should settle at after a release with [velocityPx]. */
    internal fun release(velocityPx: Float): Boolean {
        val expand = when {
            velocityPx < -FLING_VELOCITY -> true
            velocityPx > FLING_VELOCITY -> false
            else -> progress.value > 0.5f
        }
        pendingVelocity = -velocityPx / rangePx
        return expand
    }

    /** Predictive back: the open player shrinks a little while the gesture is held. */
    suspend fun seekBack(fraction: Float) {
        progress.snapTo(1f - fraction.coerceIn(0f, 1f) * BACK_PREVIEW)
    }

    /** Back gesture cancelled: spring back to fully open. */
    suspend fun reopen() {
        progress.animateTo(1f, SheetSpring)
    }

    internal suspend fun settle(expanded: Boolean) {
        val v = pendingVelocity
        pendingVelocity = 0f
        progress.animateTo(if (expanded) 1f else 0f, SheetSpring, initialVelocity = v)
    }
}

@Composable
fun rememberExpandingPlayerState(): ExpandingPlayerState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ExpandingPlayerState(scope) }
}

/** Shared vertical drag: used on the mini player (to pull it up) and the full player (to push it down). */
@Composable
fun Modifier.expandingPlayerDrag(
    state: ExpandingPlayerState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dragState = rememberDraggableState { delta -> state.dragBy(delta) }
    return draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStarted = {
            state.dragging = true
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        },
        onDragStopped = { velocity ->
            state.dragging = false
            val target = state.release(velocity)
            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
            if (target != expanded) onExpandedChange(target) else scope.launch { state.settle(target) }
        },
    )
}

/** Drives [state] from the logical [expanded] flag (taps, back gesture, flings). */
@Composable
fun ExpandingPlayerEffect(state: ExpandingPlayerState, expanded: Boolean) {
    LaunchedEffect(expanded) { state.settle(expanded) }
}

/** Rounded rectangle interpolated from the mini player's bounds to the full screen. */
private class SheetShape(
    private val from: Rect,
    private val progress: Float,
    private val startCornerPx: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val start = if (from == Rect.Zero) Rect(0f, size.height, size.width, size.height) else from
        // The settle spring can overshoot past 1 (or below 0); geometry must stay in range.
        val p = progress.coerceIn(0f, 1f)
        val corner = lerp(startCornerPx, 0f, p)
        return Outline.Rounded(
            RoundRect(
                left = lerp(start.left, 0f, p),
                top = lerp(start.top, 0f, p),
                right = lerp(start.right, size.width, p),
                bottom = lerp(start.bottom, size.height, p),
                cornerRadius = CornerRadius(corner),
            )
        )
    }
}

/**
 * The growing sheet. Composed only while open or moving; the full player inside fades in
 * after the first fifth of the gesture and rises slightly, so mid-drag it reads as the mini
 * player physically unfolding.
 */
@Composable
fun ExpandingPlayerSheet(
    state: ExpandingPlayerState,
    content: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val cornerPx = with(density) { 28.dp.toPx() }
    val risePx = with(density) { 96.dp.toPx() }
    val visible by remember(state) { derivedStateOf { state.isActive } }
    if (!visible) return
    val container = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                shape = SheetShape(state.miniBounds, state.progress.value, cornerPx)
                clip = true
            }
            .background(container)
    ) {
        content(
            Modifier.graphicsLayer {
                val p = state.progress.value
                alpha = ((p - 0.2f) / 0.6f).coerceIn(0f, 1f)
                translationY = (1f - p) * risePx
            }
        )
    }
}
