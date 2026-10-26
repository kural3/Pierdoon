package com.ku.pierdoon.ui.components

import android.content.Context
import android.os.Build
import android.view.View
import android.widget.FrameLayout
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
 * Wraps the content and listens to every invalidation passing through on its way to the
 * window, Compose's layer-only repaints included. Each one also redraws a 1 px transparent
 * view in the frame already being scheduled, and that view casts a "high" frame-rate category
 * vote: the same trick Compose uses internally. Nothing is invalidated on its own, so when
 * nothing animates nothing is drawn, no vote is cast, and the adaptive panel falls back to its
 * idle rate.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private class FrameRateBoosterFrame(context: Context) : FrameLayout(context) {

    private val booster = object : View(context) {
        init {
            isFocusable = false
            isClickable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setWillNotDraw(false)
        }
    }

    init {
        addView(booster, LayoutParams(1, 1))
    }

    override fun onDescendantInvalidated(child: View, target: View) {
        super.onDescendantInvalidated(child, target)
        if (target === booster) return
        val high = HighFrameRate.enabled && !HighFrameRate.suppressed
        val category = if (high) REQUESTED_FRAME_RATE_CATEGORY_HIGH else REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
        if (booster.requestedFrameRate != category) booster.requestedFrameRate = category
        if (high) booster.invalidate() // joins the frame this invalidation already asked for
    }
}

/**
 * [content] inside the frame-rate booster, to be the activity's content view; [content]
 * itself before Android 15.
 */
fun withFrameRateBooster(content: View): View =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        FrameRateBoosterFrame(content.context).apply { addView(content) }
    } else {
        content
    }

/**
 * Put directly before an animated `graphicsLayer`: the category is applied to the next
 * layer in the chain, which then votes "high" whenever its properties change.
 */
actual fun Modifier.highFrameRate(): Modifier =
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
