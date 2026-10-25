package com.ku.pierdoon.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
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
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.scaleBy

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
    onEdit: () -> Unit,
    /** Null where the system can't ask for consent to delete (below Android 11). */
    onDelete: (() -> Unit)?,
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
                        ActionItem(PlayerIcons.Edit, stringResource(R.string.edit_track)) {
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onDismiss()
                            onEdit()
                        }
                        if (target.playlistId != null) {
                            ActionItem(PlayerIcons.Remove, stringResource(R.string.remove_from_playlist)) {
                                act(onRemoveFromPlaylist)
                            }
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

@Composable
fun PlaylistActionsSheet(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onCover: () -> Unit,
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
            ActionItem(PlayerIcons.Image, stringResource(R.string.cover)) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onDismiss()
                onCover()
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
internal fun ActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
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

/** Shape and picture of a playlist cover; every change applies at once and morphs in place. */
@Composable
fun PlaylistCoverSheet(
    playlist: Playlist,
    firstSong: Song?,
    onDismiss: () -> Unit,
    onShape: (String) -> Unit,
    onPickImage: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val hasImage = CoverStore.playlistVersion(playlist.id) != null
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickImage(uri)
    }
    val selectedShape = PlaylistShapes.all.firstOrNull { it.first == playlist.shape }?.first
        ?: PlaylistShapes.all.first().first

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
        ) {
            PlaylistCover(playlist, firstSong, Modifier.size(160.dp), large = true, placeholderIconSize = 56.dp)
            Spacer(Modifier.height(20.dp))
            SheetLabel(stringResource(R.string.playlist_cover_shape))
            PlaylistShapes.all.chunked(4).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    row.forEach { (name, polygon) ->
                        val selected = name == selectedShape
                        val fill by animateColorAsState(
                            if (selected) colors.primary else colors.secondaryContainer,
                            MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "shapeFill",
                        )
                        val source = remember { MutableInteractionSource() }
                        val scale = animatePressAsState(source, 0.85f)
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .scaleBy(scale)
                                .clip(polygon.toShape())
                                .background(fill)
                                .clickable(interactionSource = source, indication = ripple()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onShape(name)
                                },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SheetLabel(stringResource(R.string.playlist_cover_image))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(PlayerIcons.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cover_gallery), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AnimatedVisibility(
                    visible = hasImage,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onRemoveImage()
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(PlayerIcons.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cover_remove), maxLines = 1)
                    }
                }
            }
            if (!hasImage) {
                Text(
                    stringResource(R.string.playlist_cover_from_track),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp),
    )
}
