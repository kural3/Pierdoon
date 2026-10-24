package com.ku.pierdoon.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.R
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.PlayerIcons

/** What a long-pressed track can do; [playlistId] is set when opened from inside a playlist. */
data class SongActionTarget(val song: Song, val playlistId: Long? = null)

@Composable
fun SongActionsSheet(
    target: SongActionTarget,
    isFavorite: Boolean,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var pickingPlaylist by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun act(block: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        block()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Artwork(target.song.id, target.song.albumId, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        target.song.title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        target.song.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = pickingPlaylist,
                transitionSpec = { fadeIn(effects).togetherWith(fadeOut(effects)) },
                label = "sheetPage",
            ) { picking ->
                Column {
                    if (!picking) {
                        ActionItem(PlayerIcons.PlayNext, stringResource(R.string.play_next)) { act(onPlayNext) }
                        ActionItem(PlayerIcons.Queue, stringResource(R.string.add_to_queue)) { act(onAddToQueue) }
                        ActionItem(
                            if (isFavorite) PlayerIcons.Favorite else PlayerIcons.FavoriteBorder,
                            stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites),
                        ) { act(onToggleFavorite) }
                        ActionItem(PlayerIcons.PlaylistAdd, stringResource(R.string.add_to_playlist)) {
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            pickingPlaylist = true
                        }
                        if (target.playlistId != null) {
                            ActionItem(PlayerIcons.Remove, stringResource(R.string.remove_from_playlist)) {
                                act(onRemoveFromPlaylist)
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

@Composable
fun PlaylistActionsSheet(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ActionItem(PlayerIcons.Edit, stringResource(R.string.rename)) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onDismiss()
                onRename()
            }
            ActionItem(PlayerIcons.Delete, stringResource(R.string.delete)) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onDismiss()
                onDelete()
            }
        }
    }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Create / rename dialog. */
@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDeleteDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_playlist_confirm, name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
