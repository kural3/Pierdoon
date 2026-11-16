package com.ku.pierdoon.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ku.pierdoon.AppModel
import androidx.compose.runtime.rememberCoroutineScope
import com.ku.pierdoon.data.Backup
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.ui.components.rememberToast
import com.ku.pierdoon.ui.resources.backup_restored
import com.ku.pierdoon.ui.settings.RestoreBackupDialog
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.UserData
import com.ku.pierdoon.radio.RadioStart
import com.ku.pierdoon.radio.RadioTrack
import com.ku.pierdoon.ui.library.BulkActionsSheet
import com.ku.pierdoon.ui.library.BulkTarget
import com.ku.pierdoon.ui.library.ConfirmDeleteDialog
import com.ku.pierdoon.ui.library.PlaylistActionsSheet
import com.ku.pierdoon.ui.library.PlaylistCoverSheet
import com.ku.pierdoon.ui.library.PlaylistNameDialog
import com.ku.pierdoon.ui.library.SongActionTarget
import com.ku.pierdoon.ui.library.SongActionsSheet
import com.ku.pierdoon.ui.navigation.Navigator
import com.ku.pierdoon.ui.navigation.Route
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.create
import com.ku.pierdoon.ui.resources.new_playlist
import com.ku.pierdoon.ui.resources.rename
import com.ku.pierdoon.ui.resources.save
import com.ku.pierdoon.ui.update.UpdateDialog
import org.jetbrains.compose.resources.stringResource

/** Which of the app's menus and dialogs about tracks and playlists is up; one at a time, opened from anywhere. */
class AppSheets {
    var songAction by mutableStateOf<SongActionTarget?>(null)
    var bulk by mutableStateOf<BulkTarget?>(null)
    var playlistAction by mutableStateOf<Playlist?>(null)
    var coverSheetFor by mutableStateOf<Long?>(null)
    internal var dialog by mutableStateOf<PlaylistDialog?>(null)

    /** A backup read from a file, waiting for "restore it?" to be answered. */
    var restoring by mutableStateOf<Backup?>(null)

    fun openSong(song: Song, playlistId: Long? = null) {
        songAction = SongActionTarget(song, playlistId)
    }

    fun openBulk(songs: List<Song>, playlistId: Long?, done: () -> Unit) {
        bulk = BulkTarget(songs, playlistId, done)
    }

    fun createPlaylist(songIds: List<Long> = emptyList()) {
        dialog = PlaylistDialog.Create(songIds)
    }

    fun renamePlaylist(playlist: Playlist) {
        dialog = PlaylistDialog.Rename(playlist)
    }

    fun deletePlaylist(playlist: Playlist) {
        dialog = PlaylistDialog.Delete(playlist)
    }
}

internal sealed interface PlaylistDialog {
    data class Create(val songIds: List<Long>) : PlaylistDialog
    data class Rename(val playlist: Playlist) : PlaylistDialog
    data class Delete(val playlist: Playlist) : PlaylistDialog
}

@Composable
fun rememberAppSheets(): AppSheets = remember { AppSheets() }

/**
 * Draws whichever of [sheets] is up: a track's menu, the menu for many tracks, a playlist's
 * menu and cover, naming and deleting playlists, and the "new version" dialog.
 */
