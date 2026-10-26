package com.ku.pierdoon.ui.player

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.resources.*
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.playback.QueueEntry
import com.ku.pierdoon.playback.QueueState
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.library.SongRow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The play queue, in place of the art like the lyrics: the current track, then what plays next.
 * Tap to jump, swipe sideways to remove, drag the handle to reorder.
 *
 * Edits show at once from a local copy of the list; the player's updated queue then replaces it.
 */
@Composable
fun QueuePanel(
    queue: QueueState,
    onPlay: (key: String) -> Unit,
    onRemove: (key: String) -> Unit,
    onMove: (key: String, position: Int) -> Unit,
    onLongPress: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val currentQueue by rememberUpdatedState(queue)

    var local by remember { mutableStateOf<List<QueueEntry>?>(null) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // A new queue from the player wins over local edits, and over a drag (the track may have changed).
    LaunchedEffect(queue.upcoming) {
        local = null
        draggingKey = null
        dragOffset = 0f
    }
    val upcoming = local ?: queue.upcoming

    fun currentUpcoming() = local ?: currentQueue.upcoming

    /** Swaps the dragged entry with the one under its centre; the offset keeps it under the finger. */
    fun swapUnderFinger() {
        val key = draggingKey ?: return
        val list = currentUpcoming()
        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == key } ?: return
        val center = dragged.offset + dragOffset + dragged.size / 2f
        val target = visible.firstOrNull { info ->
            info.key != key && list.any { it.key == info.key } &&
                center >= info.offset && center < info.offset + info.size
        } ?: return
        val from = list.indexOfFirst { it.key == key }
        val to = list.indexOfFirst { it.key == target.key }
        // Lazy lists keep the first visible item in place across a reorder; pin the scroll instead.
        listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        local = list.toMutableList().apply { add(to, removeAt(from)) }
        dragOffset += dragged.offset - target.offset
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    fun endDrag() {
        val key = draggingKey ?: return
        val position = currentUpcoming().indexOfFirst { it.key == key }
        val original = currentQueue.upcoming.indexOfFirst { it.key == key }
        if (position >= 0 && position != original) onMove(key, position)
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
        // Glide into the slot instead of snapping; the entry stays on top until it lands.
        scope.launch {
            animate(dragOffset, 0f, animationSpec = settleSpec) { value, _ -> dragOffset = value }
            if (draggingKey == key) draggingKey = null
        }
    }

    // Near the top or bottom edge the list scrolls under the dragged entry, faster the closer it gets.
    val edgePx = with(LocalDensity.current) { AUTOSCROLL_EDGE.toPx() }
    LaunchedEffect(draggingKey) {
        val key = draggingKey ?: return@LaunchedEffect
        while (isActive && draggingKey == key) {
            val info = listState.layoutInfo
            val dragged = info.visibleItemsInfo.firstOrNull { it.key == key }
            if (dragged != null) {
                val top = dragged.offset + dragOffset
                val bottom = top + dragged.size
                val speed = when {
                    top < info.viewportStartOffset + edgePx -> -(info.viewportStartOffset + edgePx - top) / edgePx
                    bottom > info.viewportEndOffset - edgePx -> (bottom - info.viewportEndOffset + edgePx) / edgePx
                    else -> 0f
                }.coerceIn(-1f, 1f) * AUTOSCROLL_MAX_PX_PER_FRAME
                if (speed != 0f) {
                    dragOffset += listState.scrollBy(speed)
                    swapUnderFinger()
                }
            }
            withFrameNanos { }
        }
    }

    val placement = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val removeLabel = stringResource(Res.string.remove_from_queue)

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = modifier.fillMaxSize().fadingEdges(),
    ) {
        queue.current?.let { current ->
            item(key = "label:now") { Label(stringResource(Res.string.now_playing)) }
            item(key = current.key) {
                SongRow(
                    song = current.song,
                    isCurrent = true,
                    onClick = {},
                    onLongClick = { onLongPress(current.song) },
                    modifier = Modifier.animateItem(fade, placement, fade),
                )
            }
        }
        item(key = "label:next") {
            Column(Modifier.animateItem(fade, placement, fade)) {
                Label(stringResource(Res.string.up_next))
                if (!queue.canReorder && upcoming.size > 1) {
                    Text(
                        text = stringResource(Res.string.queue_shuffled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 6.dp),
                    )
                }
            }
        }
        if (upcoming.isEmpty()) {
            item(key = "end") {
                Text(
                    text = stringResource(Res.string.queue_end),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.animateItem(fade, placement, fade).padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
        }
        items(upcoming, key = { it.key }) { entry ->
            val dragging = entry.key == draggingKey
            UpcomingRow(
                entry = entry,
                dragging = dragging,
                dragOffset = { if (dragging) dragOffset else 0f },
                canReorder = queue.canReorder && draggingKey.let { it == null || dragging },
                removeLabel = removeLabel,
                onClick = { onPlay(entry.key) },
                onLongClick = { onLongPress(entry.song) },
                onRemove = {
                    local = currentUpcoming().filterNot { it.key == entry.key }
                    onRemove(entry.key)
                },
                onDragStart = {
                    local = currentUpcoming()
                    draggingKey = entry.key
                    dragOffset = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                },
                onDrag = { dy ->
                    dragOffset += dy
                    swapUnderFinger()
                },
                onDragEnd = ::endDrag,
                // The dragged row follows the finger, not the placement spring.
                modifier = Modifier.animateItem(fade, if (dragging) null else placement, fade),
            )
        }
    }
}

@Composable
private fun UpcomingRow(
    entry: QueueEntry,
    dragging: Boolean,
    dragOffset: () -> Float,
    canReorder: Boolean,
    removeLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "queueLift",
    )
    val swipe = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipe,
        gesturesEnabled = !dragging,
        onDismiss = { onRemove() },
        backgroundContent = {
            if (swipe.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Box(
                    contentAlignment = if (swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        // Rows are see-through, so show red only on the strip the row has uncovered.
                        // Read in the draw phase: the swipe doesn't recompose.
                        .drawWithContent {
                            val offset = runCatching { swipe.requireOffset() }.getOrDefault(0f)
                            if (offset >= 0f) {
                                clipRect(right = offset) { this@drawWithContent.drawContent() }
                            } else {
                                clipRect(left = size.width + offset) { this@drawWithContent.drawContent() }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.errorContainer)
                        .padding(horizontal = 20.dp),
                ) {
                    Icon(PlayerIcons.Delete, contentDescription = null, tint = colors.onErrorContainer)
                }
            }
        },
        modifier = modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset()
                val s = 1f + 0.03f * lift
                scaleX = s
                scaleY = s
            }
            .semantics {
                customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemove(); true })
            },
    ) {
        Box {
            // Picked-up entry gets a surface of its own, so it reads as lifted above the list.
            if (lift > 0f) {
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .graphicsLayer {
                            alpha = lift
                            shadowElevation = 8.dp.toPx() * lift
                            shape = RoundedCornerShape(20.dp)
                            clip = true
                        }
                        .background(colors.surfaceContainerHigh),
                )
            }
            SongRow(
                song = entry.song,
                isCurrent = false,
                onClick = onClick,
                onLongClick = onLongClick,
                trailing = if (canReorder) {
                    { DragHandle(onDragStart, onDrag, onDragEnd) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun DragHandle(onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val description = stringResource(Res.string.reorder)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { currentOnDragStart() },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() },
                ) { change, amount ->
                    change.consume()
                    currentOnDrag(amount.y)
                }
            },
    ) {
        Icon(PlayerIcons.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Lets content reach [amount] past the parent's side padding (padding can't be negative). */
internal fun Modifier.horizontalBleed(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = (amount * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

private val AUTOSCROLL_EDGE = 64.dp
private const val AUTOSCROLL_MAX_PX_PER_FRAME = 18f
