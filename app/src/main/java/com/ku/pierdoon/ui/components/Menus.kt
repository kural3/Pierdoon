package com.ku.pierdoon.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** One line of an [AppMenu]. [selected] non-null makes it a choice (one of several) and shows a check. */
class MenuAction(
    val label: String,
    val icon: ImageVector?,
    val supporting: String? = null,
    val selected: Boolean? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The app's dropdown menu: Material 3 Expressive's segmented menu, where each of [groups] is its
 * own rounded card and the items' corners follow their place in it. Closes itself on a pick.
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    groups: List<List<MenuAction>>,
    anchor: MenuAnchorPosition = MenuAnchorPosition.Below,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismiss,
        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(anchor, offset),
    ) {
        groups.forEachIndexed { g, items ->
            if (g > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(g, groups.size)) {
                items.forEachIndexed { i, action ->
                    val shapes = MenuDefaults.itemShape(i, items.size)
                    val pick = {
                        onDismiss()
                        action.onClick()
                    }
                    val text: @Composable () -> Unit = { Text(action.label) }
                    val supporting = action.supporting?.let { s -> @Composable { Text(s, maxLines = 1) } }
                    val leading = action.icon?.let { icon -> @Composable { Icon(icon, contentDescription = null) } }
                    if (action.selected != null) {
                        SelectableDropdownMenuItem(
                            selected = action.selected,
                            onClick = pick,
                            text = text,
                            shapes = shapes,
                            leadingIcon = leading,
                            selectedLeadingIcon = { Icon(PlayerIcons.Check, contentDescription = null) },
                            supportingText = supporting,
                            enabled = action.enabled,
                        )
                    } else {
                        val error = MaterialTheme.colorScheme.error
                        DropdownMenuItem(
                            onClick = pick,
                            text = text,
                            shape = shapes.shape,
                            leadingIcon = leading,
                            supportingText = supporting,
                            enabled = action.enabled,
                            colors = if (action.destructive) {
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
