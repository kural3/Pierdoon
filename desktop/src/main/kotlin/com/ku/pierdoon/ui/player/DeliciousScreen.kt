package com.ku.pierdoon.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.LyricsState
import com.ku.pierdoon.data.DeliciousStyle
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.playback.AudioLevels
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.PositionSnapshot
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.resources.*
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayPauseButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.desktop.BackHandler
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Delicious mode (beta): the screen given over to light in the cover's colours, drawn in one of
 * the [DeliciousStyle]s: flowing clouds, rolling waves, drifting dust, or calm clouds that ignore
 * the music. Everything eases in and out, with no flashes. The current line of the lyrics sits in
 * the centre. The controls show while the
 * mouse moves and step aside a moment after it stops.
 *
 * The beat comes from what the speakers play ([levels], measured by the audio engine), not from
 * the file, so it stays in step through seeks, crossfades and pauses.
 */
@Composable
fun DeliciousScreen(
    nowPlaying: NowPlaying,
    isPlaying: Boolean,
    position: () -> PositionSnapshot,
    durationMs: Long,
    lyrics: LyricsState,
    lyricsOffsetMs: Long,
    levels: () -> AudioLevels,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val scheme = MaterialTheme.colorScheme
    val palette = remember(scheme.primary, scheme.tertiary, scheme.secondary) {
        listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primary, scheme.tertiary).map(::vivid)
    }
    val style = SettingsStore.settings.collectAsState().value.deliciousStyle
    val currentStyle by rememberUpdatedState(style)
    val motion = remember { Motion() }
    // Bumped every frame; read only where drawing or the lyric index needs it.
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016 else ((now - last) / 1e9).coerceIn(0.0, 0.1)
                last = now
                motion.step(dt, levels(), currentStyle)
                frame = now
            }
        }
    }

    var moveTick by remember { mutableIntStateOf(0) }
    var chrome by remember { mutableStateOf(true) }
    LaunchedEffect(moveTick) {
        chrome = true
        delay(CHROME_HIDE_MS)
        chrome = false
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Move || e.type == PointerEventType.Press) moveTick++
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame // redraw every frame
            drawBackdrop(motion, palette, style)
        }

        CenterText(nowPlaying, lyrics, lyricsOffsetMs, position, motion) { frame }

        // Controls ------------------------------------------------------------------------
        AnimatedVisibility(chrome, enter = fadeIn(tween(250)), exit = fadeOut(tween(600)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.TopStart)) {
                    ExpressiveIconButton(
                        onClick = onClose,
                        icon = PlayerIcons.Close,
                        contentDescription = stringResource(Res.string.delicious_close),
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text("Delicious", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.delicious_beta),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                StylePicker(style, Modifier.align(Alignment.TopEnd))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    ThinProgress(position, durationMs) { frame }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                nowPlaying.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                nowPlaying.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ExpressiveIconButton(
                                onClick = onPrevious,
                                icon = PlayerIcons.Previous,
                                contentDescription = stringResource(Res.string.previous),
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White,
                                modifier = Modifier.size(52.dp),
                            )
                            PlayPauseButton(
                                isPlaying = isPlaying,
                                onClick = onTogglePlay,
                                playLabel = stringResource(Res.string.play),
                                pauseLabel = stringResource(Res.string.pause),
                                iconSize = 30.dp,
                                modifier = Modifier.size(width = 80.dp, height = 60.dp),
                            )
                            ExpressiveIconButton(
                                onClick = onNext,
                                icon = PlayerIcons.Next,
                                contentDescription = stringResource(Res.string.next),
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White,
                                modifier = Modifier.size(52.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * The music's levels smoothed for the eye. Nothing jumps: levels rise over a fifth of a second
 * and fall over most of one, and a kick is a gentle swell rather than a flash, so the picture
 * moves with the beat without flickering. [time] is the clock the shapes drift by; it runs a
 * little faster when the music is loud, and [flow] carries the dust outwards.
 */
private class Motion {
    var energy = 0f
    var bass = 0f
    /** Swells on a kick and settles again within about a second; never more than 1. */
    var kick = 0f
    private var kickTarget = 0f
    var time = 0.0
    var flow = 0.0
    private var lastBeats = -1L

    fun step(dt: Double, l: AudioLevels, style: DeliciousStyle) {
        val calm = style == DeliciousStyle.Calm
        energy = follow(energy, if (calm) 0.3f else l.energy, dt, rise = 0.22, fall = 0.9)
        bass = follow(bass, if (calm) 0.3f else l.bass, dt, rise = 0.15, fall = 0.7)
        if (lastBeats >= 0 && l.beats != lastBeats && !calm) kickTarget = 1f
        lastBeats = l.beats
        kickTarget *= exp(-dt / KICK_FALL_S).toFloat()
        kick = follow(kick, kickTarget, dt, rise = KICK_RISE_S, fall = KICK_FALL_S)
        time += dt * (0.12 + 0.3 * energy)
        flow += dt * (0.03 + 0.09 * energy + 0.05 * kick)
    }

    private fun follow(current: Float, target: Float, dt: Double, rise: Double, fall: Double): Float {
        val tau = if (target > current) rise else fall
        return current + (target - current) * (1 - exp(-dt / tau)).toFloat()
    }
}

private fun DrawScope.drawBackdrop(m: Motion, palette: List<Color>, style: DeliciousStyle) {
    drawRect(lerp(Color.Black, palette[0], 0.08f))
    when (style) {
        DeliciousStyle.Aura, DeliciousStyle.Calm -> drawAura(m, palette, strength = 1f)
        DeliciousStyle.Waves -> {
            drawAura(m, palette, strength = 0.45f)
            drawWaves(m, palette)
        }
        DeliciousStyle.Dust -> {
            drawAura(m, palette, strength = 0.4f)
            drawDust(m, palette)
        }
    }

    val w = size.width
    val h = size.height
    val center = Offset(w / 2, h / 2)
    // A soft shade behind the words, so a bright moment never swallows them.
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Black.copy(alpha = 0.38f),
            1f to Color.Transparent,
            center = center,
            radius = min(w, h) * 0.6f,
        ),
        radius = min(w, h) * 0.6f,
        center = center,
    )
    // Darker edges keep the eye in the middle.
    drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.5f),
            center = center,
            radius = max(w, h) * 0.75f,
        )
    )
}

