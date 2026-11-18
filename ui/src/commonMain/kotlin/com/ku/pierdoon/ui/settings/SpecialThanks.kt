package com.ku.pierdoon.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.about_thanks
import com.ku.pierdoon.ui.resources.about_thanks_base
import com.ku.pierdoon.ui.resources.about_thanks_claude
import com.ku.pierdoon.ui.resources.about_thanks_downloads
import com.ku.pierdoon.ui.resources.about_thanks_lyrics
import com.ku.pierdoon.ui.resources.about_thanks_projects
import com.ku.pierdoon.ui.resources.about_thanks_sound
import com.ku.pierdoon.ui.resources.about_thanks_tags
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** How long the song playing takes to fade away under the thanks: about as long as everything takes to come. */
const val SPECIAL_THANKS_FADE_MS = 20_000L

private class Person(val name: String, val colors: List<Color>, val shape: RoundedPolygon)

/** Found by pulling the logo in About far down. Each name has its own colours and its own shape. */
private val People = listOf(
    Person("kuronika", listOf(Color(0xFFFF7EB3), Color(0xFFB57BFF), Color(0xFFFFC2E2)), MaterialShapes.Heart),
    Person("keychan", listOf(Color(0xFF6EE7FF), Color(0xFF5B8CFF), Color(0xFFC6F6FF)), MaterialShapes.Sunny),
    Person("sando", listOf(Color(0xFFFFC857), Color(0xFFFF7A59), Color(0xFFFFE8A3)), MaterialShapes.Cookie9Sided),
    Person("sayamoon", listOf(Color(0xFFC9B6FF), Color(0xFF8EA2FF), Color(0xFFE8ECFF)), MaterialShapes.SemiCircle),
    Person("chinganchkuk", listOf(Color(0xFF7CFFCB), Color(0xFF2EC4B6), Color(0xFFD9FFF1)), MaterialShapes.Clover4Leaf),
    Person("bbshbbsh", listOf(Color(0xFFFF5F8F), Color(0xFFFF9E57), Color(0xFFFFD1DC)), MaterialShapes.Flower),
    Person("bebra", listOf(Color(0xFFC6FF6B), Color(0xFF4ADE80), Color(0xFFF0FFC2)), MaterialShapes.Burst),
    Person("iva_ze", listOf(Color(0xFF7DD3FC), Color(0xFFA78BFA), Color(0xFFF0ABFC)), MaterialShapes.Gem),
    Person("badma", listOf(Color(0xFFFFD166), Color(0xFFEF476F), Color(0xFFFFE5A8)), MaterialShapes.SoftBurst),
    Person("arkadianec", listOf(Color(0xFF5EEAD4), Color(0xFF8B5CF6), Color(0xFF67E8F9)), MaterialShapes.Clover8Leaf),
    Person("ahav", listOf(Color(0xFFFFB4A2), Color(0xFFFF6B6B), Color(0xFFFFD6A5)), MaterialShapes.Puffy),
)

/** Whose sites and programs the app stands on, by what they give it. */
private val Projects: List<Pair<StringResource, List<String>>> = listOf(
    Res.string.about_thanks_lyrics to listOf("LRCLIB", "Genius"),
    Res.string.about_thanks_downloads to listOf("yt-dlp", "youtubedl-android", "FFmpeg"),
    Res.string.about_thanks_tags to listOf("MusicBrainz", "Cover Art Archive", "Deezer", "iTunes", "jaudiotagger"),
    Res.string.about_thanks_sound to listOf("JavaCV", "cognito perceptu · Freesound", "Google Sans", "JetBrains Mono", "IBM Plex Mono"),
    Res.string.about_thanks_base to listOf("Jetpack Compose", "Compose Multiplatform", "Media3", "Material 3", "GitHub"),
)

private val Night = Color(0xFF0B0A12)
private val OnNight = Color(0xFFECE8F4)
private val Gold = listOf(Color(0xFFFFF3D6), Color(0xFFFFD58A), Color.White, Color(0xFFE6D2FF))

/** When things come in, in seconds from the start. */
private const val HERO_AT = 0.3f
private const val TITLE_AT = 1.1f
private const val NAMES_AT = 2.6f
private const val NAME_EVERY = 0.75f
private val PROJECTS_AT = NAMES_AT + People.size * NAME_EVERY + 2.1f
/** The breath after the first name before the rest. */
private const val FIRST_PAUSE = 0.9f
private const val GROUP_EVERY = 1.1f
private const val CHIP_EVERY = 0.14f
private val CLAUDE_AT = PROJECTS_AT + 0.8f + Projects.size * GROUP_EVERY + 1.4f
/** How long one thing takes to come in. */
private const val POP = 1.1f

