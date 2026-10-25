package com.ku.pierdoon.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.LibraryState
import com.ku.pierdoon.MainViewModel
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.UserData
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.resources.*
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.ArtworkCache
import com.ku.pierdoon.ui.components.Backdrop
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.backdropSource
import com.ku.pierdoon.ui.components.glass
import com.ku.pierdoon.ui.components.rememberBackdrop
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.desktop.BackHandler
import com.ku.pierdoon.ui.desktop.Files
import com.ku.pierdoon.ui.desktop.Toast
import com.ku.pierdoon.ui.desktop.ToastHost
import com.ku.pierdoon.ui.download.DownloadScreen
import com.ku.pierdoon.ui.edit.EditSongScreen
import com.ku.pierdoon.ui.library.BulkActionsSheet
import com.ku.pierdoon.ui.library.BulkTarget
import com.ku.pierdoon.ui.library.CollectionScreen
import com.ku.pierdoon.ui.library.ConfirmDeleteDialog
import com.ku.pierdoon.ui.library.FavoritesCover
import com.ku.pierdoon.ui.library.FillBox
import com.ku.pierdoon.ui.library.HomeScreen
import com.ku.pierdoon.ui.library.LibraryScreen
import com.ku.pierdoon.ui.library.MenuEntry
import com.ku.pierdoon.ui.library.PlaylistActionsSheet
import com.ku.pierdoon.ui.library.PlaylistCover
import com.ku.pierdoon.ui.library.PlaylistCoverSheet
import com.ku.pierdoon.ui.library.PlaylistNameDialog
import com.ku.pierdoon.ui.library.PlaylistsScreen
import com.ku.pierdoon.ui.library.SearchScreen
import com.ku.pierdoon.ui.library.SongActionTarget
import com.ku.pierdoon.ui.library.SongActionsSheet
import com.ku.pierdoon.ui.navigation.MotionSpecs
import com.ku.pierdoon.ui.navigation.NavDirection
import com.ku.pierdoon.ui.navigation.Navigator
import com.ku.pierdoon.ui.navigation.Route
import com.ku.pierdoon.ui.navigation.pageTransition
import com.ku.pierdoon.ui.player.LyricsPanel
import com.ku.pierdoon.ui.player.NowPlayingScreen
import com.ku.pierdoon.ui.player.PlayerBar
import com.ku.pierdoon.ui.player.QueuePanel
import com.ku.pierdoon.ui.settings.SettingsScreen
import com.ku.pierdoon.ui.theme.AlbumThemeSeed
import com.ku.pierdoon.ui.update.UpdateDialog
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

private sealed interface PlaylistDialog {
    data class Create(val songIds: List<Long>) : PlaylistDialog
    data class Rename(val playlist: Playlist) : PlaylistDialog
    data class Delete(val playlist: Playlist) : PlaylistDialog
}

/** Songs waiting for "move to the Recycle Bin?" to be answered. */
private class PendingDelete(val songs: List<Song>, val done: () -> Unit)

/** What the window shows on its right edge next to the library. */
enum class SidePanel { Lyrics, Queue }

/**
 * The window's content. On a PC there's room for everything at once: navigation on the left,
 * the library in the middle, lyrics or the queue on the right, and the player along the
 * bottom. The full player still opens over all of it, like the phone's.
 */
