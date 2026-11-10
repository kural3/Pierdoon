package com.ku.pierdoon.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import com.ku.pierdoon.ui.components.morphClip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.TrackView
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.lively
import com.ku.pierdoon.ui.components.onSecondaryClick
import com.ku.pierdoon.ui.formatDuration
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The PC track list in the look the user picked: one card of rows ([TrackView.List]), or compact
 * tiles in as many columns as the window has room for ([TrackView.Tiles]).
 */
@Composable
fun TrackList(
    view: TrackView,
    songs: List<Song>,
    currentSongId: Long?,
    onPlay: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    topPadding: Dp,
    bottomPadding: Dp,
    listState: LazyListState,
    gridState: LazyGridState,
    selectedIds: Set<Long>?,
    onToggle: (Long) -> Unit,
) {
    fun click(index: Int, song: Song) = if (selectedIds != null) onToggle(song.id) else onPlay(songs, index)
    fun longClick(song: Song) = if (selectedIds != null) onToggle(song.id) else onSongLongPress(song)
    val padding = PaddingValues(top = topPadding + 4.dp, bottom = bottomPadding, start = 16.dp, end = 16.dp)
    when (view) {
        TrackView.List -> LazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                CardRow(
                    song = song, shape = cardShape(index, songs.size),
                    isCurrent = song.id == currentSongId, selected = selectedIds?.contains(song.id),
                    onClick = { click(index, song) }, onLongClick = { longClick(song) },
                )
            }
        }
        TrackView.Tiles -> LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 340.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                TileRow(
                    song = song,
                    isCurrent = song.id == currentSongId, selected = selectedIds?.contains(song.id),
                    onClick = { click(index, song) }, onLongClick = { longClick(song) },
                )
            }
        }
    }
}

/** Hover, the playing track and a ticked one, each its own fill. */
@Composable
private fun rowFill(hovered: Boolean, isCurrent: Boolean, selected: Boolean?, rest: Color): Color {
    val colors = MaterialTheme.colorScheme
    val target = when {
        selected == true -> colors.primaryContainer
        isCurrent -> colors.secondaryContainer
        hovered -> colors.surfaceContainerHigh
        else -> rest
    }
    return animateColorAsState(target, MaterialTheme.motionScheme.fastEffectsSpec(), label = "trackFill").value
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.trackClicks(source: MutableInteractionSource, onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    return this
        .hoverable(source)
        .onSecondaryClick(onLongClick)
        .combinedClickable(
            interactionSource = source,
            indication = null,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        }
}

@Composable
private fun TitleAndArtist(song: Song, isCurrent: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(
            song.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isCurrent) colors.primary else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.artist.ifEmpty { stringResource(Res.string.unknown_artist) },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Flowers a cover turns into: the playing track's in the list, the paused one in the player. */
internal val CoverFlowers by lazy {
    listOf(
        MaterialShapes.Flower, MaterialShapes.Clover4Leaf, MaterialShapes.Clover8Leaf, MaterialShapes.Sunny,
        MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst, MaterialShapes.Puffy,
    )
}

/** A rounded square as a polygon the cover can morph from; [corner] is a share of the side. */
private fun roundedSquare(corner: Float): RoundedPolygon =
    RoundedPolygon.rectangle(width = 2f, height = 2f, rounding = CornerRounding(corner * 2f)).normalized()

/**
 * The cover; on the playing track it blooms into a flower, on a ticked one a check sits over it.
 * [corner] is its rounding while it's a plain square.
 */
@Composable
private fun MarkedCover(song: Song, isCurrent: Boolean, selected: Boolean?, corner: Dp) {
    val square = remember(corner) { roundedSquare(corner / 48.dp) }
    var flower by remember { mutableStateOf(CoverFlowers.random()) }
    // Picked while the cover is still square, so the swap never shows.
    LaunchedEffect(isCurrent) { if (isCurrent) flower = CoverFlowers.random() }
    val morph = remember(square, flower) { Morph(square, flower) }
    val bloom = animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "coverBloom",
    )
    Box(contentAlignment = Alignment.Center) {
        Artwork(song.id, song.albumId, Modifier.size(48.dp).morphClip(morph, bloom))
        if (selected == true) {
            Box(
                Modifier.size(48.dp).morphClip(morph, bloom).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PlayerIcons.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ---- List: one card of rows ----

/** Rows joined into one card: round outer corners, tight ones between, as in the settings. */
private fun cardShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

@Composable
private fun CardRow(
    song: Song,
    shape: Shape,
    isCurrent: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .lively(tilt = 0f, sink = 0.02f, mouseOnly = true)
            .clip(shape)
            .background(rowFill(hovered, isCurrent, selected, rest = colors.surfaceContainer))
            .trackClicks(source, onClick, onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        MarkedCover(song, isCurrent, selected, corner = 14.dp)
        Spacer(Modifier.width(16.dp))
        TitleAndArtist(song, isCurrent, Modifier.weight(1f))
        Text(
            song.album,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f).padding(horizontal = 16.dp),
        )
        Text(formatDuration(song.durationMs), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
    }
}

// ---- Tiles ----

@Composable
private fun TileRow(
    song: Song,
    isCurrent: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .lively(tilt = 0f, sink = 0.03f, mouseOnly = true)
            .clip(RoundedCornerShape(18.dp))
            .background(rowFill(hovered, isCurrent, selected, rest = colors.surfaceContainerLow))
            .trackClicks(source, onClick, onLongClick)
            .padding(8.dp),
    ) {
        MarkedCover(song, isCurrent, selected, corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        TitleAndArtist(song, isCurrent, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(song.durationMs), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
    }
}
