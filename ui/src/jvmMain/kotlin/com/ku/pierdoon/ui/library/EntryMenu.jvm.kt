package com.ku.pierdoon.ui.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ku.pierdoon.ui.components.PlayerIcons

@Composable
actual fun EntryMenu(expanded: Boolean, onDismiss: () -> Unit, groups: List<List<MenuEntry>>) {
    val haptics = LocalHapticFeedback.current
    val shown = groups.filter { it.isNotEmpty() }
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        shown.forEachIndexed { g, group ->
            if (g > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(g, shown.size)) {
                group.forEachIndexed { i, e ->
                    val shapes = MenuDefaults.itemShape(i, group.size)
                    val icon: (@Composable () -> Unit)? = e.icon?.let {
                        { Icon(it, contentDescription = null, modifier = Modifier.size(MenuDefaults.LeadingIconSize)) }
                    }
                    val tick: @Composable () -> Unit = {
                        Icon(PlayerIcons.Check, contentDescription = null, modifier = Modifier.size(MenuDefaults.LeadingIconSize))
                    }
                    val supporting: (@Composable () -> Unit)? = e.supporting?.let { t -> { Text(t, maxLines = 1) } }
                    val click = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onDismiss()
                        e.onClick()
                    }
                    when {
                        e.selected != null -> DropdownMenuItem(
                            selected = e.selected,
                            onClick = click,
                            text = { Text(e.label) },
                            supportingText = supporting,
                            enabled = e.enabled,
                            shapes = shapes,
                            leadingIcon = icon,
                            selectedLeadingIcon = tick,
                        )
                        e.checked != null -> DropdownMenuItem(
                            checked = e.checked,
                            onCheckedChange = { click() },
                            text = { Text(e.label) },
                            supportingText = supporting,
                            enabled = e.enabled,
                            shapes = shapes,
                            leadingIcon = icon,
                            checkedLeadingIcon = tick,
                        )
                        else -> {
                            val error = MaterialTheme.colorScheme.error
                            DropdownMenuItem(
                                onClick = click,
                                text = { Text(e.label) },
                                supportingText = supporting,
                                enabled = e.enabled,
                                shape = shapes.shape,
                                leadingIcon = icon,
                                colors = if (e.destructive) {
                                    MenuDefaults.itemColors(textColor = error, leadingIconColor = error)
                                } else {
                                    MenuDefaults.itemColors()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
