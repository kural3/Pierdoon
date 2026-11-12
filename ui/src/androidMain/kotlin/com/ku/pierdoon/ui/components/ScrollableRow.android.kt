package com.ku.pierdoon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@Composable
actual fun ScrollableRow(
    modifier: Modifier,
    contentPadding: PaddingValues,
    horizontalArrangement: Arrangement.Horizontal,
    firstKey: Any?,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    // The list keeps its place by key, so a new first item would land just past the left edge.
    LaunchedEffect(firstKey) {
        if (firstKey != null && state.firstVisibleItemIndex <= 1) state.scrollToItem(0)
    }
    LazyRow(
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        modifier = modifier,
        content = content,
    )
}
