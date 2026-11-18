package com.ku.pierdoon.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import com.ku.pierdoon.ui.components.hairline
import com.ku.pierdoon.ui.components.LocalBackdrop
import com.ku.pierdoon.ui.desktop.WindowButtons
import com.ku.pierdoon.ui.desktop.WindowChrome
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.update.Updater
import com.ku.pierdoon.LibraryState
import com.ku.pierdoon.MainViewModel
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.NAV_MIN_WIDTH
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.PlayHistory
import com.ku.pierdoon.radio.RadioState
import com.ku.pierdoon.ui.player.PlayerRadio
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.UserData
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.playback.PlaybackController
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.components.ArtworkCache
import com.ku.pierdoon.ui.components.Backdrop
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.backdropSource
import com.ku.pierdoon.ui.components.glass
import com.ku.pierdoon.ui.components.rememberBackdrop
import com.ku.pierdoon.ui.desktop.BackHandler
import com.ku.pierdoon.ui.desktop.Files
import com.ku.pierdoon.ui.desktop.Toast
import com.ku.pierdoon.ui.desktop.ToastHost
import com.ku.pierdoon.ui.desktop.trackPointer
import com.ku.pierdoon.ui.library.FillBox
import com.ku.pierdoon.ui.library.LibraryScreen
import com.ku.pierdoon.ui.navigation.Navigator
import com.ku.pierdoon.ui.navigation.NavRail
import com.ku.pierdoon.ui.navigation.NavTab
import com.ku.pierdoon.ui.navigation.Route
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import com.ku.pierdoon.ui.player.rememberExpandingPlayerState
import com.ku.pierdoon.ui.player.expandingPlayerDrag
import com.ku.pierdoon.ui.player.MINI_HANDOFF
import com.ku.pierdoon.ui.player.ExpandingPlayerSheet
import com.ku.pierdoon.ui.player.ExpandingPlayerEffect
import com.ku.pierdoon.ui.player.DeliciousScreen
import com.ku.pierdoon.ui.player.LyricsPanel
import com.ku.pierdoon.ui.player.LocalLyricsSearch
import com.ku.pierdoon.ui.player.LyricsSearch
import com.ku.pierdoon.ui.player.NowPlayingScreen
import com.ku.pierdoon.ui.player.PlayerBar
import com.ku.pierdoon.ui.player.QueuePanel
import com.ku.pierdoon.ui.settings.SPECIAL_THANKS_FADE_MS
import com.ku.pierdoon.ui.settings.SettingsScreen
import com.ku.pierdoon.ui.phone.PhoneScreen
import com.ku.pierdoon.ui.theme.AlbumThemeSeed
import com.ku.pierdoon.data.Backup
import com.ku.pierdoon.data.Duplicates
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.io.File


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
    // A quiz snippet plays: what it is stays hidden until it is answered.
    val quizHides by vm.quizHidesPlayer.collectAsState()
    val position = vm.position.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val lyricsOffset by vm.lyricsOffset.collectAsState()
    val userData by vm.userData.collectAsState()
    val radio by vm.radio.collectAsState()
    val queue by vm.queue.collectAsState()
    val update by vm.update.collectAsState()
    val updatePrompt by vm.updatePrompt.collectAsState()
    val lyricsSearch = remember(vm) { LyricsSearch(
            initialText = vm::lyricsSearchText,
            search = vm::searchLyrics,
            pick = vm::pickLyrics,
            songId = vm::currentSongId,
            ownText = vm::ownLyrics,
            saveOwn = vm::saveOwnLyrics,
            removeOwn = vm::removeOwnLyrics,
            togglePlay = vm::togglePlay,
        )
    }
    val settings by SettingsStore.settings.collectAsState()
    val nowPlaying = rememberLastNonNull(player.nowPlaying)
    val backdrop = rememberBackdrop()
    // How wide the floating rail is, so the screen beside it starts past it.
    var railWidth by remember { mutableStateOf(NAV_MIN_WIDTH.dp) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val exportTitle = stringResource(Res.string.playlist_export)
    val exportedText = stringResource(Res.string.playlist_exported)
    val exportFailedText = stringResource(Res.string.playlist_export_failed)
    val importTitle = stringResource(Res.string.playlist_import)
    val importFailedText = stringResource(Res.string.playlist_import_failed)
    val importNoneText = stringResource(Res.string.playlist_import_none)

    LaunchedEffect(Unit) { vm.loadLibrary() }

    val noOutput = stringResource(Res.string.output_unavailable)
    LaunchedEffect(Unit) {
        PlaybackController.outputUnavailable.collect { Toast.show(noOutput, Toast.LENGTH_LONG) }
    }

    // "Album art" palette: extract a seed once per album, only while that mode is on.
    val nowPlayingCover = nowPlaying?.let { CoverStore.songVersion(it.songId) }
    val nowPlayingArtRevision = nowPlaying?.let { ArtworkCache.albumRevision(it.albumId) }
    LaunchedEffect(nowPlaying?.albumId, nowPlayingCover, nowPlayingArtRevision, settings.colorSource) {
        val np = nowPlaying
        AlbumThemeSeed.seed.value =
            if (settings.colorSource == ColorSource.AlbumArt && np != null) ArtworkCache.themeSeed(np.songId, np.albumId) else null
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    // The Delicious mode, over everything; opened from the bar or the full player.
    var delicious by remember { mutableStateOf(false) }
    // The open player grows out of the bar and shrinks back into it, as on the phone; the bar can
    // also be dragged up (and the player down) with the mouse.
    val sheet = rememberExpandingPlayerState()
    ExpandingPlayerEffect(sheet, expanded && nowPlaying != null)
    val covered by remember { derivedStateOf { sheet.progress.value >= 1f && !sheet.isMoving } }
    var sidePanel by rememberSaveable { mutableStateOf<SidePanel?>(null) }
    // Lyrics are fetched only while actually on screen: the side panel, the open player's, or the
    // Delicious mode's.
    val lyricsOnScreen = (sidePanel == SidePanel.Lyrics && !expanded) || (expanded && sidePanel != SidePanel.Queue) || delicious
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

    val sheets = rememberAppSheets()
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
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

    CompositionLocalProvider(LocalLyricsSearch provides lyricsSearch, LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).trackPointer()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (covered) 0f else 1f }
                    .backdropSource(backdrop)
            ) {
                Row(Modifier.fillMaxSize()) {
                    // The rail floats above, on glass over this strip; it can't sit inside what it blurs.
                    Spacer(Modifier.width(railWidth))
                    // Pages start below the window's top strip, as they did below Windows' own bar, so
                    // nothing scrolls up under the window buttons or sticks to the edge.
                    val chromeTop = if (WindowChrome.controls.value != null) WindowChrome.HEIGHT else 0.dp
                    Box(Modifier.weight(1f).fillMaxHeight().padding(top = chromeTop)) {
                        when {
                            noFolders && (library as? LibraryState.Ready)?.songs.isNullOrEmpty() -> PermissionScreen(
                                onGrant = { Files.pickFolder(chooseFolder)?.let(vm::addMusicFolder) },
                            )
                            library is LibraryState.Loading -> FillBox { LoadingIndicator(Modifier.size(64.dp)) }
                            else -> Screens(
                                onOpenPlayer = { expanded = true },
                                nav = nav,
                                vm = vm,
                                songs = (library as LibraryState.Ready).songs,
                                userData = userData,
                                bottomInset = bottomInset,
                                backEnabled = !expanded,
                                sheets = sheets,
                                onDeleteSongs = deleteSongs,
                                onImportPlaylist = {
                                    Files.pickPlaylist(importTitle)?.let { source ->
                                        scope.launch {
                                            val match = vm.importPlaylist(source)
                                            Toast.show(
                                                when {
                                                    match == null -> importFailedText
                                                    match.songIds.isEmpty() -> importNoneText
                                                    else -> getString(Res.string.playlist_imported, match.songIds.size, match.missed)
                                                },
                                                Toast.LENGTH_LONG,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            NavRail(
                nav = nav,
                tabs = DesktopNavTabs,
                splitAfter = Route.Phone,
                width = settings.navWidth,
                onResize = { w -> SettingsStore.update { it.copy(navWidth = w) } },
                bottomInset = bottomInset,
                backdrop = backdrop,
                modifier = Modifier
                    .onSizeChanged { railWidth = with(density) { it.width.toDp() } }
                    .graphicsLayer { alpha = if (covered) 0f else 1f },
            )
            // Over the screen rather than beside it, on frosted glass: opening it moves nothing.
            AnimatedVisibility(
                visible = sidePanel != null && nowPlaying != null && ready && !quizHides,
                enter = slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 3 } + fadeIn(effects),
                exit = slideOutHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 3 } + fadeOut(effects),
                modifier = Modifier.align(Alignment.TopEnd).graphicsLayer { alpha = if (covered) 0f else 1f },
            ) {
                SidePanelContent(
                    panel = sidePanel ?: SidePanel.Lyrics,
                    bottomInset = bottomInset,
                    backdrop = backdrop,
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
                            onSaveAsPlaylist = { name, songs -> vm.createPlaylist(name, songs.map { it.id }) },
                            onLongPress = { sheets.openSong(it) },
                            modifier = modifier,
                        )
                    },
                )
            }

            // The window's own minimize, maximize and close, over everything in the top right corner.
            WindowChrome.controls.value?.let { controls ->
                WindowButtons(
                    controls = controls,
                    backdrop = backdrop,
                    modifier = Modifier.align(Alignment.TopEnd).graphicsLayer { alpha = if (covered) 0f else 1f },
                )
            }

            if (ready) {
                AnimatedVisibility(
                    visible = player.nowPlaying != null && !quizHides,
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
                            isFavorite = np.songId in userData.favorites || radio?.kept?.containsKey(np.songId) == true,
                            radio = playerRadio(radio, np.songId, vm),
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
                            onOpenDelicious = { delicious = true },
                            onToggleQueue = { sidePanel = if (sidePanel == SidePanel.Queue) null else SidePanel.Queue },
                            onVolume = vm::setVolume,
                            onToggleMute = vm::toggleMute,
                            onOpenArtist = { nav.push(Route.ArtistDetail(it)) },
                            onExpand = { expanded = true },
                            modifier = Modifier
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                .onGloballyPositioned { sheet.onMiniBounds(it.boundsInRoot()) }
                                // Hands over to the growing player in the first quarter of the way.
                                .graphicsLayer { alpha = (1f - sheet.progress.value / MINI_HANDOFF).coerceIn(0f, 1f) }
                                .expandingPlayerDrag(sheet, expanded) { expanded = it },
                        )
                    }
                }
            }

            ExpandingPlayerSheet(sheet) { contentModifier ->
                nowPlaying?.let { np ->
                    NowPlayingScreen(
                        nowPlaying = np,
                        state = player,
                        position = { position.value },
                        lyrics = lyrics,
                        showLyrics = sidePanel != SidePanel.Queue,
                        queue = queue,
                        showQueue = sidePanel == SidePanel.Queue,
                        isFavorite = np.songId in userData.favorites || radio?.kept?.containsKey(np.songId) == true,
                        radio = playerRadio(radio, np.songId, vm),
                        volume = settings.volume,
                        muted = settings.muted,
                        onToggleFavorite = { vm.toggleFavorite(np.songId) },
                        onToggleLyrics = { sidePanel = SidePanel.Lyrics },
                        onToggleQueue = { sidePanel = if (sidePanel == SidePanel.Queue) SidePanel.Lyrics else SidePanel.Queue },
                        onPlayQueueEntry = vm::playQueueEntry,
                        onRemoveQueueEntry = vm::removeQueueEntry,
                        onMoveQueueEntry = vm::moveQueueEntry,
                        onSaveQueue = { name, songs -> vm.createPlaylist(name, songs.map { it.id }) },
                        onSongLongPress = { sheets.openSong(it) },
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
                        onOpenDelicious = { delicious = true },
                        modifier = contentModifier,
                        dragModifier = Modifier.expandingPlayerDrag(sheet, expanded) { expanded = it },
                    )
                }
            }

            AnimatedVisibility(
                visible = delicious && nowPlaying != null,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(350)),
                modifier = Modifier.fillMaxSize(),
            ) {
                nowPlaying?.let { np ->
                    DeliciousScreen(
                        nowPlaying = np,
                        isPlaying = player.isPlaying,
                        position = { position.value },
                        durationMs = player.durationMs,
                        lyrics = lyrics,
                        lyricsOffsetMs = lyricsOffset,
                        levels = PlaybackController.player::levels,
                        onTogglePlay = vm::togglePlay,
                        onPrevious = vm::previous,
                        onNext = vm::next,
                        onClose = { delicious = false },
                    )
                }
            }

            ToastHost(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 16.dp))
        }

    AppSheetsHost(
        sheets = sheets,
        vm = vm,
        nav = nav,
        userData = userData,
        radioEnabled = settings.radioEnabled,
        currentVersion = Updater.currentVersion,
        deleteSongs = deleteSongs,
        onExportPlaylist = { p ->
            Files.savePlaylist(exportTitle, p.name)?.let { target ->
                scope.launch {
                    val ok = vm.exportPlaylist(p, target)
                    Toast.show(if (ok) exportedText else exportFailedText)
                }
            }
        },
        sendToComputer = null,
        onLeavePlayer = { expanded = false },
    )
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
    }
}

