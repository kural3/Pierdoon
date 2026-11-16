package com.ku.pierdoon.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.AppModel
import com.ku.pierdoon.data.Backup
import com.ku.pierdoon.data.DuplicateGroup
import com.ku.pierdoon.data.Duplicates
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.SharedSettings
import com.ku.pierdoon.data.SharedSettingsStore
import com.ku.pierdoon.data.SmartPlaylist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.UserData
import com.ku.pierdoon.data.download.DownloadQueue
import com.ku.pierdoon.data.history.DailyMix
import com.ku.pierdoon.data.history.ListeningStats
import com.ku.pierdoon.data.history.Play
import com.ku.pierdoon.data.history.StatsPeriod
import com.ku.pierdoon.data.history.YearReview
import com.ku.pierdoon.radio.RadioStart
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.SystemBackHandler
import com.ku.pierdoon.ui.download.DownloadScreen
import com.ku.pierdoon.ui.edit.BatchEditScreen
import com.ku.pierdoon.ui.edit.EditSongScreen
import com.ku.pierdoon.ui.library.CollectionScreen
import com.ku.pierdoon.ui.library.DuplicatesScreen
import com.ku.pierdoon.ui.library.FavoritesCover
import com.ku.pierdoon.ui.library.FoldersScreen
import com.ku.pierdoon.ui.library.HeroTrack
import com.ku.pierdoon.ui.library.HomeScreen
import com.ku.pierdoon.ui.library.LibraryPlaylists
import com.ku.pierdoon.ui.library.MenuEntry
import com.ku.pierdoon.ui.library.MixCollage
import com.ku.pierdoon.ui.library.PlaylistCover
import com.ku.pierdoon.ui.library.SearchScreen
import com.ku.pierdoon.ui.library.SmartPlaylistCover
import com.ku.pierdoon.ui.library.SmartPlaylistEditor
import com.ku.pierdoon.ui.library.StatsScreen
import com.ku.pierdoon.ui.library.YearReviewScreen
import com.ku.pierdoon.ui.navigation.NavDirection
import com.ku.pierdoon.ui.navigation.Navigator
import com.ku.pierdoon.ui.navigation.Route
import com.ku.pierdoon.ui.navigation.pageTransition
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.daily_mix
import com.ku.pierdoon.ui.resources.daily_mix_desc
import com.ku.pierdoon.ui.resources.delete
import com.ku.pierdoon.ui.resources.favorites
import com.ku.pierdoon.ui.resources.smart_badge
import com.ku.pierdoon.ui.resources.smart_edit_rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import com.ku.pierdoon.ui.resources.cover as coverLabel
import com.ku.pierdoon.ui.resources.rename as renameLabel

/** What a page an app draws its own way (its library, its settings, its pages for the other device) gets to work with. */
class PageContext(
    val nav: Navigator,
    val songs: List<Song>,
    val collections: MusicCollections,
    val currentSongId: Long?,
    val userData: UserData,
    val bottomInset: Dp,
    val onPlay: (List<Song>, Int) -> Unit,
    val onSongLongPress: (Song) -> Unit,
    val onBulk: (List<Song>, () -> Unit) -> Unit,
    val onDelete: ((List<Song>, () -> Unit) -> Unit)?,
    /** The playlists as the library's lists of them show them, smart ones too. */
    val lists: LibraryPlaylists,
    /** Asks whether to take [Backup] in (the dialog is the shared one). */
    val onRestore: (Backup) -> Unit,
)

/** What each app does its own way on the pages both have. */
class PageOptions(
    /** The tabs that switch in place rather than slide. */
    val tabs: List<Route.Tab>,
    /** Settings and its pages change in place, as tabs do (the computer); or slide (the phone). */
    val settingsInPlace: Boolean,
    val downloads: DownloadQueue,
    /** Downloads is a tab with nothing to go back to (the computer), or a page (the phone). */
    val downloadsIsTab: Boolean,
    /** What tells copies apart on the duplicates page: the file's path, or its album and date. */
    val describeDuplicate: (Song) -> String,
    val onClearHistory: () -> Unit,
    /** Beside the greeting on the home page (the phone's way to the computer). */
    val homeAction: (@Composable () -> Unit)? = null,
)

