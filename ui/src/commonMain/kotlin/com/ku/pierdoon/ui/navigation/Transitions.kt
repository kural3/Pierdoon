package com.ku.pierdoon.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** Which way the app moves: along the tabs (to one further along, or back), or into a page and out. */
enum class NavDirection { TabForward, TabBack, Forward, Back }

/**
 * Tabs slide a little the way the bar runs, one fading into the next. Opening a page zooms in:
 * the new page grows out of the distance while the old one swells past and fades; going back is
 * the same move undone. Long, gently slowing curves with no bounce, so nothing jerks.
 */
fun pageTransition(direction: NavDirection): ContentTransform {
    val t = when (direction) {
        NavDirection.TabForward -> sharedAxis(forward = true)
        NavDirection.TabBack -> sharedAxis(forward = false)
        NavDirection.Forward -> zoomIn()
        NavDirection.Back -> zoomOut()
    }
    // Pages overlap while moving, so the container must not clip them.
    return ContentTransform(t.targetContentEnter, t.initialContentExit, t.targetContentZIndex, SizeTransform(clip = false))
}

/** Material "emphasized decelerate": quick to start, a long soft landing. */
private val Settle = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Gentle in and out, for fades that overlap the movement. */
private val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private const val TAB_MS = 450
private const val PAGE_MS = 500

private fun <T> move(ms: Int) = tween<T>(ms, 0, Settle)
private fun <T> fade(ms: Int, delay: Int = 0) = tween<T>(ms, delay, Ease)

/** The old tab drifts off one way and fades; the new one drifts in from the other side. */
private fun sharedAxis(forward: Boolean): ContentTransform {
    val sign = if (forward) 1 else -1
    return (slideInHorizontally(move(TAB_MS)) { sign * it / 8 } + fadeIn(fade(TAB_MS * 2 / 3, delay = TAB_MS / 6)))
        .togetherWith(slideOutHorizontally(move(TAB_MS)) { -sign * it / 8 } + fadeOut(fade(TAB_MS / 3)))
}

/** Into a page: it grows out of the distance over the old one, which swells and fades. */
private fun zoomIn(): ContentTransform =
    (scaleIn(move(PAGE_MS), initialScale = 0.88f) + fadeIn(fade(PAGE_MS * 3 / 5, delay = PAGE_MS / 10)))
        .togetherWith(scaleOut(move(PAGE_MS), targetScale = 1.06f) + fadeOut(fade(PAGE_MS * 2 / 5)))
        .apply { targetContentZIndex = 1f }

/** Back out: the page shrinks away on top while the one behind settles back from close up. */
private fun zoomOut(): ContentTransform =
    (scaleIn(move(PAGE_MS), initialScale = 1.06f) + fadeIn(fade(PAGE_MS * 3 / 5)))
        .togetherWith(scaleOut(move(PAGE_MS), targetScale = 0.88f) + fadeOut(fade(PAGE_MS * 2 / 5)))
        .apply { targetContentZIndex = -1f }
