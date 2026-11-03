package com.ku.pierdoon.ui.library

import androidx.compose.ui.unit.Dp

/**
 * Forms and settings stop growing here on a wide window; past it the rows only got longer.
 * A phone is never that wide: there it's no limit at all.
 */
expect val CONTENT_MAX_WIDTH: Dp
