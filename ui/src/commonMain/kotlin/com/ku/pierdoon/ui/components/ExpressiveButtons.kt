package com.ku.pierdoon.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shape, scale and clip from one press-progress value, all applied in the layer phase:
 * no recomposition while animating. Corners tighten on press, scale squishes, and both
 * overshoot slightly on release.
 */
private fun Modifier.expressiveLayer(
    press: State<Float>,
    restCorner: State<Float>,
    pressedCorner: Int,
    pressedScale: Float,
) = highFrameRate().graphicsLayer {
    val p = press.value
    val s = 1f - (1f - pressedScale) * p
    scaleX = s
    scaleY = s
    val corner = restCorner.value + (pressedCorner - restCorner.value) * p.coerceAtLeast(0f)
    shape = RoundedCornerShape(corner.toInt().coerceIn(0, 50))
    clip = true
}

@Composable
private fun animateRestCorner(target: Int): State<Float> = animateFloatAsState(
    targetValue = target.toFloat(),
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    label = "restCorner",
)

@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    iconSize: Dp = 24.dp,
    restCorner: Int = 50,
    pressedCorner: Int = 24,
    haptic: HapticFeedbackType = HapticFeedbackType.VirtualKey,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val haptics = LocalHapticFeedback.current
    val press = rememberPressProgress(interactionSource)
    val rest = animateRestCorner(restCorner)
    Box(
        modifier = modifier
            .expressiveLayer(press, rest, pressedCorner, pressedScale = 0.9f)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            // The glyph squishes a little extra, which reads as "depth" on tap.
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    val s = 1f - 0.12f * press.value
                    scaleX = s
                    scaleY = s
                },
        )
    }
}

/** Round when off, squircle when on — the M3 Expressive toggle shape change. */
@Composable
fun ExpressiveToggleButton(
    checked: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    uncheckedColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interactionSource)
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        if (checked) colors.primary else uncheckedColor, effects, label = "container"
    )
    val content by animateColorAsState(
        if (checked) colors.onPrimary else colors.onSurfaceVariant, effects, label = "content"
    )
    val rest = animateRestCorner(if (checked) 30 else 50)
    Box(
        modifier = modifier
            .expressiveLayer(press, rest, pressedCorner = 18, pressedScale = 0.88f)
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Switch,
            ) {
                haptics.performHapticFeedback(
                    if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                )
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = content, modifier = Modifier.size(iconSize))
    }
}

/** Play/pause with a morphing container (pill → squircle while playing) and a bouncy icon swap. */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    playLabel: String,
    pauseLabel: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 36.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val haptics = LocalHapticFeedback.current
    val press = rememberPressProgress(interactionSource)
    val rest = animateRestCorner(if (isPlaying) 30 else 50)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .expressiveLayer(press, rest, pressedCorner = 16, pressedScale = 0.88f)
            .background(colors.primary)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = colors.onPrimary),
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(
                    if (isPlaying) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                )
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (scaleIn(spatial, initialScale = 0.3f) + fadeIn(effects))
                    .togetherWith(scaleOut(spatial, targetScale = 0.3f) + fadeOut(effects))
            },
            label = "playPause",
        ) { playing ->
            Icon(
                imageVector = if (playing) PlayerIcons.Pause else PlayerIcons.Play,
                contentDescription = if (playing) pauseLabel else playLabel,
                tint = colors.onPrimary,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