@Composable
fun AppSheetsHost(
    sheets: AppSheets,
    vm: AppModel,
    nav: Navigator,
    userData: UserData,
    radioEnabled: Boolean,
    currentVersion: String,
    /** Deletes the files, once the user agreed; null where the app can't. */
    deleteSongs: ((List<Song>, () -> Unit) -> Unit)?,
    onExportPlaylist: (Playlist) -> Unit,
    /** "Send to computer", with one paired. */
    sendToComputer: ((List<Song>) -> Unit)?,
    /** Before a page opens from a menu: the open player folds away. */
    onLeavePlayer: () -> Unit,
) {
    sheets.songAction?.let { target ->
        SongActionsSheet(
            target = target,
            isFavorite = target.song.id in userData.favorites,
            playlists = userData.playlists,
            onDismiss = { sheets.songAction = null },
            onPlayNext = { vm.playNext(target.song) },
            onAddToQueue = { vm.addToQueue(target.song) },
            onToggleFavorite = { vm.toggleFavorite(target.song.id) },
            onAddToPlaylist = { id -> vm.addToPlaylist(id, target.song.id) },
            onCreatePlaylist = { sheets.createPlaylist(listOf(target.song.id)) },
            onRemoveFromPlaylist = { target.playlistId?.let { vm.removeFromPlaylist(it, target.song.id) } },
            onEdit = {
                onLeavePlayer()
                nav.push(Route.EditSong(target.song.id))
            },
            onDelete = deleteSongs?.let { delete -> { delete(listOf(target.song)) {} } },
            onSendToComputer = sendToComputer?.let { send -> { send(listOf(target.song)) } },
            onRadio = if (radioEnabled && !RadioTrack.isRadio(target.song.id)) {
                { vm.startRadio(RadioStart.track(target.song)) }
            } else {
                null
            },
        )
    }
    sheets.bulk?.let { target ->
        val ids = target.songs.map { it.id }
        BulkActionsSheet(
            target = target,
            playlists = userData.playlists,
            onDismiss = { sheets.bulk = null },
            onPlay = { vm.playSongs(target.songs, 0) },
            onPlayNext = { vm.playNext(target.songs) },
            onAddToQueue = { vm.addToQueue(target.songs) },
            onAddFavorites = { vm.addFavorites(ids) },
            onAddToPlaylist = { id -> vm.addToPlaylist(id, ids) },
            onCreatePlaylist = { sheets.createPlaylist(ids) },
            onRemoveFromPlaylist = { target.playlistId?.let { vm.removeFromPlaylist(it, ids.toSet()) } },
            onDelete = deleteSongs?.let { delete -> { delete(target.songs, target.onDone) } },
            onSendToComputer = sendToComputer?.let { send -> { send(target.songs) } },
            onEditTags = { nav.push(Route.EditSongs(ids)) },
        )
    }
    sheets.playlistAction?.let { p ->
        PlaylistActionsSheet(
            playlist = p,
            onDismiss = { sheets.playlistAction = null },
            onRename = { sheets.renamePlaylist(p) },
            onCover = { sheets.coverSheetFor = p.id },
            onDelete = { sheets.deletePlaylist(p) },
            onExport = { onExportPlaylist(p) },
            onSendToComputer = sendToComputer?.let { send -> { send(vm.resolve(p.songIds)) } },
        )
    }
    // Read from the live list, so the sheet shows each change as it's made.
    sheets.coverSheetFor?.let { id -> userData.playlists.firstOrNull { it.id == id } }?.let { p ->
        PlaylistCoverSheet(
            playlist = p,
            firstSong = vm.resolve(p.songIds.take(1)).firstOrNull(),
            onDismiss = { sheets.coverSheetFor = null },
            onShape = { vm.setPlaylistShape(p.id, it) },
            onPickImage = { vm.setPlaylistCover(p.id, it) },
            onRemoveImage = { vm.removePlaylistCover(p.id) },
        )
    }
    sheets.restoring?.let { backup ->
        val scope = rememberCoroutineScope()
        val toast = rememberToast()
        RestoreBackupDialog(
            backup = backup,
            onDismiss = { sheets.restoring = null },
            onConfirm = {
                sheets.restoring = null
                scope.launch {
                    val (found, total) = vm.restoreBackup(backup)
                    toast(getString(Res.string.backup_restored, found, total))
                }
            },
        )
    }
    val updatePrompt by vm.updatePrompt.collectAsState()
    if (updatePrompt) {
        val update by vm.update.collectAsState()
        UpdateDialog(state = update, currentVersion = currentVersion, onInstall = vm::installUpdate, onDismiss = vm::dismissUpdate)
    }
    when (val d = sheets.dialog) {
        is PlaylistDialog.Create -> PlaylistNameDialog(
            title = stringResource(Res.string.new_playlist),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = { sheets.dialog = null },
            onConfirm = { name ->
                vm.createPlaylist(name, d.songIds)
                sheets.dialog = null
            },
        )
        is PlaylistDialog.Rename -> PlaylistNameDialog(
            title = stringResource(Res.string.rename),
            confirmLabel = stringResource(Res.string.save),
            initial = d.playlist.name,
            onDismiss = { sheets.dialog = null },
            onConfirm = { name ->
                vm.renamePlaylist(d.playlist.id, name)
                sheets.dialog = null
            },
        )
        is PlaylistDialog.Delete -> ConfirmDeleteDialog(
            name = d.playlist.name,
            onDismiss = { sheets.dialog = null },
            onConfirm = {
                vm.deletePlaylist(d.playlist.id)
                if (nav.current == Route.PlaylistDetail(d.playlist.id)) nav.pop()
                sheets.dialog = null
            },
        )
        null -> Unit
    }
}
