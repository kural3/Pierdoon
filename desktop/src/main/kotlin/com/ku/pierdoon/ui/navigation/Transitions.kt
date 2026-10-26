package com.ku.pierdoon.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/** Specs pulled from the theme's motion scheme, so this file stays free of @Composable. */
class MotionSpecs(
    val spatialOffset: FiniteAnimationSpec<IntOffset>,
    val spatial: FiniteAnimationSpec<Float>,
    val effects: FiniteAnimationSpec<Float>,
)

enum class NavDirection { Tab, Forward, Back }

/**
 * Smooth going in, springy coming back: tabs fade through and detail pages glide in on
 * Material easing; returning (button or predictive back gesture) settles on an expressive
 * spring, so the page you come back to lands with a little bounce.
 */
fun pageTransition(direction: NavDirection, m: MotionSpecs): ContentTransform {
    val t = when (direction) {
        NavDirection.Tab, NavDirection.Forward -> smoothTransition(direction)
        NavDirection.Back -> springBack(m)
    }
    // Pages overlap while sliding, so the container must not clip them.
    return ContentTransform(t.targetContentEnter, t.initialContentExit, t.targetContentZIndex, SizeTransform(clip = false))
}

// --- Back: M3 Expressive springs -----------------------------------------------------------

/** The leaving page slides off on top; the revealed page grows back from slightly behind. */
private fun springBack(m: MotionSpecs): ContentTransform =
    (slideInHorizontally(m.spatialOffset) { -it / 5 } + scaleIn(m.spatial, initialScale = 0.92f) +
        fadeIn(m.effects, initialAlpha = 0.4f))
        .togetherWith(
            slideOutHorizontally(m.spatialOffset) { it } + scaleOut(m.spatial, targetScale = 0.9f)
        )
        .apply { targetContentZIndex = -1f }

// --- Smooth (Material Motion easing, no overshoot) ------------------------------------------

/** Material "emphasized decelerate": things arriving on screen. */
private val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Material "emphasized accelerate": things leaving the screen. */
private val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private const val PUSH_MS = 400
private const val LEAVE_MS = 200
private const val FADE_THROUGH_OUT_MS = 90
private const val FADE_THROUGH_IN_MS = 210

private fun <T> arrive(ms: Int = PUSH_MS, delay: Int = 0) = tween<T>(ms, delay, Decelerate)
private fun <T> leave(ms: Int = LEAVE_MS) = tween<T>(ms, 0, Accelerate)

private fun smoothTransition(direction: NavDirection): ContentTransform = when (direction) {
    // Fade-through: the old tab fades out quickly, then the new one fades and grows in.
    NavDirection.Tab ->
        (fadeIn(arrive(FADE_THROUGH_IN_MS, FADE_THROUGH_OUT_MS)) +
            scaleIn(arrive(FADE_THROUGH_IN_MS, FADE_THROUGH_OUT_MS), initialScale = 0.94f))
            .togetherWith(fadeOut(leave(FADE_THROUGH_OUT_MS)))
    // Parallax: the new page glides in over a gently receding, fading old page.
    NavDirection.Forward ->
        (slideInHorizontally(arrive()) { it * 2 / 5 } + fadeIn(arrive(PUSH_MS / 2)))
            .togetherWith(slideOutHorizontally(arrive()) { -it / 6 } + fadeOut(leave()))
            .apply { targetContentZIndex = 1f }
    NavDirection.Back -> error("back uses springBack")
}
