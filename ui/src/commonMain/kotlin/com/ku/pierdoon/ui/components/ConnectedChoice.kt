package com.ku.pierdoon.ui.components

import androidx.compose.material3.ToggleButtonColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
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
 * One of several choices too many or too long for [ConnectedChoice]: looks and morphs like its
 * segments, but stands alone, so a row of these can wrap or scroll.
 */
@Composable
fun ChoiceChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Unchecked colour; pick one that contrasts with whatever sits behind it. */
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val haptics = LocalHapticFeedback.current
    ToggleButton(
        checked = selected,
        onCheckedChange = {
            if (!selected) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
        },
        enabled = enabled,
        colors = toggleColors(containerColor),
        modifier = modifier.semantics { role = Role.RadioButton },
    ) {
        Text(label, maxLines = 1, softWrap = false)
    }
}

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
    enabled: Boolean = true,
) {
    val colors = toggleColors(containerColor)
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
                enabled = enabled,
                colors = colors,
                // Four segments on a phone are narrow; tighter sides keep "Playlists" whole.
                contentPadding = PaddingValues(horizontal = if (options.size > 3) 6.dp else 16.dp, vertical = 8.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    // Four segments share little room: longer labels get more of it.
                    .weight(if (options.size > 3) label.length + 4f else 1f)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The toggle buttons' colours over [containerColor]; Material 3 renamed the call between versions. */
@Composable
internal expect fun toggleColors(containerColor: Color): ToggleButtonColors