/**
 * Large soft clouds of the cover's colours flowing into each other, breathing with the bass.
 * Painted over one another rather than added up, so where they meet they mix instead of turning
 * white.
 */
private fun DrawScope.drawAura(m: Motion, palette: List<Color>, strength: Float) {
    val w = size.width
    val h = size.height
    val unit = min(w, h)
    val t = m.time
    palette.forEachIndexed { i, color ->
        val x = w * (0.5 + 0.34 * sin(t * (0.21 + 0.04 * i) + i * 1.9) + 0.06 * sin(t * 0.7 + i * 0.7)).toFloat()
        val y = h * (0.5 + 0.3 * cos(t * (0.17 + 0.035 * i) + i * 2.6) + 0.05 * cos(t * 0.8 + i)).toFloat()
        val radius = unit * (0.6f + 0.1f * sin(t * 0.4 + i).toFloat()) * (1f + 0.14f * m.bass + 0.06f * m.kick)
        val alpha = (0.4f + 0.12f * m.energy + 0.06f * m.kick) * strength * (1f - 0.35f * color.luminance())
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = alpha),
                0.45f to color.copy(alpha = alpha * 0.55f),
                1f to color.copy(alpha = 0f),
                center = Offset(x, y),
                radius = radius,
            ),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

/** Layers of slow waves rolling across the lower half, rising with the bass. */
private fun DrawScope.drawWaves(m: Motion, palette: List<Color>) {
    val w = size.width
    val h = size.height
    val unit = min(w, h)
    val t = m.time
    val layers = 4
    for (i in 0 until layers) {
        val color = palette[i % palette.size]
        val base = h * (0.6f + 0.09f * i)
        val amplitude = unit * (0.035f + 0.07f * m.bass + 0.025f * m.kick) * (1f - 0.15f * i)
        val path = Path()
        path.moveTo(0f, h)
        val steps = 72
        for (s in 0..steps) {
            val fx = s / steps.toFloat()
            val phase = fx * PI * (1.6 + 0.4 * i)
            val y = base - amplitude * (
                sin(phase + t * (1.1 + 0.25 * i) + i * 1.3) * 0.65 +
                    sin(phase * 2.3 - t * (0.7 + 0.2 * i) + i) * 0.35
                ).toFloat()
            path.lineTo(fx * w, y)
        }
        path.lineTo(w, h)
        path.close()
        val alpha = (0.32f + 0.1f * m.energy) * (1f - 0.3f * color.luminance())
        drawPath(
            path,
            Brush.verticalGradient(
                0f to color.copy(alpha = alpha),
                1f to color.copy(alpha = alpha * 0.15f),
                startY = base - amplitude,
                endY = h,
            ),
        )
    }
}

