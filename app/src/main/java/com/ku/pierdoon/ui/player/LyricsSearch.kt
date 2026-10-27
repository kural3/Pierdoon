package com.ku.pierdoon.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.data.lyrics.LyricsCandidate
import com.ku.pierdoon.R
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.formatDuration
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.res.stringResource

/** Picking lyrics by hand, for the track playing; the app provides it to every lyrics view. */
class LyricsSearch(
    val initialText: () -> String,
    val search: suspend (String) -> List<LyricsCandidate>,
    val pick: (Lyrics) -> Unit,
)

val LocalLyricsSearch = staticCompositionLocalOf<LyricsSearch?> { null }

private sealed interface SearchState {
    data object Loading : SearchState
    data object Failed : SearchState
    data class Done(val results: List<LyricsCandidate>) : SearchState
}

/** A search box over LRCLIB and its results, each with its first line; one click puts it on the track. */
@Composable
fun LyricsSearchDialog(search: LyricsSearch, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(search.initialText()) }
    // Bumped by Enter or the search button; the search runs for the text as it was then.
    var runs by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<SearchState>(SearchState.Loading) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(runs) {
        if (text.isBlank()) return@LaunchedEffect
        state = SearchState.Loading
        state = try {
            SearchState.Done(search.search(text))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SearchState.Failed
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_search)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.lyrics_search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runs++ }),
                    trailingIcon = {
                        IconButton(onClick = { runs++ }) {
                            Icon(PlayerIcons.Search, contentDescription = stringResource(R.string.lyrics_search))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                    when (val s = state) {
                        SearchState.Loading -> LoadingIndicator(Modifier.size(48.dp))
                        SearchState.Failed -> Note(stringResource(R.string.lyrics_search_failed))
                        is SearchState.Done ->
                            if (s.results.isEmpty()) Note(stringResource(R.string.lyrics_search_empty))
                            else LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth().height(360.dp),
                            ) {
                                items(s.results) { c ->
                                    CandidateRow(c) {
                                        search.pick(c.lyrics)
                                        onDismiss()
                                    }
                                }
                            }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun CandidateRow(c: LyricsCandidate, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val firstLine = remember(c) {
        when (val l = c.lyrics) {
            is Lyrics.Synced -> l.lines.firstOrNull { it.text.isNotBlank() }?.text
            is Lyrics.Plain -> l.lines.firstOrNull { it.isNotBlank() }
            else -> null
        }.orEmpty()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            val synced = c.lyrics is Lyrics.Synced
            Text(
                stringResource(if (synced) R.string.lyrics_synced else R.string.lyrics_plain),
                style = MaterialTheme.typography.labelSmall,
                color = if (synced) colors.onPrimaryContainer else colors.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (synced) colors.primaryContainer else colors.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            listOf(c.artist, c.album, if (c.durationMs > 0) formatDuration(c.durationMs) else "")
                .filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (firstLine.isNotEmpty()) {
            Text(
                firstLine,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
