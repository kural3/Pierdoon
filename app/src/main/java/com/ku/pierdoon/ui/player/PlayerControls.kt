package com.ku.pierdoon.ui.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.R
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayPauseButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.rememberPressProgress

/**
 * Connected button group: the pressed button widens and pushes its neighbours,
 * like the M3 Expressive ButtonGroup interaction. Weights follow press progress,
 * so the neighbours also bounce when it springs back.
 */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    sideColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val prevSource = remember { MutableInteractionSource() }
    val playSource = remember { MutableInteractionSource() }
    val nextSource = remember { MutableInteractionSource() }

    val prevPress by rememberPressProgress(prevSource)
    val playPress by rememberPressProgress(playSource)
    val nextPress by rememberPressProgress(nextSource)

    Row(
        modifier = modifier.height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExpressiveIconButton(
            onClick = onPrevious,
            icon = PlayerIcons.Previous,
            contentDescription = stringResource(R.string.previous),
            iconSize = 32.dp,
            containerColor = sideColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            interactionSource = prevSource,
            modifier = Modifier.weight(1f + GROW * prevPress).fillMaxHeight(),
        )
        PlayPauseButton(
            isPlaying = isPlaying,
            onClick = onTogglePlay,
            playLabel = stringResource(R.string.play),
            pauseLabel = stringResource(R.string.pause),
            iconSize = 44.dp,
            interactionSource = playSource,
            modifier = Modifier.weight(1.8f + GROW * playPress).fillMaxHeight(),
        )
        ExpressiveIconButton(
            onClick = onNext,
            icon = PlayerIcons.Next,
            contentDescription = stringResource(R.string.next),
            iconSize = 32.dp,
            containerColor = sideColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            interactionSource = nextSource,
            modifier = Modifier.weight(1f + GROW * nextPress).fillMaxHeight(),
        )
    }
}

private const val GROW = 0.45f