/** Specks of light drifting out from the middle, quicker when the music is loud. */
private fun DrawScope.drawDust(m: Motion, palette: List<Color>) {
    val w = size.width
    val h = size.height
    val center = Offset(w / 2, h / 2)
    val reach = hypot(w / 2, h / 2)
    for (i in 0 until DUST) {
        // Fixed per speck: a direction, a pace and a place in its life.
        val angle = rand(i, 1) * 2 * PI
        val pace = 0.5 + rand(i, 2)
        val life = ((m.flow * pace + rand(i, 3)) % 1.0).toFloat()
        val wobble = 0.08 * sin(m.time * 2 + i)
        val d = reach * (0.08f + 0.95f * life * life)
        val p = Offset(
            center.x + (cos(angle + wobble) * d).toFloat(),
            center.y + (sin(angle + wobble) * d).toFloat(),
        )
        val fade = sin(PI * life).toFloat()
        val r = density * (1.2f + 2.6f * rand(i, 4).toFloat()) * (0.6f + 0.9f * life) * (1f + 0.25f * m.bass)
        val color = lerp(palette[i % palette.size], Color.White, 0.3f)
        drawCircle(color.copy(alpha = 0.55f * fade * (0.75f + 0.25f * m.energy)), radius = r, center = p)
    }
}

/** A steady pseudo-random number in 0..1 for speck [i]'s property [k]. */
private fun rand(i: Int, k: Int): Double {
    val x = sin(i * 12.9898 + k * 78.233) * 43758.5453
    return x - floor(x)
}

