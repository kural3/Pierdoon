package com.ku.pierdoon.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.RADIO_BUFFER_RANGE
import com.ku.pierdoon.data.SharedSettings
import com.ku.pierdoon.ui.resources.settings_cassette_mode_desc
import com.ku.pierdoon.ui.resources.settings_cassette_mode
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.settings_beta
import com.ku.pierdoon.ui.resources.settings_beta_note
import com.ku.pierdoon.ui.resources.settings_radio
import com.ku.pierdoon.ui.resources.settings_radio_buffer
import com.ku.pierdoon.ui.resources.settings_radio_buffer_desc
import com.ku.pierdoon.ui.resources.settings_radio_desc
import com.ku.pierdoon.ui.resources.settings_radio_forget
import com.ku.pierdoon.ui.resources.settings_radio_forget_desc
import com.ku.pierdoon.ui.resources.settings_radio_forgotten
import com.ku.pierdoon.ui.resources.settings_radio_novelty
import com.ku.pierdoon.ui.resources.settings_radio_novelty_all
import com.ku.pierdoon.ui.resources.settings_radio_novelty_none
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** The Beta page: the radio, its novelty slider and its memory of dislikes. */
@Composable
fun BetaGroups(
    settings: SharedSettings,
    onRadio: (Boolean) -> Unit,
    onNovelty: (Int) -> Unit,
    onBuffer: (Int) -> Unit,
    onForgetDislikes: () -> Unit,
    onCassette: (Boolean) -> Unit,
) {
    Text(
        stringResource(Res.string.settings_beta_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
    )
    var forgotten by remember { mutableStateOf(false) }
    Group(stringResource(Res.string.settings_radio)) {
        buildList<@Composable (Shape) -> Unit> {
            add { shape ->
                SwitchItem(
                    shape = shape,
                    title = stringResource(Res.string.settings_radio),
                    subtitle = stringResource(Res.string.settings_radio_desc),
                    checked = settings.radioEnabled,
                    onChange = onRadio,
                )
            }
            if (settings.radioEnabled) {
                add { shape -> NoveltyItem(shape, settings.radioNovelty, onNovelty) }
                add { shape -> BufferItem(shape, settings.radioBuffer, onBuffer) }
                add { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_radio_forget),
                        subtitle = stringResource(
                            if (forgotten) Res.string.settings_radio_forgotten else Res.string.settings_radio_forget_desc,
                        ),
                        onClick = {
                            onForgetDislikes()
                            forgotten = true
                        },
                    )
                }
            }
        }
    }
    Group(stringResource(Res.string.settings_cassette_mode)) {
        listOf<@Composable (Shape) -> Unit>({ shape ->
            SwitchItem(
                shape = shape,
                title = stringResource(Res.string.settings_cassette_mode),
                subtitle = stringResource(Res.string.settings_cassette_mode_desc),
                checked = settings.cassetteMode,
                onChange = onCassette,
            )
        })
    }
}

@Composable
fun betaTitle(): String = stringResource(Res.string.settings_beta)

/** How many new tracks are downloaded ahead, [RADIO_BUFFER_RANGE]. */
@Composable
private fun BufferItem(shape: Shape, buffer: Int, onChange: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var dragged by remember(buffer) { mutableIntStateOf(buffer) }
    ItemContainer(shape) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.settings_radio_buffer),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                Text("$dragged", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                stringResource(Res.string.settings_radio_buffer_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = dragged.toFloat(),
                onValueChange = {
                    val v = it.roundToInt().coerceIn(RADIO_BUFFER_RANGE)
                    if (v != dragged) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    dragged = v
                },
                onValueChangeFinished = { onChange(dragged) },
                valueRange = RADIO_BUFFER_RANGE.first.toFloat()..RADIO_BUFFER_RANGE.last.toFloat(),
                steps = RADIO_BUFFER_RANGE.last - RADIO_BUFFER_RANGE.first - 1,
            )
        }
    }
}

/** 0 (only the library) to 100 (only new), in tens. */
@Composable
private fun NoveltyItem(shape: Shape, novelty: Int, onChange: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    // Follows the drag; the setting takes the value on release.
    var dragged by remember(novelty) { mutableIntStateOf(novelty) }
    ItemContainer(shape) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.settings_radio_novelty),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                Text("$dragged%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = dragged.toFloat(),
                onValueChange = {
                    val v = (it / 10f).roundToInt() * 10
                    if (v != dragged) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    dragged = v
                },
                onValueChangeFinished = { onChange(dragged) },
                valueRange = 0f..100f,
                steps = 9,
            )
            Row {
                Text(
                    stringResource(Res.string.settings_radio_novelty_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(Res.string.settings_radio_novelty_all),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
