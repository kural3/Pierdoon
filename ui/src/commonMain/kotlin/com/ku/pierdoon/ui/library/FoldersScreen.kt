package com.ku.pierdoon.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.Folder
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.ui.resources.Res
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The library by the folders its files lie in, for music kept in folders rather than tagged into
 * albums. A folder opens as a collection of its tracks.
 */
@Composable
fun FoldersScreen(
    folders: List<Folder>,
    onBack: () -> Unit,
    onOpen: (Folder) -> Unit,
    bottomInset: Dp,
) {
    AppScaffold(
        title = stringResource(Res.string.folders),
        subtitle = folders.takeIf { it.isNotEmpty() }?.let { pluralStringResource(Res.plurals.folders_count, it.size, it.size) },
        onBack = onBack,
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = listBottomPadding(bottomInset)),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (folders.isEmpty()) {
                item(key = "empty") { EmptyMessage(stringResource(Res.string.folders_none)) }
            }
            items(folders, key = { it.path }) { folder ->
                val count = pluralStringResource(Res.plurals.songs_count, folder.songs.size, folder.songs.size)
                CollectionRow(
                    title = folder.name,
                    subtitle = if (folder.location.isEmpty()) count else "$count · ${folder.location}",
                    cover = { MixCollage(folder.songs, Modifier.size(56.dp)) },
                    onClick = { onOpen(folder) },
                )
            }
        }
    }
}
