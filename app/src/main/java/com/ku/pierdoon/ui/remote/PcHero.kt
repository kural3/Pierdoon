package com.ku.pierdoon.ui.remote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ku.pierdoon.remote.PcRemote
import com.ku.pierdoon.remote.PcSync
import com.ku.pierdoon.remote.SyncStatus
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.ui.settings.lastSyncText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The top of the computer page: the phone and the computer side by side with the link between
 * them, how it stands, and syncing: a big button with how the last one went, and the switch for
 * doing it on connecting.
 */
@Composable
internal fun PcHero(connection: PcRemote.Connection, trackCount: Int?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val status by PcSync.status.collectAsStateWithLifecycle()
    val auto by PcSync.auto.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val online = connection == PcRemote.Connection.Connected
    val running = status == SyncStatus.Running
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(colors.primaryContainer)
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        // The two devices and the link.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Device(PlayerIcons.Smartphone, colors.tertiary, colors.onTertiary, width = 52.dp, height = 84.dp, corner = 16.dp)
            Link(online, running, Modifier.width(88.dp))
            Device(PlayerIcons.Desktop, colors.primary, colors.onPrimary, width = 104.dp, height = 72.dp, corner = 18.dp)
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(connection)
            if (trackCount != null) {
                Text(
                    stringResource(Res.string.tracks_count, trackCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch { PcSync.sync(context) }
            },
            enabled = !running && online,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (running) {
                LoadingIndicator(Modifier.size(28.dp), color = colors.onPrimary)
            } else {
                Icon(PlayerIcons.Sync, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(if (running) Res.string.sync_running else Res.string.sync_now),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            targetState = status,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "syncStatus",
        ) { s ->
            Text(
                when (s) {
                    SyncStatus.Running -> lastSyncText()
                    is SyncStatus.Done -> stringResource(Res.string.sync_done, s.matched) + " · " + lastSyncText()
                    SyncStatus.Failed -> stringResource(Res.string.sync_failed)
                    SyncStatus.Outdated -> stringResource(Res.string.sync_outdated)
                    SyncStatus.Idle -> lastSyncText()
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (s == SyncStatus.Failed || s == SyncStatus.Outdated) colors.error else colors.onPrimaryContainer.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.onPrimaryContainer.copy(alpha = 0.08f))
                .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Text(
                stringResource(Res.string.sync_auto),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Switch(checked = auto, onCheckedChange = PcSync::setAuto)
        }
    }
}

/** A device drawn as a filled rounded block with its icon on a shape in the middle. */
@Composable
private fun Device(icon: ImageVector, color: Color, ink: Color, width: Dp, height: Dp, corner: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(width, height).clip(RoundedCornerShape(corner)).background(color),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(ink),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
    }
}

/** Dots between the devices: solid while connected, faint otherwise, a sync badge in the middle. */
@Composable
private fun Link(online: Boolean, running: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val dots = colors.onPrimaryContainer.copy(alpha = if (online) 0.6f else 0.2f)
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            repeat(7) { Box(Modifier.size(5.dp).clip(CircleShape).background(dots)) }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer)
                .padding(3.dp)
                .clip(CircleShape)
                .background(if (online) colors.secondary else colors.surfaceVariant),
        ) {
            if (running) {
                LoadingIndicator(Modifier.size(22.dp), color = colors.onSecondary)
            } else {
                Icon(
                    if (online) PlayerIcons.Sync else PlayerIcons.CloudOff,
                    contentDescription = null,
                    tint = if (online) colors.onSecondary else colors.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** "Connected" with a dot, or how the link otherwise stands. */
@Composable
private fun StatusChip(connection: PcRemote.Connection) {
    val colors = MaterialTheme.colorScheme
    val dot = when (connection) {
        PcRemote.Connection.Connected -> colors.primary
        PcRemote.Connection.Offline -> colors.error
        else -> colors.tertiary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.onPrimaryContainer.copy(alpha = 0.10f))
            .padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(7.dp))
        Text(
            stringResource(
                when (connection) {
                    PcRemote.Connection.Connected -> Res.string.pc_status_connected
                    PcRemote.Connection.Offline -> Res.string.pc_status_offline
                    else -> Res.string.pc_status_connecting
                },
            ),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onPrimaryContainer,
            maxLines = 1,
        )
    }
}
