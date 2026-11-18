package com.ku.pierdoon.ui

import androidx.compose.foundation.layout.plus
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import org.jetbrains.compose.resources.getString
import com.ku.pierdoon.data.Duplicates
import com.ku.pierdoon.data.Backup
import com.ku.pierdoon.data.update.Updater
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedContentTransitionScope
import com.ku.pierdoon.ui.player.PlayerDevices
import com.ku.pierdoon.ui.remote.PcPlayback
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import com.ku.pierdoon.ui.components.LocalRemoteCovers
import com.ku.pierdoon.ui.remote.rememberPcPlayback
import com.ku.pierdoon.remote.PcRemote
import com.ku.pierdoon.remote.PcTransfers
import com.ku.pierdoon.ui.remote.TransferPrompts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ku.pierdoon.LibraryState
import com.ku.pierdoon.MainViewModel
import com.ku.pierdoon.MainViewModelHolder
import com.ku.pierdoon.R
import com.ku.pierdoon.data.M3U_EXTENSION
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.ProgressStyle
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.PlayHistory
import com.ku.pierdoon.ui.player.PlayerRadio
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.ui.components.ArtworkCache
import com.ku.pierdoon.ui.components.FrameRateBoost
import com.ku.pierdoon.ui.components.HighFrameRate
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.backdropSource
import com.ku.pierdoon.ui.components.rememberBackdrop
import com.ku.pierdoon.ui.library.FillBox
import com.ku.pierdoon.ui.library.LibraryScreen
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.ui.download.offer
import com.ku.pierdoon.ui.library.PlaylistsScreen
import com.ku.pierdoon.ui.settings.SPECIAL_THANKS_FADE_MS
import com.ku.pierdoon.ui.settings.SettingsScreen
import com.ku.pierdoon.ui.remote.PcRemoteScreen
import com.ku.pierdoon.ui.navigation.Navigator
import com.ku.pierdoon.ui.navigation.FloatingNavBar
import com.ku.pierdoon.ui.navigation.NavRail
import com.ku.pierdoon.ui.navigation.NavTab
import com.ku.pierdoon.ui.navigation.useNavRail
import com.ku.pierdoon.data.NAV_MIN_WIDTH
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableFloatStateOf
import com.ku.pierdoon.ui.navigation.Route
import com.ku.pierdoon.ui.navigation.rememberNavigator
import com.ku.pierdoon.ui.player.ExpandingPlayerEffect
import com.ku.pierdoon.ui.player.ExpandingPlayerSheet
import com.ku.pierdoon.ui.player.MINI_HANDOFF
import com.ku.pierdoon.ui.player.FullPlayer
import com.ku.pierdoon.ui.player.LocalLyricsSearch
import com.ku.pierdoon.ui.player.LyricsSearch
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



