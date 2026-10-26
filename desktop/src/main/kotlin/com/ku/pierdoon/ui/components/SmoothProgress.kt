package com.ku.pierdoon.ui.components

import com.ku.pierdoon.platform.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.ku.pierdoon.PositionSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Continuous playback fraction without fast polling.
 *
 * Position samples arrive every ~500 ms; between them the position is extrapolated and the
 * value is advanced exactly when the bar would move by one pixel (`duration / widthPx` ms).
 * A 3-minute track on a 600 px bar ticks every 300 ms — visually continuous, yet far fewer
 * wakeups than animating every frame. Paused: no wakeups at all.
 */
@Composable
fun rememberSmoothProgress(
    position: () -> PositionSnapshot,
    durationMs: Long,
    widthPx: Int,
): State<Float> {
    // Seeded from the current sample so the first frame already shows the real spot.
    val fraction = remember {
        mutableFloatStateOf(if (durationMs > 0) (position().estimate().toFloat() / durationMs).coerceIn(0f, 1f) else 0f)
    }
    val currentPosition by rememberUpdatedState(position)
    LaunchedEffect(durationMs, widthPx) {
        snapshotFlow { currentPosition() }.collectLatest { snap ->
            while (true) {
                val now = snap.estimate(SystemClock.elapsedRealtime())
                fraction.floatValue =
                    if (durationMs > 0) (now.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                if (!snap.playing || durationMs <= 0 || widthPx <= 0) break
                delay((durationMs / widthPx).coerceIn(MIN_STEP_MS, MAX_STEP_MS))
            }
        }
    }
    return fraction
}

private const val MIN_STEP_MS = 16L
private const val MAX_STEP_MS = 1_000L
