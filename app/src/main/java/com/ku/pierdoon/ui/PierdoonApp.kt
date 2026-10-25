package com.ku.pierdoon.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ripple
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.scaleBy
import android.Manifest
import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ku.pierdoon.LibraryState
import com.ku.pierdoon.MainViewModel
import com.ku.pierdoon.R
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.ArtworkCache
import com.ku.pierdoon.ui.components.FrameRateBoost
import com.ku.pierdoon.ui.components.HighFrameRate
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.backdropSource
import com.ku.pierdoon.ui.components.glass
import com.ku.pierdoon.ui.components.rememberBackdrop
import com.ku.pierdoon.ui.library.BulkActionsSheet
import com.ku.pierdoon.ui.library.BulkTarget
import com.ku.pierdoon.ui.library.CollectionScreen
import com.ku.pierdoon.ui.library.MenuEntry
import com.ku.pierdoon.ui.library.SearchScreen
import com.ku.pierdoon.ui.library.ConfirmDeleteDialog
import com.ku.pierdoon.ui.library.FavoritesCover
import com.ku.pierdoon.ui.library.FillBox
import com.ku.pierdoon.ui.library.HomeScreen
import com.ku.pierdoon.ui.library.LibraryScreen
import com.ku.pierdoon.ui.library.PlaylistActionsSheet
import com.ku.pierdoon.ui.library.PlaylistCover
import com.ku.pierdoon.ui.library.PlaylistCoverSheet
import com.ku.pierdoon.ui.edit.EditSongScreen
import com.ku.pierdoon.ui.download.DownloadScreen
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.ui.update.UpdateDialog
import com.ku.pierdoon.ui.library.PlaylistNameDialog
import com.ku.pierdoon.ui.library.PlaylistsScreen
import com.ku.pierdoon.ui.library.SongActionTarget
import com.ku.pierdoon.ui.library.SongActionsSheet
import com.ku.pierdoon.ui.settings.SettingsScreen
import com.ku.pierdoon.ui.navigation.MotionSpecs
import com.ku.pierdoon.ui.navigation.NavDirection
import com.ku.pierdoon.ui.navigation.Navigator
import com.ku.pierdoon.ui.navigation.pageTransition
import com.ku.pierdoon.ui.navigation.Route
import com.ku.pierdoon.ui.navigation.rememberNavigator
import com.ku.pierdoon.ui.player.ExpandingPlayerEffect
import com.ku.pierdoon.ui.player.ExpandingPlayerSheet
import com.ku.pierdoon.ui.player.FullPlayer
import com.ku.pierdoon.ui.player.expandingPlayerDrag
import com.ku.pierdoon.ui.player.rememberExpandingPlayerState
import com.ku.pierdoon.ui.player.MiniPlayer
import kotlin.coroutines.cancellation.CancellationException
import com.ku.pierdoon.ui.theme.AlbumThemeSeed

private val audioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun Context.hasAudioPermission() =
    ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

private sealed interface PlaylistDialog {
    data class Create(val songIds: List<Long>) : PlaylistDialog
    data class Rename(val playlist: Playlist) : PlaylistDialog
    data class Delete(val playlist: Playlist) : PlaylistDialog
}

