package com.ku.pierdoon.ui.player

import androidx.compose.material3.Icon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import com.ku.pierdoon.PositionSnapshot
import com.ku.pierdoon.ui.components.rememberSmoothProgress
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.ku.pierdoon.R
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.Backdrop
import com.ku.pierdoon.ui.components.glass
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayPauseButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.morphClip
import com.ku.pierdoon.ui.components.scaleBy

private val MiniShape = RoundedCornerShape(28.dp)
private val MiniMorph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

@Composable
fun MiniPlayer(
    nowPlaying: NowPlaying,
    isPlaying: Boolean,
    position: () -> PositionSnapshot,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
    /** Name of the computer whose playback this shows; null for the phone's own. */
    sourceName: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val swipe = rememberTrackSwipeState()
    val source = remember { MutableInteractionSource() }
    var barWidthPx by remember { mutableIntStateOf(0) }
    val progress by rememberSmoothProgress(position, durationMs, barWidthPx)
    val scale = animatePressAsState(source, 0.97f)
    val morph = animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "miniMorph",
    )

    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onExpand()
        },
        interactionSource = source,
        shape = MiniShape,
        color = Color.Transparent,
        // Transparent containers can't derive a content colour; without this text renders black.
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.08f)),
        modifier = modifier
            .then(dragModifier)
            .trackSwipe(swipe, onNext, onPrevious)
            .scaleBy(scale)
            .glass(
                backdrop = backdrop,
                shape = MiniShape,
                tint = colors.surfaceContainerHigh.copy(alpha = 0.62f),
                fallback = colors.surfaceContainerHigh,
            ),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
            ) {
                Artwork(
                    songId = nowPlaying.songId,
                    albumId = nowPlaying.albumId,
                    modifier = Modifier.size(48.dp).trackSwipeOffset(swipe).morphClip(MiniMorph, morph),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).trackSwipeOffset(swipe)) {
                    Text(
                        text = nowPlaying.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sourceName != null) {
                            Icon(
                                PlayerIcons.Desktop,
                                contentDescription = sourceName,
                                tint = colors.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = nowPlaying.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                PlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = onTogglePlay,
                    playLabel = stringResource(R.string.play),
                    pauseLabel = stringResource(R.string.pause),
                    iconSize = 26.dp,
                    modifier = Modifier.size(width = 56.dp, height = 48.dp),
                )
                Spacer(Modifier.width(6.dp))
                ExpressiveIconButton(
                    onClick = onNext,
                    icon = PlayerIcons.Next,
                    contentDescription = stringResource(R.string.next),
                    containerColor = Color.Transparent,
                    contentColor = colors.onSurface,
                    modifier = Modifier.size(48.dp),
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                color = colors.primary,
                trackColor = colors.surfaceContainerHighest,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 8.dp)
                    .height(3.dp)
                    .onSizeChanged { barWidthPx = it.width },
            )
        }
    }
}
