package com.ku.pierdoon.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.PositionSnapshot
import com.ku.pierdoon.ui.components.rememberSmoothProgress
import com.ku.pierdoon.ui.formatDuration
import com.ku.pierdoon.ui.theme.LocalUiPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private const val DRAG_TICKS = 40

/** Slightly longer than the indicator's own amplitude-decrease animation. */
private const val WAVE_SETTLE_MS = 700L

/** A seek stalls playback for a moment; a pause shorter than this keeps the wave up. */
private const val STALL_GRACE_MS = 400L

/** How long the bar holds a seek's target while the player catches up to it. */
private const val SEEK_HOLD_MS = 1_500L

/** Jumps larger than this glide instead of snapping. */
private const val GLIDE_THRESHOLD = 0.01f

/**
 * Seek bar drawn as an M3 Expressive wavy indicator. The wave flattens when paused, the stroke
 * thickens under the finger and scrubbing ticks the haptic motor like detents. Progress moves
 * continuously (pixel-step extrapolation, no fast polling); a seek glides to its spot and stays
 * there until the player catches up instead of flicking back to the old position.
 */
@Composable
fun WavySeekBar(
    position: () -> PositionSnapshot,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val dragging = dragFraction != null
    var widthPx by remember { mutableIntStateOf(0) }
    val playFraction by rememberSmoothProgress(position, durationMs, widthPx)
    // Where a finished seek went, until playback reports it.
    var seekTarget by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(seekTarget) {
        val target = seekTarget ?: return@LaunchedEffect
        withTimeoutOrNull(SEEK_HOLD_MS) {
            snapshotFlow { abs(playFraction - target) < GLIDE_THRESHOLD }.first { it }
        }
        seekTarget = null
    }
    fun seek(fraction: Float) {
        seekTarget = fraction
        onSeek((fraction * durationMs).toLong())
    }
    val glideSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    // Starts where playback is, so opening the player doesn't glide in from zero.
    val shown = remember { Animatable(playFraction) }
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(dragFraction, seekTarget, playFraction) }.collectLatest { (drag, held, play) ->
            val target = drag ?: held ?: play
            if (drag == null && abs(target - shown.value) > GLIDE_THRESHOLD) {
                shown.animateTo(target, glideSpec)
            } else {
                shown.snapTo(target)
            }
        }
    }

    // The indicator animates amplitude itself; it must receive the *target* (0 or 1), not an
    // already-animating value, or it can get stuck mid-way. Read through a state so a change
    // invalidates its draw cache.
    val nothing = LocalUiPrefs.current.nothing
    val wavyEnabled = LocalUiPrefs.current.wavyProgress && !nothing
    // Scrubbing keeps the wave; only a real pause flattens it, not the stall right after a seek.
    var playingSteady by remember { mutableStateOf(isPlaying) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) delay(STALL_GRACE_MS)
        playingSteady = isPlaying
    }
    val waveOn by rememberUpdatedState(wavyEnabled && playingSteady)
    // Keep the wave travelling until it has flattened, then stop it: a still wave costs no frames.
    var waveMoving by remember { mutableStateOf(wavyEnabled && isPlaying) }
    LaunchedEffect(waveOn) {
        if (waveOn) {
            waveMoving = true
        } else {
            delay(WAVE_SETTLE_MS)
            waveMoving = false
        }
    }

    val thickness by animateDpAsState(
        targetValue = if (dragging) 10.dp else 6.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "thickness",
    )
    val stroke = with(LocalDensity.current) { Stroke(width = thickness.toPx(), cap = StrokeCap.Round) }

    Column(modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs <= 0) return@detectTapGestures
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        seek((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(durationMs) {
                    var lastTick = -1
                    fun fraction(x: Float) = (x / size.width).coerceIn(0f, 1f)
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (durationMs <= 0) return@detectHorizontalDragGestures
                            val f = fraction(offset.x)
                            dragFraction = f
                            lastTick = (f * DRAG_TICKS).toInt()
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        },
                        onDragEnd = {
                            dragFraction?.let(::seek)
                            dragFraction = null
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        if (dragFraction == null) return@detectHorizontalDragGestures
                        change.consume()
                        val f = fraction(change.position.x)
                        dragFraction = f
                        val tick = (f * DRAG_TICKS).toInt()
                        if (tick != lastTick) {
                            lastTick = tick
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                    }
                },
        ) {
            if (nothing) {
                DotProgress(
                    progress = { shown.value.coerceIn(0f, 1f) },
                    dragging = dragging,
                    modifier = Modifier.fillMaxWidth().height(20.dp),
                )
            } else {
                LinearWavyProgressIndicator(
                    progress = { shown.value.coerceIn(0f, 1f) },
                    amplitude = { if (waveOn) 1f else 0f },
                    stroke = stroke,
                    trackStroke = stroke,
                    waveSpeed = if (waveMoving) 16.dp else 0.dp,
                    modifier = Modifier.fillMaxWidth().height(20.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // Recomposes once per second, not on every progress step.
            val shownSeconds by remember(durationMs) {
                derivedStateOf { (((dragFraction ?: seekTarget ?: playFraction) * durationMs) / 1000).toLong() }
            }
            val style = MaterialTheme.typography.labelLarge
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(formatDuration(shownSeconds * 1000), style = style, color = color)
            Text(formatDuration(durationMs), style = style, color = color)
        }
    }
}

/**
 * Nothing-style track: a row of dots, lit up to the playhead, which is a larger red dot.
 * The head swells under the finger.
 */
@Composable
private fun DotProgress(progress: () -> Float, dragging: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val head by animateDpAsState(
        targetValue = if (dragging) 8.dp else 5.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "dotHead",
    )
    Canvas(modifier) {
        val pitch = DOT_PITCH.toPx()
        val count = (size.width / pitch).toInt().coerceAtLeast(2)
        val step = (size.width - pitch) / (count - 1)
        val y = size.height / 2
        val at = progress() * (count - 1)
        val lit = colors.onSurface
        val unlit = colors.onSurface.copy(alpha = 0.22f)
        for (i in 0 until count) {
            drawCircle(if (i <= at) lit else unlit, radius = DOT_RADIUS.toPx(), center = Offset(pitch / 2 + i * step, y))
        }
        drawCircle(colors.primary, radius = head.toPx(), center = Offset(pitch / 2 + at * step, y))
    }
}

private val DOT_PITCH = 9.dp
private val DOT_RADIUS = 2.dp