@Composable
private fun SidePanelContent(
    panel: SidePanel,
    bottomInset: Dp,
    backdrop: Backdrop,
    lyrics: @Composable (Modifier) -> Unit,
    queue: @Composable (Modifier) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .width(SIDE_PANEL_WIDTH)
            .fillMaxHeight()
            .padding(top = (if (WindowChrome.controls.value != null) WindowChrome.HEIGHT + 4.dp else 12.dp), end = 12.dp, bottom = bottomInset + 12.dp)
            .shadow(16.dp, shape)
            .glass(
                backdrop = backdrop,
                shape = shape,
                tint = colors.surfaceContainerLow.copy(alpha = 0.62f),
                fallback = colors.surfaceContainerLow,
                blurRadius = 40.dp,
            )
            .hairline(shape)
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

/** The pages; the library tab, settings and the phone's page are the computer's own. */
@Composable
private fun Screens(
    nav: Navigator,
    /** Opens the full player (the home screen's top card). */
    onOpenPlayer: () -> Unit,
    vm: MainViewModel,
    songs: List<Song>,
    userData: UserData,
    bottomInset: Dp,
    backEnabled: Boolean,
    sheets: AppSheets,
    onDeleteSongs: (List<Song>, () -> Unit) -> Unit,
    onImportPlaylist: () -> Unit,
) {
    val settings by SettingsStore.settings.collectAsState()
    val plays by PlayHistory.plays.collectAsState()
    val scope = rememberCoroutineScope()
    val backupTitle = stringResource(Res.string.backup_export)
    val restoreTitle = stringResource(Res.string.backup_import)
    val backupSaved = stringResource(Res.string.backup_saved)
    val backupFailed = stringResource(Res.string.backup_save_failed)
    val backupUnreadable = stringResource(Res.string.backup_read_failed)
    val options = remember {
        PageOptions(
            tabs = DesktopTabs,
            settingsInPlace = true,
            downloads = Downloader,
            downloadsIsTab = true,
            describeDuplicate = { it.path },
            onClearHistory = PlayHistory::clear,
        )
    }
    AppPages(
        nav = nav,
        vm = vm,
        songs = songs,
        userData = userData,
        plays = plays,
        settings = settings,
        options = options,
        bottomInset = bottomInset,
        backEnabled = backEnabled,
        onOpenPlayer = onOpenPlayer,
        onSongLongPress = sheets::openSong,
        onBulk = sheets::openBulk,
        onDeleteSongs = onDeleteSongs,
        onPlaylistLongPress = { sheets.playlistAction = it },
        onImportPlaylist = onImportPlaylist,
        onCreatePlaylist = { sheets.createPlaylist() },
        onRenamePlaylist = sheets::renamePlaylist,
        onDeletePlaylist = sheets::deletePlaylist,
        onPlaylistCover = { sheets.coverSheetFor = it.id },
        onRestore = { sheets.restoring = it },
    ) { route, page ->
        when (route) {
            Route.Library -> LibraryScreen(
                songs = page.songs,
                collections = page.collections,
                currentSongId = page.currentSongId,
                onPlay = page.onPlay,
                onShuffleAll = vm::shuffleAll,
                onSongLongPress = page.onSongLongPress,
                onOpen = nav::push,
                sort = settings.songSort,
                sortDescending = settings.songSortDescending,
                onSort = ::setSongSort,
                trackView = settings.trackView,
                onTrackView = { v -> SettingsStore.update { it.copy(trackView = v) } },
                onBulk = page.onBulk,
                onDelete = page.onDelete,
                lists = page.lists,
                bottomInset = bottomInset,
            )
            Route.Phone -> PhoneScreen(settings = settings, bottomInset = bottomInset)
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
                    onExcludeFolder = vm::addExcludedFolder,
                    onUnexcludeFolder = vm::removeExcludedFolder,
                    onDownloadFolder = vm::setDownloadFolder,
                    updateState = updateState,
                    onCheckUpdates = vm::checkForUpdates,
                    onShowUpdate = vm::showUpdate,
                    onThanks = { vm.fadeOutAndPause(SPECIAL_THANKS_FADE_MS) },
                    onFindDuplicates = { nav.push(Route.Duplicates) },
                    onExportBackup = {
                        Files.saveBackup(backupTitle, Backup.suggestedName(System.currentTimeMillis()))?.let { target ->
                            scope.launch { Toast.show(if (vm.exportBackup(target)) backupSaved else backupFailed) }
                        }
                    },
                    onImportBackup = {
                        Files.pickBackup(restoreTitle)?.let { source ->
                            scope.launch { vm.readBackup(source)?.let { sheets.restoring = it } ?: Toast.show(backupUnreadable, Toast.LENGTH_LONG) }
                        }
                    },
                    bottomInset = bottomInset,
                )
            }
            // The phone's pages; nothing leads there on the computer.
            else -> Unit
        }
    }
}

