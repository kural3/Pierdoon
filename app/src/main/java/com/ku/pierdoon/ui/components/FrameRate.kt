package com.ku.pierdoon.ui.components

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.unit.dp

/**
 * High refresh rate for animations (Android 15+ frame-rate categories).
 *
 * Compose already votes "high" for moves and resizes, but changes to a layer's scale, alpha
 * or shape vote nothing, so the system renders them in the "normal" (60 Hz) category. These
 * helpers vote "high" only on frames that actually animate: an idle screen draws nothing and
 * the panel still drops to its lowest rate.
 */
object HighFrameRate {
    /** Mirrors the "max smoothness" setting; read while building modifiers. */
    @Volatile
    var enabled: Boolean = true

    /**
     * True while something animates endlessly (the seek-bar wave in the open player).
     * Those frames keep the system's default rate so a playing player doesn't pin 120 Hz.
     */
    @Volatile
    var suppressed: Boolean = false
}

/**
 * Window-wide "high" vote for every frame the app draws anyway.
 *
 * A 1 px transparent view re-invalidates itself in the pre-draw pass of each frame, so it is
 * redrawn inside that same frame and casts a "high" frame-rate category vote — the same trick
 * Compose uses internally. It never schedules a frame of its own: when nothing animates
 * nothing is drawn, no vote is cast, and the adaptive panel falls back to its idle rate.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class FrameRateBoosterView(context: Context) : View(context) {

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        if (HighFrameRate.enabled && !HighFrameRate.suppressed) {
            requestedFrameRate = REQUESTED_FRAME_RATE_CATEGORY_HIGH
            invalidate() // part of the frame already being drawn; does not request a new one
        } else {
            requestedFrameRate = REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
        }
        true
    }

    init {
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(preDraw)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDraw)
        super.onDetachedFromWindow()
    }
}

/** Adds [FrameRateBoosterView] to an activity's content frame; no-op before Android 15. */
fun installFrameRateBooster(content: ViewGroup) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        content.addView(FrameRateBoosterView(content.context), ViewGroup.LayoutParams(1, 1))
    }
}

/**
 * Put directly before an animated `graphicsLayer`: the category is applied to the next
 * layer in the chain, which then votes "high" whenever its properties change.
 */
fun Modifier.highFrameRate(): Modifier =
    if (HighFrameRate.enabled) preferredFrameRate(FrameRateCategory.High) else this

/**
 * While [active], nudges a 1 dp invisible layer every frame so each frame votes "high".
 * Used for transitions whose layers live inside library code (AnimatedContent etc.).
 * Inactive = not composed = zero cost.
 */
@Composable
fun FrameRateBoost(active: Boolean) {
    if (!active || !HighFrameRate.enabled) return
    val tick = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { tick.intValue++ }
    }
    Box(
        Modifier
            .size(1.dp)
            .highFrameRate()
            .graphicsLayer { alpha = if (tick.intValue % 2 == 0) 0.001f else 0.002f }
    )
}
