package com.ku.pierdoon.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.R
import com.ku.pierdoon.data.Album
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.navigation.Route
import java.util.Calendar

@Composable
fun HomeScreen(
    recent: List<Song>,
    collections: MusicCollections,
    playlists: List<Playlist>,
    favoritesCount: Int,
    resolveCover: (Playlist) -> Song?,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffleAll: () -> Unit,
    onOpen: (Route) -> Unit,
    onSongLongPress: (Song) -> Unit,
    bottomInset: Dp,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = listBottomPadding(bottomInset)),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "greeting") {
            Text(
                text = stringResource(greetingRes()),
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 20.dp),
            )
        }
        item(key = "quick") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                QuickCard(
                    title = stringResource(R.string.shuffle_all),
                    icon = PlayerIcons.Shuffle,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onShuffleAll,
                    modifier = Modifier.weight(1f),
                )
                QuickCard(
                    title = stringResource(R.string.favorites),
                    subtitle = stringResource(R.string.songs_count, favoritesCount),
                    icon = PlayerIcons.Favorite,
                    shape = MaterialShapes.Sunny.toShape(),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onOpen(Route.Favorites) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recentTitle") { SectionTitle(stringResource(R.string.recently_played)) }
            item(key = "recent") { RecentCarousel(recent, onPlay) }
        }
        if (collections.recentlyAdded.isNotEmpty()) {
            item(key = "addedTitle") { SectionTitle(stringResource(R.string.recently_added)) }
            item(key = "added") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(collections.recentlyAdded, key = { _, s -> s.id }) { i, song ->
                        CoverCard(
                            title = song.title,
                            subtitle = song.artist,
                            songId = song.id,
                            albumId = song.albumId,
                            onClick = { onPlay(collections.recentlyAdded, i) },
                            onLongClick = { onSongLongPress(song) },
                        )
                    }
                }
            }
        }
        if (collections.albums.isNotEmpty()) {
            item(key = "albumsTitle") { SectionTitle(stringResource(R.string.nav_albums)) }
            item(key = "albums") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(collections.albums.take(12), key = { it.id }) { album -> AlbumCard(album, onOpen) }
                }
            }
        }
        if (playlists.isNotEmpty()) {
            item(key = "playlistsTitle") { SectionTitle(stringResource(R.string.your_playlists)) }
            item(key = "playlists") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(playlists, key = { it.id }) { p ->
                        val cover = resolveCover(p)
                        CoverCard(
                            title = p.name,
                            subtitle = stringResource(R.string.songs_count, p.songIds.size),
                            songId = cover?.id ?: -1L,
                            albumId = cover?.albumId ?: -1L,
                            onClick = { onOpen(Route.PlaylistDetail(p.id)) },
                            art = { m -> PlaylistCover(p, cover, m, placeholderIconSize = 40.dp) },
                        )
                    }
                }
            }
        }
    }
}

private fun greetingRes(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_day
    in 18..22 -> R.string.greeting_evening
    else -> R.string.greeting_night
}

@Composable
private fun QuickCard(
    title: String,
    icon: ImageVector,
    shape: Shape,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.93f)
    Column(
        modifier = modifier
            .scaleBy(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(interactionSource = source, indication = ripple()) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(shape)
                .background(content.copy(alpha = 0.14f)),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = content.copy(alpha = 0.75f),
            maxLines = 1,
        )
    }
}

@Composable
private fun RecentCarousel(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val state = rememberCarouselState { songs.size }
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 200.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) { index ->
        val song = songs[index]
        Box(
            Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onPlay(songs, index)
                },
        ) {
            Artwork(song.id, song.albumId, Modifier.fillMaxSize(), placeholderIconSize = 48.dp)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))
                    )
            )
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .graphicsLayer {
                        // Follow the visible (masked) part of the item and fade out as it narrows,
                        // so squeezed items never show clipped, overlapping titles.
                        val info = carouselItemDrawInfo
                        translationX = info.maskRect.left
                        val open = if (info.maxSize > 0f) info.size / info.maxSize else 1f
                        alpha = ((open - 0.7f) / 0.3f).coerceIn(0f, 1f)
                    }
                    .padding(16.dp),
            )
        }
    }
}

@Composable
fun CoverCard(
    title: String,
    subtitle: String,
    songId: Long,
    albumId: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
    shape: Shape = RoundedCornerShape(28.dp),
    onLongClick: (() -> Unit)? = null,
    /** Draws the picture instead of the track's art; gets the square, unclipped bounds. */
    art: (@Composable (Modifier) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.94f)
    Column(
        modifier = modifier
            .width(width)
            .scaleBy(scale)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickableCompat(source, onClick, onLongClick, haptics)
            .padding(4.dp),
    ) {
        if (art != null) {
            art(Modifier.fillMaxWidth().aspectRatio(1f))
        } else {
            Artwork(
                songId = songId,
                albumId = albumId,
                placeholderIconSize = 40.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = subtitle.ifEmpty { stringResource(R.string.unknown_artist) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
fun AlbumCard(album: Album, onOpen: (Route) -> Unit, modifier: Modifier = Modifier, width: Dp = 148.dp) {
    CoverCard(
        title = album.title,
        subtitle = album.artist,
        songId = album.coverSongId,
        albumId = album.coverAlbumId,
        onClick = { onOpen(Route.AlbumDetail(album.id)) },
        width = width,
        modifier = modifier,
    )
}