/**
 * Every page of the app, switched with the springy transitions, each keeping its scroll and
 * fields while away. The pages both apps draw the same are here; [platformPage] draws the rest
 * (the library tab, settings, the pages about the other device).
 */
@Composable
fun AppPages(
    nav: Navigator,
    vm: AppModel,
    songs: List<Song>,
    userData: UserData,
    plays: List<Play>?,
    settings: SharedSettings,
    options: PageOptions,
    bottomInset: Dp,
    backEnabled: Boolean,
    onOpenPlayer: () -> Unit,
    onSongLongPress: (Song, Long?) -> Unit,
    onBulk: (List<Song>, Long?, () -> Unit) -> Unit,
    onDeleteSongs: ((List<Song>, () -> Unit) -> Unit)?,
    onPlaylistLongPress: (Playlist) -> Unit,
    onImportPlaylist: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onRenamePlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onPlaylistCover: (Playlist) -> Unit,
    /** Asks whether to take a backup in. */
    onRestore: (Backup) -> Unit,
    /** Told while pages slide, so the phone can ask for its top refresh rate meanwhile. */
    transitionEffect: @Composable (running: Boolean) -> Unit = {},
    platformPage: @Composable (Route, PageContext) -> Unit,
) {
    val collections by vm.collections.collectAsState()
    val player by vm.player.collectAsState()
    val radio by vm.radio.collectAsState()
    val currentSongId = player.nowPlaying?.songId
    val stateHolder = rememberSaveableStateHolder()
    val resolveCover: (Playlist) -> Song? = { p -> vm.resolve(p.songIds.take(1)).firstOrNull() }
    val onPlay: (List<Song>, Int) -> Unit = { list, i -> vm.playSongs(list, i) }
    val onShuffle: (List<Song>) -> Unit = { list ->
        if (list.isNotEmpty()) vm.playSongs(list, list.indices.random(), shuffle = true)
    }
    // Built from plays up to last midnight, so it's the same all day; looked at again on each page change.
    val today = remember(nav.current) { DailyMix.dayNumber(System.currentTimeMillis()) }
    val playsLoaded = plays != null
    val dailyMix = remember(songs, userData.favorites, today, playsLoaded) {
        if (!playsLoaded) emptyList() else DailyMix.build(songs, plays.orEmpty(), userData.favorites, System.currentTimeMillis())
    }
    val yearReview = remember(plays, today) { plays?.let { YearReview.seasonYear(it, System.currentTimeMillis()) } }
    val smartTracks: (SmartPlaylist) -> List<Song> = { p ->
        p.evaluate(songs, plays.orEmpty(), userData.favorites, System.currentTimeMillis())
    }
    // The smart playlist being made (id not in the list yet) or changed; null with the editor closed.
    var smartEditor by remember { mutableStateOf<SmartEditorTarget?>(null) }

    val context = PageContext(
        nav = nav,
        songs = songs,
        collections = collections,
        currentSongId = currentSongId,
        userData = userData,
        bottomInset = bottomInset,
        onPlay = onPlay,
        onSongLongPress = { onSongLongPress(it, null) },
        onBulk = { picked, done -> onBulk(picked, null, done) },
        onDelete = onDeleteSongs,
        lists = LibraryPlaylists(
            playlists = userData.playlists,
            favoritesCount = userData.favorites.size,
            resolveCover = resolveCover,
            onCreate = onCreatePlaylist,
            onLongPress = onPlaylistLongPress,
            onImport = onImportPlaylist,
            smart = userData.smartPlaylists,
            smartCount = { smartTracks(it).size },
            onCreateSmart = { smartEditor = SmartEditorTarget(null) },
            onEditSmart = { smartEditor = SmartEditorTarget(it) },
        ),
        onRestore = onRestore,
    )

    /** A list of tracks under a cover, as albums, artists, playlists and the rest are shown. */
    @Composable
    fun Collection(
        title: String,
        subtitle: String,
        tracks: List<Song>,
        cover: @Composable (Modifier) -> Unit,
        playlistId: Long? = null,
        emptyText: String? = null,
        menu: List<MenuEntry> = emptyList(),
    ) = CollectionScreen(
        title = title,
        subtitle = subtitle,
        songs = tracks,
        currentSongId = currentSongId,
        cover = cover,
        onBack = { nav.pop() },
        onPlay = onPlay,
        onShuffle = onShuffle,
        onSongLongPress = { onSongLongPress(it, playlistId) },
        onBulk = { picked, done -> onBulk(picked, playlistId, done) },
        onDelete = onDeleteSongs,
        bottomInset = bottomInset,
        emptyText = emptyText,
        menu = menu,
    )

    // One page = its saved UI state + its content.
    val pageBody: @Composable (Route) -> Unit = { route ->
        stateHolder.SaveableStateProvider(route.key) {
            when (route) {
                Route.Home -> {
                    val recent = remember(userData.recent, songs) { vm.resolve(userData.recent) }
                    HomeScreen(
                        greetingAction = options.homeAction,
                        onQuote = vm::randomLyricQuote,
                        recent = recent,
                        collections = collections,
                        playlists = userData.playlists,
                        favoritesCount = userData.favorites.size,
                        dailyMix = dailyMix,
                        weekListenedMs = plays?.let { p ->
                            // The same week as the statistics page: seven calendar days, tracks still in the library.
                            val since = ListeningStats.periodStart(StatsPeriod.Week, System.currentTimeMillis())
                            val ids = songs.mapTo(HashSet()) { it.id }
                            p.sumOf { if (it.atMs >= since && it.songId in ids) it.listenedMs else 0L }
                        },
                        resolveCover = resolveCover,
                        onPlay = onPlay,
                        onShuffleAll = vm::shuffleAll,
                        onOpen = nav::push,
                        onSongLongPress = { onSongLongPress(it, null) },
                        bottomInset = bottomInset,
                        onMyWave = if (settings.radioEnabled && songs.isNotEmpty()) {
                            { vm.startRadio(RadioStart.wave(dailyMix.ifEmpty { songs.shuffled().take(DailyMix.SIZE) })) }
                        } else {
                            null
                        },
                        yearReview = yearReview,
                        hero = player.nowPlaying?.let { HeroTrack(it.songId, it.albumId, it.title, it.artist, playing = true) }
                            ?: recent.firstOrNull()?.let { HeroTrack(it.id, it.albumId, it.title, it.artist, playing = false) },
                        isPlaying = player.isPlaying,
                        onOpenPlayer = onOpenPlayer,
                        onTogglePlay = vm::togglePlay,
                        radioOn = radio != null,
                    )
                }
                Route.Search -> SearchScreen(
                    songs = songs,
                    collections = collections,
                    currentSongId = currentSongId,
                    onBack = { nav.pop() },
                    onPlay = onPlay,
                    onSongLongPress = { onSongLongPress(it, null) },
                    onOpenArtist = { nav.push(Route.ArtistDetail(it)) },
                    onOpenAlbum = { nav.push(Route.AlbumDetail(it)) },
                    bottomInset = bottomInset,
                )
                Route.Downloads -> DownloadScreen(
                    downloads = options.downloads,
                    onBack = if (options.downloadsIsTab) null else ({ nav.pop() }),
                    onPlay = { id -> vm.song(id)?.let { vm.playSongs(listOf(it), 0) } },
                    bottomInset = bottomInset,
                )
                is Route.AlbumDetail -> {
                    val album = collections.albums.firstOrNull { it.id == route.albumId }
                    Collection(
                        title = album?.title.orEmpty(),
                        subtitle = album?.artist.orEmpty(),
                        tracks = album?.songs.orEmpty(),
                        cover = { m ->
                            if (album != null) {
                                Artwork(album.coverSongId, album.coverAlbumId, m.clip(RoundedCornerShape(40.dp)), large = true, placeholderIconSize = 64.dp)
                            }
                        },
                    )
                }
                is Route.ArtistDetail -> {
                    val artist = collections.artist(route.name)
                    Collection(
                        title = artist?.name ?: route.name,
                        subtitle = "",
                        tracks = artist?.songs.orEmpty(),
                        cover = { m ->
                            if (artist != null) {
                                Artwork(artist.coverSong.id, artist.coverSong.albumId, m.clip(CircleShape), large = true, placeholderIconSize = 64.dp)
                            }
                        },
                    )
                }
                is Route.PlaylistDetail -> {
                    val playlist = userData.playlists.firstOrNull { it.id == route.playlistId }
                    val tracks = remember(playlist, songs) { playlist?.let { vm.resolve(it.songIds) }.orEmpty() }
                    Collection(
                        title = playlist?.name.orEmpty(),
                        subtitle = "",
                        tracks = tracks,
                        cover = { m -> PlaylistCover(playlist, tracks.firstOrNull(), m, large = true, placeholderIconSize = 64.dp) },
                        playlistId = playlist?.id,
                        emptyText = stringResource(PlatformStrings.emptyPlaylist),
                        menu = if (playlist != null) {
                            listOf(
                                MenuEntry(stringResource(Res.string.coverLabel), PlayerIcons.Image) { onPlaylistCover(playlist) },
                                MenuEntry(stringResource(Res.string.renameLabel), PlayerIcons.Edit) { onRenamePlaylist(playlist) },
                                MenuEntry(stringResource(Res.string.delete), PlayerIcons.Delete) { onDeletePlaylist(playlist) },
                            )
                        } else {
                            emptyList()
                        },
                    )
                }
                is Route.EditSongs -> {
                    val picked = remember(songs, route.songIds) { route.songIds.mapNotNull(vm::song) }
                    if (picked.isNotEmpty()) {
                        BatchEditScreen(songs = picked, vm = vm, onBack = { nav.pop() }, bottomInset = bottomInset)
                    } else {
                        LaunchedEffect(Unit) { nav.pop() }
                    }
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
                Route.DailyMix -> Collection(
                    title = stringResource(Res.string.daily_mix),
                    subtitle = stringResource(Res.string.daily_mix_desc),
                    tracks = dailyMix,
                    cover = { m -> MixCollage(dailyMix, m) },
                )
                Route.Stats -> StatsScreen(
                    plays = plays,
                    songs = songs,
                    currentSongId = currentSongId,
                    onBack = { nav.pop() },
                    onPlay = onPlay,
                    onSongLongPress = { onSongLongPress(it, null) },
                    onOpenArtist = { nav.push(Route.ArtistDetail(it)) },
                    onClear = options.onClearHistory,
                    onYearReview = { nav.push(Route.YearReview(it)) },
                    bottomInset = bottomInset,
                )
                is Route.YearReview -> YearReviewScreen(
                    year = route.year,
                    plays = plays,
                    songs = songs,
                    onBack = { nav.pop() },
                    onPlay = onPlay,
                    onOpenArtist = { nav.push(Route.ArtistDetail(it)) },
                    bottomInset = bottomInset,
                )
                Route.Folders -> FoldersScreen(
                    folders = collections.folders,
                    onBack = { nav.pop() },
                    onOpen = { nav.push(Route.FolderDetail(it.path)) },
                    bottomInset = bottomInset,
                )
                is Route.FolderDetail -> {
                    val folder = collections.folders.firstOrNull { it.path == route.path }
                    val tracks = folder?.songs.orEmpty()
                    Collection(
                        title = folder?.name.orEmpty(),
                        subtitle = folder?.location.orEmpty(),
                        tracks = tracks,
                        cover = { m -> MixCollage(tracks, m) },
                    )
                }
                Route.Duplicates -> {
                    val groups by produceState<List<DuplicateGroup>?>(null, songs, userData, plays) {
                        value = withContext(Dispatchers.Default) {
                            val playCounts = plays.orEmpty().groupingBy { it.songId }.eachCount()
                            val inPlaylists = userData.playlists.flatMap { it.songIds }.groupingBy { it }.eachCount()
                            Duplicates.find(songs, userData.favorites, inPlaylists, playCounts)
                        }
                    }
                    DuplicatesScreen(
                        groups = groups,
                        currentSongId = currentSongId,
                        describe = options.describeDuplicate,
                        onBack = { nav.pop() },
                        onPlay = onPlay,
                        onDelete = { picked, done -> onDeleteSongs?.invoke(picked, done) },
                        bottomInset = bottomInset,
                    )
                }
                is Route.SmartPlaylistDetail -> {
                    val playlist = userData.smartPlaylists.firstOrNull { it.id == route.playlistId }
                    val tracks = remember(playlist, songs, plays, userData.favorites, today) { playlist?.let(smartTracks).orEmpty() }
                    Collection(
                        title = playlist?.name.orEmpty(),
                        subtitle = stringResource(Res.string.smart_badge),
                        tracks = tracks,
                        cover = { m -> SmartPlaylistCover(m) },
                        emptyText = stringResource(PlatformStrings.emptyPlaylist),
                        menu = if (playlist != null) {
                            listOf(
                                MenuEntry(stringResource(Res.string.smart_edit_rules), PlayerIcons.Edit) { smartEditor = SmartEditorTarget(playlist) },
                                MenuEntry(stringResource(Res.string.delete), PlayerIcons.Delete) {
                                    vm.deleteSmartPlaylist(playlist.id)
                                    nav.pop()
                                },
                            )
                        } else {
                            emptyList()
                        },
                    )
                }
                Route.Favorites -> {
                    val tracks = remember(userData.favorites, songs) { vm.resolve(userData.favorites) }
                    Collection(
                        title = stringResource(Res.string.favorites),
                        subtitle = "",
                        tracks = tracks,
                        cover = { m -> FavoritesCover(m) },
                    )
                }
                // The library tab, settings, the pages about the other device.
                else -> platformPage(route, context)
            }
        }
    }

    // System back (the phone's gesture; Esc, Alt+Left or the mouse's back button) pops the page.
    SystemBackHandler(enabled = backEnabled && nav.previous != null) { nav.pop() }

    val pageTransition = updateTransition(nav.current, label = "screens")
    transitionEffect(pageTransition.isRunning)
    pageTransition.AnimatedContent(
        contentKey = { it.key },
        transitionSpec = {
            val from = initialState
            val to = targetState
            val direction = when {
                // Tabs slide the way the bar runs: to a tab further along from the right.
                from in options.tabs && to in options.tabs ->
                    if (options.tabs.indexOf(to) > options.tabs.indexOf(from)) NavDirection.TabForward else NavDirection.TabBack
                options.settingsInPlace && from.isSettings() && to.isSettings() ->
                    if (to == Route.Settings) NavDirection.TabBack else NavDirection.TabForward
                nav.forward -> NavDirection.Forward
                else -> NavDirection.Back
            }
            pageTransition(direction)
        },
        modifier = Modifier.fillMaxSize(),
    ) { route ->
        // Opaque page background: pages overlap while sliding over each other.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { pageBody(route) }
    }

    smartEditor?.let { target ->
        SmartPlaylistEditor(
            initial = target.playlist,
            count = { smartTracks(it).size },
            onDismiss = { smartEditor = null },
            onSave = { p ->
                vm.putSmartPlaylist(p)
                smartEditor = null
                if (target.playlist == null) nav.push(Route.SmartPlaylistDetail(p.id))
            },
            onDelete = target.playlist?.let { p ->
                {
                    vm.deleteSmartPlaylist(p.id)
                    smartEditor = null
                    if (nav.current == Route.SmartPlaylistDetail(p.id)) nav.pop()
                }
            },
        )
    }
}

/** The library's sort, saved for both apps. */
fun setSongSort(sort: com.ku.pierdoon.data.SongSort, descending: Boolean) =
    SharedSettingsStore.update { copy(songSort = sort, songSortDescending = descending) }

/** The smart playlist editor: a new one, or [playlist] to change. */
private class SmartEditorTarget(val playlist: SmartPlaylist?)

private fun Route.isSettings() = this == Route.Settings || this is Route.SettingsPage
