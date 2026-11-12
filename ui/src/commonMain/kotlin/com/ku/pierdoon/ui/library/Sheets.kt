package com.ku.pierdoon.ui.library

import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.ui.components.rememberImagePicker
import com.ku.pierdoon.ui.components.PickedImage
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.PlatformStrings
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.ui.resources.Res
import org.jetbrains.compose.resources.stringResource

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
    /** Null where the system can't ask for consent to delete (Android below 11). */
    onDelete: (() -> Unit)?,
    /** Null without a paired computer. */
    onSendToComputer: (() -> Unit)? = null,
    /** A radio from this track; null with the radio switched off. */
    onRadio: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    var pickingPlaylist by remember { mutableStateOf(false) }
    fun act(block: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        block()
        onDismiss()
    }

    ActionSheetFrame(onDismiss) {
        SongSheetHeader(target.song)
        ActionSheetDivider()

        val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = pickingPlaylist,
            transitionSpec = { fadeIn(effects).togetherWith(fadeOut(effects)) },
            label = "sheetPage",
        ) { picking ->
            Column {
                if (!picking) {
                    ActionItem(PlayerIcons.PlayNext, stringResource(Res.string.play_next)) { act(onPlayNext) }
                    ActionItem(PlayerIcons.Queue, stringResource(Res.string.add_to_queue)) { act(onAddToQueue) }
                    if (onRadio != null) {
                        ActionItem(PlayerIcons.Radio, stringResource(Res.string.radio_start)) { act(onRadio) }
                    }
                    ActionItem(
                        if (isFavorite) PlayerIcons.Favorite else PlayerIcons.FavoriteBorder,
                        stringResource(if (isFavorite) Res.string.remove_from_favorites else Res.string.add_to_favorites),
                    ) { act(onToggleFavorite) }
                    ActionItem(PlayerIcons.PlaylistAdd, stringResource(Res.string.add_to_playlist)) {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        pickingPlaylist = true
                    }
                    ActionItem(PlayerIcons.Edit, stringResource(Res.string.edit_track)) {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onDismiss()
                        onEdit()
                    }
                    val sendLabel = PlatformStrings.sendToComputer
                    if (onSendToComputer != null && sendLabel != null) {
                        ActionItem(PlayerIcons.Desktop, stringResource(sendLabel)) { act(onSendToComputer) }
                    }
                    if (target.playlistId != null) {
                        ActionItem(PlayerIcons.Remove, stringResource(Res.string.remove_from_playlist)) {
                            act(onRemoveFromPlaylist)
                        }
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

@Composable
fun PlaylistActionsSheet(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onCover: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    /** Its tracks to the paired computer; null without one (and always on the computer). */
    onSendToComputer: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    ActionSheetFrame(onDismiss) {
        ActionSheetTitle(playlist.name)
        val sendLabel = PlatformStrings.sendToComputer
        if (onSendToComputer != null && sendLabel != null && playlist.songIds.isNotEmpty()) {
            ActionItem(PlayerIcons.Desktop, stringResource(sendLabel)) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onDismiss()
                onSendToComputer()
            }
        }
        ActionItem(PlayerIcons.Edit, stringResource(Res.string.rename)) {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onDismiss()
            onRename()
        }
        ActionItem(PlayerIcons.Image, stringResource(Res.string.cover)) {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onDismiss()
            onCover()
        }
        ActionItem(PlayerIcons.Upload, stringResource(Res.string.playlist_export)) {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onDismiss()
            onExport()
        }
        ActionItem(PlayerIcons.Delete, stringResource(Res.string.delete), destructive = true) {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onDismiss()
            onDelete()
        }
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
                label = { Text(stringResource(Res.string.playlist_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
fun ConfirmDeleteDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_playlist_confirm, name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/**
 * Shape and picture of a playlist cover; every change applies at once and morphs in place.
 * The picture comes from the phone's gallery or a file on the computer.
 */
@Composable
fun PlaylistCoverSheet(
    playlist: Playlist,
    firstSong: Song?,
    onDismiss: () -> Unit,
    onShape: (String) -> Unit,
    onPickImage: (PickedImage) -> Unit,
    onRemoveImage: () -> Unit,
) {
    // Whether it has a picture of its own, rather than its first track's.
    val hasImage = CoverStore.playlistVersion(playlist.id) != null
    val pickImage = rememberImagePicker(onPickImage)
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val selectedShape = PlaylistShapes.all.firstOrNull { it.first == playlist.shape }?.first
        ?: PlaylistShapes.all.first().first

    PanelSheet(onDismiss) {
        PlaylistCover(playlist, firstSong, Modifier.size(160.dp), large = true, placeholderIconSize = 56.dp)
        Spacer(Modifier.height(20.dp))
        SheetLabel(stringResource(Res.string.playlist_cover_shape))
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
        SheetLabel(stringResource(Res.string.playlist_cover_image))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = pickImage,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(PlayerIcons.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(PlatformStrings.coverPick), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Text(stringResource(Res.string.cover_remove), maxLines = 1)
                }
            }
        }
        if (!hasImage) {
            Text(
                stringResource(Res.string.playlist_cover_from_track),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
            )
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