@Composable
fun PierdoonApp(vm: MainViewModel = viewModel<MainViewModelHolder>().model) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasAudioPermission()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = context.hasAudioPermission() }
    LaunchedEffect(granted) { if (granted) vm.loadLibrary() }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    val library by vm.library.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    // A quiz snippet plays: what it is stays hidden until it is answered.
    val quizHides by vm.quizHidesPlayer.collectAsStateWithLifecycle()
    val position = vm.position.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val lyricsOffset by vm.lyricsOffset.collectAsStateWithLifecycle()
    val userData by vm.userData.collectAsStateWithLifecycle()
    val radio by vm.radio.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val updatePrompt by vm.updatePrompt.collectAsStateWithLifecycle()
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

    // The paired computer's playback takes the player over while it's the active one: playing
    // something on the computer hands it over (and pauses the phone), playing here takes it back.
    // The open player keeps an eye on the computer even while showing the phone: its switch says
    // what plays there.
    val pc = rememberPcPlayback(lyricsVisible = expanded && showLyrics, peek = expanded)
    val pcActive by PcRemote.active.collectAsStateWithLifecycle()
    val pcPaired by PcRemote.paired.collectAsStateWithLifecycle()
    val pcState by PcRemote.state.collectAsStateWithLifecycle()
    val pcConnection by PcRemote.connection.collectAsStateWithLifecycle()
    // Still drawn while the player animates back to the phone, when pc is already null.
    val lastPc = remember { arrayOfNulls<PcPlayback>(1) }
    if (pc != null) lastPc[0] = pc
    SideEffect { PcRemote.inPlayer = pc != null }
    LaunchedEffect(player.isPlaying) { if (player.isPlaying) PcRemote.setActive(false) }
    LaunchedEffect(pcActive) { if (pcActive && vm.player.value.isPlaying) vm.togglePlay() }
    val pcCovers = remember { { id: Long, size: Int -> PcRemote.coverUrl(id, size) } }

    // Lyrics are fetched only while actually on screen.
    LaunchedEffect(expanded, showLyrics, pc == null) { vm.setLyricsVisible(expanded && showLyrics && pc == null) }

    val shownNowPlaying = pc?.nowPlaying ?: nowPlaying
    val sheet = rememberExpandingPlayerState()
    ExpandingPlayerEffect(sheet, expanded && shownNowPlaying != null)
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
    HighFrameRate.suppressed = covered && player.isPlaying && settings.progressStyle == ProgressStyle.Wavy && !settings.nothingStyle


    // A link shared into the app (or the downloads notification) opens the downloads page.
    val openDownloads by Downloader.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openDownloads) {
        if (openDownloads) {
            Downloader.openRequest.value = false
            expanded = false
            nav.push(Route.Downloads)
        }
    }
    // With a computer paired, a shared link asks first which of the two should download it.
    val shared by PcRemote.shareRequest.collectAsStateWithLifecycle()
    shared?.let { url ->
        ShareTargetDialog(
            pcName = pcPaired?.name.orEmpty(),
            onPhone = {
                PcRemote.shareRequest.value = null
                Downloader.offer(url)
                Downloader.openRequest.value = true
            },
            onPc = {
                PcRemote.shareRequest.value = null
                PcRemote.pendingDownload.value = url
                expanded = false
                nav.push(Route.Computer)
            },
            onDismiss = { PcRemote.shareRequest.value = null },
        )
    }

    val sheets = rememberAppSheets()

    val spatialF = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val playlistFiles = rememberCoroutineScope()
    var exporting by remember { mutableStateOf<Playlist?>(null) }
    val exportedText = stringResource(Res.string.playlist_exported)
    val exportFailedText = stringResource(Res.string.playlist_export_failed)
    val importFailedText = stringResource(Res.string.playlist_import_failed)
    val importNoneText = stringResource(Res.string.playlist_import_none)
    val resources = LocalResources.current
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(M3U_MIME)) { target ->
        val playlist = exporting
        exporting = null
        if (target != null && playlist != null) {
            playlistFiles.launch {
                val ok = vm.exportPlaylist(playlist, target)
                Toast.makeText(context, if (ok) exportedText else exportFailedText, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source != null) {
            playlistFiles.launch {
                val match = vm.importPlaylist(source)
                val text = when {
                    match == null -> importFailedText
                    match.songIds.isEmpty() -> importNoneText
                    else -> resources.getString(R.string.playlist_imported, match.songIds.size, match.missed)
                }
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }
        }
    }
    // The system asks before the files go; saved, so a recreated activity still cleans up after it.
    var deletingSongIds by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var afterDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    val deletedText = stringResource(Res.string.deleted)
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

    // Lists reserve exactly the height of the floating chrome below them.
    var chromeHeightPx by remember { mutableIntStateOf(0) }
    val bottomInset = with(LocalDensity.current) { chromeHeightPx.toDp() }
    // The rail's width as dragged (icons only at first), and as laid out with the screen's edge.
    var railSize by rememberSaveable { mutableFloatStateOf(NAV_MIN_WIDTH) }
    var railWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val ready = granted && library is LibraryState.Ready

    // A tablet, or the phone on its side: the tabs go down a rail on the left, as on the computer.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val rail = useNavRail(maxWidth)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (covered) 0f else 1f }
                .backdropSource(backdrop)
                .padding(start = if (rail && ready) railWidth else 0.dp)
        ) {
            when {
                !granted -> PermissionScreen(onGrant = { permissionLauncher.launch(audioPermission) })
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
                    onImportPlaylist = { importFile.launch(M3U_TYPES) },
                )
            }
        }

        if (ready) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Beside the rail, which runs the full height: a phone on its side has none to spare.
                    .padding(start = if (rail) railWidth else 0.dp)
                    .graphicsLayer { alpha = if (covered) 0f else 1f }
                    .onSizeChanged { chromeHeightPx = it.height },
            ) {
                AnimatedVisibility(
                    visible = (player.nowPlaying != null && !quizHides) || pc != null,
                    enter = slideInVertically(spatialOffset) { it } + fadeIn(effects),
                    exit = slideOutVertically(spatialOffset) { it } + fadeOut(effects),
                ) {
                    AnimatedContent(
                        targetState = pc != null,
                        transitionSpec = { playerSwitch(effects, spatialF) },
                        label = "miniSource",
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp)
                            .fillMaxWidth()
                            .onGloballyPositioned { sheet.onMiniBounds(it.boundsInRoot()) }
                            .graphicsLayer { alpha = (1f - sheet.progress.value / MINI_HANDOFF).coerceIn(0f, 1f) },
                    ) { onPc ->
                        val shownPc = if (onPc) pc ?: lastPc[0] else null
                        val np = shownPc?.nowPlaying ?: nowPlaying ?: return@AnimatedContent
                        CompositionLocalProvider(LocalRemoteCovers provides if (shownPc != null) pcCovers else null) {
                            MiniPlayer(
                                nowPlaying = np,
                                isPlaying = shownPc?.state?.isPlaying ?: player.isPlaying,
                                position = { shownPc?.position ?: position.value },
                                durationMs = shownPc?.state?.durationMs ?: player.durationMs,
                                onTogglePlay = if (shownPc != null) PcRemote::toggle else vm::togglePlay,
                                onNext = if (shownPc != null) PcRemote::next else vm::next,
                                onPrevious = if (shownPc != null) PcRemote::previous else vm::previousTrack,
                                sourceName = shownPc?.name,
                                onExpand = { expanded = true },
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth(),
                                dragModifier = Modifier.expandingPlayerDrag(sheet, expanded) { expanded = it },
                            )
                        }
                    }
                }
                if (rail) Spacer(Modifier.navigationBarsPadding()) else FloatingNavBar(nav, PhoneNavTabs, backdrop)
            }
            // Beside the pages and the mini player, top to bottom; it can't sit inside what it blurs.
            if (rail) {
                NavRail(
                    nav = nav,
                    tabs = PhoneNavTabs,
                    width = railSize,
                    onResize = { railSize = it },
                    bottomInset = 0.dp,
                    backdrop = backdrop,
                    modifier = Modifier
                        // Measured with the inset beside it, so the pages start past both.
                        .onSizeChanged { railWidth = with(density) { it.width.toDp() } }
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
                        )
                        .graphicsLayer { alpha = if (covered) 0f else 1f },
                )
            }
        }

        FrameRateBoost(active = sheet.isMoving)
        val devices = pcPaired?.let { paired ->
            val online = pcConnection == PcRemote.Connection.Connected
            PlayerDevices(
                pcName = paired.name,
                onPc = pc != null,
                phoneTrack = nowPlaying?.let { listOf(it.title, it.artist).filter(String::isNotBlank).joinToString(" · ") },
                pcTrack = pcState.song?.takeIf { online }?.let { listOf(it.title, it.artist).filter(String::isNotBlank).joinToString(" · ") },
                pcOnline = online,
                onSwitch = PcRemote::setActive,
                onOpenComputer = {
                    expanded = false
                    nav.push(Route.Computer)
                },
            )
        }
        if (shownNowPlaying != null) {
            ExpandingPlayerSheet(sheet) { contentModifier ->
                AnimatedContent(
                    targetState = pc != null,
                    transitionSpec = { playerSwitch(effects, spatialF) },
                    label = "playerSource",
                ) { onPc ->
                    val pc = if (onPc) pc ?: lastPc[0] else null
                    if (pc != null) {
                        val np = pc.nowPlaying
                        CompositionLocalProvider(LocalRemoteCovers provides pcCovers) {
                        FullPlayer(
                            nowPlaying = np,
                            state = pc.state,
                            position = { pc.position },
                            lyrics = pc.lyrics,
                            showLyrics = showLyrics,
                            queue = pc.queue,
                            showQueue = showQueue,
                            isFavorite = pc.favorite,
                            onToggleFavorite = { PcRemote.toggleFavorite(np.songId) },
                            onToggleLyrics = { showLyrics = !showLyrics; showQueue = false },
                            onToggleQueue = { showQueue = !showQueue; showLyrics = false },
                            onPlayQueueEntry = PcRemote::playQueueEntry,
                            onRemoveQueueEntry = PcRemote::removeQueueEntry,
                            onMoveQueueEntry = PcRemote::moveQueueEntry,
                            // The phone's song actions (playlists, edit, delete) are about its own files.
                            onSongLongPress = { },
                            onRetryLyrics = { },
                            lyricsOffsetMs = pc.lyricsOffsetMs,
                            onShiftLyrics = PcRemote::shiftLyrics,
                            onResetLyricsOffset = PcRemote::resetLyricsOffset,
                            onTogglePlay = PcRemote::toggle,
                            onPrevious = PcRemote::previous,
                            onNext = PcRemote::next,
                            onSeek = PcRemote::seek,
                            onToggleShuffle = PcRemote::toggleShuffle,
                            onCycleRepeat = PcRemote::cycleRepeat,
                            onOpenArtist = {
                                expanded = false
                                nav.push(Route.Computer)
                            },
                            pickArtist = false,
                            onCollapse = { expanded = false },
                            modifier = contentModifier,
                            dragModifier = Modifier.expandingPlayerDrag(sheet, expanded) { expanded = it },
                            devices = devices,
                        )
                        }
                        return@AnimatedContent
                    }
                    val np = nowPlaying ?: return@AnimatedContent
                    // Lyrics picked by hand go on the phone's own track; the computer's player above gets none.
                    CompositionLocalProvider(LocalLyricsSearch provides lyricsSearch) {
                        FullPlayer(
                            nowPlaying = np,
                            state = player,
                            position = { position.value },
                            lyrics = lyrics,
                            showLyrics = showLyrics,
                            queue = queue,
                            showQueue = showQueue,
                            isFavorite = np.songId in userData.favorites || radio?.kept?.containsKey(np.songId) == true,
                            onToggleFavorite = { vm.toggleFavorite(np.songId) },
                            // Lyrics and queue share the space above the controls: one at a time.
                            onToggleLyrics = { showLyrics = !showLyrics; showQueue = false },
                            onToggleQueue = { showQueue = !showQueue; showLyrics = false },
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
                            onPreviousTrack = vm::previousTrack,
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
                            devices = devices,
                            // The phone's radio, not shown over the computer's player.
                            radio = radio?.takeIf { devices?.onPc != true }?.let { r ->
                                PlayerRadio(
                                    seed = r.seed,
                                    // Kept in the library, it's no longer new.
                                    fresh = np.songId in r.fresh && np.songId !in r.kept,
                                    offline = r.offline,
                                    onDislike = { vm.dislikeRadio(np.songId) },
                                    onStop = vm::stopRadio,
                                )
                            },
                        )
                    }
                }
            }
        }
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
            exporting = p
            exportFile.launch("${p.name}.$M3U_EXTENSION")
        },
        sendToComputer = pcPaired?.let { { songs -> PcTransfers.sendToComputer(songs) } },
        onLeavePlayer = { expanded = false },
    )
    TransferPrompts()
}

