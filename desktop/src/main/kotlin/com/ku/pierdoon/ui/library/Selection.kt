package com.ku.pierdoon.ui.library

import com.ku.pierdoon.ui.desktop.ContextMenu
import com.ku.pierdoon.ui.desktop.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.resources.*
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
    TopBarIcon(PlayerIcons.SelectAll, stringResource(Res.string.select_all)) { selection.toggleAll(all.map { it.id }) }
    if (onDelete != null) {
        TopBarIcon(PlayerIcons.Delete, stringResource(Res.string.delete_from_device), enabled = picked.isNotEmpty()) {
            onDelete(picked)
        }
    }
    TopBarIcon(PlayerIcons.More, stringResource(Res.string.more_actions), enabled = picked.isNotEmpty()) { onBulk(picked) }
}

@Composable
fun SelectionBackHandler(selection: SongSelection) {
    BackHandler(enabled = selection.active) { selection.clear() }
}

/** "Selected: 3" while selecting, [title] otherwise. */
@Composable
fun selectionTitle(selection: SongSelection, title: String): String =
    if (selection.active) stringResource(Res.string.selected_count, selection.ids.size) else title

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

/**
 * One entry of a ⋮ menu. [selected] makes it one of several choices (a sort order),
 * [checked] an on/off switch; both fill the item in while on.
 */
class MenuEntry(
    val label: String,
    val icon: ImageVector? = null,
    val selected: Boolean? = null,
    val checked: Boolean? = null,
    val onClick: () -> Unit,
)

/** ⋮ button with an expressive menu: each group is a rounded card of its own. */
@Composable
fun OverflowMenu(
    groups: List<List<MenuEntry>>,
    containerColor: Color = Color.Transparent,
    size: Dp = 48.dp,
) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val shown = groups.filter { it.isNotEmpty() }
    Box {
        ExpressiveIconButton(
            onClick = { open = true },
            icon = PlayerIcons.More,
            contentDescription = stringResource(Res.string.more_actions),
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size),
        )
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            shown.forEachIndexed { g, group ->
                if (g > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(g, shown.size)) {
                    group.forEachIndexed { i, e ->
                        val shapes = MenuDefaults.itemShape(i, group.size)
                        val icon: (@Composable () -> Unit)? = e.icon?.let {
                            { Icon(it, contentDescription = null, modifier = Modifier.size(MenuDefaults.LeadingIconSize)) }
                        }
                        val tick: @Composable () -> Unit = {
                            Icon(PlayerIcons.Check, contentDescription = null, modifier = Modifier.size(MenuDefaults.LeadingIconSize))
                        }
                        val click = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            open = false
                            e.onClick()
                        }
                        when {
                            e.selected != null -> DropdownMenuItem(
                                selected = e.selected,
                                onClick = click,
                                text = { Text(e.label) },
                                shapes = shapes,
                                leadingIcon = icon,
                                selectedLeadingIcon = tick,
                            )
                            e.checked != null -> DropdownMenuItem(
                                checked = e.checked,
                                onCheckedChange = { click() },
                                text = { Text(e.label) },
                                shapes = shapes,
                                leadingIcon = icon,
                                checkedLeadingIcon = tick,
                            )
                            else -> DropdownMenuItem(
                                onClick = click,
                                text = { Text(e.label) },
                                shape = shapes.shape,
                                leadingIcon = icon,
                            )
                        }
                    }
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
    fun act(block: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        block()
        onDismiss()
        target.onDone()
    }

    ContextMenu(onDismiss = onDismiss) {
        Column {
            Text(
                pluralStringResource(Res.plurals.tracks_selected, target.songs.size, target.songs.size),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = pickingPlaylist,
                transitionSpec = { fadeIn(effects).togetherWith(fadeOut(effects)) },
                label = "bulkPage",
            ) { picking ->
                Column {
                    if (!picking) {
                        ActionItem(PlayerIcons.Play, stringResource(Res.string.play)) { act(onPlay) }
                        ActionItem(PlayerIcons.PlayNext, stringResource(Res.string.play_next)) { act(onPlayNext) }
                        ActionItem(PlayerIcons.Queue, stringResource(Res.string.add_to_queue)) { act(onAddToQueue) }
                        ActionItem(PlayerIcons.FavoriteBorder, stringResource(Res.string.add_to_favorites)) { act(onAddFavorites) }
                        ActionItem(PlayerIcons.PlaylistAdd, stringResource(Res.string.add_to_playlist)) {
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            pickingPlaylist = true
                        }
                        if (target.playlistId != null) {
                            ActionItem(PlayerIcons.Remove, stringResource(Res.string.remove_from_playlist)) { act(onRemoveFromPlaylist) }
                        }
                        if (onDelete != null) {
                            ActionItem(PlayerIcons.Delete, stringResource(Res.string.delete_from_device)) {
                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onDismiss()
                                onDelete()
                            }
                        }
                    } else {
                        ActionItem(PlayerIcons.Add, stringResource(Res.string.new_playlist)) { act(onCreatePlaylist) }
                        playlists.forEach { p ->
                            ActionItem(PlayerIcons.Playlist, p.name) { act { onAddToPlaylist(p.id) } }
                        }
                    }
                }
            }
        }
    }
}
