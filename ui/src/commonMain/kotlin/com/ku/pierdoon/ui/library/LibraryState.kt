package com.ku.pierdoon.ui.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.SongSort
import com.ku.pierdoon.data.sortSongs
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.sort_album
import com.ku.pierdoon.ui.resources.sort_artist
import com.ku.pierdoon.ui.resources.sort_date_added
import com.ku.pierdoon.ui.resources.sort_duration
import com.ku.pierdoon.ui.resources.sort_title
import org.jetbrains.compose.resources.stringResource

/**
 * The track list of the library tab, whatever it is drawn as: its order, the tracks picked,
 * and where it is scrolled to (as rows, or as the computer's tiles).
 */
class LibraryTracks(
    val sorted: List<Song>,
    val selection: SongSelection,
    val listState: LazyListState,
    val gridState: LazyGridState,
) {
    /** At the very top: the Shuffle button shows its label. */
    val atTop: Boolean by derivedStateOf {
        listState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemIndex == 0
    }
}

/**
 * [songs] in the chosen order. Picking tracks belongs to the track list, the first [section]:
 * switching to another one ends it, as does the system back. A new order starts from the top;
 * the list would otherwise hold on to the row that was first.
 */
@Composable
fun rememberLibraryTracks(songs: List<Song>, sort: SongSort, descending: Boolean, section: Int): LibraryTracks {
    val sorted = remember(songs, sort, descending) { sortSongs(songs, sort, descending) }
    val selection = rememberSongSelection()
    SelectionBackHandler(selection)
    LaunchedEffect(section) { if (section != 0) selection.clear() }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    var shownSort by rememberSaveable { mutableStateOf(sort to descending) }
    LaunchedEffect(sort, descending) {
        if (shownSort != sort to descending) {
            shownSort = sort to descending
            listState.scrollToItem(0)
            gridState.scrollToItem(0)
        }
    }
    return remember(sorted, selection, listState, gridState) { LibraryTracks(sorted, selection, listState, gridState) }
}

/** The orders a track list can take, with their names, as the sort menu lists them. */
@Composable
fun songSortLabels(): List<Pair<SongSort, String>> = listOf(
    SongSort.Title to stringResource(Res.string.sort_title),
    SongSort.Artist to stringResource(Res.string.sort_artist),
    SongSort.Album to stringResource(Res.string.sort_album),
    SongSort.DateAdded to stringResource(Res.string.sort_date_added),
    SongSort.Duration to stringResource(Res.string.sort_duration),
)
