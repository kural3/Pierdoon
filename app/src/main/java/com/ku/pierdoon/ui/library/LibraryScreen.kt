package com.ku.pierdoon.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.R
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.SongSort
import com.ku.pierdoon.data.sortSongs
import androidx.compose.runtime.LaunchedEffect
import com.ku.pierdoon.ui.components.ConnectedChoice
import com.ku.pierdoon.ui.components.FrameRateBoost
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.navigation.Route

/**
 * One tab for the whole collection. The header and section selector stay fixed; only the
 * section body animates, sliding in from the side of the chosen segment.
 */
@Composable
fun LibraryScreen(
    songs: List<Song>,
    collections: MusicCollections,
    currentSongId: Long?,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffleAll: () -> Unit,
    onSongLongPress: (Song) -> Unit,
    onOpen: (Route) -> Unit,
    sort: SongSort,
    sortDescending: Boolean,
    onSort: (SongSort, Boolean) -> Unit,
    onBulk: (List<Song>, () -> Unit) -> Unit,
    /** Null where the system can't ask for consent to delete (below Android 11). */
    onDelete: ((List<Song>, () -> Unit) -> Unit)?,
    bottomInset: Dp,
) {
    val haptics = LocalHapticFeedback.current
    var section by rememberSaveable { mutableIntStateOf(0) }
    val sorted = remember(songs, sort, sortDescending) { sortSongs(songs, sort, sortDescending) }
    val selection = rememberSongSelection()
    SelectionBackHandler(selection)
    // The selection belongs to the track list; other sections and a changed library end it.
    LaunchedEffect(section) { if (section != 0) selection.clear() }
    val sectionState = rememberSaveableStateHolder()
    val songsListState = rememberLazyListState()
    // A new order starts from the top; the list would otherwise hold on to the row that was first.
    var shownSort by rememberSaveable { mutableStateOf(sort to sortDescending) }
    LaunchedEffect(sort, sortDescending) {
        if (shownSort != sort to sortDescending) {
            shownSort = sort to sortDescending
            songsListState.scrollToItem(0)
        }
    }
    val fabExpanded by remember { derivedStateOf { songsListState.firstVisibleItemIndex == 0 } }

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val spatialF = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    val sortLabels = listOf(
        SongSort.Title to R.string.sort_title,
        SongSort.Artist to R.string.sort_artist,
        SongSort.Album to R.string.sort_album,
        SongSort.DateAdded to R.string.sort_date_added,
        SongSort.Duration to R.string.sort_duration,
    ).map { (s, res) -> s to stringResource(res) }
    val selectLabel = stringResource(R.string.select)
    val descendingLabel = stringResource(R.string.sort_descending)

    AppScaffold(
        title = selectionTitle(selection, stringResource(R.string.nav_library)),
        onBack = if (selection.active) selection::clear else null,
        subtitle = when (section) {
            0 -> stringResource(R.string.tracks_count, songs.size)
            1 -> stringResource(R.string.albums_count, collections.albums.size)
            else -> stringResource(R.string.artists_count, collections.artists.size)
        },
        actions = {
            if (selection.active) {
                SelectionActions(
                    selection = selection,
                    all = sorted,
                    onBulk = { picked -> onBulk(picked, selection::clear) },
                    onDelete = onDelete?.let { delete -> { picked -> delete(picked, selection::clear) } },
                )
            } else {
                TopBarIcon(PlayerIcons.Search, stringResource(R.string.search_library)) { onOpen(Route.Search) }
                TopBarIcon(PlayerIcons.Download, stringResource(R.string.downloads)) { onOpen(Route.Downloads) }
                if (section == 0 && songs.isNotEmpty()) {
                    OverflowMenu(
                        listOf(MenuEntry(selectLabel, PlayerIcons.Checklist) { selection.start() }, null) +
                            sortLabels.map { (s, label) ->
                                MenuEntry(label, checked = s == sort) { onSort(s, sortDescending) }
                            } +
                            listOf(
                                null,
                                MenuEntry(descendingLabel, PlayerIcons.Sort, checked = sortDescending) {
                                    onSort(sort, !sortDescending)
                                },
                            )
                    )
                }
            }
        },
        belowTitle = {
            ConnectedChoice(
                options = listOf(
                    stringResource(R.string.nav_songs),
                    stringResource(R.string.nav_albums),
                    stringResource(R.string.nav_artists),
                ),
                selected = section,
                onSelect = { section = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = section == 0 && songs.isNotEmpty() && !selection.active,
                enter = scaleIn(spatialF, initialScale = 0.6f) + fadeIn(effects),
                exit = scaleOut(spatialF, targetScale = 0.6f) + fadeOut(effects),
                modifier = Modifier.padding(bottom = bottomInset),
            ) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.shuffle_all)) },
                    icon = { Icon(PlayerIcons.Shuffle, contentDescription = null) },
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onShuffleAll()
                    },
                    expanded = fabExpanded,
                )
            }
        },
    ) { inner ->
        val top = inner.calculateTopPadding()
        val sectionTransition = updateTransition(section, label = "librarySection")
        FrameRateBoost(active = sectionTransition.isRunning)
        sectionTransition.AnimatedContent(
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spatial) { dir * it / 3 } + fadeIn(effects) +
                    scaleIn(spatialF, initialScale = 0.97f))
                    .togetherWith(slideOutHorizontally(spatial) { -dir * it / 3 } + fadeOut(effects))
                    .using(SizeTransform(clip = false))
            },
        ) { s ->
            // Each section keeps its own scroll position across switches.
            sectionState.SaveableStateProvider(s) {
                when (s) {
                    0 -> SongsList(sorted, currentSongId, onPlay, onSongLongPress, top, bottomInset, songsListState, selection)
                    1 -> AlbumsGrid(collections.albums, onOpen, top, bottomInset)
                    else -> ArtistsList(collections.artists, onOpen, top, bottomInset)
                }
            }
        }
    }
}
