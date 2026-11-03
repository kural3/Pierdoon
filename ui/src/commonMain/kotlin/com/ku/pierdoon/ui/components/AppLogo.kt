package com.ku.pierdoon.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// The app's icon, drawn rather than loaded so it is sharp at any size: the note-shaped P on a
// graphite Material "cookie". The same geometry as desktop/icons/Icon.java.

private val Mark = Color(0xFFEEEAE3)
private val Top = Color(0xFF3D3E43)
private val Bottom = Color(0xFF232428)
private val Side = Color(0xFF4A4B51)

private const val LOBES = 10
private const val LOBE_DEPTH = 0.042f
private const val RADIUS = 0.46f

/** The cookie's outline around the origin, in units of the icon's size. */
private val Outline: List<Offset> by lazy {
    List(OUTLINE_POINTS) { i ->
        val t = i * 2 * PI / OUTLINE_POINTS
        val r = RADIUS * (1 + LOBE_DEPTH * cos(LOBES * t))
        Offset((r * sin(t)).toFloat(), (-r * cos(t)).toFloat())
    }
}
private const val OUTLINE_POINTS = 200

/** The P, in the Android foreground's 108-unit space. */
private val MarkPath: Path by lazy {
    fun rect(l: Float, t: Float, r: Float, b: Float) = Path().apply { addRect(Rect(l, t, r, b)) }
    fun halfDisk(cx: Float, cy: Float, r: Float) = Path().apply {
        moveTo(cx, cy - r)
        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), -90f, 180f, false)
        close()
    }
    fun disk(cx: Float, cy: Float, r: Float) = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    var outer = Path.combine(PathOperation.Union, rect(52f, 33f, 57.5f, 55f), halfDisk(57.5f, 44f, 11f))
    outer = Path.combine(PathOperation.Union, outer, rect(52f, 33f, 57f, 66.5f))
    outer = Path.combine(PathOperation.Union, outer, disk(48.5f, 66.5f, 8.5f))
    val hole = Path.combine(PathOperation.Union, rect(57f, 38f, 57.5f, 50f), halfDisk(57.5f, 44f, 6f))
    Path.combine(PathOperation.Difference, outer, hole)
}

private fun cookiePath(size: Float): Path = Path().apply {
    Outline.forEachIndexed { i, p ->
        val x = size / 2 + p.x * size
        val y = size / 2 + p.y * size
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * The face of the icon in a [size]-px square at the origin. [gloss] lays a soft light on it at
 * [light] (fractions of the size), for the turning logo.
 */
private fun DrawScope.drawLogoFace(size: Float, cookie: Path, gloss: Float = 0f, light: Offset = Offset(0.3f, 0.2f)) {
    drawPath(cookie, Brush.verticalGradient(listOf(Top, Bottom), 0f, size))
    if (gloss > 0f) {
        drawPath(
            cookie,
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = gloss), Color.Transparent),
                center = Offset(size * light.x, size * light.y),
                radius = size * 0.7f,
            ),
        )
    }
    // A faint light rim, so the graphite doesn't sink into a dark background.
    drawPath(
        cookie,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.27f), Color.White.copy(alpha = 0.05f)), 0f, size),
        style = Stroke(max(size * 0.012f, 1f)),
    )
    val k = size / 108f * (if (size <= 32f) 1.62f else 1.45f)
    withTransform({
        translate(size / 2, size / 2)
        scale(k, k, pivot = Offset.Zero)
        translate(-54.25f, -54f)
    }) {
        drawPath(MarkPath, Mark)
    }
}

private class PathCache {
    private var size = -1f
    private var path = Path()
    fun cookie(s: Float): Path {
        if (s != size) { size = s; path = cookiePath(s) }
        return path
    }
}

/**
 * The icon as a thick coin in space: drag it to turn it any way, let go and it spins on and
 * settles facing you; a tap gives it a full turn. Left alone it sways a little.
 */
