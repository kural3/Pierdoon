package com.ku.pierdoon.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import com.ku.pierdoon.ui.PlatformStrings
import com.ku.pierdoon.ui.components.SystemBackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.Song
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
        TopBarIcon(PlayerIcons.Delete, stringResource(PlatformStrings.deleteFromDevice), enabled = picked.isNotEmpty()) {
            onDelete(picked)
        }
    }
    TopBarIcon(PlayerIcons.More, stringResource(Res.string.more_actions), enabled = picked.isNotEmpty()) { onBulk(picked) }
}

@Composable
fun SelectionBackHandler(selection: SongSelection) {
    SystemBackHandler(enabled = selection.active) { selection.clear() }
}

/** "Selected: 3" while selecting, [title] otherwise. */
@Composable
fun selectionTitle(selection: SongSelection, title: String): String =
    if (selection.active) stringResource(Res.string.selected_count, selection.ids.size) else title

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
    /** Null without a paired computer (and always on the computer itself). */
    onSendToComputer: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    var pickingPlaylist by remember { mutableStateOf(false) }
    fun act(block: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        block()
        onDismiss()
        target.onDone()
    }

    ActionSheet(
        title = pluralStringResource(Res.plurals.tracks_selected, target.songs.size, target.songs.size),
        onDismiss = onDismiss,
    ) {
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
                    val sendLabel = PlatformStrings.sendToComputer
                    if (onSendToComputer != null && sendLabel != null) {
                        ActionItem(PlayerIcons.Desktop, stringResource(sendLabel)) { act(onSendToComputer) }
                    }
                    if (target.playlistId != null) {
                        ActionItem(PlayerIcons.Remove, stringResource(Res.string.remove_from_playlist)) { act(onRemoveFromPlaylist) }
                    }
                    if (onDelete != null) {
                        ActionItem(PlayerIcons.Delete, stringResource(PlatformStrings.deleteFromDevice), destructive = true) {
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
