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
import com.ku.pierdoon.ui.components.PlayerIcons
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
    onBulk: (List<Song>, () -> Unit) -> Unit,
    /** Null where the system can't ask for consent to delete (below Android 11). */
    onDelete: ((List<Song>, () -> Unit) -> Unit)?,
    emptyText: String? = null,
    /** Extra ⋮ entries after "Select" (a playlist's cover, rename, delete). */
    menu: List<MenuEntry> = emptyList(),
) {
    val selection = rememberSongSelection()
    SelectionBackHandler(selection)
    val selectLabel = stringResource(R.string.select)
    AppScaffold(
        title = selectionTitle(selection, title),
        onBack = if (selection.active) selection::clear else onBack,
        actions = {
            if (selection.active) {
                SelectionActions(
                    selection = selection,
                    all = songs,
                    onBulk = { picked -> onBulk(picked, selection::clear) },
                    onDelete = onDelete?.let { delete -> { picked -> delete(picked, selection::clear) } },
                )
            } else if (songs.isNotEmpty() || menu.isNotEmpty()) {
                OverflowMenu(
                    listOf(
                        if (songs.isNotEmpty()) listOf(MenuEntry(selectLabel, PlayerIcons.Checklist) { selection.start() }) else emptyList(),
                        menu,
                    )
                )
            }
        },
    ) { inner ->
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
                val selecting = selection.active
                SongRow(
                    song = song,
                    isCurrent = song.id == currentSongId,
                    onClick = { if (selecting) selection.toggle(song.id) else onPlay(songs, index) },
                    onLongClick = { if (selecting) selection.toggle(song.id) else onSongLongPress(song) },
                    selected = if (selecting) song.id in selection.ids else null,
                )
            }
        }
    }
}