/** The current lyric line large in the middle, the next one faint under it; else the track. */
@Composable
private fun CenterText(
    nowPlaying: NowPlaying,
    lyrics: LyricsState,
    offsetMs: Long,
    position: () -> PositionSnapshot,
    motion: Motion,
    frame: () -> Long,
) {
    val synced = ((lyrics as? LyricsState.Ready)?.lyrics as? Lyrics.Synced)?.takeIf { it.lines.isNotEmpty() }
    // Recomposes only when the line changes, though it's worked out every frame.
    val index by remember(synced, offsetMs) {
        derivedStateOf {
            frame()
            synced?.indexAt(position().estimate() - offsetMs + LEAD_MS) ?: -1
        }
    }
    val shadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 28f)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 110.dp), contentAlignment = Alignment.Center) {
        val big = if (maxWidth > 900.dp) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium
        AnimatedContent(
            targetState = if (synced != null && index >= 0) index else -1,
            transitionSpec = {
                (fadeIn(tween(380)) + slideInVertically(tween(420)) { it / 4 } + scaleIn(tween(420), initialScale = 0.94f))
                    .togetherWith(fadeOut(tween(260)) + slideOutVertically(tween(320)) { -it / 4 } + scaleOut(tween(320), targetScale = 1.04f))
                    .using(SizeTransform(clip = false))
            },
            label = "deliciousLine",
        ) { i ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 1100.dp)
                    // A faint breath with the bass; only the layer changes, nothing is laid out again.
                    .graphicsLayer {
                        frame()
                        val s = 1f + 0.012f * motion.kick + 0.008f * motion.bass
                        scaleX = s
                        scaleY = s
                    }
                    // Inside the layer: room for the shadow and the breath, which it would cut off.
                    .padding(32.dp),
            ) {
                if (synced == null || i < 0) {
                    Text(
                        nowPlaying.title,
                        style = big.copy(fontWeight = FontWeight.Bold, shadow = shadow),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        nowPlaying.artist,
                        style = MaterialTheme.typography.headlineSmall.copy(shadow = shadow),
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    val line = synced.lines[i]
                    // Until the next line, but not through a long instrumental after it.
                    val longest = SING_BASE_MS + SING_PER_CHAR_MS * line.text.length
                    val end = min(synced.lines.getOrNull(i + 1)?.timeMs ?: Long.MAX_VALUE, line.timeMs + longest)
                    SungLine(
                        // A wordless gap between lines: a quiet mark rather than nothing at all.
                        line.text.ifBlank { "♪" },
                        style = big.copy(fontWeight = FontWeight.Bold, shadow = shadow),
                        startMs = line.timeMs,
                        endMs = end,
                        now = { position().estimate() - offsetMs },
                        frame = frame,
                    )
                    synced.lines.getOrNull(i + 1)?.text?.takeIf { it.isNotBlank() }?.let { following ->
                        Spacer(Modifier.height(20.dp))
                        Text(
                            following,
                            style = MaterialTheme.typography.headlineSmall.copy(shadow = shadow),
                            color = Color.White.copy(alpha = 0.45f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A lyric line that fills with light while it's sung, left to right and line after line when it
 * wraps: the part sung so far bright, the rest dim. LRC times lines, not words, so the fill runs
 * evenly over the line's time; it stands in for following each word.
 */
@Composable
private fun SungLine(
    text: String,
    style: TextStyle,
    startMs: Long,
    endMs: Long,
    now: () -> Long,
    frame: () -> Long,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = style,
        color = Color.White.copy(alpha = UNSUNG_ALPHA),
        textAlign = TextAlign.Center,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = Modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            frame()
            val f = ((now() - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
            val widths = (0 until l.lineCount).map { l.getLineRight(it) - l.getLineLeft(it) }
            var left = f * widths.sum()
            val feather = SING_FEATHER.toPx()
            for (line in 0 until l.lineCount) {
                if (left <= 0f) break
                val x0 = l.getLineLeft(line)
                val edge = x0 + min(left, widths[line])
                // Tails and accents reach past the line's box: the first line is open above, the last
                // below, and the ones between reach a little into their neighbours.
                val overhang = GLYPH_OVERHANG * (l.getLineBottom(line) - l.getLineTop(line))
                val top = if (line == 0) -size.height else l.getLineTop(line) - overhang
                val bottom = if (line == l.lineCount - 1) size.height * 2 else l.getLineBottom(line) + overhang
                val solid = edge - feather / 2
                clipRect(left = x0 - feather, top = top, right = solid, bottom = bottom) {
                    drawText(l, color = Color.White, shadow = Shadow.None)
                }
                // A soft edge, so the light seems to flow on rather than be cut. In slices: a
                // gradient brush on text draws nothing on the desktop.
                val slice = feather / FEATHER_SLICES
                for (s in 0 until FEATHER_SLICES) {
                    val from = solid + s * slice
                    clipRect(left = from, top = top, right = from + slice, bottom = bottom) {
                        drawText(l, color = Color.White, alpha = 1f - (s + 0.5f) / FEATHER_SLICES, shadow = Shadow.None)
                    }
                }
                left -= widths[line]
            }
        },
    )
}

/** A hairline of progress along the bottom. */
@Composable
private fun ThinProgress(position: () -> PositionSnapshot, durationMs: Long, frame: () -> Long) {
    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
        frame()
        val f = if (durationMs > 0) (position().estimate().toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.2f), cornerRadius = r)
        drawRoundRect(Color.White, size = size.copy(width = size.width * f), cornerRadius = r)
    }
}

/** [c] pushed bright and saturated, so it glows on black whatever the theme made of it. */
private fun vivid(c: Color): Color {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    val delta = max - min
    if (delta < 0.04f) return Color.hsv(0f, 0f, 0.9f)
    val hue = when (max) {
        c.red -> 60f * (((c.green - c.blue) / delta) % 6f)
        c.green -> 60f * ((c.blue - c.red) / delta + 2f)
        else -> 60f * ((c.red - c.green) / delta + 4f)
    }.let { if (it < 0) it + 360f else it }
    val saturation = (delta / max).coerceAtLeast(0.6f)
    return Color.hsv(hue, saturation, maxOf(max, 0.9f))
}

private const val CHROME_HIDE_MS = 2_500L
/** Lines change slightly ahead of the beat, which reads as "in sync" to the ear. */
private const val LEAD_MS = 150L
private const val KICK_RISE_S = 0.12
private const val KICK_FALL_S = 0.45
private const val DUST = 140
// The current line: how bright it is before it's sung, how soft the edge of the light is, and
// the longest it may take to sing (beyond that it's an instrumental, not a slow line).
private const val UNSUNG_ALPHA = 0.38f
private val SING_FEATHER = 28.dp
private const val FEATHER_SLICES = 8
private const val GLYPH_OVERHANG = 0.2f
private const val SING_BASE_MS = 1_500L
private const val SING_PER_CHAR_MS = 350L

/** The styles as small pills; the chosen one filled. */
@Composable
private fun StylePicker(selected: DeliciousStyle, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        DeliciousStyle.entries.forEach { s ->
            val on = s == selected
            Text(
                stringResource(
                    when (s) {
                        DeliciousStyle.Aura -> Res.string.delicious_aura
                        DeliciousStyle.Waves -> Res.string.delicious_waves
                        DeliciousStyle.Dust -> Res.string.delicious_dust
                        DeliciousStyle.Calm -> Res.string.delicious_calm
                    }
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (on) Color.Black else Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Color.White else Color.White.copy(alpha = 0.12f))
                    .clickable { SettingsStore.update { it.copy(deliciousStyle = s) } }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
