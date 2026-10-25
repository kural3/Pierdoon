package com.ku.pierdoon.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.R
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

/** Tracks ticked in a list while it's in selection mode. Survives rotation. */
@Stable
class SongSelection(active: Boolean = false, ids: Set<Long> = emptySet()) {
    var active by mutableStateOf(active)
        private set
    var ids by mutableStateOf(ids)
        private set

    fun start(first: Long? = null) {
        active = true
        ids = setOfNotNull(first)
    }

    fun toggle(id: Long) {
        ids = if (id in ids) ids - id else ids + id
    }

    /** Everything, or nothing if everything is already ticked. */
    fun toggleAll(all: List<Long>) {
        ids = if (all.isNotEmpty() && ids.containsAll(all)) emptySet() else all.toSet()
    }

    fun clear() {
        active = false
        ids = emptySet()
    }
}

@Composable
fun rememberSongSelection(): SongSelection = rememberSaveable(
    saver = listSaver(
        save = { listOf(if (it.active) 1L else 0L) + it.ids },
        restore = { SongSelection(active = it.first() == 1L, ids = it.drop(1).toSet()) },
    ),
) { SongSelection() }

/** What the bulk actions sheet works on; [onDone] leaves selection mode. */
class BulkTarget(val songs: List<Song>, val playlistId: Long?, val onDone: () -> Unit)

/**
 * Top bar pieces for a list: the usual [actions] normally, select-all / delete / more while
 * selecting. System back leaves selection mode first.
 */
@Composable
fun SelectionActions(
    selection: SongSelection,
    all: List<Song>,
    onBulk: (List<Song>) -> Unit,
    onDelete: ((List<Song>) -> Unit)?,
) {
    val picked = all.filter { it.id in selection.ids }
    TopBarIcon(PlayerIcons.SelectAll, stringResource(R.string.select_all)) { selection.toggleAll(all.map { it.id }) }
    if (onDelete != null) {
        TopBarIcon(PlayerIcons.Delete, stringResource(R.string.delete_from_device), enabled = picked.isNotEmpty()) {
            onDelete(picked)
        }
    }
    TopBarIcon(PlayerIcons.More, stringResource(R.string.more_actions), enabled = picked.isNotEmpty()) { onBulk(picked) }
}

@Composable
fun SelectionBackHandler(selection: SongSelection) {
    BackHandler(enabled = selection.active) { selection.clear() }
}

/** "Selected: 3" while selecting, [title] otherwise. */
@Composable
fun selectionTitle(selection: SongSelection, title: String): String =
    if (selection.active) stringResource(R.string.selected_count, selection.ids.size) else title

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

/** One entry of a ⋮ menu; [checked] shows a tick for the current choice. */
class MenuEntry(val label: String, val icon: ImageVector? = null, val checked: Boolean = false, val onClick: () -> Unit)

/** ⋮ button with a dropdown; a null entry draws a divider. */
@Composable
fun OverflowMenu(entries: List<MenuEntry?>) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        TopBarIcon(PlayerIcons.More, stringResource(R.string.more_actions)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { e ->
                if (e == null) {
                    HorizontalDivider()
                } else {
                    DropdownMenuItem(
                        text = { Text(e.label) },
                        leadingIcon = e.icon?.let { { Icon(it, contentDescription = null) } },
                        trailingIcon = if (e.checked) {
                            { Icon(PlayerIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            open = false
                            e.onClick()
                        },
                    )
                }
            }
        }
    }
}

/** Play, queue, favourites, playlists and removal for several tracks at once. */
@Composable
fun BulkActionsSheet(
    target: BulkTarget,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddFavorites: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    var pickingPlaylist by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun act(block: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        block()
        onDismiss()
        target.onDone()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                pluralStringResource(R.plurals.tracks_selected, target.songs.size, target.songs.size),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = pickingPlaylist,
                transitionSpec = { fadeIn(effects).togetherWith(fadeOut(effects)) },
                label = "bulkPage",
            ) { picking ->
                Column {
                    if (!picking) {
                        ActionItem(PlayerIcons.Play, stringResource(R.string.play)) { act(onPlay) }
                        ActionItem(PlayerIcons.PlayNext, stringResource(R.string.play_next)) { act(onPlayNext) }
                        ActionItem(PlayerIcons.Queue, stringResource(R.string.add_to_queue)) { act(onAddToQueue) }
                        ActionItem(PlayerIcons.FavoriteBorder, stringResource(R.string.add_to_favorites)) { act(onAddFavorites) }
                        ActionItem(PlayerIcons.PlaylistAdd, stringResource(R.string.add_to_playlist)) {
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            pickingPlaylist = true
                        }
                        if (target.playlistId != null) {
                            ActionItem(PlayerIcons.Remove, stringResource(R.string.remove_from_playlist)) { act(onRemoveFromPlaylist) }
                        }
                        if (onDelete != null) {
                            ActionItem(PlayerIcons.Delete, stringResource(R.string.delete_from_device)) {
                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onDismiss()
                                onDelete()
                            }
                        }
                    } else {
                        ActionItem(PlayerIcons.Add, stringResource(R.string.new_playlist)) { act(onCreatePlaylist) }
                        playlists.forEach { p ->
                            ActionItem(PlayerIcons.Playlist, p.name) { act { onAddToPlaylist(p.id) } }
                        }
                    }
                }
            }
        }
    }
}
