package com.ku.pierdoon.ui.player

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private const val DRAG_TICKS = 40

/** Slightly longer than the indicator's own amplitude-decrease animation. */
private const val WAVE_SETTLE_MS = 700L

/**
 * Seek bar drawn as an M3 Expressive wavy indicator. The wave flattens when paused or while
 * scrubbing, the stroke thickens under the finger and scrubbing ticks the haptic motor
 * like detents. Progress moves continuously (pixel-step extrapolation, no fast polling).
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

    // The indicator animates amplitude itself; it must receive the *target* (0 or 1), not an
    // already-animating value, or it can get stuck mid-way. Read through a state so a change
    // invalidates its draw cache.
    val wavyEnabled = LocalUiPrefs.current.wavyProgress
    val waveOn by rememberUpdatedState(wavyEnabled && isPlaying && !dragging)
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
                        onSeek(((offset.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
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
                            dragFraction?.let { onSeek((it * durationMs).toLong()) }
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
            LinearWavyProgressIndicator(
                progress = { dragFraction ?: playFraction },
                amplitude = { if (waveOn) 1f else 0f },
                stroke = stroke,
                trackStroke = stroke,
                waveSpeed = if (waveMoving) 16.dp else 0.dp,
                modifier = Modifier.fillMaxWidth().height(20.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // Recomposes once per second, not on every progress step.
            val shownSeconds by remember(durationMs) {
                derivedStateOf { (((dragFraction ?: playFraction) * durationMs) / 1000).toLong() }
            }
            val style = MaterialTheme.typography.labelLarge
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(formatDuration(shownSeconds * 1000), style = style, color = color)
            Text(formatDuration(durationMs), style = style, color = color)
        }
    }
}
