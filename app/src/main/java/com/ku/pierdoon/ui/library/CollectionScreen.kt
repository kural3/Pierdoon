package com.ku.pierdoon.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.ku.pierdoon.R
import com.ku.pierdoon.data.Song

/** Album / artist / playlist / favourites: header with cover and actions, then the tracks. */
@Composable
fun CollectionScreen(
    title: String,
    subtitle: String,
    songs: List<Song>,
    currentSongId: Long?,
    cover: @Composable (Modifier) -> Unit,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSongLongPress: (Song) -> Unit,
    bottomInset: Dp,
    emptyText: String? = null,
    actions: @Composable () -> Unit = {},
) {
    AppScaffold(title = title, onBack = onBack, actions = actions) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = listBottomPadding(bottomInset)),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                CollectionHeader(
                    cover = cover,
                    subtitle = subtitle.ifEmpty { stringResource(R.string.songs_count, songs.size) },
                    onPlay = { onPlay(songs, 0) },
                    onShuffle = { onShuffle(songs) },
                    enabled = songs.isNotEmpty(),
                )
            }
            if (songs.isEmpty() && emptyText != null) {
                item(key = "empty") { EmptyMessage(emptyText) }
            }
            itemsIndexed(songs, key = { _, s -> s.id }, contentType = { _, _ -> "song" }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == currentSongId,
                    onClick = { onPlay(songs, index) },
                    onLongClick = { onSongLongPress(song) },
                )
            }
        }
    }
}
