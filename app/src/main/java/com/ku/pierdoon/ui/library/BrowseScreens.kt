package com.ku.pierdoon.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.R
import com.ku.pierdoon.data.Album
import com.ku.pierdoon.data.Artist
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.navigation.Route

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
) {
    if (songs.isEmpty()) {
        FillBox { EmptyMessage(stringResource(R.string.empty_library)) }
        return
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = topPadding, bottom = listBottomPadding(bottomInset, extra = 88.dp)),
        modifier = Modifier.fillMaxSize(),
    ) {
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

@Composable
fun AlbumsGrid(albums: List<Album>, onOpen: (Route) -> Unit, topPadding: Dp, bottomInset: Dp) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = topPadding,
            bottom = listBottomPadding(bottomInset),
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album, onOpen, width = Dp.Unspecified, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ArtistsList(artists: List<Artist>, onOpen: (Route) -> Unit, topPadding: Dp, bottomInset: Dp) {
    LazyColumn(
        contentPadding = PaddingValues(top = topPadding, bottom = listBottomPadding(bottomInset)),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(artists, key = { it.name }) { artist ->
            CollectionRow(
                title = artist.name,
                subtitle = stringResource(R.string.songs_count, artist.songs.size) + " · " +
                    stringResource(R.string.albums_count, artist.albumCount),
                cover = {
                    Artwork(
                        artist.coverSong.id,
                        artist.coverSong.albumId,
                        Modifier.size(56.dp).clip(CircleShape),
                    )
                },
                onClick = { onOpen(Route.ArtistDetail(artist.name)) },
            )
        }
    }
}

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    favoritesCount: Int,
    resolveCover: (Playlist) -> Song?,
    onOpen: (Route) -> Unit,
    onCreate: () -> Unit,
    onPlaylistLongPress: (Playlist) -> Unit,
    bottomInset: Dp,
) {
    val haptics = LocalHapticFeedback.current
    AppScaffold(
        title = stringResource(R.string.nav_playlists),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.new_playlist)) },
                icon = { Icon(PlayerIcons.Add, contentDescription = null) },
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCreate()
                },
                modifier = Modifier.padding(bottom = bottomInset),
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding(),
                bottom = listBottomPadding(bottomInset, extra = 88.dp),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "favorites") {
                CollectionRow(
                    title = stringResource(R.string.favorites),
                    subtitle = stringResource(R.string.songs_count, favoritesCount),
                    cover = { FavoritesCover(Modifier.size(56.dp)) },
                    onClick = { onOpen(Route.Favorites) },
                )
            }
            items(playlists, key = { it.id }) { p ->
                CollectionRow(
                    title = p.name,
                    subtitle = stringResource(R.string.songs_count, p.songIds.size),
                    cover = { PlaylistCover(resolveCover(p), Modifier.size(56.dp)) },
                    onClick = { onOpen(Route.PlaylistDetail(p.id)) },
                    onLongClick = { onPlaylistLongPress(p) },
                )
            }
            if (playlists.isEmpty()) {
                item(key = "empty") { EmptyMessage(stringResource(R.string.no_playlists)) }
            }
        }
    }
}

@Composable
fun CollectionRow(
    title: String,
    subtitle: String,
    cover: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.95f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .scaleBy(scale)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickableCompat(source, onClick, onLongClick, haptics)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        cover()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Playlist cover: first track's art cut into an expressive shape, or an icon when empty. */
@Composable
fun PlaylistCover(cover: Song?, modifier: Modifier = Modifier, shape: Shape = MaterialShapes.Clover4Leaf.toShape()) {
    if (cover != null) {
        Artwork(cover.id, cover.albumId, modifier.clip(shape))
    } else {
        IconCover(PlayerIcons.Playlist, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, shape, modifier)
    }
}

@Composable
fun FavoritesCover(modifier: Modifier = Modifier) {
    IconCover(
        icon = PlayerIcons.Favorite,
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialShapes.Sunny.toShape(),
        modifier = modifier,
    )
}

@Composable
private fun IconCover(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color,
    content: Color,
    shape: Shape,
    modifier: Modifier,
) {
    Box(modifier.clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.fillMaxSize(0.42f))
    }
}
