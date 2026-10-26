package com.ku.pierdoon.ui.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs

/** How far the content has been pulled sideways, shared by the gesture and what moves with it. */
@Stable
class TrackSwipeState {
    internal var offset by mutableFloatStateOf(0f)
    internal var width by mutableIntStateOf(0)

    internal suspend fun animateTo(target: Float, spec: AnimationSpec<Float>, velocity: Float) {
        animate(offset, target, velocity, spec) { value, _ -> offset = value }
    }
}

@Composable
fun rememberTrackSwipeState() = remember { TrackSwipeState() }

/**
 * Swipe sideways to change track: left for the next one, right for the previous one. The content
 * given [trackSwipeOffset] follows the finger; past a third of the width, or flung, it slides out
 * and the new track slides in from the other side. Taps underneath still work: the drag only
 * takes over once the finger has moved sideways.
 */
@Composable
fun Modifier.trackSwipe(state: TrackSwipeState, onNext: () -> Unit, onPrevious: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val dragState = rememberDraggableState { delta -> state.offset += delta }
    return this
        .onSizeChanged { state.width = it.width }
        .draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
            onDragStopped = { velocity ->
                val width = state.width.toFloat().coerceAtLeast(1f)
                val pulled = state.offset
                val flung = abs(velocity) > FLING_VELOCITY && velocity * pulled > 0
                if (abs(pulled) < width * THRESHOLD && !flung) {
                    state.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f), velocity)
                    return@draggable
                }
                // Pulled left reads as "show me what's next", like turning a page.
                val toNext = (pulled < 0) != rtl
                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                val out = if (pulled < 0) -width else width
                state.animateTo(out, tween(OUT_MS), velocity)
                if (toNext) next() else previous()
                state.offset = -out * 0.6f
                state.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 400f), 0f)
            },
        )
}

/** Moves with [trackSwipe]: follows the finger and fades towards the edge. */
fun Modifier.trackSwipeOffset(state: TrackSwipeState): Modifier = graphicsLayer {
    val x = state.offset
    translationX = x
    val width = state.width.toFloat()
    alpha = if (width > 0f) (1f - abs(x) / width).coerceIn(0f, 1f) else 1f
}

private const val THRESHOLD = 0.33f
private const val FLING_VELOCITY = 1200f
private const val OUT_MS = 140