@Composable
fun SpinningLogo(modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val yaw = remember { Animatable(0f) }
    val pitch = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var time by remember { mutableFloatStateOf(0f) }
    val cache = remember { PathCache() }
    val sparkles = remember { Sparkles() }
    LaunchedEffect(Unit) {
        var start = 0L
        var last = 0f
        var lastYaw = yaw.value
        var lastPitch = pitch.value
        while (true) withFrameNanos { now ->
            if (start == 0L) start = now
            time = (now - start) / 1e9f
            val dt = (time - last).coerceIn(0f, 0.1f)
            last = time
            // Stars come off the rim as fast as it turns: none at rest, a slow trickle on a spin.
            val dYaw = yaw.value - lastYaw
            val dPitch = pitch.value - lastPitch
            lastYaw = yaw.value
            lastPitch = pitch.value
            sparkles.step(dt, kotlin.math.abs(dYaw) + kotlin.math.abs(dPitch), if (dYaw >= 0) 1f else -1f)
        }
    }
    // The idle sway eases in once the logo has come to rest, and out as soon as it's touched.
    val sway by animateFloatAsState(
        if (dragging || yaw.isRunning || pitch.isRunning) 0f else 1f,
        tween(900),
        label = "logoSway",
    )

    fun settle(velocityX: Float, velocityY: Float) {
        scope.launch {
            yaw.animateDecay(velocityX, exponentialDecay(frictionMultiplier = 1.4f))
            val front = (yaw.value / 360f).roundToInt() * 360f
            yaw.animateTo(front, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
        }
        scope.launch {
            pitch.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow), initialVelocity = velocityY)
        }
    }

    Canvas(
        modifier
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        tracker.resetTracking()
                        scope.launch { yaw.stop() }
                        scope.launch { pitch.stop() }
                    },
                    onDragEnd = {
                        dragging = false
                        val v = tracker.calculateVelocity()
                        val k = DEGREES_PER_WIDTH / size.width
                        settle(v.x * k, -v.y * k)
                    },
                    onDragCancel = {
                        dragging = false
                        settle(0f, 0f)
                    },
                ) { change, drag ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val k = DEGREES_PER_WIDTH / size.width
                    scope.launch {
                        yaw.snapTo(yaw.value + drag.x * k)
                        pitch.snapTo((pitch.value - drag.y * k).coerceIn(-MAX_PITCH, MAX_PITCH))
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope.launch {
                        val next = ((yaw.value / 360f).roundToInt() + 1) * 360f
                        yaw.animateTo(next, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow))
                    }
                }
            },
    ) {
        val y = yaw.value + sway * 14f * sin(time * 0.9f)
        val p = pitch.value + sway * 7f * sin(time * 0.7f + 1f)
        drawCoin(y, p, cache)
        drawSparkles(sparkles, time)
    }
}

/** Little four-pointed stars drifting off the coin's rim while it turns. */
private class Sparkles {
    class Star(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val born: Float, val life: Float, val size: Float, val tint: Int, val twinkle: Float,
    )
    val stars = ArrayList<Star>()
    private var age = 0f
    private var owed = 0f
    private val random = kotlin.random.Random

    /** [turned]: degrees turned since the last frame; [direction]: the way it spins. */
    fun step(dt: Float, turned: Float, direction: Float) {
        age += dt
        owed += min(turned, 40f) * SPARKLES_PER_DEGREE
        while (owed >= 1f && stars.size < MAX_SPARKLES) {
            owed -= 1f
            val a = random.nextFloat() * 2f * PI.toFloat()
            val speed = 0.06f + random.nextFloat() * 0.12f
            val r = RADIUS * (0.85f + random.nextFloat() * 0.1f)
            stars += Star(
                x = cos(a) * r, y = sin(a) * r,
                // Outward, and a little along the turn, like sparks flung off.
                vx = cos(a) * speed - sin(a) * 0.05f * direction,
                vy = sin(a) * speed - 0.03f,
                born = age,
                life = 1.6f + random.nextFloat() * 1.2f,
                size = 0.012f + random.nextFloat() * 0.022f,
                tint = random.nextInt(4),
                twinkle = random.nextFloat() * 6f,
            )
        }
        if (owed > 1f) owed = 1f
        val drag = 1f - 0.7f * dt
        val it = stars.iterator()
        while (it.hasNext()) {
            val s = it.next()
            if (age - s.born > s.life) { it.remove(); continue }
            s.x += s.vx * dt; s.y += s.vy * dt
            s.vx *= drag; s.vy *= drag
            s.vy -= 0.01f * dt // they float up, slowly
        }
    }

    fun ageOf(s: Star) = age - s.born
}

private val SparkleTints = listOf(Color.White, Color(0xFFFFF3D6), Color(0xFFEEEAE3), Color(0xFFDCE8FF))
private const val SPARKLES_PER_DEGREE = 0.05f
private const val MAX_SPARKLES = 90

