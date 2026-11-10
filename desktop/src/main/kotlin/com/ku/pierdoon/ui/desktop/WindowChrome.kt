package com.ku.pierdoon.ui.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import com.ku.pierdoon.ui.components.glass
import com.ku.pierdoon.ui.components.hairline
import com.ku.pierdoon.ui.components.Backdrop

/**
 * The window's own title bar, on JetBrains Runtime. Windows still owns the frame: it resizes the
 * window by its edges, snaps it, shades it and maximizes it on a double click of the top strip. The
 * app only hides the system buttons and draws its own ([WindowButtons]).
 *
 * The top [HEIGHT] of the window drags it, except where something there takes the click: the
 * runtime asks on every mouse event, and [titleBarHitTest] answers.
 *
 * On a plain JDK (the shots tool, a run without jbr.dir) nothing changes: Windows' bar stays.
 */
object WindowChrome {
    /** The strip along the top that drags the window; pages start below it, clear of the buttons. */
    val HEIGHT = 54.dp

    private var bar: WindowDecorations.CustomTitleBar? = null

    /** What our buttons work on, once the system bar has given way to ours; the shots tool sets it to draw them. */
    val controls = mutableStateOf<WindowControls?>(null)

    /** Takes over [window]'s title bar; false where the runtime can't, and Windows' stays. */
    fun install(window: java.awt.Frame, state: WindowState, onClose: () -> Unit): Boolean {
        val supported = runCatching { JBR.isWindowDecorationsSupported() }.getOrDefault(false)
        if (!supported) return false
        val decorations = JBR.getWindowDecorations()
        val titleBar = decorations.createCustomTitleBar().apply {
            height = HEIGHT.value
            // Our own buttons stand in for the system's.
            putProperty("controls.visible", false)
        }
        decorations.setCustomTitleBar(window, titleBar)
        bar = titleBar
        controls.value = WindowControls(state, onClose)
        return true
    }

    internal fun hitTest(client: Boolean) {
        bar?.forceHitTest(client)
    }
}

/** The window [WindowButtons] minimize, maximize and close. */
class WindowControls(val state: WindowState, val onClose: () -> Unit)

/**
 * Tells the runtime, for each mouse event in the top strip, whether it's ours (something there
 * took it: a button, a field) or the window's to drag. Goes on the root, after everything else has
 * seen the event.
 */
fun Modifier.titleBarHitTest(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        // A press something took keeps the rest of that click ours, drags and all.
        var held = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val taken = event.changes.any { it.isConsumed }
            if (event.type == PointerEventType.Press) held = taken
            WindowChrome.hitTest(taken || held)
            if (event.type == PointerEventType.Release) held = false
        }
    }
}

/** Minimize, maximize and close, as a frosted capsule in the window's top right corner. */
@Composable
fun WindowButtons(controls: WindowControls, backdrop: Backdrop, modifier: Modifier = Modifier) {
    val state = controls.state
    val colors = MaterialTheme.colorScheme
    val capsule = RoundedCornerShape(50)
    val maximized = state.placement == WindowPlacement.Maximized
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            // The same margin as the rail on the left, so both top corners match.
            .padding(top = 12.dp, end = 12.dp)
            .glass(
                backdrop = backdrop,
                shape = capsule,
                tint = colors.surfaceContainerHigh.copy(alpha = 0.72f),
                fallback = colors.surfaceContainerHigh,
            )
            .hairline(capsule)
            .padding(3.dp),
    ) {
        ChromeButton("Minimize", hover = colors.surfaceContainerHighest, onHover = colors.onSurface, onClick = {
            state.isMinimized = true
        }) { tint -> minimizeGlyph(tint) }
        ChromeButton(if (maximized) "Restore" else "Maximize", hover = colors.surfaceContainerHighest, onHover = colors.onSurface, onClick = {
            state.placement = if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
        }) { tint -> if (maximized) restoreGlyph(tint) else maximizeGlyph(tint) }
        ChromeButton("Close", hover = colors.error, onHover = colors.onError, onClick = controls.onClose) { tint -> closeGlyph(tint) }
    }
}

@Composable
private fun ChromeButton(
    label: String,
    hover: Color,
    onHover: Color,
    onClick: () -> Unit,
    glyph: DrawScope.(Color) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val background by animateColorAsState(if (hovered) hover else Color.Transparent, effects, label = "chromeBg")
    val tint by animateColorAsState(if (hovered) onHover else colors.onSurfaceVariant, effects, label = "chromeFg")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 40.dp, height = 28.dp)
            .clip(CircleShape)
            .background(background)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(10.dp)) { glyph(tint) }
    }
}

private fun DrawScope.stroke() = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)

private fun DrawScope.minimizeGlyph(tint: Color) {
    drawLine(tint, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.2.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.maximizeGlyph(tint: Color) {
    drawRoundRect(tint, style = stroke(), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
}

private fun DrawScope.restoreGlyph(tint: Color) {
    val inset = 2.5.dp.toPx()
    val w = 1.2.dp.toPx()
    // The window behind: only its top and right edges peek out.
    drawLine(tint, Offset(inset, 0f), Offset(size.width, 0f), w, StrokeCap.Round)
    drawLine(tint, Offset(size.width, 0f), Offset(size.width, size.height - inset), w, StrokeCap.Round)
    drawRoundRect(
        tint, topLeft = Offset(0f, inset),
        size = androidx.compose.ui.geometry.Size(size.width - inset, size.height - inset),
        style = stroke(), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
    )
}

private fun DrawScope.closeGlyph(tint: Color) {
    val w = 1.2.dp.toPx()
    drawLine(tint, Offset.Zero, Offset(size.width, size.height), w, StrokeCap.Round)
    drawLine(tint, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
}