/** The computer's tabs, down the rail on the left. */
internal val DesktopTabs: List<Route.Tab> = listOf(Route.Home, Route.Library, Route.Downloads, Route.Phone, Route.Settings)


private val SIDE_PANEL_WIDTH = 380.dp
/** Keeps the last track around so exit animations don't render an empty player. */
@Composable
private fun rememberLastNonNull(value: NowPlaying?): NowPlaying? {
    val holder = remember { arrayOfNulls<NowPlaying>(1) }
    if (value != null) holder[0] = value
    return holder[0]
}

/** What the players show of the radio for the track [songId], or null without one. */
private fun playerRadio(radio: RadioState?, songId: Long, vm: MainViewModel): PlayerRadio? = radio?.let { r ->
    PlayerRadio(
        seed = r.seed,
        // Kept in the library, it's no longer new.
        fresh = songId in r.fresh && songId !in r.kept,
        offline = r.offline,
        onDislike = { vm.dislikeRadio(songId) },
        onStop = vm::stopRadio,
    )
}

/** The computer's tabs as the rail draws them; the ones after the phone's sit at its bottom. */
private val DesktopNavTabs: List<NavTab> = listOf(
    NavTab(Route.Home, PlayerIcons.Home, Res.string.nav_home),
    NavTab(Route.Library, PlayerIcons.Library, Res.string.nav_library),
    NavTab(Route.Downloads, PlayerIcons.Download, Res.string.nav_downloads),
    NavTab(Route.Phone, PlayerIcons.Smartphone, Res.string.nav_phone),
    NavTab(Route.Settings, PlayerIcons.Settings, Res.string.nav_settings),
)
