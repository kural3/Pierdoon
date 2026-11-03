package com.ku.pierdoon.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.playback.PlaybackSpeed
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.ui.components.ExpressiveTextButton
import com.ku.pierdoon.ui.library.EntryMenu
import com.ku.pierdoon.ui.library.MenuEntry
import org.jetbrains.compose.resources.stringResource

/** The speed on glass ("1×"), with a menu of the others. Lit while it isn't 1×, so that doesn't go unnoticed. */
@Composable
fun SpeedButton(containerColor: Color, modifier: Modifier = Modifier) {
    val speed = SettingsStore.settings.collectAsState().value.playbackSpeed
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val changed = speed != 1f
    Box(modifier) {
        ExpressiveTextButton(
            onClick = { open = true },
            text = PlaybackSpeed.label(speed),
            onClickLabel = stringResource(Res.string.speed),
            containerColor = if (changed) colors.primary else containerColor,
            contentColor = if (changed) colors.onPrimary else colors.onSurface,
            restCorner = if (changed) 30 else 50,
            modifier = Modifier.height(48.dp).widthIn(min = 48.dp),
        )
        EntryMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                PlaybackSpeed.options.map { s ->
                    MenuEntry(PlaybackSpeed.label(s), selected = s == speed) {
                        SettingsStore.update { it.copy(playbackSpeed = s) }
                    }
                }
            ),
        )
    }
}
