package com.ku.pierdoon.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.theme.LocalUiPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Lively motion (Beta). Pressed, the thing leans back at the point under the finger, as if pushed
 * there, sinks by [sink] and springs back when let go. Under a mouse it also leans a little
 * towards the pointer and rises. [tilt] is the most it turns, in degrees; 0 for rows, which only
 * sink.
 *
 * [mouseOnly] is for plain rows: a click makes them sink and spring back, but under a finger
 * they stay still, as a list row should.
 *
 * Does nothing unless the setting is on. It only watches the pointer, never consumes it, so taps,
 * swipes and scrolling underneath work as before; once the finger moves off like a scroll or a
 * swipe, it lets go.
 */
fun Modifier.lively(tilt: Float = 10f, sink: Float = 0.04f, mouseOnly: Boolean = false): Modifier = composed {
    if (!LocalUiPrefs.current.lively) return@composed this
    val rx = remember { Animatable(0f) }
    val ry = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    this
        .graphicsLayer {
            // The same lean and rise move a wide card's edges much further, into its neighbours;
            // past a cover's size the motion shrinks so the edges move about as far as a cover's.
            val k = (LIVELY_REFERENCE.toPx() / maxOf(size.width, size.height, 1f)).coerceAtMost(1f)
            val s = scale.value
            // Graphics layers turn the other way round from what the values below mean.
            rotationX = -rx.value * k
            rotationY = -ry.value * k
            scaleX = if (s > 1f) 1f + (s - 1f) * k else s
            scaleY = scaleX
            cameraDistance = CAMERA_DISTANCE * density
        }
        .pointerInput(tilt, sink, mouseOnly) {
            // Rows go in and swing back softer than covers.
            val press = if (mouseOnly) RowPress else Press
            val back = if (mouseOnly) RowBack else Back
            // A row's dip and swing after a click; hovering doesn't cut it short.
            var settle: Job? = null
            val slop = viewConfiguration.touchSlop
            awaitPointerEventScope {
                // The press being followed, and where it came down.
                var held = false
                var origin = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    // From -1 (left, top) to 1 (right, bottom).
                    val x = (change.position.x / size.width.coerceAtLeast(1) * 2f - 1f).coerceIn(-1f, 1f)
                    val y = (change.position.y / size.height.coerceAtLeast(1) * 2f - 1f).coerceIn(-1f, 1f)
                    val mouse = change.type == PointerType.Mouse
                    if (mouseOnly && !mouse) continue
                    when {
                        change.pressed && !change.previousPressed -> {
                            held = true
                            origin = change.position
                            scope.go(rx, ry, scale, tilt * y, -tilt * x, 1f - sink, press)
                        }
                        change.pressed && held -> {
                            if ((change.position - origin).getDistance() > slop && !mouse) {
                                // A scroll or a swipe, not a press.
                                held = false
                                scope.go(rx, ry, scale, 0f, 0f, 1f, back)
                            } else {
                                scope.go(rx, ry, scale, tilt * y, -tilt * x, 1f - sink, press)
                            }
                        }
                        !change.pressed && change.previousPressed && mouseOnly -> {
                            held = false
                            val rest = if (event.type != PointerEventType.Exit) 1f + sink / 2 else 1f
                            settle = scope.launch {
                                // A quick click is over before the press has sunk in: finish the dip first.
                                if (scale.value > 1f - sink * 0.8f) scale.animateTo(1f - sink, tween(DIP_MS, easing = FastOutSlowInEasing))
                                scale.animateTo(rest, back)
                            }
                        }
                        !change.pressed && change.previousPressed -> {
                            held = false
                            if (mouse && event.type != PointerEventType.Exit) {
                                scope.go(rx, ry, scale, -HOVER * tilt * y, HOVER * tilt * x, 1f + sink / 2, back)
                            } else {
                                scope.go(rx, ry, scale, 0f, 0f, 1f, back)
                            }
                        }
                        event.type == PointerEventType.Exit -> {
                            held = false
                            scope.go(rx, ry, scale, 0f, 0f, 1f, back)
                        }
                        mouse && !change.pressed && settle?.isActive == true && event.type != PointerEventType.Exit -> Unit
                        mouse && !change.pressed && (event.type == PointerEventType.Move || event.type == PointerEventType.Enter) ->
                            // Hovered: the near side comes up to meet the pointer.
                            scope.go(rx, ry, scale, -HOVER * tilt * y, HOVER * tilt * x, 1f + sink / 2, Follow)
                    }
                }
            }
        }
}

private fun CoroutineScope.go(
    rx: Animatable<Float, AnimationVector1D>,
    ry: Animatable<Float, AnimationVector1D>,
    scale: Animatable<Float, AnimationVector1D>,
    toX: Float,
    toY: Float,
    toScale: Float,
    spec: AnimationSpec<Float>,
) {
    launch { rx.animateTo(toX, spec) }
    launch { ry.animateTo(toY, spec) }
    launch { scale.animateTo(toScale, spec) }
}

