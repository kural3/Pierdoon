package com.ku.pierdoon.ui.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Where the last click in the window landed; menus open there, as on any desktop. */
object PointerTracker {
    var lastPress: Offset = Offset.Zero
        internal set
}

/** Put on the window's root: notes every press (any button) before anything handles it. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.trackPointer(): Modifier = onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { e ->
    e.changes.firstOrNull()?.let { PointerTracker.lastPress = it.position }
}

/**
 * The desktop's stand-in for the phone's action sheets: a small card at the pointer, flipped to
 * stay inside the window. Esc or a click outside closes it.
 */
@Composable
fun ContextMenu(onDismiss: () -> Unit, width: Dp = 272.dp, content: @Composable ColumnScope.() -> Unit) {
    val anchor = remember { PointerTracker.lastPress.round() }
    val provider = remember(anchor) { AtPointer(anchor) }
    // Grows out of the corner nearest the pointer.
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.9f, transformOrigin = provider.origin),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 12.dp,
                modifier = Modifier.width(width),
            ) {
                Column(
                    Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    content = content,
                )
            }
        }
    }
}

/** A centred card over a dimmed window, for the few sheets that are more than a list of actions. */
@Composable
fun DesktopSheet(onDismiss: () -> Unit, width: Dp = 440.dp, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.width(width),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 20.dp), content = content)
        }
    }
}

private class AtPointer(private val at: IntOffset) : PopupPositionProvider {
    var origin = TransformOrigin(0f, 0f)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val flipX = at.x + popupContentSize.width > windowSize.width
        val flipY = at.y + popupContentSize.height > windowSize.height
        origin = TransformOrigin(if (flipX) 1f else 0f, if (flipY) 1f else 0f)
        val x = if (flipX) at.x - popupContentSize.width else at.x
        val y = if (flipY) at.y - popupContentSize.height else at.y
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}
