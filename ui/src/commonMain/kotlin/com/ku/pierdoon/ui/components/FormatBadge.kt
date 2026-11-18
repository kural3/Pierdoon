package com.ku.pierdoon.ui.components

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ku.pierdoon.data.AudioTier
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.theme.LocalUiPrefs

/** [FormatBadge] for [song]'s file ("FLAC", "MP3"…) when the badges are on in Settings; nothing otherwise. */
@Composable
fun FormatBadge(song: Song, modifier: Modifier = Modifier, large: Boolean = false) {
    if (!LocalUiPrefs.current.formatBadges) return
    val name = song.formatName ?: return
    FormatBadge(name, song.audioTier, modifier, large)
}

/**
 * A small pill naming a file's kind, brighter the better it sounds:
 * - [AudioTier.Lossless]: the theme's colours flow across it and a glint runs over now and then;
 * - [AudioTier.High]: a calm tonal pill with a fainter, slower glint;
 * - [AudioTier.Standard]: quiet and still.
 *
 * The motion only slides layers already drawn ([graphicsLayer] translation), so a frame redraws
 * nothing: a list full of them stays smooth. Still where nothing should move.
 */
@Composable
fun FormatBadge(text: String, tier: AudioTier, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val moving = LocalUiPrefs.current.lively && tier != AudioTier.Standard
    val transition = if (moving) rememberInfiniteTransition(label = "formatBadge") else null
    val flow = transition?.loop(if (tier == AudioTier.Lossless) 3600 else 0, "flow")
    val glint = transition?.loop(if (tier == AudioTier.Lossless) 2800 else 5200, "glint")

    val (container, content) = when (tier) {
        AudioTier.Lossless -> colors.primary to colors.onPrimary
        AudioTier.High -> colors.tertiaryContainer to colors.onTertiaryContainer
        AudioTier.Standard -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    val shine = if (tier == AudioTier.Lossless) 0.6f else 0.35f
    val palette = listOf(colors.primary, colors.tertiary, colors.secondary)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(if (large) 8.dp else 6.dp))
            .background(container),
    ) {
        if (tier == AudioTier.Lossless) {
            // Drawn once, 4 widths wide and mirrored every 2: sliding it by 2 widths a loop never seams.
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { translationX = (flow?.value ?: 0.25f) * 2 * size.width }
                    .drawWithCache {
                        val w = size.width
                        val brush = Brush.linearGradient(palette, Offset(0f, 0f), Offset(w, size.height), TileMode.Mirror)
                        onDrawBehind { drawRect(brush, topLeft = Offset(-2 * w, 0f), size = Size(4 * w, size.height)) }
                    },
            )
        }
        if (glint != null) {
            // From off the left edge to off the right, then resting out of sight for the rest of the loop.
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { translationX = (glint.value * 4 - 1.5f) * size.width }
                    .drawWithCache {
                        val w = size.width
                        val brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = shine),
                            1f to Color.Transparent,
                            start = Offset(w * 0.15f, 0f),
                            end = Offset(w * 0.85f, size.height),
                        )
                        onDrawBehind { drawRect(brush) }
                    },
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (large) 12.sp else 10.sp,
                lineHeight = if (large) 14.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.06.em,
            ),
            color = content,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = if (large) 7.dp else 5.dp, vertical = 1.dp),
        )
    }
}

/** 0→1 over [millis], again and again; null for 0 (no such motion). */
@Composable
private fun InfiniteTransition.loop(millis: Int, label: String): State<Float>? =
    if (millis <= 0) null
    else animateFloat(0f, 1f, infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart), label = label)
