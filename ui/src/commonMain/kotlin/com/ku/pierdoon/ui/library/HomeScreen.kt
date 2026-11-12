package com.ku.pierdoon.ui.library

import com.ku.pierdoon.ui.components.ScrollableRow
import com.ku.pierdoon.ui.components.hairline
import com.ku.pierdoon.ui.library.TWO_PANE_WIDTH
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.splitArtists
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.BlurredArtwork
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.cornerDecor
import com.ku.pierdoon.ui.components.livelyCard
import com.ku.pierdoon.ui.components.rememberSpin
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
    /** A button beside the greeting (the phone's, to the paired computer); null for none. */
    greetingAction: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth >= TWO_PANE_WIDTH
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
    val mixCard: @Composable (Modifier) -> Unit = { m ->
        DailyMixCard(songs = dailyMix, onOpen = { onOpen(Route.DailyMix) }, onPlay = { onPlay(dailyMix, 0) }, modifier = m)
    }
    val heroCard: @Composable (HeroTrack, Modifier) -> Unit = { track, m ->
        // A track that isn't loaded any more starts again from the recent list.
        val play = { if (track.playing) onTogglePlay() else onPlay(recent, 0) }
        ContinueCard(
            track = track,
            isPlaying = isPlaying,
            onOpen = { if (track.playing) onOpenPlayer() else play() },
            onPlayPause = play,
            background = { b -> BlurredArtwork(track.songId, track.albumId, b) },
            coverSize = if (wide) 140.dp else HomeSizes.heroCover,
            modifier = m,
        )
    }
    // On a wide window the playlist of the day sits beside the hero.
    val mixBesideHero = wide && hero != null && dailyMix.isNotEmpty()
    LazyColumn(
        contentPadding = PaddingValues(bottom = listBottomPadding(bottomInset)),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "greeting") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = if (greetingAction != null) 12.dp else 24.dp, top = 32.dp, bottom = 20.dp),
            ) {
                Text(
                    text = stringResource(greetingRes()),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                greetingAction?.invoke()
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
                if (mixBesideHero) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp).height(IntrinsicSize.Min),
                    ) {
                        heroCard(hero, Modifier.weight(1.4f).fillMaxHeight())
                        mixCard(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    heroCard(hero, Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        item(key = "pills") { QuickPills(pills, Modifier.padding(top = if (hero != null) 16.dp else 0.dp), fill = wide) }
        if (dailyMix.isNotEmpty() && !mixBesideHero) {
            item(key = "mix") { mixCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) }
        }

        if (recent.isNotEmpty()) {
            item(key = "recentTitle") { SectionTitle(stringResource(Res.string.recently_played)) }
            item(key = "recent") { RecentCovers(recent, onPlay) }
        }
        if (collections.recentlyAdded.isNotEmpty()) {
            item(key = "addedTitle") { SectionTitle(stringResource(Res.string.recently_added)) }
            item(key = "added") {
                ScrollableRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    firstKey = collections.recentlyAdded.firstOrNull()?.id,
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
                ScrollableRow(
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
                ScrollableRow(
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
}

private fun greetingRes(): StringResource = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> Res.string.greeting_morning
    in 12..17 -> Res.string.greeting_day
    in 18..22 -> Res.string.greeting_evening
    else -> Res.string.greeting_night
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
        MixCollage(songs, Modifier.size(HomeSizes.mixCollage))
        Spacer(Modifier.width(HomeSizes.mixGap))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.daily_mix),
                style = if (HomeSizes.largeMixTitle) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleMediumEmphasized,
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
            modifier = Modifier.padding(end = 4.dp).size(HomeSizes.mixPlay),
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