private fun DrawScope.drawSparkles(sparkles: Sparkles, time: Float) {
    if (sparkles.stars.isEmpty()) return
    val unit = min(size.width, size.height) * 0.74f
    val cx = size.width / 2
    val cy = size.height / 2
    val star = Path()
    for (s in sparkles.stars) {
        val t = sparkles.ageOf(s) / s.life
        // In quickly, out slowly, with a gentle twinkle.
        val fade = min(1f, t * 8f) * (1f - t) * (1f - t)
        val alpha = (fade * (0.75f + 0.25f * sin(time * 9f + s.twinkle))).coerceIn(0f, 1f)
        if (alpha <= 0.01f) continue
        val x = cx + s.x * unit
        val y = cy + s.y * unit
        val r = s.size * unit * (0.6f + 0.4f * (1f - t))
        val w = r * 0.28f
        star.reset()
        star.moveTo(x, y - r)
        star.quadraticTo(x + w * 0.3f, y - w * 0.3f, x + r, y)
        star.quadraticTo(x + w * 0.3f, y + w * 0.3f, x, y + r)
        star.quadraticTo(x - w * 0.3f, y + w * 0.3f, x - r, y)
        star.quadraticTo(x - w * 0.3f, y - w * 0.3f, x, y - r)
        star.close()
        val color = SparkleTints[s.tint]
        drawCircle(color.copy(alpha = alpha * 0.18f), radius = r * 0.9f, center = Offset(x, y))
        drawPath(star, color.copy(alpha = alpha))
    }
}

private const val DEGREES_PER_WIDTH = 220f
private const val MAX_PITCH = 70f
/** Half the coin's thickness, in units of its size. */
private const val HALF_DEPTH = 0.05f
/** Camera distance, in units of the coin's size: closer is more perspective. */
private const val CAMERA = 3.2f

private fun DrawScope.drawCoin(yawDeg: Float, pitchDeg: Float, cache: PathCache) {
    val s = min(size.width, size.height) * 0.74f
    val cx = size.width / 2
    val cy = size.height / 2
    val ya = yawDeg * PI.toFloat() / 180f
    val pa = pitchDeg * PI.toFloat() / 180f
    val cyw = cos(ya); val syw = sin(ya); val cp = cos(pa); val sp = sin(pa)
    // R = Ry(yaw) · Rx(pitch), in screen axes: x right, y down, z toward the viewer.
    val r00 = cyw; val r01 = syw * sp; val r02 = syw * cp
    val r10 = 0f; val r11 = cp; val r12 = -sp
    val r20 = -syw; val r21 = cyw * sp; val r22 = cyw * cp

    fun rx(u: Float, v: Float, z: Float) = r00 * u + r01 * v + r02 * z
    fun ry(u: Float, v: Float, z: Float) = r10 * u + r11 * v + r12 * z
    fun rz(u: Float, v: Float, z: Float) = r20 * u + r21 * v + r22 * z
    fun screen(u: Float, v: Float, z: Float): Offset {
        val f = CAMERA / (CAMERA - rz(u, v, z))
        return Offset(cx + rx(u, v, z) * f * s, cy + ry(u, v, z) * f * s)
    }
    // Light from the upper left, a little toward the viewer.
    val ln = sqrt(0.35f * 0.35f + 0.55f * 0.55f + 0.75f * 0.75f)
    val lx = -0.35f / ln; val ly = -0.55f / ln; val lz = 0.75f / ln

    // A soft shadow under it, narrower as it turns edge-on.
    val wide = 0.45f + 0.55f * max(abs(r22), abs(cyw) * 0.6f)
    drawOval(
        Brush.radialGradient(
            listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent),
            center = Offset(cx, cy + s * 0.62f),
            radius = s * 0.45f,
        ),
        topLeft = Offset(cx - s * 0.45f * wide, cy + s * 0.55f),
        size = androidx.compose.ui.geometry.Size(s * 0.9f * wide, s * 0.14f),
    )

    // The rim: one quad per step of the outline, those turned toward the camera, shaded by the light.
    val n = Outline.size
    val quad = Path()
    for (i in 0 until n) {
        val a = Outline[i]
        val b = Outline[(i + 1) % n]
        val mx = (a.x + b.x) / 2; val my = (a.y + b.y) / 2
        var nx = b.y - a.y; var ny = -(b.x - a.x)
        if (nx * mx + ny * my < 0) { nx = -nx; ny = -ny }
        val nl = hypot(nx, ny); nx /= nl; ny /= nl
        val wx = rx(nx, ny, 0f); val wy = ry(nx, ny, 0f); val wz = rz(nx, ny, 0f)
        // Toward the camera: from the edge's middle to the eye at (0, 0, CAMERA).
        val px = rx(mx, my, 0f); val py = ry(mx, my, 0f); val pz = rz(mx, my, 0f)
        if (wx * -px + wy * -py + wz * (CAMERA - pz) <= 0f) continue
        val lit = 0.28f + 0.72f * max(0f, wx * lx + wy * ly + wz * lz)
        val spec = max(0f, wx * lx + wy * ly + wz * lz).let { it * it * it * it } * 0.35f
        val color = Color(
            (Side.red * lit + spec).coerceAtMost(1f),
            (Side.green * lit + spec).coerceAtMost(1f),
            (Side.blue * lit + spec).coerceAtMost(1f),
        )
        val p1 = screen(a.x, a.y, HALF_DEPTH); val p2 = screen(b.x, b.y, HALF_DEPTH)
        val p3 = screen(b.x, b.y, -HALF_DEPTH); val p4 = screen(a.x, a.y, -HALF_DEPTH)
        quad.reset()
        quad.moveTo(p1.x, p1.y); quad.lineTo(p2.x, p2.y); quad.lineTo(p3.x, p3.y); quad.lineTo(p4.x, p4.y); quad.close()
        drawPath(quad, color)
        drawPath(quad, color, style = Stroke(1.2f)) // closes the hairline seams between quads
    }

    // The face toward the camera, drawn flat and mapped onto its plane by a perspective matrix.
    // A face is seen when its normal (0, 0, ±1) turned points at the eye from its centre:
    // ±r22 · CAMERA > HALF_DEPTH. Edge-on, neither is.
    val mirror = when {
        r22 * CAMERA > HALF_DEPTH -> 1f
        -r22 * CAMERA > HALF_DEPTH -> -1f
        else -> return
    }
    val z = HALF_DEPTH * mirror
    val m = faceMatrix(s, cx, cy, z, mirror, r00, r01, r02, r10, r11, r12, r20, r21, r22)
    // The face's normal, for how much light it catches.
    val fnx = r02 * mirror; val fny = r12 * mirror; val fnz = r22 * mirror
    val catch = max(0f, fnx * lx + fny * ly + fnz * lz)
    withTransform({ transform(m) }) {
        drawLogoFace(
            s,
            cache.cookie(s),
            gloss = 0.06f + 0.16f * catch * catch,
            light = Offset(0.5f - 0.45f * sin(ya) * mirror - 0.15f, 0.5f + 0.45f * sin(pa) - 0.25f),
        )
    }
}

