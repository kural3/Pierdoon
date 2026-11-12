package com.ku.pierdoon.ui.library

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.ku.pierdoon.data.TrackView
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.data.Song

/** Library section bodies: no scaffold of their own, so the header stays put while they animate. */
@Composable
fun SongsList(
    songs: List<Song>,
    currentSongId: Long?,
    onPlay: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    topPadding: Dp,
    bottomInset: Dp,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    selection: SongSelection? = null,
    view: TrackView = TrackView.List,
) {
    if (songs.isEmpty()) {
        FillBox { EmptyMessage(stringResource(Res.string.empty_library)) }
        return
    }
    val picking = selection?.takeIf { it.active }
    TrackList(
        view = view,
        songs = songs,
        currentSongId = currentSongId,
        onPlay = onPlay,
        onSongLongPress = onSongLongPress,
        topPadding = topPadding,
        bottomPadding = listBottomPadding(bottomInset, extra = 88.dp),
        listState = listState,
        gridState = gridState,
        selectedIds = picking?.ids,
        onToggle = { id -> picking?.toggle(id) },
    )
}

/** The library section that holds playlists. */
const val PLAYLISTS = 3