/** Into a press: quick, without overshoot. */
private val Press = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)

/** Let go: back with a wobble. */
private val Back = spring<Float>(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow)

/** A row going in under a click: smooth, without a jolt. */
private val RowPress = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

/** A row let go: a slow, soft swing back. */
private val RowBack = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)

/** How long a quick click takes to finish a row's dip. */
private const val DIP_MS = 120

/** After the mouse: smooth. */
private val Follow = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

/** Up to this size [lively] moves things fully; larger ones get proportionally less. */
private val LIVELY_REFERENCE = 200.dp

/** How much of [lively]'s tilt a hovering mouse gets. */
private const val HOVER = 0.6f

/** Far enough that a big cover turned ten degrees doesn't loom at the eye. */
private const val CAMERA_DISTANCE = 16f

/**
 * How far a card's shapes have turned. A tap turns them on by a good part, with a swing, in
 * either mode: a tap is over before a spin would get going. Under lively motion they also spin
 * while the card is hovered or held ([spinOn]) and settle with a little swing when it's left.
 * Drawn by [cornerDecor] and [spunBackground].
 */
@Stable
class Spin internal constructor() {
    internal val angle = Animatable(0f)
    internal var active by mutableStateOf(false)

    /** Counts presses let go where they were made: taps, not scrolls or swipes. */
    internal var taps by mutableIntStateOf(0)

    /** Lively motion is on: a mouse over the card spins it too, not only a press. */
    internal var lively = true

    /** Degrees turned so far. Read it while drawing, so only drawing follows it. */
    val degrees: Float get() = angle.value
}

@Composable
fun rememberSpin(): Spin {
    val spin = remember { Spin() }
    val lively = LocalUiPrefs.current.lively
    spin.lively = lively
    LaunchedEffect(spin, lively) {
        suspend fun spinOn() {
            spin.angle.animateTo(spin.angle.value + 360f, tween(SPIN_TURN_MS, easing = EaseIn))
            while (true) spin.angle.animateTo(spin.angle.value + 360f, tween(SPIN_TURN_MS, easing = LinearEasing))
        }
        var seenTaps = spin.taps
        snapshotFlow { spin.active to spin.taps }.collectLatest { (active, taps) ->
            val tapped = taps != seenTaps
            seenTaps = taps
            when {
                tapped -> {
                    spin.angle.animateTo(spin.angle.value + TAP_DEGREES, TapTurn)
                    // Clicked while the mouse stays over it: back to spinning.
                    if (spin.active && lively) spinOn()
                }
                active && lively -> spinOn()
                lively && spin.angle.value != 0f ->
                    spin.angle.animateTo(spin.angle.value + SETTLE_DEGREES, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessVeryLow))
            }
        }
    }
    return spin
}

/**
 * Turns [spin] while a finger presses this or, under lively motion, a mouse is over it, and
 * counts taps. A finger that moves off like a scroll or a swipe lets go without a tap. Never
 * consumes.
 */
fun Modifier.spinOn(spin: Spin): Modifier = pointerInput(spin) {
    val slop = viewConfiguration.touchSlop
    awaitPointerEventScope {
        // Where the press being followed came down; null once it's a scroll.
        var origin: Offset? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: continue
            val mouse = change.type == PointerType.Mouse
            val start = origin
            when {
                change.pressed && !change.previousPressed -> origin = change.position
                change.pressed && start != null && !mouse && (change.position - start).getDistance() > slop -> origin = null
                !change.pressed && change.previousPressed -> {
                    if (start != null && event.type != PointerEventType.Exit) spin.taps++
                    origin = null
                }
            }
            spin.active = when {
                event.type == PointerEventType.Exit -> false
                mouse && spin.lively -> true
                else -> change.pressed && origin != null
            }
        }
    }
}

/** A card under lively motion: it leans and sinks under the pointer, and its shapes turn. */
fun Modifier.livelyCard(spin: Spin, tilt: Float = 6f, sink: Float = 0.025f): Modifier = lively(tilt, sink).spinOn(spin)

/**
 * [shape] filled with [color] behind the content, turned by [spin]: for the shaped badges behind
 * an icon, which turn while the icon stays upright. In place of `clip(shape).background(color)`.
 */
fun Modifier.spunBackground(shape: Shape, color: Color, spin: Spin): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawBehind {
        rotate(spin.degrees) { drawOutline(outline, color) }
    }
}

/** One turn of a card's shapes while it's hovered or held. */
private const val SPIN_TURN_MS = 4000

/** How far the shapes swing on once let go. */
private const val SETTLE_DEGREES = 30f

/** How far a tap turns a card's shapes. */
private const val TAP_DEGREES = 120f

/** A tap's turn: quick, with a swing past and back. */
private val TapTurn = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)
