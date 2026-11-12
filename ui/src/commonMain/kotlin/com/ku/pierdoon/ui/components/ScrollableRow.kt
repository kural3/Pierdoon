package com.ku.pierdoon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A row of items scrolled sideways. On the phone a finger does that, so it's a plain LazyRow;
 * on the computer a mouse wheel only scrolls up and down, so the row can also be dragged and
 * paged along by arrows at its ends.
 */
@Composable
expect fun ScrollableRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    /** The first item's key: when something new comes in front, a row seen from its start goes back there. */
    firstKey: Any? = null,
    content: LazyListScope.() -> Unit,
)
