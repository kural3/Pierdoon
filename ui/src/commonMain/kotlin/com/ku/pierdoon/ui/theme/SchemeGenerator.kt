package com.ku.pierdoon.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Material-style tonal schemes from a single seed colour, without pulling in a library.
 *
 * Tones are CIE L* — the same lightness scale Material's HCT uses — so "tone 40" here
 * means what it means in the M3 spec. Hue and chroma come from CIE LCh(ab); chroma is
 * reduced per tone until the colour fits the sRGB gamut.
 */
object SchemeGenerator {

    private class Palette(val hue: Double, val chroma: Double) {
        fun tone(t: Int): Color = Color(Lab.lchToArgb(t.toDouble(), chroma, hue))
    }

    /**
     * Seed for black, white and grey art: [scheme] turns it into a colourless palette instead
     * of inventing a hue the picture doesn't have. Pickers never produce a grey seed.
     */
    const val MONOCHROME_SEED: Int = 0xFF808080.toInt()

    fun scheme(seed: Int, dark: Boolean): ColorScheme {
        val (_, c, h) = Lab.argbToLch(seed)
        val mono = c < MONO_CHROMA
        val primary = Palette(h, if (mono) 0.0 else maxOf(c, 48.0))
        val secondary = Palette(h, if (mono) 0.0 else 16.0)
        val tertiary = Palette((h + 60.0) % 360.0, if (mono) 0.0 else 28.0)
        val neutral = Palette(h, if (mono) 0.0 else 4.0)
        val neutralVariant = Palette(h, if (mono) 0.0 else 9.0)
        val error = Palette(30.0, 80.0)

        return if (!dark) lightColorScheme(
            primary = primary.tone(40), onPrimary = primary.tone(100),
            primaryContainer = primary.tone(90), onPrimaryContainer = primary.tone(10),
            inversePrimary = primary.tone(80),
            secondary = secondary.tone(40), onSecondary = secondary.tone(100),
            secondaryContainer = secondary.tone(90), onSecondaryContainer = secondary.tone(10),
            tertiary = tertiary.tone(40), onTertiary = tertiary.tone(100),
            tertiaryContainer = tertiary.tone(90), onTertiaryContainer = tertiary.tone(10),
            error = error.tone(40), onError = error.tone(100),
            errorContainer = error.tone(90), onErrorContainer = error.tone(10),
            background = neutral.tone(98), onBackground = neutral.tone(10),
            surface = neutral.tone(98), onSurface = neutral.tone(10),
            surfaceVariant = neutralVariant.tone(90), onSurfaceVariant = neutralVariant.tone(30),
            surfaceTint = primary.tone(40),
            inverseSurface = neutral.tone(20), inverseOnSurface = neutral.tone(95),
            outline = neutralVariant.tone(50), outlineVariant = neutralVariant.tone(80),
            surfaceBright = neutral.tone(98), surfaceDim = neutral.tone(87),
            surfaceContainerLowest = neutral.tone(100), surfaceContainerLow = neutral.tone(96),
            surfaceContainer = neutral.tone(94), surfaceContainerHigh = neutral.tone(92),
            surfaceContainerHighest = neutral.tone(90),
        ) else darkColorScheme(
            primary = primary.tone(80), onPrimary = primary.tone(20),
            primaryContainer = primary.tone(30), onPrimaryContainer = primary.tone(90),
            inversePrimary = primary.tone(40),
            secondary = secondary.tone(80), onSecondary = secondary.tone(20),
            secondaryContainer = secondary.tone(30), onSecondaryContainer = secondary.tone(90),
            tertiary = tertiary.tone(80), onTertiary = tertiary.tone(20),
            tertiaryContainer = tertiary.tone(30), onTertiaryContainer = tertiary.tone(90),
            error = error.tone(80), onError = error.tone(20),
            errorContainer = error.tone(30), onErrorContainer = error.tone(90),
            background = neutral.tone(6), onBackground = neutral.tone(90),
            surface = neutral.tone(6), onSurface = neutral.tone(90),
            surfaceVariant = neutralVariant.tone(30), onSurfaceVariant = neutralVariant.tone(80),
            surfaceTint = primary.tone(80),
            inverseSurface = neutral.tone(90), inverseOnSurface = neutral.tone(20),
            outline = neutralVariant.tone(60), outlineVariant = neutralVariant.tone(30),
            surfaceBright = neutral.tone(24), surfaceDim = neutral.tone(6),
            surfaceContainerLowest = neutral.tone(4), surfaceContainerLow = neutral.tone(10),
            surfaceContainer = neutral.tone(12), surfaceContainerHigh = neutral.tone(17),
            surfaceContainerHighest = neutral.tone(22),
        )
    }

    /** Seed with the given hue at a lively, always-in-gamut chroma; used by the hue picker. */
    fun seedFromHue(hue: Float): Int = Lab.lchToArgb(50.0, 60.0, hue.toDouble())

    private const val MONO_CHROMA = 4.0

    fun hueOf(argb: Int): Float = Lab.argbToLch(argb).third.toFloat()

    /**
     * Picks a theme seed from a tiny image: chroma-weighted hue histogram, so a small vivid
     * accent beats a large grey area. (Near-)greyscale art gets [MONOCHROME_SEED].
     */
    fun seedFromPixels(pixels: IntArray): Int {
        val bins = DoubleArray(36)
        val chromaSum = DoubleArray(36)
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val (l, c, h) = Lab.argbToLch(p)
            if (l < 15 || l > 95 || c < 12) continue
            val bin = ((h / 10.0).toInt()).coerceIn(0, 35)
            bins[bin] += c
            chromaSum[bin] += c
        }
        var best = -1
        var bestScore = 0.0
        for (i in bins.indices) {
            // Smooth over neighbours so a hue split across two bins still wins.
            val score = bins[i] + 0.5 * (bins[(i + 35) % 36] + bins[(i + 1) % 36])
            if (score > bestScore) { bestScore = score; best = i }
        }
        if (best < 0 || bestScore < pixels.size * 2.0) return MONOCHROME_SEED
        val hue = best * 10.0 + 5.0
        return Lab.lchToArgb(50.0, 60.0, hue)
    }
}