/** The pages; the library tab, playlists, settings and the computer's page are the phone's own. */
@Composable
private fun Screens(
    nav: Navigator,
    /** Opens the full player (the home screen's top card). */
    onOpenPlayer: () -> Unit,
    vm: MainViewModel,
    songs: List<Song>,
    userData: com.ku.pierdoon.data.UserData,
    bottomInset: androidx.compose.ui.unit.Dp,
    backEnabled: Boolean,
    sheets: AppSheets,
    onDeleteSongs: ((List<Song>, () -> Unit) -> Unit)?,
    onImportPlaylist: () -> Unit,
) {
    val settings by SettingsStore.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val history = remember(context) { PlayHistory.get(context) }
    val plays by history.plays.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val backupSaved = stringResource(Res.string.backup_saved)
    val backupFailed = stringResource(Res.string.backup_save_failed)
    val backupUnreadable = stringResource(Res.string.backup_read_failed)
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { target ->
        if (target != null) scope.launch {
            Toast.makeText(context, if (vm.exportBackup(target)) backupSaved else backupFailed, Toast.LENGTH_SHORT).show()
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source != null) scope.launch {
            vm.readBackup(source)?.let { sheets.restoring = it } ?: Toast.makeText(context, backupUnreadable, Toast.LENGTH_LONG).show()
        }
    }
    val options = PageOptions(
        tabs = PhoneTabs,
        settingsInPlace = false,
        downloads = Downloader,
        downloadsIsTab = false,
        // No file path on Android: the album and when the copy arrived tell them apart.
        describeDuplicate = { s ->
            listOfNotNull(
                s.album.takeIf { !s.albumFromFolder && it.isNotBlank() },
                s.dateAdded.takeIf { it > 0 }?.let {
                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(it * 1000))
                },
            ).joinToString("\n")
        },
        onClearHistory = history::clear,
        homeAction = {
            ExpressiveIconButton(
                onClick = { nav.push(Route.Computer) },
                icon = PlayerIcons.Desktop,
                contentDescription = stringResource(Res.string.pc_open),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(48.dp),
            )
        },
    )
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
        transitionEffect = { running -> FrameRateBoost(active = running) },
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
                onBulk = page.onBulk,
                onDelete = page.onDelete,
                albumGrid = settings.albumGrid,
                onAlbumGrid = { g -> SettingsStore.update { it.copy(albumGrid = g) } },
                bottomInset = bottomInset,
            )
            Route.Playlists -> PlaylistsScreen(lists = page.lists, onOpen = nav::push, bottomInset = bottomInset)
            Route.Computer -> PcRemoteScreen(
                onBack = { nav.pop() },
                bottomInset = bottomInset,
                onSaveToPhone = if (PcTransfers.supportedToPhone) {
                    { picked -> PcTransfers.saveToPhone(picked, songs) }
                } else {
                    null
                },
            )
            Route.Settings, is Route.SettingsPage -> SettingsScreen(
                section = (route as? Route.SettingsPage)?.section,
                onOpen = { nav.push(Route.SettingsPage(it)) },
                onOpenComputer = { nav.push(Route.Computer) },
                onBack = { nav.pop() },
                settings = settings,
                onUpdate = SettingsStore::update,
                trackCount = songs.size,
                lyricsCacheSize = vm::lyricsCacheSize,
                onClearLyricsCache = vm::clearLyricsCache,
                onRescan = vm::rescanLibrary,
                musicFolders = vm::musicFolders,
                updateState = vm.update.collectAsStateWithLifecycle().value,
                onCheckUpdates = vm::checkForUpdates,
                onShowUpdate = vm::showUpdate,
                onThanks = { vm.fadeOutAndPause(SPECIAL_THANKS_FADE_MS) },
                onFindDuplicates = { nav.push(Route.Duplicates) },
                onExportBackup = { saveBackup.launch(Backup.suggestedName(System.currentTimeMillis()) + "." + Backup.EXTENSION) },
                onImportBackup = { openBackup.launch(arrayOf("*/*")) },
                bottomInset = bottomInset,
            )
            // The computer's page for the phone; nothing leads there on the phone.
            else -> Unit
        }
    }
}

