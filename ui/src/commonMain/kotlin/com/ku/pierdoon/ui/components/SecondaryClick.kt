package com.ku.pierdoon.ui.components

import androidx.compose.ui.Modifier

/** Right click does what a long press does on a phone: the item's actions. Nothing on Android. */
expect fun Modifier.onSecondaryClick(onClick: (() -> Unit)?): Modifier