/** One-shot cross-fade between schemes (a single float drives every colour). */
fun lerpScheme(a: ColorScheme, b: ColorScheme, t: Float): ColorScheme {
    if (t >= 1f) return b
    if (t <= 0f) return a
    fun l(x: Color, y: Color) = lerp(x, y, t)
    return b.copy(
        primary = l(a.primary, b.primary), onPrimary = l(a.onPrimary, b.onPrimary),
        primaryContainer = l(a.primaryContainer, b.primaryContainer),
        onPrimaryContainer = l(a.onPrimaryContainer, b.onPrimaryContainer),
        inversePrimary = l(a.inversePrimary, b.inversePrimary),
        secondary = l(a.secondary, b.secondary), onSecondary = l(a.onSecondary, b.onSecondary),
        secondaryContainer = l(a.secondaryContainer, b.secondaryContainer),
        onSecondaryContainer = l(a.onSecondaryContainer, b.onSecondaryContainer),
        tertiary = l(a.tertiary, b.tertiary), onTertiary = l(a.onTertiary, b.onTertiary),
        tertiaryContainer = l(a.tertiaryContainer, b.tertiaryContainer),
        onTertiaryContainer = l(a.onTertiaryContainer, b.onTertiaryContainer),
        background = l(a.background, b.background), onBackground = l(a.onBackground, b.onBackground),
        surface = l(a.surface, b.surface), onSurface = l(a.onSurface, b.onSurface),
        surfaceVariant = l(a.surfaceVariant, b.surfaceVariant),
        onSurfaceVariant = l(a.onSurfaceVariant, b.onSurfaceVariant),
        surfaceTint = l(a.surfaceTint, b.surfaceTint),
        inverseSurface = l(a.inverseSurface, b.inverseSurface),
        inverseOnSurface = l(a.inverseOnSurface, b.inverseOnSurface),
        outline = l(a.outline, b.outline), outlineVariant = l(a.outlineVariant, b.outlineVariant),
        surfaceBright = l(a.surfaceBright, b.surfaceBright), surfaceDim = l(a.surfaceDim, b.surfaceDim),
        surfaceContainerLowest = l(a.surfaceContainerLowest, b.surfaceContainerLowest),
        surfaceContainerLow = l(a.surfaceContainerLow, b.surfaceContainerLow),
        surfaceContainer = l(a.surfaceContainer, b.surfaceContainer),
        surfaceContainerHigh = l(a.surfaceContainerHigh, b.surfaceContainerHigh),
        surfaceContainerHighest = l(a.surfaceContainerHighest, b.surfaceContainerHighest),
    )
}

/** sRGB <-> CIE L*a*b* / LCh(ab), D65. */
private object Lab {
    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883
    private const val E = 216.0 / 24389.0
    private const val K = 24389.0 / 27.0

    private fun toLinear(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun fromLinear(v: Double): Double =
        if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055

    private fun f(t: Double) = if (t > E) cbrt(t) else (K * t + 16.0) / 116.0
    private fun fInv(t: Double): Double { val t3 = t * t * t; return if (t3 > E) t3 else (116.0 * t - 16.0) / K }

    fun argbToLch(argb: Int): Triple<Double, Double, Double> {
        val r = toLinear((argb shr 16) and 0xFF)
        val g = toLinear((argb shr 8) and 0xFF)
        val b = toLinear(argb and 0xFF)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / XN
        val y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / YN
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / ZN
        val fx = f(x); val fy = f(y); val fz = f(z)
        val l = 116.0 * fy - 16.0
        val aa = 500.0 * (fx - fy)
        val bb = 200.0 * (fy - fz)
        val h = (Math.toDegrees(atan2(bb, aa)) + 360.0) % 360.0
        return Triple(l, hypot(aa, bb), h)
    }

    /** Returns null-free ARGB; chroma is binary-searched down until the colour is displayable. */
    fun lchToArgb(l: Double, chroma: Double, hue: Double): Int {
        if (l <= 0.0) return 0xFF000000.toInt()
        if (l >= 100.0) return 0xFFFFFFFF.toInt()
        rgbOrNull(l, chroma, hue)?.let { return it }
        var lo = 0.0
        var hi = chroma
        var best = rgbOrNull(l, 0.0, hue) ?: 0xFF808080.toInt()
        repeat(14) {
            val mid = (lo + hi) / 2
            val c = rgbOrNull(l, mid, hue)
            if (c != null) { best = c; lo = mid } else hi = mid
        }
        return best
    }

    private fun rgbOrNull(l: Double, c: Double, hDeg: Double): Int? {
        val h = Math.toRadians(hDeg)
        val aa = c * cos(h)
        val bb = c * sin(h)
        val fy = (l + 16.0) / 116.0
        val x = XN * fInv(fy + aa / 500.0)
        val y = YN * fInv(fy)
        val z = ZN * fInv(fy - bb / 200.0)
        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val b = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        val eps = 1e-4
        if (r < -eps || r > 1 + eps || g < -eps || g > 1 + eps || b < -eps || b > 1 + eps) return null
        fun ch(v: Double) = (fromLinear(v.coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