@Composable
fun PierdoonApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasAudioPermission()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = context.hasAudioPermission() }
    LaunchedEffect(granted) { if (granted) vm.loadLibrary() }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    val library by vm.library.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val position = vm.position.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val lyricsOffset by vm.lyricsOffset.collectAsStateWithLifecycle()
    val userData by vm.userData.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val updatePrompt by vm.updatePrompt.collectAsStateWithLifecycle()
    val nowPlaying = rememberLastNonNull(player.nowPlaying)
    val backdrop = rememberBackdrop()
    val nav = rememberNavigator()
    val settings by SettingsStore.settings.collectAsStateWithLifecycle()

    // "Album art" palette: extract a seed once per album, only while that mode is on.
    val nowPlayingCover = nowPlaying?.let { CoverStore.songVersion(it.songId) }
    // A cover written into the file keeps the album id; the revision says the art changed.
    val nowPlayingArtRevision = nowPlaying?.let { ArtworkCache.albumRevision(it.albumId) }
    LaunchedEffect(nowPlaying?.albumId, nowPlayingCover, nowPlayingArtRevision, settings.colorSource) {
        val np = nowPlaying
        AlbumThemeSeed.seed.value =
            if (settings.colorSource == ColorSource.AlbumArt && np != null) {
                ArtworkCache.themeSeed(context.applicationContext, np.songId, np.albumId)
            } else {
                null
            }
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    // Lyrics are fetched only while actually on screen.
    LaunchedEffect(expanded, showLyrics) { vm.setLyricsVisible(expanded && showLyrics) }

    val sheet = rememberExpandingPlayerState()
    ExpandingPlayerEffect(sheet, expanded && nowPlaying != null)
    // Predictive back on the open player: it shrinks under the finger, then folds into the mini player.
    PredictiveBackHandler(enabled = expanded && !showLyrics && !showQueue) { events ->
        try {
            events.collect { e -> sheet.seekBack(e.progress) }
            expanded = false
        } catch (_: CancellationException) {
            sheet.reopen()
        }
    }
    BackHandler(enabled = expanded && showLyrics) { showLyrics = false }
    BackHandler(enabled = expanded && showQueue) { showQueue = false }
    // Once the full player fully covers the screen, stop drawing everything underneath it.
    val covered by remember { derivedStateOf { sheet.progress.value >= 1f && !sheet.isMoving } }
    // The open player's wave animates endlessly: leave those frames at the system default rate.
    HighFrameRate.suppressed = covered && player.isPlaying && settings.wavyProgress


    // A link shared into the app (or the downloads notification) opens the downloads page.
    val openDownloads by Downloader.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openDownloads) {
        if (openDownloads) {
            Downloader.openRequest.value = false
            expanded = false
            nav.push(Route.Downloads)
        }
    }

    var songAction by remember { mutableStateOf<SongActionTarget?>(null) }
    var playlistAction by remember { mutableStateOf<Playlist?>(null) }
    var coverSheetFor by remember { mutableStateOf<Long?>(null) }
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }

    // The system asks before the files go; saved, so a recreated activity still cleans up after it.
    var deletingSongIds by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var afterDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    val deletedText = stringResource(R.string.deleted)
    val resources = LocalResources.current
    val deleteConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val ids = deletingSongIds
        deletingSongIds = null
        if (r.resultCode == Activity.RESULT_OK && ids != null) {
            ids.forEach(vm::onSongDeleted)
            afterDelete?.invoke()
            val text = if (ids.size == 1) deletedText else resources.getQuantityString(R.plurals.deleted_count, ids.size, ids.size)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
        afterDelete = null
    }
    // Below Android 11 an app can only ask to delete its own files.
    val deleteSongs: ((List<Song>, () -> Unit) -> Unit)? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        { songs, done ->
            vm.deleteRequest(songs)?.let { request ->
                deletingSongIds = songs.map { it.id }.toLongArray()
                afterDelete = done
                deleteConsent.launch(IntentSenderRequest.Builder(request).build())
            }
        }
    } else {
        null
    }
    var bulk by remember { mutableStateOf<BulkTarget?>(null) }

    // Lists reserve exactly the height of the floating chrome below them.
    var chromeHeightPx by remember { mutableIntStateOf(0) }
    val bottomInset = with(LocalDensity.current) { chromeHeightPx.toDp() }

    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val ready = granted && library is LibraryState.Ready

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (covered) 0f else 1f }
                .backdropSource(backdrop)
        ) {
            when {
                !granted -> PermissionScreen(onGrant = { permissionLauncher.launch(audioPermission) })
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

        if (ready) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (covered) 0f else 1f }
                    .onSizeChanged { chromeHeightPx = it.height },
            ) {
                AnimatedVisibility(
                    visible = player.nowPlaying != null,
                    enter = slideInVertically(spatialOffset) { it } + fadeIn(effects),
                    exit = slideOutVertically(spatialOffset) { it } + fadeOut(effects),
                ) {
                    nowPlaying?.let { np ->
                        MiniPlayer(
                            nowPlaying = np,
                            isPlaying = player.isPlaying,
                            position = { position.value },
                            durationMs = player.durationMs,
                            onTogglePlay = vm::togglePlay,
                            onNext = vm::next,
                            onExpand = { expanded = true },
                            backdrop = backdrop,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp)
                                .fillMaxWidth()
                                .onGloballyPositioned { sheet.onMiniBounds(it.boundsInRoot()) }
                                .graphicsLayer { alpha = (1f - sheet.progress.value * 5f).coerceIn(0f, 1f) },
                            dragModifier = Modifier.expandingPlayerDrag(sheet, expanded) { expanded = it },
                        )
                    }
                }
                GlassNavigationBar(nav = nav, backdrop = backdrop, floating = settings.floatingNav)
            }
        }

        FrameRateBoost(active = sheet.isMoving)
        nowPlaying?.let { np ->
                ExpandingPlayerSheet(sheet) { contentModifier ->
                    FullPlayer(
                        nowPlaying = np,
                        state = player,
                        position = { position.value },
                        lyrics = lyrics,
                        showLyrics = showLyrics,
                        queue = queue,
                        showQueue = showQueue,
                        isFavorite = np.songId in userData.favorites,
                        onToggleFavorite = { vm.toggleFavorite(np.songId) },
                        // Lyrics and queue share the space above the controls: one at a time.
                        onToggleLyrics = { showLyrics = !showLyrics; showQueue = false },
                        onToggleQueue = { showQueue = !showQueue; showLyrics = false },
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
                        onOpenArtist = { name ->
                            expanded = false
                            nav.push(Route.ArtistDetail(name))
                        },
                        onCollapse = { expanded = false },
                        modifier = contentModifier,
                        dragModifier = Modifier.expandingPlayerDrag(sheet, expanded) { expanded = it },
                    )
                }
        }
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
            onDelete = deleteSongs?.let { delete -> { delete(listOf(target.song)) {} } },
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
            onDelete = deleteSongs?.let { delete -> { delete(target.songs, target.onDone) } },
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
    if (updatePrompt) {
        UpdateDialog(state = update, onInstall = vm::installUpdate, onDismiss = vm::dismissUpdate)
    }
    when (val d = dialog) {
        is PlaylistDialog.Create -> PlaylistNameDialog(
            title = stringResource(R.string.new_playlist),
            confirmLabel = stringResource(R.string.create),
            onDismiss = { dialog = null },
            onConfirm = { name ->
                vm.createPlaylist(name, d.songIds)
                dialog = null
            },
        )
        is PlaylistDialog.Rename -> PlaylistNameDialog(
            title = stringResource(R.string.rename),
            confirmLabel = stringResource(R.string.save),
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
private fun Screens(
    nav: Navigator,
    vm: MainViewModel,
    songs: List<Song>,
    currentSongId: Long?,
    userData: com.ku.pierdoon.data.UserData,
    bottomInset: androidx.compose.ui.unit.Dp,
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
    val collections by vm.collections.collectAsStateWithLifecycle()
    val settings by SettingsStore.settings.collectAsStateWithLifecycle()
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
                Route.Settings -> {
                    SettingsScreen(
                        settings = settings,
                        onUpdate = SettingsStore::update,
                        trackCount = songs.size,
                        lyricsCacheSize = vm::lyricsCacheSize,
                        onClearLyricsCache = vm::clearLyricsCache,
                        onRescan = vm::rescanLibrary,
                        updateState = vm.update.collectAsStateWithLifecycle().value,
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
                        emptyText = stringResource(R.string.empty_playlist),
                        menu = if (playlist != null) {
                            listOf(
                                MenuEntry(stringResource(R.string.cover), PlayerIcons.Image) { onPlaylistCover(playlist) },
                                MenuEntry(stringResource(R.string.rename), PlayerIcons.Edit) { onRenamePlaylist(playlist) },
                                MenuEntry(stringResource(R.string.delete), PlayerIcons.Delete) { onDeletePlaylist(playlist) },
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
                        title = stringResource(R.string.favorites),
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

    // System back (gesture or button) pops the page; the springy back transition plays.
    BackHandler(enabled = backEnabled && nav.previous != null) { nav.pop() }

    val pageTransition = updateTransition(nav.current, label = "screens")
    FrameRateBoost(active = pageTransition.isRunning)

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

/** Docked (edge-to-edge) or floating (pill) tab bar; switching cross-fades between them. */
@Composable
private fun GlassNavigationBar(
    nav: Navigator,
    backdrop: com.ku.pierdoon.ui.components.Backdrop,
    floating: Boolean,
) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = floating,
        transitionSpec = {
            (fadeIn(effects) + slideInVertically(spatial) { it / 2 })
                .togetherWith(fadeOut(effects) + slideOutVertically(spatial) { it / 2 })
                .using(SizeTransform(clip = false))
        },
        label = "navMode",
    ) { isFloating ->
        if (isFloating) FloatingNavBar(nav, backdrop) else DockedNavBar(nav, backdrop)
    }
}

/**
 * M3 Expressive floating bar: a large glass pill. Only the selected tab shows its label,
 * inside an indicator that grows to fit it, so labels always have room.
 */
@Composable
private fun FloatingNavBar(nav: Navigator, backdrop: com.ku.pierdoon.ui.components.Backdrop) {
    val colors = MaterialTheme.colorScheme
    val pill = RoundedCornerShape(50)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .height(FLOATING_NAV_HEIGHT)
            .border(1.dp, colors.onSurface.copy(alpha = 0.08f), pill)
            .glass(
                backdrop = backdrop,
                shape = pill,
                tint = colors.surfaceContainerHigh.copy(alpha = 0.82f),
                fallback = colors.surfaceContainerHigh,
            )
            .padding(8.dp),
    ) {
        Route.tabs.forEach { tab ->
            val selected = nav.tab == tab
            val weight by animateFloatAsState(
                targetValue = if (selected) SELECTED_WEIGHT else 1f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "navWeight",
            )
            FloatingNavItem(
                tab = tab,
                selected = selected,
                onClick = { nav.selectTab(tab) },
                // Springs overshoot; weights must stay positive.
                modifier = Modifier.weight(weight.coerceAtLeast(0.5f)),
            )
        }
    }
}

@Composable
private fun FloatingNavItem(tab: Route.Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.9f)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        if (selected) colors.secondaryContainer else Color.Transparent, effects, label = "navItemBg"
    )
    val content by animateColorAsState(
        if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, effects, label = "navItemFg"
    )
    val spatialF = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .scaleBy(scale)
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable(interactionSource = source, indication = ripple(), role = Role.Tab) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .semantics { this.selected = selected },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(tab.icon(), contentDescription = stringResource(tab.label()), tint = content, modifier = Modifier.size(26.dp))
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(spatialF) + fadeIn(fade),
                exit = shrinkHorizontally(spatialF) + fadeOut(fade),
            ) {
                Text(
                    text = stringResource(tab.label()),
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DockedNavBar(nav: Navigator, backdrop: com.ku.pierdoon.ui.components.Backdrop) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    ShortNavigationBar(
        containerColor = Color.Transparent,
        modifier = Modifier.glass(
            backdrop = backdrop,
            shape = RectangleShape,
            tint = colors.surfaceContainer.copy(alpha = 0.72f),
            fallback = colors.surfaceContainer,
        ),
    ) {
        Route.tabs.forEach { tab ->
            ShortNavigationBarItem(
                selected = nav.tab == tab,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    nav.selectTab(tab)
                },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = {
                    Text(
                        stringResource(tab.label()),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

private val FLOATING_NAV_HEIGHT = 72.dp
private const val SELECTED_WEIGHT = 3f

private fun Route.Tab.icon() = when (this) {
    Route.Home -> PlayerIcons.Home
    Route.Library -> PlayerIcons.Library
    Route.Playlists -> PlayerIcons.Playlist
    Route.Settings -> PlayerIcons.Settings
}

private fun Route.Tab.label() = when (this) {
    Route.Home -> R.string.nav_home
    Route.Library -> R.string.nav_library
    Route.Playlists -> R.string.nav_playlists
    Route.Settings -> R.string.nav_settings
}

/** Keeps the last track around so exit animations don't render an empty player. */
@Composable
private fun rememberLastNonNull(value: NowPlaying?): NowPlaying? {
    val holder = remember { arrayOfNulls<NowPlaying>(1) }
    if (value != null) holder[0] = value
    return holder[0]
}
