package com.ku.pierdoon.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * A cassette for the player, in place of the cover: the track on its label, the reels turning, and
 * the tape going from the left one to the right one as the side plays. Turning runs frame by frame
 * only while the tape moves; a paused deck costs nothing.
 *
 * @param sideProgress how far the side has played, 0 to 1. Read while drawing.
 * @param rewinding the tape running back; [rewindMs] is how long that takes.
 * @param cover the track's picture, on the label.
 */
@Composable
fun CassetteDeck(
    title: String,
    artist: String,
    side: String,
    isPlaying: Boolean,
    rewinding: Boolean,
    rewindMs: Int,
    sideProgress: () -> Float,
    modifier: Modifier = Modifier,
    cover: @Composable (Modifier) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val progress by rememberUpdatedState(sideProgress)
    var turnLeft by remember { mutableFloatStateOf(0f) }
    var turnRight by remember { mutableFloatStateOf(0f) }
    val rewound = remember { Animatable(0f) }

    LaunchedEffect(rewinding) {
        if (rewinding) {
            rewound.snapTo(progress().coerceIn(0f, 1f))
            rewound.animateTo(0f, tween(rewindMs, easing = LinearEasing))
        }
    }

    fun played() = if (rewinding) rewound.value else progress().coerceIn(0f, 1f)

    LaunchedEffect(isPlaying, rewinding) {
        if (!isPlaying && !rewinding) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0) {
                    val seconds = (now - last) / 1e9f
                    val f = played()
                    // The tape moves at one speed, so the emptier a reel the faster it turns.
                    val speed = if (rewinding) -REWIND_SPEED else PLAY_SPEED
                    turnLeft += speed * seconds / reelRadius(1 - f) * DEGREES
                    turnRight += speed * seconds / reelRadius(f) * DEGREES
                }
                last = now
            }
        }
    }

    BoxWithConstraints(modifier) {
        val unit = maxWidth
        val tall = maxHeight
        val body = colors.surfaceContainerHighest
        val label = colors.primaryContainer
        val onLabel = colors.onPrimaryContainer
        val window = colors.scrim.copy(alpha = 0.72f)
        val tape = Color(0xFF2B211C)
        val hub = colors.onSurfaceVariant
        val edge = colors.outlineVariant
        Canvas(Modifier.fillMaxSize()) {
            drawShell(body, label, window, edge)
            val f = played()
            drawReels(tape, hub, 1 - f, f, turnLeft, turnRight)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .offset(x = unit * LABEL_X, y = tall * 0.085f)
                .size(width = unit * (1 - 2 * LABEL_X), height = tall * 0.24f),
        ) {
            Spacer(Modifier.width(unit * 0.025f))
            Text(
                side,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                fontWeight = FontWeight.Bold,
                color = onLabel,
            )
            Spacer(Modifier.width(unit * 0.03f))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = onLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artist.isNotEmpty()) {
                    Text(
                        artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = onLabel.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(unit * 0.02f))
            Box(Modifier.size(tall * 0.2f).clip(RoundedCornerShape(8.dp))) {
                cover(Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(unit * 0.02f))
        }
    }
}

/** The radius of a reel with [share] of the tape on it, as a part of the deck's width. */
private fun reelRadius(share: Float): Float =
    sqrt(HUB_RADIUS * HUB_RADIUS + (FULL_REEL * FULL_REEL - HUB_RADIUS * HUB_RADIUS) * share.coerceIn(0f, 1f))

private fun DrawScope.drawShell(body: Color, label: Color, window: Color, edge: Color) {
    val w = size.width
    val h = size.height
    val corner = CornerRadius(w * 0.035f)
    drawRoundRect(body, cornerRadius = corner)
    drawRoundRect(edge, cornerRadius = corner, style = Stroke(w * 0.004f))

    // The label, and the window in it.
    drawRoundRect(
        label,
        topLeft = Offset(w * LABEL_X, h * 0.06f),
        size = Size(w * (1 - 2 * LABEL_X), h * 0.64f),
        cornerRadius = CornerRadius(w * 0.02f),
    )
    drawRoundRect(window, topLeft = windowTopLeft(), size = windowSize(), cornerRadius = CornerRadius(w * 0.02f))

    // The thicker part at the bottom, where the tape meets the heads.
    val foot = Path().apply {
        moveTo(w * 0.2f, h)
        lineTo(w * 0.26f, h * 0.76f)
        lineTo(w * 0.74f, h * 0.76f)
        lineTo(w * 0.8f, h)
        close()
    }
    drawPath(foot, edge.copy(alpha = 0.55f))
    for (x in listOf(0.38f, 0.5f, 0.62f)) {
        drawCircle(body, radius = w * 0.012f, center = Offset(w * x, h * 0.88f))
    }
    for (x in listOf(0.035f, 0.965f)) for (y in listOf(0.06f, 0.94f)) {
        drawCircle(edge, radius = w * 0.009f, center = Offset(w * x, h * y))
    }
}

private fun DrawScope.windowTopLeft() = Offset(size.width * 0.2f, size.height * 0.36f)
private fun DrawScope.windowSize() = Size(size.width * 0.6f, size.height * 0.3f)

private fun DrawScope.drawReels(tape: Color, hub: Color, left: Float, right: Float, turnLeft: Float, turnRight: Float) {
    val w = size.width
    val y = size.height * 0.51f
    val centers = listOf(Offset(w * 0.33f, y), Offset(w * 0.67f, y))
    val window = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                windowTopLeft().x, windowTopLeft().y,
                windowTopLeft().x + windowSize().width, windowTopLeft().y + windowSize().height,
                CornerRadius(w * 0.02f),
            )
        )
    }
    clipPath(window) {
        drawCircle(tape, radius = w * reelRadius(left), center = centers[0])
        drawCircle(tape, radius = w * reelRadius(right), center = centers[1])
    }
    drawHub(hub, centers[0], turnLeft)
    drawHub(hub, centers[1], turnRight)
}

/** A hub with its six teeth, which is all of a turning reel that shows it's turning. */
private fun DrawScope.drawHub(color: Color, center: Offset, turn: Float) {
    val w = size.width
    val r = w * HUB_RADIUS
    drawCircle(color, radius = r, center = center, style = Stroke(w * 0.011f))
    rotate(turn, center) {
        repeat(6) { i ->
            rotate(i * 60f, center) {
                drawLine(
                    color,
                    start = Offset(center.x, center.y - r),
                    end = Offset(center.x, center.y - r * 0.55f),
                    strokeWidth = w * 0.011f,
                )
            }
        }
    }
}

private const val LABEL_X = 0.06f
private const val HUB_RADIUS = 0.05f
private const val FULL_REEL = 0.15f
private const val DEGREES = 180f / PI.toFloat()
/** Parts of the deck's width per second, about a turn in two seconds on a half-full reel. */
private const val PLAY_SPEED = 0.34f
private const val REWIND_SPEED = 3.2f
