package com.ku.pierdoon.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.more_actions
import org.jetbrains.compose.resources.stringResource

@Composable
fun TopBarIcon(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    ExpressiveIconButton(
        onClick = { if (enabled) onClick() },
        icon = icon,
        contentDescription = description,
        containerColor = Color.Transparent,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(48.dp),
    )
}

/**
 * One entry of a ⋮ menu. [selected] makes it one of several choices (a sort order),
 * [checked] an on/off switch; both fill the item in while on.
 */
class MenuEntry(
    val label: String,
    val icon: ImageVector? = null,
    /** A second, smaller line under the label. */
    val supporting: String? = null,
    val selected: Boolean? = null,
    val checked: Boolean? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** ⋮ button with an expressive menu: each group is a rounded card of its own. */
@Composable
fun OverflowMenu(
    groups: List<List<MenuEntry>>,
    containerColor: Color = Color.Transparent,
    size: Dp = 48.dp,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ExpressiveIconButton(
            onClick = { open = true },
            icon = PlayerIcons.More,
            contentDescription = stringResource(Res.string.more_actions),
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size),
        )
        EntryMenu(open, { open = false }, groups)
    }
}

/**
 * The menu itself, for putting under a button of another kind: Material 3 Expressive's segmented
 * menu, each group a rounded card of its own. Closes itself on a pick.
 */
@Composable
expect fun EntryMenu(expanded: Boolean, onDismiss: () -> Unit, groups: List<List<MenuEntry>>)