@Composable
fun PierdoonApp(vm: MainViewModel, nav: Navigator) {
    val library by vm.library.collectAsState()
    val player by vm.player.collectAsState()
    val position = vm.position.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val lyricsOffset by vm.lyricsOffset.collectAsState()
    val userData by vm.userData.collectAsState()
    val queue by vm.queue.collectAsState()
    val update by vm.update.collectAsState()
    val updatePrompt by vm.updatePrompt.collectAsState()
    val settings by SettingsStore.settings.collectAsState()
    val nowPlaying = rememberLastNonNull(player.nowPlaying)
    val backdrop = rememberBackdrop()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.loadLibrary() }

    // "Album art" palette: extract a seed once per album, only while that mode is on.
    val nowPlayingCover = nowPlaying?.let { CoverStore.songVersion(it.songId) }
    val nowPlayingArtRevision = nowPlaying?.let { ArtworkCache.albumRevision(it.albumId) }
    LaunchedEffect(nowPlaying?.albumId, nowPlayingCover, nowPlayingArtRevision, settings.colorSource) {
        val np = nowPlaying
        AlbumThemeSeed.seed.value =
            if (settings.colorSource == ColorSource.AlbumArt && np != null) ArtworkCache.themeSeed(np.songId, np.albumId) else null
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    var sidePanel by rememberSaveable { mutableStateOf<SidePanel?>(null) }
    // Lyrics are fetched only while actually on screen: the side panel, or the open player's.
    val lyricsOnScreen = (sidePanel == SidePanel.Lyrics && !expanded) || (expanded && sidePanel != SidePanel.Queue)
    LaunchedEffect(lyricsOnScreen) { vm.setLyricsVisible(lyricsOnScreen) }
    BackHandler(enabled = expanded) { expanded = false }
    LaunchedEffect(player.nowPlaying == null) { if (player.nowPlaying == null) expanded = false }

    // A link dropped on the window (or passed on the command line) opens the downloads page.
    val openDownloads by Downloader.openRequest.collectAsState()
    LaunchedEffect(openDownloads) {
        if (openDownloads) {
            Downloader.openRequest.value = false
            expanded = false
            nav.selectTab(Route.Downloads)
        }
    }

    var songAction by remember { mutableStateOf<SongActionTarget?>(null) }
    var playlistAction by remember { mutableStateOf<Playlist?>(null) }
    var coverSheetFor by remember { mutableStateOf<Long?>(null) }
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var bulk by remember { mutableStateOf<BulkTarget?>(null) }
    val deleteSongs: (List<Song>, () -> Unit) -> Unit = { songs, done ->
        if (songs.isNotEmpty()) pendingDelete = PendingDelete(songs, done)
    }

    var barHeightPx by remember { mutableIntStateOf(0) }
    val bottomInset = with(LocalDensity.current) { barHeightPx.toDp() }
    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val ready = library is LibraryState.Ready
    val noFolders = settings.musicFolders.none { File(it).isDirectory }
    val chooseFolder = stringResource(Res.string.choose_folder)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (expanded) 0f else 1f }
                .backdropSource(backdrop)
        ) {
            Row(Modifier.fillMaxSize()) {
                NavRail(nav = nav, compact = settings.floatingNav)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when {
                        noFolders && (library as? LibraryState.Ready)?.songs.isNullOrEmpty() -> PermissionScreen(
                            onGrant = { Files.pickFolder(chooseFolder)?.let(vm::addMusicFolder) },
                        )
                        library is LibraryState.Loading -> FillBox { LoadingIndicator(Modifier.size(64.dp)) }
                        else -> Screens(
                            nav = nav,
                            vm = vm,
                            songs = (library as LibraryState.Ready).songs,
                            currentSongId = player.nowPlaying?.songId,
                            userData = userData,
                            bottomInset = bottomInset,
                            backEnabled = !expanded,
                            onSongLongPress = { song, playlistId -> songAction = SongActionTarget(song, playlistId) },
                            onBulk = { songs, playlistId, done -> bulk = BulkTarget(songs, playlistId, done) },
                            onDeleteSongs = deleteSongs,
                            onPlaylistLongPress = { playlistAction = it },
                            onCreatePlaylist = { dialog = PlaylistDialog.Create(emptyList()) },
                            onRenamePlaylist = { dialog = PlaylistDialog.Rename(it) },
                            onDeletePlaylist = { dialog = PlaylistDialog.Delete(it) },
                            onPlaylistCover = { coverSheetFor = it.id },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = sidePanel != null && nowPlaying != null && ready,
                    enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(effects),
                    exit = shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(effects),
                ) {
                    SidePanelContent(
                        panel = sidePanel ?: SidePanel.Lyrics,
                        bottomInset = bottomInset,
                        lyrics = { modifier ->
                            LyricsPanel(
                                state = lyrics,
                                position = { position.value },
                                onSeek = vm::seekTo,
                                onRetry = vm::retryLyrics,
                                offsetMs = lyricsOffset,
                                onShift = vm::shiftLyrics,
                                onResetOffset = vm::resetLyricsOffset,
                                modifier = modifier,
                            )
                        },
                        queue = { modifier ->
                            QueuePanel(
                                queue = queue,
                                onPlay = vm::playQueueEntry,
                                onRemove = vm::removeQueueEntry,
                                onMove = vm::moveQueueEntry,
                                onLongPress = { songAction = SongActionTarget(it) },
                                modifier = modifier,
                            )
                        },
                    )
                }
            }
        }

        if (ready) {
            AnimatedVisibility(
                visible = player.nowPlaying != null && !expanded,
                enter = slideInVertically(spatialOffset) { it } + fadeIn(effects),
                exit = slideOutVertically(spatialOffset) { it } + fadeOut(effects),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { barHeightPx = it.height },
            ) {
                nowPlaying?.let { np ->
                    PlayerBar(
                        nowPlaying = np,
                        state = player,
                        position = { position.value },
                        isFavorite = np.songId in userData.favorites,
                        volume = settings.volume,
                        muted = settings.muted,
                        showLyrics = sidePanel == SidePanel.Lyrics,
                        showQueue = sidePanel == SidePanel.Queue,
                        backdrop = backdrop,
                        onToggleFavorite = { vm.toggleFavorite(np.songId) },
                        onTogglePlay = vm::togglePlay,
                        onPrevious = vm::previous,
                        onNext = vm::next,
                        onSeek = vm::seekTo,
                        onToggleShuffle = vm::toggleShuffle,
                        onCycleRepeat = vm::cycleRepeat,
                        onToggleLyrics = { sidePanel = if (sidePanel == SidePanel.Lyrics) null else SidePanel.Lyrics },
                        onToggleQueue = { sidePanel = if (sidePanel == SidePanel.Queue) null else SidePanel.Queue },
                        onVolume = vm::setVolume,
                        onToggleMute = vm::toggleMute,
                        onOpenArtist = { nav.push(Route.ArtistDetail(it)) },
                        onExpand = { expanded = true },
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded && nowPlaying != null,
            enter = fadeIn(effects) + scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.96f),
            exit = fadeOut(effects) + scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec(), targetScale = 0.96f),
        ) {
            nowPlaying?.let { np ->
                NowPlayingScreen(
                    nowPlaying = np,
                    state = player,
                    position = { position.value },
                    lyrics = lyrics,
                    showLyrics = sidePanel != SidePanel.Queue,
                    queue = queue,
                    showQueue = sidePanel == SidePanel.Queue,
                    isFavorite = np.songId in userData.favorites,
                    volume = settings.volume,
                    muted = settings.muted,
                    onToggleFavorite = { vm.toggleFavorite(np.songId) },
                    onToggleLyrics = { sidePanel = SidePanel.Lyrics },
                    onToggleQueue = { sidePanel = if (sidePanel == SidePanel.Queue) SidePanel.Lyrics else SidePanel.Queue },
                    onPlayQueueEntry = vm::playQueueEntry,
                    onRemoveQueueEntry = vm::removeQueueEntry,
                    onMoveQueueEntry = vm::moveQueueEntry,
                    onSongLongPress = { songAction = SongActionTarget(it) },
                    onRetryLyrics = vm::retryLyrics,
                    lyricsOffsetMs = lyricsOffset,
                    onShiftLyrics = vm::shiftLyrics,
                    onResetLyricsOffset = vm::resetLyricsOffset,
                    onTogglePlay = vm::togglePlay,
                    onPrevious = vm::previous,
                    onNext = vm::next,
                    onSeek = vm::seekTo,
                    onToggleShuffle = vm::toggleShuffle,
                    onCycleRepeat = vm::cycleRepeat,
                    onVolume = vm::setVolume,
                    onToggleMute = vm::toggleMute,
                    onOpenArtist = { name ->
                        expanded = false
                        nav.push(Route.ArtistDetail(name))
                    },
                    onCollapse = { expanded = false },
                )
            }
        }

        ToastHost(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 16.dp))
    }

    songAction?.let { target ->
        SongActionsSheet(
            target = target,
            isFavorite = target.song.id in userData.favorites,
            playlists = userData.playlists,
            onDismiss = { songAction = null },
            onPlayNext = { vm.playNext(target.song) },
            onAddToQueue = { vm.addToQueue(target.song) },
            onToggleFavorite = { vm.toggleFavorite(target.song.id) },
            onAddToPlaylist = { id -> vm.addToPlaylist(id, target.song.id) },
            onCreatePlaylist = { dialog = PlaylistDialog.Create(listOf(target.song.id)) },
            onRemoveFromPlaylist = { target.playlistId?.let { vm.removeFromPlaylist(it, target.song.id) } },
            onEdit = {
                expanded = false
                nav.push(Route.EditSong(target.song.id))
            },
            onDelete = { deleteSongs(listOf(target.song)) {} },
        )
    }
    bulk?.let { target ->
        val ids = target.songs.map { it.id }
        BulkActionsSheet(
            target = target,
            playlists = userData.playlists,
            onDismiss = { bulk = null },
            onPlay = { vm.playSongs(target.songs, 0) },
            onPlayNext = { vm.playNext(target.songs) },
            onAddToQueue = { vm.addToQueue(target.songs) },
            onAddFavorites = { vm.addFavorites(ids) },
            onAddToPlaylist = { id -> vm.addToPlaylist(id, ids) },
            onCreatePlaylist = { dialog = PlaylistDialog.Create(ids) },
            onRemoveFromPlaylist = { target.playlistId?.let { vm.removeFromPlaylist(it, ids.toSet()) } },
            onDelete = { deleteSongs(target.songs, target.onDone) },
        )
    }
    playlistAction?.let { p ->
        PlaylistActionsSheet(
            playlist = p,
            onDismiss = { playlistAction = null },
            onRename = { dialog = PlaylistDialog.Rename(p) },
            onCover = { coverSheetFor = p.id },
            onDelete = { dialog = PlaylistDialog.Delete(p) },
        )
    }
    // Read from the live list, so the sheet shows each change as it's made.
    coverSheetFor?.let { id -> userData.playlists.firstOrNull { it.id == id } }?.let { p ->
        PlaylistCoverSheet(
            playlist = p,
            firstSong = vm.resolve(p.songIds.take(1)).firstOrNull(),
            onDismiss = { coverSheetFor = null },
            onShape = { vm.setPlaylistShape(p.id, it) },
            onPickImage = { vm.setPlaylistCover(p.id, it) },
            onRemoveImage = { vm.removePlaylistCover(p.id) },
        )
    }
    pendingDelete?.let { pd ->
        val deletedOne = stringResource(Res.string.deleted)
        val deletedMany = pluralStringResource(Res.plurals.deleted_count, pd.songs.size, pd.songs.size)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.delete_songs_title)) },
            text = {
                Text(
                    if (pd.songs.size == 1) stringResource(Res.string.delete_song_body, pd.songs.first().title)
                    else stringResource(Res.string.delete_songs_body, pd.songs.size)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val n = vm.deleteSongs(pd.songs)
                        if (n > 0) {
                            pd.done()
                            Toast.show(if (n == 1) deletedOne else deletedMany)
                        }
                    }
                }) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
    if (updatePrompt) {
        UpdateDialog(state = update, onInstall = vm::installUpdate, onDismiss = vm::dismissUpdate)
    }
    when (val d = dialog) {
        is PlaylistDialog.Create -> PlaylistNameDialog(
            title = stringResource(Res.string.new_playlist),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = { dialog = null },
            onConfirm = { name ->
                vm.createPlaylist(name, d.songIds)
                dialog = null
            },
        )
        is PlaylistDialog.Rename -> PlaylistNameDialog(
            title = stringResource(Res.string.rename),
            confirmLabel = stringResource(Res.string.save),
            initial = d.playlist.name,
            onDismiss = { dialog = null },
            onConfirm = { name ->
                vm.renamePlaylist(d.playlist.id, name)
                dialog = null
            },
        )
        is PlaylistDialog.Delete -> ConfirmDeleteDialog(
            name = d.playlist.name,
            onDismiss = { dialog = null },
            onConfirm = {
                vm.deletePlaylist(d.playlist.id)
                if (nav.current == Route.PlaylistDetail(d.playlist.id)) nav.pop()
                dialog = null
            },
        )
        null -> Unit
    }
}

