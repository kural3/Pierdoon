package com.ku.pierdoon.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.DuplicateGroup
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The library's doubles, a group per recording. All but the first of each group start ticked:
 * the first is the one worth keeping (a favourite, in more playlists, played more). A tap plays,
 * a long press (a right click on a PC) ticks and unticks; the button deletes what's ticked.
 */
@Composable
fun DuplicatesScreen(
    groups: List<DuplicateGroup>?,
    currentSongId: Long?,
    /** A line about where a track lives: its file on a PC, its album on the phone. */
    describe: (Song) -> String,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onDelete: (List<Song>, () -> Unit) -> Unit,
    bottomInset: Dp,
) {
    // Ticked ones, by id; back to the defaults whenever the groups change (after a delete, say).
    var ticked by remember(groups) { mutableStateOf(groups.orEmpty().flatMap { g -> g.songs.drop(1).map { it.id } }.toSet()) }
    val picked = groups.orEmpty().flatMap { it.songs }.filter { it.id in ticked }
    fun toggle(song: Song) {
        ticked = if (song.id in ticked) ticked - song.id else ticked + song.id
    }

    AppScaffold(
        title = stringResource(Res.string.duplicates),
        subtitle = groups?.takeIf { it.isNotEmpty() }?.let { pluralStringResource(Res.plurals.duplicates_groups, it.size, it.size) },
        onBack = onBack,
        floatingActionButton = {
            if (picked.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(pluralStringResource(Res.plurals.duplicates_delete, picked.size, picked.size)) },
                    icon = { Icon(PlayerIcons.Delete, contentDescription = null) },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = { onDelete(picked) { ticked = emptySet() } },
                    modifier = Modifier.padding(bottom = bottomInset),
                )
            }
        },
    ) { inner ->
        if (groups == null) return@AppScaffold
        LazyColumn(
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = listBottomPadding(bottomInset, extra = 88.dp)),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (groups.isEmpty()) {
                item(key = "empty") { EmptyMessage(stringResource(Res.string.duplicates_none)) }
                return@LazyColumn
            }
            item(key = "hint") {
                Text(
                    stringResource(Res.string.duplicates_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            for (group in groups) {
                val first = group.songs.first()
                item(key = "g:${first.id}") {
                    SectionTitle("${first.title} — ${first.artist.ifEmpty { stringResource(Res.string.unknown_artist) }}")
                }
                items(group.songs, key = { "s:${it.id}" }, contentType = { "song" }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        onClick = { onPlay(group.songs, group.songs.indexOf(song)) },
                        onLongClick = { toggle(song) },
                        selected = song.id in ticked,
                        trailing = {
                            Box(Modifier.fillMaxWidth(0.4f)) {
                                Text(
                                    describe(song),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
