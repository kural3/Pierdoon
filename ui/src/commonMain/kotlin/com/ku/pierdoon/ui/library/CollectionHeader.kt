package com.ku.pierdoon.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The top of an album, artist or playlist page: its cover, the count and Play / Shuffle.
 * Stacked in a column on the phone, side by side in a window.
 */
@Composable
expect fun CollectionHeader(
    cover: @Composable (Modifier) -> Unit,
    subtitle: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    enabled: Boolean = true,
)
