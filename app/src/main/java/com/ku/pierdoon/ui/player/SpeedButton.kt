package com.ku.pierdoon.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.playback.PlaybackSpeed
import com.ku.pierdoon.R
import com.ku.pierdoon.ui.components.PlayerIcons
import androidx.compose.ui.res.stringResource

/** The speed on glass ("1×"), with a menu of the others. Lit while it isn't 1×, so that doesn't go unnoticed. */
@Composable
fun SpeedButton(containerColor: Color, modifier: Modifier = Modifier) {
    val speed = SettingsStore.settings.collectAsStateWithLifecycle().value.playbackSpeed
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val changed = speed != 1f
    Box(modifier) {
        Text(
            PlaybackSpeed.label(speed),
            style = MaterialTheme.typography.labelLarge,
            color = if (changed) colors.onPrimary else colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .height(48.dp)
                .widthIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(if (changed) colors.primary else containerColor)
                .clickable(onClickLabel = stringResource(R.string.speed)) { open = true }
                .padding(horizontal = 12.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PlaybackSpeed.options.forEach { s ->
                DropdownMenuItem(
                    text = { Text(PlaybackSpeed.label(s)) },
                    onClick = {
                        SettingsStore.update { it.copy(playbackSpeed = s) }
                        open = false
                    },
                    trailingIcon = if (s == speed) {
                        { Icon(PlayerIcons.Check, contentDescription = null) }
                    } else null,
                )
            }
        }
    }
}
