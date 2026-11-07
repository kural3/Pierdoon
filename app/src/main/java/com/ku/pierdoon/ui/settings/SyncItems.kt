package com.ku.pierdoon.ui.settings

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ku.pierdoon.remote.PcRemote
import com.ku.pierdoon.remote.PcSync
import com.ku.pierdoon.remote.SyncStatus
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Sync with the paired computer: sync now (with how the last one went) and the switch for doing it
 * on connecting. On the computer screen and on the Sync page of Settings; with nothing paired,
 * [onOpenComputer] (when given) leads to pairing instead.
 */
@Composable
fun PcSyncGroup(title: String?, onOpenComputer: (() -> Unit)? = null) {
    val paired by PcRemote.paired.collectAsStateWithLifecycle()
    val status by PcSync.status.collectAsStateWithLifecycle()
    val auto by PcSync.auto.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pc = paired
    if (pc == null) {
        if (onOpenComputer != null) {
            Group(title) {
                listOf<@Composable (Shape) -> Unit>(
                    { shape ->
                        ActionItem(
                            shape = shape,
                            title = stringResource(Res.string.sync_no_pc),
                            subtitle = stringResource(Res.string.sync_no_pc_desc),
                            onClick = onOpenComputer,
                        )
                    },
                )
            }
        }
        return
    }
    val subtitle = when (val s = status) {
        SyncStatus.Running -> stringResource(Res.string.sync_running)
        is SyncStatus.Done -> stringResource(Res.string.sync_done, s.matched) + " · " + lastSyncText()
        SyncStatus.Failed -> stringResource(Res.string.sync_failed)
        SyncStatus.Outdated -> stringResource(Res.string.sync_outdated)
        SyncStatus.Idle -> lastSyncText()
    }
    Group(title) {
        listOf<@Composable (Shape) -> Unit>(
            { shape ->
                ActionItem(
                    shape = shape,
                    title = stringResource(Res.string.sync_now),
                    // Settings don't show which computer otherwise.
                    subtitle = if (onOpenComputer != null) stringResource(Res.string.sync_with, pc.name) + " · " + subtitle else subtitle,
                    onClick = { if (status != SyncStatus.Running) scope.launch { PcSync.sync(context) } },
                )
            },
            { shape ->
                SwitchItem(
                    shape = shape,
                    title = stringResource(Res.string.sync_auto),
                    subtitle = stringResource(Res.string.sync_auto_desc),
                    checked = auto,
                    onChange = PcSync::setAuto,
                )
            },
        )
    }
}

/** "Last synced 5 minutes ago", or that it never has. */
@Composable
fun lastSyncText(): String = syncAgo()?.let { stringResource(Res.string.sync_last, it) } ?: stringResource(Res.string.sync_never)

/** How long ago the last sync was ("5 minutes ago", [short]: "5 min. ago"), or null if it never was. */
@Composable
fun syncAgo(short: Boolean = false): String? {
    val last by PcSync.lastSyncAt.collectAsStateWithLifecycle()
    if (last <= 0) return null
    val now = System.currentTimeMillis()
    // DateUtils says "0 minutes ago" for the first minute.
    if (now - last < DateUtils.MINUTE_IN_MILLIS) return stringResource(Res.string.sync_just_now)
    val flags = if (short) DateUtils.FORMAT_ABBREV_RELATIVE else 0
    return DateUtils.getRelativeTimeSpanString(last, now, DateUtils.MINUTE_IN_MILLIS, flags).toString().lowercase()
}
