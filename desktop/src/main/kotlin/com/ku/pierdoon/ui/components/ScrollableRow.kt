package com.ku.pierdoon.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A [LazyRow] that a mouse can scroll: a mouse wheel only scrolls up and down, so the row can be
 * dragged sideways, and round arrows at its ends (shown while the pointer is over it, and only
 * where there's more to see) page it along.
 */
@Composable
fun ScrollableRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    /** The first item's key: when something new comes in front, a row seen from its start goes back there. */
    firstKey: Any? = null,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    // The list keeps its place by key, so a new first item would land just past the left edge.
    LaunchedEffect(firstKey) {
        if (firstKey != null && state.firstVisibleItemIndex <= 1) state.scrollToItem(0)
    }
    val scope = rememberCoroutineScope()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val drag = rememberDraggableState { delta -> state.dispatchRawDelta(-delta) }
    fun page(direction: Int) = scope.launch {
        val width = state.layoutInfo.viewportSize.width
        state.animateScrollBy(direction * width * 0.8f, RowSpring)
    }
    Box(modifier.fillMaxWidth().hoverable(hover)) {
        LazyRow(
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            modifier = Modifier.fillMaxWidth().draggable(drag, Orientation.Horizontal),
            content = content,
        )
        EdgeArrow(
            visible = hovered && state.canScrollBackward,
            back = true,
            onClick = { page(-1) },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
        )
        EdgeArrow(
            visible = hovered && state.canScrollForward,
            back = false,
            onClick = { page(1) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        )
    }
}

private val RowSpring = androidx.compose.animation.core.spring<Float>(
    dampingRatio = 0.85f,
    stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
)

@Composable
private fun EdgeArrow(visible: Boolean, back: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.7f),
        exit = fadeOut() + scaleOut(targetScale = 0.7f),
        modifier = modifier,
    ) {
        ExpressiveIconButton(
            onClick = onClick,
            icon = PlayerIcons.ChevronRight,
            contentDescription = null,
            containerColor = colors.surfaceContainerHighest.copy(alpha = 0.92f),
            contentColor = colors.onSurface,
            modifier = Modifier.size(48.dp).graphicsLayer { if (back) rotationZ = 180f },
        )
    }
}
