package com.ku.pierdoon.ui.library

import org.jetbrains.compose.resources.StringResource
import com.ku.pierdoon.ui.components.hairline
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.Album
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.splitArtists
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.BlurredArtwork
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.cornerDecor
import com.ku.pierdoon.ui.components.livelyCard
import com.ku.pierdoon.ui.components.rememberSpin
import com.ku.pierdoon.ui.components.spunBackground
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.navigation.Route
import java.util.Calendar

@Composable
fun HomeScreen(
    recent: List<Song>,
    collections: MusicCollections,
    playlists: List<Playlist>,
    favoritesCount: Int,
    dailyMix: List<Song>,
    /** Time listened over the last seven days; null until the play log is read. */
    weekListenedMs: Long?,
    resolveCover: (Playlist) -> Song?,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffleAll: () -> Unit,
    onOpen: (Route) -> Unit,
    onSongLongPress: (Song) -> Unit,
    bottomInset: Dp,
    /** Starts the radio; null with the radio switched off. */
    onMyWave: (() -> Unit)? = null,
    /** The year whose review the home screen offers (in December and January), or null. */
    yearReview: Int? = null,
    /** The track for the card at the top: the one playing, or else the last one played; null hides it. */
    hero: HeroTrack? = null,
    isPlaying: Boolean = false,
    onOpenPlayer: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    /** The radio is on (playing or paused): its pill shows it. */
    radioOn: Boolean = false,
) {
    val pills = homePills(
        shuffleLabel = stringResource(Res.string.shuffle_all),
        onShuffle = onShuffleAll,
        favoritesLabel = stringResource(Res.string.favorites),
        favoritesDetail = pluralStringResource(Res.plurals.songs_count, favoritesCount, favoritesCount),
        onFavorites = { onOpen(Route.Favorites) },
        radioLabel = stringResource(Res.string.radio),
        onRadio = onMyWave,
        statsLabel = stringResource(Res.string.stats),
        statsDetail = weekListenedMs?.let { listenedTime(it) },
        onStats = weekListenedMs?.let { { onOpen(Route.Stats) } },
        radioOn = radioOn,
        onOpenPlayer = onOpenPlayer,
    )
    LazyColumn(
        contentPadding = PaddingValues(bottom = listBottomPadding(bottomInset)),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "greeting") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 12.dp, top = 32.dp, bottom = 20.dp),
            ) {
                Text(
                    text = stringResource(greetingRes()),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                ExpressiveIconButton(
                    onClick = { onOpen(Route.Computer) },
                    icon = PlayerIcons.Desktop,
                    contentDescription = stringResource(Res.string.pc_open),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        if (yearReview != null) {
            item(key = "yearReview") {
                YearReviewCard(
                    year = yearReview,
                    onClick = { onOpen(Route.YearReview(yearReview)) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
        if (hero != null) {
            item(key = "hero") {
                // A track that isn't loaded any more starts again from the recent list.
                val play = { if (hero.playing) onTogglePlay() else onPlay(recent, 0) }
                ContinueCard(
                    track = hero,
                    isPlaying = isPlaying,
                    onOpen = { if (hero.playing) onOpenPlayer() else play() },
                    onPlayPause = play,
                    background = { b -> BlurredArtwork(hero.songId, hero.albumId, b) },
                    coverSize = 96.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item(key = "pills") { QuickPills(pills, Modifier.padding(top = if (hero != null) 16.dp else 0.dp)) }
        if (dailyMix.isNotEmpty()) {
            item(key = "mix") {
                DailyMixCard(
                    songs = dailyMix,
                    onOpen = { onOpen(Route.DailyMix) },
                    onPlay = { onPlay(dailyMix, 0) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recentTitle") { SectionTitle(stringResource(Res.string.recently_played)) }
            item(key = "recent") { RecentCarousel(recent, onPlay) }
        }
        if (collections.recentlyAdded.isNotEmpty()) {
            item(key = "addedTitle") { SectionTitle(stringResource(Res.string.recently_added)) }
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
            item(key = "albumsTitle") { SectionTitle(stringResource(Res.string.nav_albums)) }
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
            item(key = "playlistsTitle") { SectionTitle(stringResource(Res.string.your_playlists)) }
            item(key = "playlists") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(playlists, key = { it.id }) { p ->
                        val cover = resolveCover(p)
                        CoverCard(
                            title = p.name,
                            subtitle = pluralStringResource(Res.plurals.songs_count, p.songIds.size, p.songIds.size),
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

private fun greetingRes(): StringResource = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> Res.string.greeting_morning
    in 12..17 -> Res.string.greeting_day
    in 18..22 -> Res.string.greeting_evening
    else -> Res.string.greeting_night
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
            text = subtitle.ifEmpty { stringResource(Res.string.unknown_artist) },
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

/** The playlist of the day: its covers, a few of its artists and a play button. */
@Composable
private fun DailyMixCard(songs: List<Song>, onOpen: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.97f)
    val spin = rememberSpin()
    val artists = remember(songs) { songs.flatMap { splitArtists(it.artist) }.distinct().take(4).joinToString(", ") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .livelyCard(spin)
            .scaleBy(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.secondaryContainer)
            .hairline(RoundedCornerShape(28.dp))
            .cornerDecor(MaterialShapes.Clover8Leaf.toShape(), colors.onSecondaryContainer.copy(alpha = 0.07f), size = 150.dp, spin = spin)
            .clickable(interactionSource = source, indication = ripple()) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onOpen()
            }
            .padding(12.dp),
    ) {
        MixCollage(songs, Modifier.size(88.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.daily_mix),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artists.ifEmpty { stringResource(Res.string.daily_mix_desc) },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSecondaryContainer.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        ExpressiveIconButton(
            onClick = onPlay,
            icon = PlayerIcons.Play,
            contentDescription = stringResource(Res.string.play_all),
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            iconSize = 28.dp,
            haptic = HapticFeedbackType.Confirm,
            modifier = Modifier.padding(end = 4.dp).size(52.dp),
        )
    }
}

/** Four covers of the mix in a square, or one when there are fewer. */
@Composable
fun MixCollage(songs: List<Song>, modifier: Modifier = Modifier) {
    val shown = remember(songs) { songs.distinctBy { it.albumId }.take(4) }
    Box(modifier.clip(RoundedCornerShape(20.dp))) {
        if (shown.size < 4) {
            val s = shown.firstOrNull()
            Artwork(s?.id ?: -1L, s?.albumId ?: -1L, Modifier.fillMaxSize(), placeholderIconSize = 40.dp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        for (col in 0..1) {
                            val s = shown[row * 2 + col]
                            Artwork(s.id, s.albumId, Modifier.weight(1f).fillMaxHeight(), placeholderIconSize = 20.dp)
                        }
                    }
                }
            }
        }
    }
}

