package com.ku.pierdoon.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.desktop.ContextMenu
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.resources.*
import com.ku.pierdoon.playback.SleepTimer
import com.ku.pierdoon.playback.SleepTimerState
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.library.ActionItem
import kotlinx.coroutines.delay

/** Moon button in the player header; filled while a timer runs. */
@Composable
fun SleepTimerButton(active: Boolean, onClick: () -> Unit, glass: Color) {
    val colors = MaterialTheme.colorScheme
    ExpressiveIconButton(
        onClick = onClick,
        icon = PlayerIcons.Bedtime,
        contentDescription = stringResource(Res.string.sleep_timer),
        containerColor = if (active) colors.primary else glass,
        contentColor = if (active) colors.onPrimary else colors.onSurface,
        restCorner = if (active) 30 else 50,
        modifier = Modifier.size(48.dp),
    )
}

/** "Pauses in 12:34" / "Pauses after this track", ticking once a second; null without a timer. */
@Composable
fun sleepTimerLabel(state: SleepTimerState?): String? {
    val remaining by produceState(0L, state) {
        while (state is SleepTimerState.At) {
            value = state.remainingMs
            delay(1_000L - value % 1_000L)
        }
    }
    return when (state) {
        null -> null
        SleepTimerState.EndOfTrack -> stringResource(Res.string.sleep_after_track)
        is SleepTimerState.At -> {
            val s = (remaining + 999) / 1000
            stringResource(Res.string.sleep_pauses_in, "%d:%02d".format(s / 60, s % 60))
        }
    }
}

@Composable
fun SleepTimerSheet(state: SleepTimerState?, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    fun act(block: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        block()
        onDismiss()
    }
    ContextMenu(onDismiss = onDismiss) {
        Column {
            Text(
                stringResource(Res.string.sleep_timer),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            sleepTimerLabel(state)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                )
            }
            SLEEP_MINUTES.forEach { m ->
                ActionItem(PlayerIcons.Bedtime, pluralStringResource(Res.plurals.sleep_minutes, m, m)) {
                    act { SleepTimer.start(m) }
                }
            }
            ActionItem(PlayerIcons.MusicNote, stringResource(Res.string.sleep_end_of_track)) { act(SleepTimer::endOfTrack) }
            if (state != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ActionItem(PlayerIcons.Close, stringResource(Res.string.sleep_off)) { act(SleepTimer::cancel) }
            }
        }
    }
}

private val SLEEP_MINUTES = listOf(15, 30, 45, 60, 90)
