package com.ku.pierdoon.ui.library

import com.ku.pierdoon.ui.components.FormatBadge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import com.ku.pierdoon.ui.components.onSecondaryClick
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
import androidx.compose.material3.TopAppBarState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import kotlin.math.roundToInt
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
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


/** Space reserved below lists for the floating bottom chrome (mini player + nav bar). */
fun listBottomPadding(bottomInset: Dp, extra: Dp = 16.dp) = bottomInset + extra

/**
 * A row's shape in a list drawn as one card, as in the settings: round outer corners, tight
 * ones between the rows, which sit a hair apart on the card's colour.
 */
fun songCardShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

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
    /** Its place in a list drawn as one card ([songCardShape]); null for a row on its own. */
    card: Shape? = null,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.95f)
    val container by animateColorAsState(
        targetValue = when {
            selected == true -> colors.primaryContainer
            isCurrent -> colors.secondaryContainer
            card != null -> colors.surfaceContainer
            else -> Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "rowContainer",
    )
    // The playing track's cover blooms into a flower, another each time (as on the computer);
    // picked while the cover is still square, so the swap never shows.
    var flower by remember { mutableStateOf(CoverFlowers.random()) }
    LaunchedEffect(isCurrent) { if (isCurrent) flower = CoverFlowers.random() }
    val rowMorph = remember(flower) { Morph(MaterialShapes.Square, flower) }
    val morph = animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "rowMorph",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (card != null) 16.dp else 12.dp, vertical = if (card != null) 1.dp else 2.dp)
            .scaleBy(scale)
            .clip(card ?: RoundedCornerShape(20.dp))
            .background(container)
            .onSecondaryClick(onLongClick)
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
                modifier = Modifier.size(52.dp).morphClip(rowMorph, morph),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    modifier = Modifier.weight(1f, fill = false),
                )
                FormatBadge(song, Modifier.padding(start = 6.dp))
            }
            Text(
                text = "${song.artist.ifEmpty { stringResource(Res.string.unknown_artist) }} · ${formatDuration(song.durationMs)}",
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
            if (onBack == null) {
                TabHeader(title, subtitle, actions, belowTitle, scrollBehavior.state)
                return@Scaffold
            }
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            MediumFlexibleTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = {
                    ExpressiveIconButton(
                        onClick = onBack,
                        icon = PlayerIcons.Back,
                        contentDescription = stringResource(Res.string.back),
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp).size(48.dp),
                    )
                },
                actions = { actions() },
                scrollBehavior = scrollBehavior,
                // No grey band once scrolled: the bar stays the page's own colour, and the list just
                // slips under it.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            belowTitle?.invoke()
            }
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/**
 * Header of a bottom-bar tab: the title sits at the very top in the home screen's large type and
 * scrolls away with the list; [belowTitle] (search, sections) stays pinned.
 */
@Composable
private fun TabHeader(
    title: String,
    subtitle: String?,
    actions: @Composable () -> Unit,
    belowTitle: (@Composable () -> Unit)?,
    state: TopAppBarState,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        Layout(
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .graphicsLayer { alpha = 1f - state.collapsedFraction }
                        .padding(start = 24.dp, end = 12.dp, top = 32.dp, bottom = 16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.displaySmallEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    actions()
                }
            },
            modifier = Modifier.clipToBounds(),
        ) { measurables, constraints ->
            val title = measurables.first().measure(constraints.copy(minHeight = 0))
            // The list's scroll eats into this height, as with the flexible app bars elsewhere.
            val limit = -title.height.toFloat()
            if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
            val height = (title.height + state.heightOffset).roundToInt().coerceIn(0, title.height)
            layout(title.width, height) { title.place(0, height - title.height) }
        }
        belowTitle?.invoke()
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
): Modifier = onSecondaryClick(onLongClick).combinedClickable(
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
