package com.ku.pierdoon.ui.library

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.cornerDecor
import com.ku.pierdoon.ui.components.hairline
import com.ku.pierdoon.ui.components.livelyCard
import com.ku.pierdoon.ui.components.rememberSpin
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.components.spunBackground
import com.ku.pierdoon.ui.theme.LocalUiPrefs
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.home_continue
import com.ku.pierdoon.ui.resources.now_playing
import com.ku.pierdoon.ui.resources.pause
import com.ku.pierdoon.ui.resources.play
import com.ku.pierdoon.ui.resources.radio_on
import com.ku.pierdoon.ui.resources.unknown_artist
import org.jetbrains.compose.resources.stringResource

/** The track at the top of the home screen: the one playing, or else the last one played. */
data class HeroTrack(val songId: Long, val albumId: Long, val title: String, val artist: String, val playing: Boolean)

/**
 * The top of the home screen: [track] over its own cover, blurred ([background] draws that, each
 * app its own way), to pick up where you left off. The card opens the player (or, for a track
 * that isn't loaded, plays it); the round button plays and pauses.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContinueCard(
    track: HeroTrack,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    background: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    coverSize: Dp = 120.dp,
) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.97f)
    val spin = rememberSpin()
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = coverSize + 40.dp)
            .livelyCard(spin)
            .scaleBy(scale)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = source, indication = ripple()) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onOpen()
            },
    ) {
        background(Modifier.matchParentSize())
        // Dark enough on the left for white text over any cover.
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.22f))))
                .cornerDecor(MaterialShapes.Flower.toShape(), Color.White.copy(alpha = 0.08f), size = 190.dp, spin = spin)
                .hairline(shape),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        ) {
            Artwork(
                songId = track.songId,
                albumId = track.albumId,
                large = true,
                placeholderIconSize = 40.dp,
                modifier = Modifier.size(coverSize).clip(RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (track.playing && isPlaying) Res.string.now_playing else Res.string.home_continue),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist.ifEmpty { stringResource(Res.string.unknown_artist) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            val playing = track.playing && isPlaying
            ExpressiveIconButton(
                onClick = onPlayPause,
                icon = if (playing) PlayerIcons.Pause else PlayerIcons.Play,
                contentDescription = stringResource(if (playing) Res.string.pause else Res.string.play),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                iconSize = 30.dp,
                haptic = HapticFeedbackType.Confirm,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

/** One of the home screen's quick ways in, as a pill: a shaped badge with the icon, and a label. */
class QuickPill(
    val label: String,
    val icon: ImageVector,
    val shape: Shape,
    val container: Color,
    val content: Color,
    val onClick: () -> Unit,
    /** A second, quieter line: how many, how long. */
    val detail: String? = null,
    /** Something going on (the radio playing): the badge's shape keeps turning. */
    val active: Boolean = false,
)

/**
 * The quick ways in under the hero, in one row: scrolled sideways on a narrow screen, spread
 * evenly across a wide one ([fill]).
 */
@Composable
fun QuickPills(pills: List<QuickPill>, modifier: Modifier = Modifier, fill: Boolean = false) {
    if (fill) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            for (p in pills) Pill(p, Modifier.weight(1f))
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            for (p in pills) Pill(p)
        }
    }
}

@Composable
private fun Pill(pill: QuickPill, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.94f)
    val spin = rememberSpin()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .livelyCard(spin, tilt = 8f)
            .scaleBy(scale)
            .clip(CircleShape)
            .background(pill.container)
            .clickable(interactionSource = source, indication = ripple()) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                pill.onClick()
            }
            .padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
            // The shape turns on its own while the pill is active (not with simple animations: it
            // would keep drawing frames); the icon stays upright.
            val turn = if (pill.active && LocalUiPrefs.current.lively) {
                rememberInfiniteTransition(label = "pillTurn").animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(ACTIVE_TURN_MS, easing = LinearEasing)),
                    label = "turn",
                )
            } else {
                null
            }
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = turn?.value ?: 0f }
                    .spunBackground(pill.shape, pill.content.copy(alpha = if (pill.active) 0.24f else 0.14f), spin),
            )
            Icon(pill.icon, contentDescription = null, tint = pill.content, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(pill.label, style = MaterialTheme.typography.titleSmall, color = pill.content, maxLines = 1)
            if (pill.detail != null) {
                Text(
                    pill.detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = pill.content.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The pills every home screen has, in their order and colours. */
@Composable
fun homePills(
    onShuffle: () -> Unit,
    favoritesLabel: String,
    favoritesDetail: String,
    onFavorites: () -> Unit,
    radioLabel: String,
    onRadio: (() -> Unit)?,
    statsLabel: String,
    statsDetail: String?,
    onStats: (() -> Unit)?,
    shuffleLabel: String,
    /** The radio is on (playing or paused): its pill lights up, turns, and opens the player. */
    radioOn: Boolean = false,
    onOpenPlayer: () -> Unit = {},
): List<QuickPill> {
    val c = MaterialTheme.colorScheme
    val radioOnLabel = stringResource(Res.string.radio_on)
    return buildList {
        add(QuickPill(shuffleLabel, PlayerIcons.Shuffle, MaterialShapes.Cookie9Sided.toShape(), c.primaryContainer, c.onPrimaryContainer, onShuffle))
        add(QuickPill(favoritesLabel, PlayerIcons.Favorite, MaterialShapes.Sunny.toShape(), c.tertiaryContainer, c.onTertiaryContainer, onFavorites, favoritesDetail))
        if (radioOn) {
            add(
                QuickPill(
                    radioLabel, PlayerIcons.Radio, MaterialShapes.Cookie12Sided.toShape(), c.primary, c.onPrimary,
                    onOpenPlayer, detail = radioOnLabel, active = true,
                ),
            )
        } else if (onRadio != null) {
            add(QuickPill(radioLabel, PlayerIcons.Radio, MaterialShapes.Cookie12Sided.toShape(), c.secondaryContainer, c.onSecondaryContainer, onRadio))
        }
        if (onStats != null) {
            add(QuickPill(statsLabel, PlayerIcons.BarChart, MaterialShapes.Cookie6Sided.toShape(), c.surfaceContainerHigh, c.onSurface, onStats, statsDetail))
        }
    }
}

/** One turn of an active pill's shape. */
private const val ACTIVE_TURN_MS = 5000
