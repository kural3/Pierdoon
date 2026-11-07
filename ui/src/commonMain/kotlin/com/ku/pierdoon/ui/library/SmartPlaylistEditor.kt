package com.ku.pierdoon.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.toShape
import androidx.compose.ui.draw.clip
import com.ku.pierdoon.data.SmartField
import com.ku.pierdoon.data.SmartPlaylist
import com.ku.pierdoon.data.SmartRule
import com.ku.pierdoon.data.SmartSort
import com.ku.pierdoon.ui.components.ConnectedChoice
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.SoftFieldShape
import com.ku.pierdoon.ui.components.softFieldColors
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Makes or changes a smart playlist: its name, rules (all or any of them), order and length.
 * [count] says how many tracks it would hold now, as the rules change.
 */
@Composable
fun SmartPlaylistEditor(
    initial: SmartPlaylist?,
    count: (SmartPlaylist) -> Int,
    onDismiss: () -> Unit,
    onSave: (SmartPlaylist) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val presetNames = listOf(
        stringResource(Res.string.smart_preset_new),
        stringResource(Res.string.smart_preset_top),
        stringResource(Res.string.smart_preset_unheard),
        stringResource(Res.string.smart_preset_forgotten),
    )
    var draft by remember {
        mutableStateOf(initial ?: SmartPlaylist(System.currentTimeMillis(), "", listOf(SmartRule(SmartField.Artist))))
    }
    val n = remember(draft) { count(draft) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) Res.string.smart_new else Res.string.smart_edit)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            ) {
                if (initial == null) {
                    // A ready-made start: tap one to fill the form in.
                    Text(stringResource(Res.string.smart_presets), style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartPlaylist.presets(draft.id).forEachIndexed { i, p ->
                            FilterChip(
                                selected = false,
                                onClick = { draft = p.copy(id = draft.id, name = presetNames[i]) },
                                label = { Text(presetNames[i]) },
                            )
                        }
                    }
                }
                TextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(Res.string.playlist_name)) },
                    singleLine = true,
                    shape = SoftFieldShape,
                    colors = softFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                ConnectedChoice(
                    options = listOf(stringResource(Res.string.smart_match_all), stringResource(Res.string.smart_match_any)),
                    selected = if (draft.matchAll) 0 else 1,
                    onSelect = { draft = draft.copy(matchAll = it == 0) },
                    modifier = Modifier.fillMaxWidth(),
                )
                draft.rules.forEachIndexed { i, rule ->
                    RuleRow(
                        rule = rule,
                        onChange = { r -> draft = draft.copy(rules = draft.rules.toMutableList().also { it[i] = r }) },
                        onRemove = { draft = draft.copy(rules = draft.rules.filterIndexed { j, _ -> j != i }) },
                    )
                }
                TextButton(onClick = { draft = draft.copy(rules = draft.rules + SmartRule(SmartField.Artist)) }) {
                    Icon(PlayerIcons.Add, contentDescription = null)
                    Text(stringResource(Res.string.smart_add_rule), modifier = Modifier.padding(start = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Picker(
                        label = stringResource(Res.string.smart_sort),
                        value = stringResource(draft.sort.label()),
                        options = SmartSort.entries.map { stringResource(it.label()) },
                        onPick = { draft = draft.copy(sort = SmartSort.entries[it]) },
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        label = stringResource(Res.string.smart_limit),
                        value = draft.limit,
                        onChange = { draft = draft.copy(limit = it) },
                        blankZero = true,
                        modifier = Modifier.width(120.dp),
                    )
                }
                Text(
                    pluralStringResource(Res.plurals.songs_count, n, n),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.name.isNotBlank(),
                onClick = { onSave(draft) },
            ) { Text(stringResource(if (initial == null) Res.string.create else Res.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        },
    )
}

@Composable
private fun RuleRow(rule: SmartRule, onChange: (SmartRule) -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Picker(
                label = null,
                value = stringResource(rule.field.label()),
                options = SmartField.entries.map { stringResource(it.label()) },
                onPick = { onChange(rule.copy(field = SmartField.entries[it])) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(PlayerIcons.Close, contentDescription = stringResource(Res.string.remove), modifier = Modifier.size(20.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = rule.negate,
                onClick = { onChange(rule.copy(negate = !rule.negate)) },
                label = { Text(stringResource(Res.string.smart_not)) },
            )
            when {
                rule.field == SmartField.Favorite -> Unit
                rule.field.numeric -> NumberField(
                    label = stringResource(rule.field.unit()),
                    value = rule.number,
                    onChange = { onChange(rule.copy(number = it)) },
                    modifier = Modifier.weight(1f),
                )
                else -> TextField(
                    value = rule.text,
                    onValueChange = { onChange(rule.copy(text = it)) },
                    placeholder = { Text(stringResource(Res.string.smart_contains)) },
                    singleLine = true,
                    shape = SoftFieldShape,
                    colors = softFieldColors(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A whole number. [blankZero] shows 0 as an empty field (the limit: none); elsewhere 0 means
 * something ("played at most 0 times") and is shown. The field may still be cleared while typing.
 */
@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, blankZero: Boolean = false) {
    fun shown(v: Int) = if (v == 0 && blankZero) "" else v.toString()
    var text by remember { mutableStateOf(shown(value)) }
    // Changed from outside, as when a preset is picked: show the new value.
    if ((text.toIntOrNull() ?: 0) != value) text = shown(value)
    TextField(
        value = text,
        onValueChange = { t ->
            val digits = t.filter(Char::isDigit).take(5)
            text = digits
            onChange(digits.toIntOrNull() ?: 0)
        },
        label = { Text(label, maxLines = 1) },
        placeholder = { Text("0") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = SoftFieldShape,
        colors = softFieldColors(),
        modifier = modifier,
    )
}

@Composable
private fun Picker(label: String?, value: String, options: List<String>, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall)
            }
            Icon(PlayerIcons.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(i) })
            }
        }
    }
}

private fun SmartField.label(): StringResource = when (this) {
    SmartField.Artist -> Res.string.smart_field_artist
    SmartField.Album -> Res.string.smart_field_album
    SmartField.Title -> Res.string.smart_field_title
    SmartField.AddedWithinDays -> Res.string.smart_field_added
    SmartField.PlayedAtLeast -> Res.string.smart_field_played_min
    SmartField.PlayedAtMost -> Res.string.smart_field_played_max
    SmartField.NotPlayedForDays -> Res.string.smart_field_not_played
    SmartField.PlayedWithinDays -> Res.string.smart_field_played_recently
    SmartField.LongerThanMinutes -> Res.string.smart_field_longer
    SmartField.ShorterThanMinutes -> Res.string.smart_field_shorter
    SmartField.Favorite -> Res.string.smart_field_favorite
}

private fun SmartField.unit(): StringResource = when (this) {
    SmartField.PlayedAtLeast, SmartField.PlayedAtMost -> Res.string.smart_unit_times
    SmartField.LongerThanMinutes, SmartField.ShorterThanMinutes -> Res.string.smart_unit_minutes
    else -> Res.string.smart_unit_days
}

private fun SmartSort.label(): StringResource = when (this) {
    SmartSort.Random -> Res.string.smart_sort_random
    SmartSort.Title -> Res.string.smart_sort_title
    SmartSort.Artist -> Res.string.smart_sort_artist
    SmartSort.RecentlyAdded -> Res.string.smart_sort_added
    SmartSort.MostPlayed -> Res.string.smart_sort_most
    SmartSort.LeastPlayed -> Res.string.smart_sort_least
    SmartSort.RecentlyPlayed -> Res.string.smart_sort_recent
}

/** The cover of a smart playlist: a sparkle on the tertiary colours, cut as a cookie. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmartPlaylistCover(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(androidx.compose.material3.MaterialShapes.Cookie9Sided.toShape())
            .background(colors.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PlayerIcons.Sparkle, contentDescription = null, tint = colors.onTertiaryContainer, modifier = Modifier.fillMaxSize(0.42f))
    }
}
