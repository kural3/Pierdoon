package com.ku.pierdoon.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.R
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.searchLibrary
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.navigation.Route

/** Tracks, albums and artists matching what's typed; opens with the keyboard up. */
@Composable
fun SearchScreen(
    songs: List<Song>,
    collections: MusicCollections,
    currentSongId: Long?,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onOpen: (Route) -> Unit,
    bottomInset: Dp,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val found = remember(query, songs, collections) { searchLibrary(query, songs, collections) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focus.requestFocus()
            focusedOnce = true
        }
    }

    AppScaffold(
        title = stringResource(R.string.search_library),
        onBack = onBack,
        belowTitle = {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_library_hint)) },
                leadingIcon = { Icon(PlayerIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(PlayerIcons.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .focusRequester(focus),
            )
        },
    ) { inner ->
        if (query.isNotBlank() && found.isEmpty) {
            FillBox { EmptyMessage(stringResource(R.string.search_library_empty)) }
            return@AppScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = listBottomPadding(bottomInset)),
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            if (found.artists.isNotEmpty()) {
                item(key = "artists") { SectionTitle(stringResource(R.string.search_artists)) }
                items(found.artists.take(MAX_COLLECTIONS), key = { "artist:" + it.name }) { artist ->
                    CollectionRow(
                        title = artist.name,
                        subtitle = pluralStringResource(R.plurals.songs_count, artist.songs.size, artist.songs.size),
                        cover = {
                            Artwork(artist.coverSong.id, artist.coverSong.albumId, Modifier.size(56.dp).clip(CircleShape))
                        },
                        onClick = { onOpen(Route.ArtistDetail(artist.name)) },
                    )
                }
            }
            if (found.albums.isNotEmpty()) {
                item(key = "albums") { SectionTitle(stringResource(R.string.search_albums)) }
                items(found.albums.take(MAX_COLLECTIONS), key = { "album:" + it.id }) { album ->
                    CollectionRow(
                        title = album.title,
                        subtitle = album.artist,
                        cover = {
                            Artwork(album.coverSongId, album.coverAlbumId, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
                        },
                        onClick = { onOpen(Route.AlbumDetail(album.id)) },
                    )
                }
            }
            if (found.songs.isNotEmpty()) {
                item(key = "songs") { SectionTitle(stringResource(R.string.search_tracks)) }
                itemsIndexed(found.songs, key = { _, s -> s.id }, contentType = { _, _ -> "song" }) { index, song ->
                    SongRow(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        onClick = { onPlay(found.songs, index) },
                        onLongClick = { onSongLongPress(song) },
                    )
                }
            }
        }
    }
}

private const val MAX_COLLECTIONS = 5
