package com.ku.pierdoon.ui.library

import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    selection: SongSelection? = null,
) {
    if (songs.isEmpty()) {
        FillBox { EmptyMessage(stringResource(Res.string.empty_library)) }
        return
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = topPadding, bottom = listBottomPadding(bottomInset, extra = 88.dp)),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(songs, key = { _, s -> s.id }, contentType = { _, _ -> "song" }) { index, song ->
            val selecting = selection?.takeIf { it.active }
            SongRow(
                song = song,
                isCurrent = song.id == currentSongId,
                onClick = { if (selecting != null) selecting.toggle(song.id) else onPlay(songs, index) },
                onLongClick = { if (selecting != null) selecting.toggle(song.id) else onSongLongPress(song) },
                selected = selecting?.let { song.id in it.ids },
            )
        }
    }
}

/** The library section that holds playlists. */
const val PLAYLISTS = 3