@Composable
private fun SidePanelContent(
    panel: SidePanel,
    bottomInset: Dp,
    lyrics: @Composable (Modifier) -> Unit,
    queue: @Composable (Modifier) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(SIDE_PANEL_WIDTH)
            .fillMaxHeight()
            .padding(top = 12.dp, end = 12.dp, bottom = bottomInset + 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainerLow)
            .padding(top = 16.dp, start = 12.dp, end = 12.dp),
    ) {
        Text(
            stringResource(if (panel == SidePanel.Queue) Res.string.queue else Res.string.lyrics),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = panel,
            transitionSpec = { fadeIn(effects).togetherWith(fadeOut(effects)) },
            label = "sidePanel",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { p ->
            when (p) {
                SidePanel.Lyrics -> lyrics(Modifier.fillMaxSize())
                SidePanel.Queue -> queue(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun Screens(
    nav: Navigator,
    vm: MainViewModel,
    songs: List<Song>,
    currentSongId: Long?,
    userData: UserData,
    bottomInset: Dp,
    backEnabled: Boolean,
    onSongLongPress: (Song, Long?) -> Unit,
    onBulk: (List<Song>, Long?, () -> Unit) -> Unit,
    onDeleteSongs: ((List<Song>, () -> Unit) -> Unit)?,
    onPlaylistLongPress: (Playlist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onRenamePlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onPlaylistCover: (Playlist) -> Unit,
) {
    val collections by vm.collections.collectAsState()
    val settings by SettingsStore.settings.collectAsState()
    val stateHolder = rememberSaveableStateHolder()
    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val motion = remember(spatialOffset, spatial, effects) { MotionSpecs(spatialOffset, spatial, effects) }
    val resolveCover: (Playlist) -> Song? = { p -> vm.resolve(p.songIds.take(1)).firstOrNull() }
    val onPlay: (List<Song>, Int) -> Unit = { list, i -> vm.playSongs(list, i) }
    val onShuffle: (List<Song>) -> Unit = { list ->
        if (list.isNotEmpty()) vm.playSongs(list, list.indices.random(), shuffle = true)
    }

    // One page = its saved UI state + its content.
    val pageBody: @Composable (Route) -> Unit = { route ->
        stateHolder.SaveableStateProvider(route.key) {
            when (route) {
                Route.Home -> {
                    val recent = remember(userData.recent, songs) { vm.resolve(userData.recent) }
                    HomeScreen(
                        recent = recent,
                        collections = collections,
                        playlists = userData.playlists,
                        favoritesCount = userData.favorites.size,
                        resolveCover = resolveCover,
                        onPlay = onPlay,
                        onShuffleAll = vm::shuffleAll,
                        onOpen = nav::push,
                        onSongLongPress = { onSongLongPress(it, null) },
                        bottomInset = bottomInset,
                    )
                }
                Route.Library -> LibraryScreen(
                    songs = songs,
                    collections = collections,
                    currentSongId = currentSongId,
                    onPlay = onPlay,
                    onShuffleAll = vm::shuffleAll,
                    onSongLongPress = { onSongLongPress(it, null) },
                    onOpen = nav::push,
                    sort = settings.songSort,
                    sortDescending = settings.songSortDescending,
                    onSort = { sort, desc -> SettingsStore.update { it.copy(songSort = sort, songSortDescending = desc) } },
                    onBulk = { picked, done -> onBulk(picked, null, done) },
                    onDelete = onDeleteSongs,
                    bottomInset = bottomInset,
                )
                Route.Search -> SearchScreen(
                    songs = songs,
                    collections = collections,
                    currentSongId = currentSongId,
                    onBack = { nav.pop() },
                    onPlay = onPlay,
                    onSongLongPress = { onSongLongPress(it, null) },
                    onOpen = nav::push,
                    bottomInset = bottomInset,
                )
                Route.Downloads -> DownloadScreen(
                    onBack = { nav.pop() },
                    onPlay = { id -> vm.song(id)?.let { vm.playSongs(listOf(it), 0) } },
                    bottomInset = bottomInset,
                )
                Route.Settings, is Route.SettingsPage -> {
                    val updateState by vm.update.collectAsState()
                    SettingsScreen(
                        section = (route as? Route.SettingsPage)?.section,
                        onOpen = { nav.push(Route.SettingsPage(it)) },
                        onBack = { nav.pop() },
                        settings = settings,
                        onUpdate = SettingsStore::update,
                        trackCount = songs.size,
                        lyricsCacheSize = vm::lyricsCacheSize,
                        onClearLyricsCache = vm::clearLyricsCache,
                        onRescan = vm::rescanLibrary,
                        onAddFolder = vm::addMusicFolder,
                        onRemoveFolder = vm::removeMusicFolder,
                        updateState = updateState,
                        onCheckUpdates = vm::checkForUpdates,
                        onShowUpdate = vm::showUpdate,
                        bottomInset = bottomInset,
                    )
                }
                Route.Playlists -> PlaylistsScreen(
                    playlists = userData.playlists,
                    favoritesCount = userData.favorites.size,
                    resolveCover = resolveCover,
                    onOpen = nav::push,
                    onCreate = onCreatePlaylist,
                    onPlaylistLongPress = onPlaylistLongPress,
                    bottomInset = bottomInset,
                )
                is Route.AlbumDetail -> {
                    val album = collections.albums.firstOrNull { it.id == route.albumId }
                    CollectionScreen(
                        title = album?.title.orEmpty(),
                        subtitle = album?.artist.orEmpty(),
                        songs = album?.songs.orEmpty(),
                        currentSongId = currentSongId,
                        cover = { m ->
                            if (album != null) {
                                Artwork(album.coverSongId, album.coverAlbumId, m.clip(RoundedCornerShape(40.dp)), large = true, placeholderIconSize = 64.dp)
                            }
                        },
                        onBack = { nav.pop() },
                        onPlay = onPlay,
                        onShuffle = onShuffle,
                        onSongLongPress = { onSongLongPress(it, null) },
                        onBulk = { picked, done -> onBulk(picked, null, done) },
                        onDelete = onDeleteSongs,
                        bottomInset = bottomInset,
                    )
                }
                is Route.ArtistDetail -> {
                    val artist = collections.artists.firstOrNull { it.name == route.name }
                    CollectionScreen(
                        title = route.name,
                        subtitle = "",
                        songs = artist?.songs.orEmpty(),
                        currentSongId = currentSongId,
                        cover = { m ->
                            if (artist != null) {
                                Artwork(artist.coverSong.id, artist.coverSong.albumId, m.clip(CircleShape), large = true, placeholderIconSize = 64.dp)
                            }
                        },
                        onBack = { nav.pop() },
                        onPlay = onPlay,
                        onShuffle = onShuffle,
                        onSongLongPress = { onSongLongPress(it, null) },
                        onBulk = { picked, done -> onBulk(picked, null, done) },
                        onDelete = onDeleteSongs,
                        bottomInset = bottomInset,
                    )
                }
                is Route.PlaylistDetail -> {
                    val playlist = userData.playlists.firstOrNull { it.id == route.playlistId }
                    val tracks = remember(playlist, songs) { playlist?.let { vm.resolve(it.songIds) }.orEmpty() }
                    CollectionScreen(
                        title = playlist?.name.orEmpty(),
                        subtitle = "",
                        songs = tracks,
                        currentSongId = currentSongId,
                        cover = { m ->
                            PlaylistCover(playlist, tracks.firstOrNull(), m, large = true, placeholderIconSize = 64.dp)
                        },
                        onBack = { nav.pop() },
                        onPlay = onPlay,
                        onShuffle = onShuffle,
                        onSongLongPress = { onSongLongPress(it, playlist?.id) },
                        onBulk = { picked, done -> onBulk(picked, playlist?.id, done) },
                        onDelete = onDeleteSongs,
                        bottomInset = bottomInset,
                        emptyText = stringResource(Res.string.empty_playlist),
                        menu = if (playlist != null) {
                            listOf(
                                MenuEntry(stringResource(Res.string.cover), PlayerIcons.Image) { onPlaylistCover(playlist) },
                                MenuEntry(stringResource(Res.string.rename), PlayerIcons.Edit) { onRenamePlaylist(playlist) },
                                MenuEntry(stringResource(Res.string.delete), PlayerIcons.Delete) { onDeletePlaylist(playlist) },
                            )
                        } else {
                            emptyList()
                        },
                    )
                }
                is Route.EditSong -> {
                    val song = remember(songs, route.songId) { vm.song(route.songId) }
                    if (song != null) {
                        EditSongScreen(song = song, vm = vm, onBack = { nav.pop() }, bottomInset = bottomInset)
                    } else {
                        // The file is gone (deleted elsewhere): nothing left to edit.
                        LaunchedEffect(Unit) { nav.pop() }
                    }
                }
                Route.Favorites -> {
                    val tracks = remember(userData.favorites, songs) { vm.resolve(userData.favorites) }
                    CollectionScreen(
                        title = stringResource(Res.string.favorites),
                        subtitle = "",
                        songs = tracks,
                        currentSongId = currentSongId,
                        cover = { m -> FavoritesCover(m) },
                        onBack = { nav.pop() },
                        onPlay = onPlay,
                        onShuffle = onShuffle,
                        onSongLongPress = { onSongLongPress(it, null) },
                        onBulk = { picked, done -> onBulk(picked, null, done) },
                        onDelete = onDeleteSongs,
                        bottomInset = bottomInset,
                    )
                }
            }
        }
    }

    // Esc, Alt+Left or the mouse's back button pops the page; the springy back transition plays.
    BackHandler(enabled = backEnabled && nav.previous != null) { nav.pop() }

    val pageTransition = updateTransition(nav.current, label = "screens")
    pageTransition.AnimatedContent(
        contentKey = { it.key },
        transitionSpec = {
            val from = initialState
            val to = targetState
            val direction = when {
                from is Route.Tab && to is Route.Tab -> NavDirection.Tab
                nav.forward -> NavDirection.Forward
                else -> NavDirection.Back
            }
            pageTransition(direction, motion)
        },
        modifier = Modifier.fillMaxSize(),
    ) { route ->
        // Opaque page background: pages overlap while sliding over each other.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { pageBody(route) }
    }
}

/**
 * The phone's tab bar turned on its side: a column of pills, the selected one filled. With
 * "compact" set it shows icons only.
 */
@Composable
private fun NavRail(nav: Navigator, compact: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxHeight()
            .width(if (compact) 88.dp else 220.dp)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 20.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(colors.primaryContainer),
            ) {
                Icon(PlayerIcons.MusicNote, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
            if (!compact) {
                Spacer(Modifier.width(12.dp))
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleLargeEmphasized)
            }
        }
        Route.tabs.forEach { tab ->
            RailItem(tab = tab, selected = nav.tab == tab, compact = compact) {
                nav.selectTab(tab)
            }
            if (tab == Route.Search) Spacer(Modifier.height(8.dp))
            if (tab == Route.Downloads) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RailItem(tab: Route.Tab, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.94f)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(if (selected) colors.secondaryContainer else Color.Transparent, effects, label = "railBg")
    val content by animateColorAsState(
        if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, effects, label = "railFg"
    )
    val label = stringResource(tab.label())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scaleBy(scale)
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable(interactionSource = source, indication = null, role = Role.Tab) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp),
    ) {
        Icon(tab.icon(), contentDescription = if (compact) label else null, tint = content, modifier = Modifier.size(24.dp))
        if (!compact) {
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val SIDE_PANEL_WIDTH = 380.dp

private fun Route.Tab.icon() = when (this) {
    Route.Home -> PlayerIcons.Home
    Route.Search -> PlayerIcons.Search
    Route.Library -> PlayerIcons.Library
    Route.Playlists -> PlayerIcons.Playlist
    Route.Downloads -> PlayerIcons.Download
    Route.Settings -> PlayerIcons.Settings
}

private fun Route.Tab.label() = when (this) {
    Route.Home -> Res.string.nav_home
    Route.Search -> Res.string.search_short
    Route.Library -> Res.string.nav_library
    Route.Playlists -> Res.string.nav_playlists
    Route.Downloads -> Res.string.nav_downloads
    Route.Settings -> Res.string.nav_settings
}

/** Keeps the last track around so exit animations don't render an empty player. */
@Composable
private fun rememberLastNonNull(value: NowPlaying?): NowPlaying? {
    val holder = remember { arrayOfNulls<NowPlaying>(1) }
    if (value != null) holder[0] = value
    return holder[0]
}