/**
 * The special thanks over the whole screen, like the end of a film, in Material 3 Expressive:
 * big soft shapes turning slowly on a night sky, a cookie with a heart, the title, then the
 * names popping in one by one as shimmering pills with their own shapes, and after them the
 * projects the app stands on. The page then glides down to them. A tap or Back closes it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SpecialThanks(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val clock = remember { mutableFloatStateOf(0f) }
        val time by clock
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { clock.floatValue = (it - start) / 1e9f }
        }
        val scroll = rememberScrollState()
        LaunchedEffect(Unit) {
            // Once the names are in, the page glides down to the projects, slowly.
            delay((PROJECTS_AT * 1000).toLong() - 600)
            scroll.animateScrollTo(scroll.maxValue, tween(8_500, easing = FastOutSlowInEasing))
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = ease(clock.floatValue / 1.4f) }
                .background(Night)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    // Not in the first moment: the pull that opened it may still be letting go.
                    if (clock.floatValue > 1f) onDismiss()
                },
        ) {
            val w = maxWidth
            val h = maxHeight
            // Big shapes in the names' colours, drifting and turning very slowly behind everything.
            Decor.forEachIndexed { i, d ->
                Box(
                    Modifier
                        .size(d.size.dp)
                        .graphicsLayer {
                            val t = clock.floatValue
                            translationX = (w * d.x).toPx() - d.size.dp.toPx() / 2 + 40.dp.toPx() * sin(t * 0.06f + i)
                            translationY = (h * d.y).toPx() - d.size.dp.toPx() / 2 + 50.dp.toPx() * cos(t * 0.05f + i * 1.3f)
                            rotationZ = t * d.spin
                            alpha = ease((t - 0.2f - i * 0.25f) / 2f)
                        }
                        .background(d.color.copy(alpha = 0.13f), d.shape.toShape()),
                )
            }
            Stars(clock)

            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // A cookie with a heart, popping in with a spring and turning slowly.
                Box(
                    Modifier
                        .size(112.dp)
                        .graphicsLayer {
                            val t = clock.floatValue
                            val s = popScale(t - HERO_AT)
                            scaleX = s
                            scaleY = s
                            alpha = ease((t - HERO_AT) / 0.5f)
                            rotationZ = t * 9f
                        }
                        .background(Brush.linearGradient(listOf(Color(0xFFFFD58A), Color(0xFFFF8FB1))), MaterialShapes.Cookie12Sided.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    // Drawn, not "♥": the phone would show its red emoji instead.
                    Box(
                        Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                val t = clock.floatValue
                                rotationZ = -t * 9f
                                val beat = 1f + 0.08f * heartbeat(t)
                                scaleX = beat
                                scaleY = beat
                            }
                            .background(Night, MaterialShapes.Heart.toShape()),
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(Res.string.about_thanks),
                    style = MaterialTheme.typography.displaySmallEmphasized.copy(brush = shimmer(Gold, time, 0f, 40f)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.appear(clock, TITLE_AT),
                )
                Spacer(Modifier.height(32.dp))
                FlowRow(
                    Modifier.widthIn(max = 620.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The first comes alone, and the rest only after a breath.
                    People.forEachIndexed { i, p ->
                        val at = NAMES_AT + i * NAME_EVERY + if (i == 0) 0f else FIRST_PAUSE
                        NameChip(p, i, time, Modifier.appear(clock, at))
                    }
                }

                Spacer(Modifier.height(56.dp))
                Text(
                    stringResource(Res.string.about_thanks_projects),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = OnNight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.appear(clock, PROJECTS_AT),
                )
                Spacer(Modifier.height(24.dp))
                Projects.forEachIndexed { g, (label, names) ->
                    val at = PROJECTS_AT + 0.8f + g * GROUP_EVERY
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = People[g * 2].colors[0],
                        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp).appear(clock, at),
                    )
                    FlowRow(
                        Modifier.widthIn(max = 620.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        names.forEachIndexed { i, name ->
                            Text(
                                name,
                                style = MaterialTheme.typography.titleSmall,
                                color = OnNight,
                                modifier = Modifier
                                    .appear(clock, at + 0.2f + i * CHIP_EVERY)
                                    .background(lerp(Night, People[g * 2].colors[1], 0.22f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                // Last of all, the one who helped write it.
                Spacer(Modifier.height(48.dp))
                Column(
                    Modifier.appear(clock, CLAUDE_AT),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        Modifier
                            .background(lerp(Night, ClaudeColor, 0.22f), CircleShape)
                            .padding(start = 12.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ClaudeSpark(clock, Modifier.size(28.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "Claude",
                            style = MaterialTheme.typography.titleLargeEmphasized.copy(brush = shimmer(ClaudeColors, time, 40f, 45f)),
                        )
                    }
                    Text(
                        stringResource(Res.string.about_thanks_claude),
                        style = MaterialTheme.typography.labelLarge,
                        color = OnNight.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(56.dp))
            }
        }
    }
}

/** A name in a pill of its colour, with its own little shape turning beside it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NameChip(p: Person, index: Int, time: Float, modifier: Modifier) {
    val shape: Shape = p.shape.toShape()
    // The first one, quietly: a heart that beats with the big one
    // and a thread of gold in the shimmer.
    val first = index == 0
    val beat = heartbeat(time)
    Row(
        modifier
            .background(lerp(Night, p.colors[1], 0.24f), CircleShape)
            .padding(start = 12.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .graphicsLayer {
                    if (first) {
                        val s = 1f + 0.12f * beat
                        scaleX = s
                        scaleY = s
                    } else {
                        rotationZ = time * (if (index % 2 == 0) 24f else -24f)
                    }
                }
                .background(Brush.linearGradient(p.colors.take(2)), shape),
        )
        Spacer(Modifier.size(12.dp))
        val colors = if (first) p.colors + Gold[1] else p.colors
        Text(
            p.name,
            style = MaterialTheme.typography.titleLargeEmphasized.copy(
                brush = shimmer(colors, time, index * 97f, if (first) 38f else 55f + index * 4f),
            ),
        )
    }
}

/** 0 → 1 → 0, twice a beat: the big heart's rhythm, shared with the first name. */
private fun heartbeat(time: Float): Float = sin(time * 2.4f).let { it * it }

