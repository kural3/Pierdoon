package com.ku.pierdoon.ui.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton

/** Right click does what a long press does on a phone: the item's actions. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.onSecondaryClick(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) this
    else this.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onClick)
