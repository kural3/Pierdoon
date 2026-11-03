package com.ku.pierdoon.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.ku.pierdoon.playback.CASSETTE_SOUND_MS
import com.ku.pierdoon.playback.CASSETTE_WIND_MS
import com.ku.pierdoon.playback.TapeMotion
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A cassette for the player, in place of the cover: the track on its label, the reels turning, and
 * the tape going from the left one to the right one as the side plays. Turning runs frame by frame
 * only while the tape moves; a paused deck costs nothing.
 *
 * What the deck does besides playing shows too: a spin to the next or previous track whirls the
 * reels and moves the tape there, the end of side A lifts the cassette out and turns it over, the
 * end of side B runs the tape back, and play and pause give it the knock of a key going down.
 *
 * @param sideProgress how far the side has played, 0 to 1. Read while drawing.
 * @param motion what the deck is doing with the tape besides playing it.
 * @param rewindMs how long a [TapeMotion.Rewind] takes, which depends on the length of the side.
 * @param cover the track's picture, on the label.
 */
@Composable
fun CassetteDeck(
    title: String,
    artist: String,
    sideB: Boolean,
    isPlaying: Boolean,
    motion: TapeMotion,
    rewindMs: Long,
    sideProgress: () -> Float,
    modifier: Modifier = Modifier,
    cover: @Composable (Modifier) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val progress by rememberUpdatedState(sideProgress)
    val currentMotion by rememberUpdatedState(motion)
    val currentRewindMs by rememberUpdatedState(rewindMs)
    var turnLeft by remember { mutableFloatStateOf(0f) }
    var turnRight by remember { mutableFloatStateOf(0f) }
    /** The tape as last drawn, where a rewind or a spin starts from. */
    val shown = remember { FloatArray(1) }
    val rewound = remember { Animatable(0f) }
    val windFrom = remember { FloatArray(1) }
    val wound = remember { Animatable(1f) }
    val flip = remember { Animatable(0f) }
    val flipFrom = remember { FloatArray(1) }
    val knock = remember { Animatable(0f) }

    LaunchedEffect(motion) {
        when (motion) {
            TapeMotion.Rewind -> {
                rewound.snapTo(shown[0])
                rewound.animateTo(0f, tween(currentRewindMs.toInt(), easing = LinearEasing))
            }
            TapeMotion.Forward, TapeMotion.Back -> {
                windFrom[0] = shown[0]
                wound.snapTo(0f)
                wound.animateTo(1f, tween(CASSETTE_WIND_MS.toInt(), easing = FastOutSlowInEasing))
            }
            TapeMotion.Flip -> {
                flipFrom[0] = shown[0]
                flip.snapTo(0f)
                flip.animateTo(1f, tween(CASSETTE_SOUND_MS.toInt(), easing = LinearEasing))
            }
            TapeMotion.Still -> {
                // Cut short (a key, a track picked): what was under way settles quickly instead of jumping.
                if (wound.value < 1f) wound.animateTo(1f, tween(SETTLE_MS))
                if (flip.value > 0f) {
                    flip.animateTo(1f, tween(SETTLE_MS))
                    flip.snapTo(0f)
                }
            }
        }
    }

    // Play and pause knock the cassette down a little, as the key goes in. Not when the deck
    // itself stops for a side's end, nor when the screen first comes up.
    var seenPlaying by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isPlaying) {
        val before = seenPlaying
        seenPlaying = isPlaying
        if (before == null || before == isPlaying || currentMotion == TapeMotion.Flip || currentMotion == TapeMotion.Rewind) return@LaunchedEffect
        knock.snapTo(1f)
        knock.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
    }

    /**
     * Past the middle of turning over, the other side faces up, and the reels have swapped places:
     * the end of side A is the start of side B, and the other way round.
     */
    fun turnedOver() = motion == TapeMotion.Flip && flipTurn(flip.value) > 90f

    fun played(): Float {
        val now = progress().coerceIn(0f, 1f)
        return when {
            motion == TapeMotion.Rewind -> rewound.value
            turnedOver() -> 1f - flipFrom[0]
            wound.value < 1f -> windFrom[0] + (now - windFrom[0]) * wound.value
            else -> now
        }
    }

    val spinning = motion == TapeMotion.Rewind || motion == TapeMotion.Forward || motion == TapeMotion.Back
    LaunchedEffect(isPlaying && motion != TapeMotion.Flip, spinning) {
        if (!(isPlaying && currentMotion != TapeMotion.Flip) && !spinning) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0) {
                    val seconds = (now - last) / 1e9f
                    val f = played()
                    // A spin gets up to speed and slows again; a rewind runs flat out.
                    val whirl = sin(PI.toFloat() * wound.value).coerceAtLeast(0.3f)
                    // The tape moves at one speed, so the emptier a reel the faster it turns.
                    val speed = when (currentMotion) {
                        TapeMotion.Rewind -> -WIND_SPEED
                        TapeMotion.Back -> -WIND_SPEED * whirl
                        TapeMotion.Forward -> WIND_SPEED * whirl
                        else -> PLAY_SPEED
                    }
                    turnLeft += speed * seconds / reelRadius(1 - f) * DEGREES
                    turnRight += speed * seconds / reelRadius(f) * DEGREES
                }
                last = now
            }
        }
    }

    val lifted = Modifier.graphicsLayer {
        val t = flip.value
        val lift = flipLift(t)
        val turn = flipTurn(t)
        translationY = -lift * size.height * LIFT + knock.value * size.height * KNOCK
        val s = 1f - lift * 0.04f
        scaleX = s
        scaleY = s
        // Past the middle the other face is drawn, turned the rest of the way, so it never shows mirrored.
        rotationY = if (turn > 90f) turn - 180f else turn
        cameraDistance = 14f * density
    }

    BoxWithConstraints(modifier.then(lifted)) {
        val side = if (sideB != turnedOver()) "B" else "A"
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
            shown[0] = f
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

/** How far out of the deck the cassette is, 0 to 1, at [t] of turning it over: out, turned, back in. */
private fun flipLift(t: Float): Float = when {
    t < 0.15f -> FastOutSlowInEasing.transform(t / 0.15f)
    t < 0.75f -> 1f
    t < 0.9f -> 1f - FastOutSlowInEasing.transform((t - 0.75f) / 0.15f)
    else -> 0f
}

/** How far the cassette has turned over, in degrees, at [t]: while it's out of the deck. */
private fun flipTurn(t: Float): Float = when {
    t < 0.15f -> 0f
    t < 0.75f -> FastOutSlowInEasing.transform((t - 0.15f) / 0.6f) * 180f
    else -> 180f
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
private const val WIND_SPEED = 3.2f
/** How high the cassette comes out to be turned over, and how far a key knocks it, as parts of its height. */
private const val LIFT = 0.07f
private const val KNOCK = 0.012f
private const val SETTLE_MS = 180