/** Keeps the last track around so exit animations don't render an empty player. */
@Composable
private fun rememberLastNonNull(value: NowPlaying?): NowPlaying? {
    val holder = remember { arrayOfNulls<NowPlaying>(1) }
    if (value != null) holder[0] = value
    return holder[0]
}

/** Phone ⇄ computer in the player: the new one fades and grows in over the old. */
private fun AnimatedContentTransitionScope<Boolean>.playerSwitch(
    effects: FiniteAnimationSpec<Float>,
    spatial: FiniteAnimationSpec<Float>,
): ContentTransform =
    (fadeIn(effects) + scaleIn(spatial, initialScale = 0.94f))
        .togetherWith(fadeOut(effects) + scaleOut(spatial, targetScale = 1.04f))
        .using(SizeTransform(clip = false))

/** "Share → Pier.doon" with a computer paired: download here or there. */
@Composable
private fun ShareTargetDialog(pcName: String, onPhone: () -> Unit, onPc: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(PlayerIcons.Download, contentDescription = null) },
        title = { Text(stringResource(Res.string.share_where)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(PlayerIcons.Smartphone, stringResource(Res.string.share_phone), onPhone),
                    Triple(PlayerIcons.Desktop, stringResource(Res.string.share_pc, pcName), onPc),
                ).forEach { (icon, label, onClick) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(onClick = onClick)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

private const val M3U_MIME = "audio/x-mpegurl"
/** What file managers call a playlist; some know only the bare bytes. */
private val M3U_TYPES = arrayOf(M3U_MIME, "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl", "application/octet-stream")

/** The phone's tabs; Downloads and the computer open as pages. */
private val PhoneNavTabs: List<NavTab> = listOf(
    NavTab(Route.Home, PlayerIcons.Home, Res.string.nav_home),
    NavTab(Route.Library, PlayerIcons.Library, Res.string.nav_library),
    NavTab(Route.Playlists, PlayerIcons.Playlist, Res.string.nav_playlists),
    NavTab(Route.Settings, PlayerIcons.Settings, Res.string.nav_settings),
)

private val PhoneTabs: List<Route.Tab> = PhoneNavTabs.map { it.route }