private fun abs(x: Float) = kotlin.math.abs(x)

/**
 * Maps the flat face (px in an [s]-wide square) onto the plane z = [z] of the turned coin, with
 * perspective: (lx, ly, 1) → (x·w, y·w, w).
 */
private fun faceMatrix(
    s: Float, cx: Float, cy: Float, z: Float, mirror: Float,
    r00: Float, r01: Float, r02: Float,
    r10: Float, r11: Float, r12: Float,
    r20: Float, r21: Float, r22: Float,
): Matrix {
    // u = (lx / s − ½) · mirror, v = ly / s − ½
    val au = mirror / s; val bu = -0.5f * mirror
    val av = 1f / s; val bv = -0.5f
    // The turned point: X = R · (u, v, z), linear in lx and ly.
    val xL = r00 * au; val xM = r01 * av; val x0 = r00 * bu + r01 * bv + r02 * z
    val yL = r10 * au; val yM = r11 * av; val y0 = r10 * bu + r11 * bv + r12 * z
    val zL = r20 * au; val zM = r21 * av; val z0 = r20 * bu + r21 * bv + r22 * z
    // w = CAMERA − X.z; screen = c + X · s · CAMERA / w  →  homogeneous: c·w + s·CAMERA·X
    val wL = -zL; val wM = -zM; val w0 = CAMERA - z0
    val k = s * CAMERA
    val m = Matrix()
    val v = m.values
    v[0] = cx * wL + k * xL; v[4] = cx * wM + k * xM; v[12] = cx * w0 + k * x0
    v[1] = cy * wL + k * yL; v[5] = cy * wM + k * yM; v[13] = cy * w0 + k * y0
    v[3] = wL; v[7] = wM; v[15] = w0
    v[2] = 0f; v[6] = 0f; v[8] = 0f; v[9] = 0f; v[10] = 1f; v[11] = 0f; v[14] = 0f
    return m
}
