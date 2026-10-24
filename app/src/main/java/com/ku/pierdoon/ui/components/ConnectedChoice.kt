package com.ku.pierdoon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

/**
 * M3 Expressive connected button group used as a single-choice selector: outer corners are
 * round, inner ones tight, and the checked segment morphs into a full pill.
 */
@Composable
fun ConnectedChoice(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Unchecked segment colour; pick one that contrasts with whatever sits behind the group. */
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val colors = ToggleButtonDefaults.colors(containerColor = containerColor)
    val haptics = LocalHapticFeedback.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { index, label ->
            ToggleButton(
                checked = index == selected,
                onCheckedChange = {
                    if (index != selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    }
                },
                colors = colors,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
