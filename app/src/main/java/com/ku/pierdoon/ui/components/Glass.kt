package com.ku.pierdoon.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.theme.LocalUiPrefs

val GlassSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Pixel-style frosted glass without a library.
 *
 * The content behind is recorded once per frame into a [GraphicsLayer] (a display list,
 * not pixels — this costs the same as drawing it normally). Each glass panel re-draws only
 * its own slice of that layer through a GPU blur, so the blurred area is just the panel,
 * and nothing extra is drawn while the screen is still.
 */
@Stable
class Backdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Marks the content that glass panels blur. Glass must not be inside this content. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * Frosted panel: blurred backdrop + translucent [tint]. Below Android 12 there is no
 * RenderEffect, so the panel falls back to the opaque [fallback] colour.
 */
@Composable
fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape,
    tint: Color,
    fallback: Color,
    blurRadius: Dp = 28.dp,
): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val enabled = GlassSupported && LocalUiPrefs.current.glass
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .clip(shape)
        .drawWithCache {
            val blurPx = blurRadius.toPx()
            val blurLayer = if (enabled) {
                obtainGraphicsLayer().apply {
                    renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                }
            } else {
                null
            }
            onDrawBehind {
                if (blurLayer == null) {
                    drawRect(fallback)
                    return@onDrawBehind
                }
                val delta = backdrop.origin - origin
                blurLayer.record {
                    translate(delta.x, delta.y) { drawLayer(backdrop.layer) }
                }
                drawLayer(blurLayer)
                drawRect(tint)
            }
        }
}