private val ClaudeColor = Color(0xFFD97757)
private val ClaudeColors = listOf(Color(0xFFD97757), Color(0xFFF0A07E), Color(0xFFFFE1D2))

/** A little spark of rounded rays, long and short in turn, in Claude's terracotta, turning slowly. */
@Composable
private fun ClaudeSpark(clock: State<Float>, modifier: Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2
        val turn = clock.value * 20f
        val rays = 12
        for (i in 0 until rays) {
            val a = (turn + i * 360f / rays) * PI.toFloat() / 180f
            val len = r * (if (i % 2 == 0) 1f else 0.72f)
            drawLine(
                ClaudeColor,
                start = c + Offset(cos(a), sin(a)) * (r * 0.12f),
                end = c + Offset(cos(a), sin(a)) * len,
                strokeWidth = r * 0.2f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/** Pops in at [at] seconds: a spring past full size and back, fading in on the way up. */
private fun Modifier.appear(clock: State<Float>, at: Float): Modifier = graphicsLayer {
    val t = clock.value - at
    val s = popScale(t)
    scaleX = s
    scaleY = s
    alpha = ease(t / (POP * 0.6f))
    translationY = (1f - ease(t / POP)) * 24.dp.toPx()
}

/** 0.6 → a little past 1 → 1 over [POP] seconds, like an expressive spatial spring. */
private fun popScale(t: Float): Float {
    val x = (t / POP).coerceIn(0f, 1f)
    val c1 = 2.2f
    val c3 = c1 + 1f
    val back = 1f + c3 * (x - 1f).let { it * it * it } + c1 * (x - 1f).let { it * it }
    return 0.6f + 0.4f * back
}

/** Colours flowing sideways through the letters, back and forth. */
private fun shimmer(colors: List<Color>, time: Float, phase: Float, speed: Float): Brush {
    val shift = time * speed + phase
    return Brush.linearGradient(
        colors = colors + colors.first(),
        start = Offset(shift, 0f),
        end = Offset(shift + 260f, 90f),
        tileMode = TileMode.Mirror,
    )
}

/** 0 → 1, slow at both ends; clamped. */
private fun ease(t: Float): Float = 0.5f - 0.5f * cos(t.coerceIn(0f, 1f) * PI.toFloat())

private class DecorShape(val shape: RoundedPolygon, val color: Color, val size: Int, val x: Float, val y: Float, val spin: Float)

private val Decor = listOf(
    DecorShape(MaterialShapes.Cookie12Sided, Color(0xFFB57BFF), 340, 0.08f, 0.12f, 3f),
    DecorShape(MaterialShapes.Flower, Color(0xFFFF7EB3), 260, 0.95f, 0.28f, -4f),
    DecorShape(MaterialShapes.Sunny, Color(0xFFFFC857), 220, 0.15f, 0.62f, 5f),
    DecorShape(MaterialShapes.Clover8Leaf, Color(0xFF2EC4B6), 300, 0.88f, 0.82f, -3f),
    DecorShape(MaterialShapes.SoftBurst, Color(0xFF5B8CFF), 200, 0.55f, 1.02f, 4f),
)

/** Small stars rising slowly and twinkling. */
@Composable
private fun Stars(clock: State<Float>) {
    val stars = remember {
        val r = Random(7)
        List(80) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.4f, 0.004f + r.nextFloat() * 0.01f, 0.8f + r.nextFloat() * 2.2f, r.nextFloat() * 6.3f) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value
        for (s in stars) {
            // x, y, radius, rise speed, twinkle speed, twinkle phase
            val y = ((s[1] - t * s[3]) % 1f + 1f) % 1f
            val a = 0.2f + 0.8f * (0.5f + 0.5f * sin(t * s[4] + s[5]))
            drawCircle(Color.White.copy(alpha = a * 0.7f), s[2].dp.toPx(), Offset(s[0] * size.width, y * size.height))
        }
    }
}
