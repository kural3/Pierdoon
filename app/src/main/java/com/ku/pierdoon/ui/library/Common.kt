package com.ku.pierdoon.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.ku.pierdoon.R
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.morphClip
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.formatDuration

private val RowMorph by lazy { Morph(MaterialShapes.Square, MaterialShapes.Cookie9Sided) }

/** Space reserved below lists for the floating bottom chrome (mini player + nav bar). */
fun listBottomPadding(bottomInset: Dp, extra: Dp = 16.dp) = bottomInset + extra

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** Non-null in selection mode: whether this row is ticked. */
    selected: Boolean? = null,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.95f)
    val container by animateColorAsState(
        targetValue = when {
            selected == true -> colors.primaryContainer
            isCurrent -> colors.secondaryContainer
            else -> Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "rowContainer",
    )
    val morph = animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "rowMorph",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .scaleBy(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .combinedClickable(
                interactionSource = source,
                indication = ripple(),
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            Artwork(
                songId = song.id,
                albumId = song.albumId,
                modifier = Modifier.size(52.dp).morphClip(RowMorph, morph),
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = selected == true,
                enter = androidx.compose.animation.scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) +
                    androidx.compose.animation.fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = androidx.compose.animation.scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) +
                    androidx.compose.animation.fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PlayerIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(28.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = if (isCurrent) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
                color = when {
                    selected == true -> colors.onPrimaryContainer
                    isCurrent -> colors.onSecondaryContainer
                    else -> colors.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${song.artist.ifEmpty { stringResource(R.string.unknown_artist) }} · ${formatDuration(song.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Collapsing flexible top bar + content; bottom space is handled by each list. */
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    belowTitle: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            MediumFlexibleTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = {
                    if (onBack != null) {
                        ExpressiveIconButton(
                            onClick = onBack,
                            icon = PlayerIcons.Back,
                            contentDescription = stringResource(R.string.back),
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp).size(48.dp),
                        )
                    }
                },
                actions = { actions() },
                scrollBehavior = scrollBehavior,
            )
            belowTitle?.invoke()
            }
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/** Big cover, title and Play / Shuffle pills for album, artist and playlist screens. */
@Composable
fun CollectionHeader(
    cover: @Composable (Modifier) -> Unit,
    subtitle: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        cover(Modifier.size(200.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        if (enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ExpressiveIconButton(
                    onClick = onPlay,
                    icon = PlayerIcons.Play,
                    contentDescription = stringResource(R.string.play_all),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    iconSize = 28.dp,
                    haptic = HapticFeedbackType.Confirm,
                    modifier = Modifier.weight(1.4f).height(64.dp),
                )
                ExpressiveIconButton(
                    onClick = onShuffle,
                    icon = PlayerIcons.Shuffle,
                    contentDescription = stringResource(R.string.shuffle_all),
                    iconSize = 26.dp,
                    haptic = HapticFeedbackType.Confirm,
                    modifier = Modifier.weight(1f).height(64.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLargeEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
    )
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(32.dp),
    )
}

@Composable
fun FillBox(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Click + optional long-click with haptics, shared by cards and rows. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(
    source: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    haptics: HapticFeedback,
): Modifier = combinedClickable(
    interactionSource = source,
    indication = ripple(),
    onLongClick = onLongClick?.let {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            it()
        }
    },
) {
    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
    onClick()
}
