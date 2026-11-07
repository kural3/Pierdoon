package com.ku.pierdoon.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.history.DayPart
import com.ku.pierdoon.data.history.Play
import com.ku.pierdoon.data.history.YearReview
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.cornerDecor
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The year in music, as a run of cards: swipe or tap the right half for the next one, the left
 * for the one before. Each card has its own colours from the theme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YearReviewScreen(
    year: Int,
    plays: List<Play>?,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenArtist: (String) -> Unit,
    bottomInset: Dp,
) {
    val review = remember(plays, songs, year) {
        plays?.let { YearReview.from(it, songs.associateBy { s -> s.id }, year) }
    }
    val colors = MaterialTheme.colorScheme
    if (review == null || review.plays == 0) {
        AppScaffold(title = stringResource(Res.string.year_review_title, year), onBack = onBack) { inner ->
            Box(Modifier.padding(inner)) { if (review != null) EmptyMessage(stringResource(Res.string.stats_empty)) }
        }
        return
    }
    val cards: List<@Composable () -> Unit> = buildList {
        add { IntroCard(review) }
        add { TimeCard(review) }
        if (review.topArtists.isNotEmpty()) add { ArtistsCard(review, onOpenArtist) }
        if (review.topTracks.isNotEmpty()) add { TracksCard(review, onPlay) }
        add { MonthsCard(review) }
        add { HabitsCard(review) }
        if (review.newArtists > 0) add { NewArtistsCard(review, onOpenArtist) }
        add { OutroCard(review, onPlay) }
    }
    val palettes = listOf(
        colors.primaryContainer to colors.onPrimaryContainer,
        colors.tertiaryContainer to colors.onTertiaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer,
        colors.primary to colors.onPrimary,
        colors.surfaceContainerHighest to colors.onSurface,
        colors.tertiary to colors.onTertiary,
    )
    val shapes = listOf(
        MaterialShapes.SoftBurst, MaterialShapes.Cookie9Sided, MaterialShapes.Flower,
        MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pill,
    )
    val pager = rememberPagerState { cards.size }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(bottom = bottomInset)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            ) {
                ExpressiveIconButton(
                    onClick = onBack,
                    icon = PlayerIcons.Back,
                    contentDescription = stringResource(Res.string.back),
                    containerColor = Color.Transparent,
                    contentColor = colors.onSurface,
                    modifier = Modifier.size(48.dp),
                )
                // One bar per card, filled up to the one shown.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    repeat(cards.size) { i ->
                        val fill by animateFloatAsState(if (i <= pager.currentPage) 1f else 0.25f, MaterialTheme.motionScheme.defaultEffectsSpec())
                        Box(
                            Modifier.weight(1f).height(4.dp).clip(CircleShape)
                                .background(colors.primary.copy(alpha = fill)),
                        )
                    }
                }
            }
            HorizontalPager(
                state = pager,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val (container, content) = palettes[page % palettes.size]
                val shape = RoundedCornerShape(36.dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(container)
                        .cornerDecor(shapes[page % shapes.size].toShape(), content.copy(alpha = 0.08f), size = 260.dp),
                ) {
                    // Taps on either half step through the cards, under whatever is clickable on them.
                    Row(Modifier.fillMaxSize()) {
                        val none = remember { MutableInteractionSource() }
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(none, null) {
                            scope.launch { pager.animateScrollToPage((page - 1).coerceAtLeast(0)) }
                        })
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(none, null) {
                            scope.launch { pager.animateScrollToPage((page + 1).coerceAtMost(cards.lastIndex)) }
                        })
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides content,
                    ) {
                        Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(28.dp)) { cards[page]() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Big(text: String, modifier: Modifier = Modifier) = Text(
    text,
    style = MaterialTheme.typography.displayLargeEmphasized,
    modifier = modifier,
)

@Composable
private fun Headline(text: String) = Text(text, style = MaterialTheme.typography.headlineMediumEmphasized)

@Composable
private fun Body(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.8f),
)

@Composable
private fun IntroCard(r: YearReview) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Big("${r.year}")
        Headline(stringResource(Res.string.year_review_intro))
        Body(stringResource(Res.string.year_review_tap))
    }
}

@Composable
private fun TimeCard(r: YearReview) {
    val hours = (r.listenedMs / 3_600_000).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Body(stringResource(Res.string.year_review_time_lead))
        Big(if (hours >= 1) pluralStringResource(Res.plurals.hours_count, hours, hours) else listenedTimeText(r.listenedMs))
        // Days only once there's at least one whole day to speak of.
        val days = r.listenedMs / 86_400_000.0
        if (days >= 1) Headline(stringResource(Res.string.year_review_time_days, "%.1f".format(days)))
        Spacer(Modifier.height(8.dp))
        Body(stringResource(Res.string.year_review_counts, r.plays, r.tracks, r.artists))
    }
}

@Composable
private fun ArtistsCard(r: YearReview, onOpenArtist: (String) -> Unit) {
    val top = r.topArtists.first()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Body(stringResource(Res.string.year_review_top_artist))
        Artwork(top.cover.id, top.cover.albumId, Modifier.size(132.dp).clip(MaterialShapes.Cookie9Sided.toShape()), large = true)
        Headline(top.name)
        Body(pluralStringResource(Res.plurals.plays_count, top.plays, top.plays) + " · " + listenedTimeText(top.listenedMs))
        Spacer(Modifier.height(6.dp))
        r.topArtists.drop(1).forEachIndexed { i, a ->
            RankRow(i + 2, a.name, pluralStringResource(Res.plurals.plays_count, a.plays, a.plays)) { onOpenArtist(a.name) }
        }
    }
}

@Composable
private fun TracksCard(r: YearReview, onPlay: (List<Song>, Int) -> Unit) {
    val top = r.topTracks.first()
    val songs = r.topTracks.map { it.song }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Body(stringResource(Res.string.year_review_top_track))
        Artwork(top.song.id, top.song.albumId, Modifier.size(132.dp).clip(RoundedCornerShape(28.dp)), large = true)
        Headline(top.song.title)
        Body(top.song.artist + " · " + pluralStringResource(Res.plurals.plays_count, top.plays, top.plays))
        Spacer(Modifier.height(6.dp))
        r.topTracks.drop(1).forEachIndexed { i, t ->
            RankRow(i + 2, t.song.title, pluralStringResource(Res.plurals.plays_count, t.plays, t.plays)) { onPlay(songs, i + 1) }
        }
    }
}

@Composable
private fun RankRow(rank: Int, title: String, trailing: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Text("$rank", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.width(32.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MonthsCard(r: YearReview) {
    val content = androidx.compose.material3.LocalContentColor.current
    val month = monthName(r.topMonth)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Body(stringResource(Res.string.year_review_month_lead))
        Big(month)
        Body(stringResource(Res.string.year_review_month_time, listenedTimeText(r.months[r.topMonth])))
        Spacer(Modifier.height(8.dp))
        val max = r.months.maxOrNull()?.coerceAtLeast(1) ?: 1
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val step = size.width / 12
            val bar = step * 0.62f
            val floor = 4.dp.toPx()
            r.months.forEachIndexed { i, v ->
                val h = if (v == 0L) floor else floor + (size.height - floor) * v / max
                drawRoundRect(
                    color = content.copy(alpha = if (i == r.topMonth) 1f else 0.35f),
                    topLeft = Offset(i * step + (step - bar) / 2, size.height - h),
                    size = Size(bar, h),
                    cornerRadius = CornerRadius(minOf(bar / 2, 10.dp.toPx())),
                )
            }
        }
    }
}

@Composable
private fun HabitsCard(r: YearReview) {
    val day = remember(r.topDayMs) { DateFormat.getDateInstance(DateFormat.LONG).format(Date(r.topDayMs)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Body(stringResource(Res.string.year_review_streak_lead))
        Big(pluralStringResource(Res.plurals.days_count, r.longestStreakDays, r.longestStreakDays))
        Headline(stringResource(Res.string.year_review_streak))
        Spacer(Modifier.height(12.dp))
        Body(stringResource(Res.string.year_review_top_day, day, listenedTimeText(r.topDayListenedMs)))
        Body(
            stringResource(
                when (r.dayPart) {
                    DayPart.Night -> Res.string.year_review_night
                    DayPart.Morning -> Res.string.year_review_morning
                    DayPart.Afternoon -> Res.string.year_review_afternoon
                    DayPart.Evening -> Res.string.year_review_evening
                }
            )
        )
    }
}

@Composable
private fun NewArtistsCard(r: YearReview, onOpenArtist: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Body(stringResource(Res.string.year_review_new_lead))
        Big("${r.newArtists}")
        Headline(pluralStringResource(Res.plurals.year_review_new_artists, r.newArtists))
        r.topNewArtist?.let { a ->
            Spacer(Modifier.height(8.dp))
            Body(stringResource(Res.string.year_review_new_top))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onOpenArtist(a.name) }.padding(4.dp),
            ) {
                Artwork(a.cover.id, a.cover.albumId, Modifier.size(64.dp).clip(CircleShape))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(a.name, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(Res.plurals.plays_count, a.plays, a.plays), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun OutroCard(r: YearReview, onPlay: (List<Song>, Int) -> Unit) {
    val content = androidx.compose.material3.LocalContentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Big(stringResource(Res.string.year_review_thanks))
        r.firstSong?.let { Body(stringResource(Res.string.year_review_first, it.title, it.artist)) }
        if (r.topTracks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onPlay(r.topTracks.map { it.song }, 0) },
                colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.height(56.dp),
            ) {
                Icon(PlayerIcons.Play, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.year_review_play_top), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** "12 h 5 min", or minutes alone below an hour. */
@Composable
private fun listenedTimeText(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) stringResource(Res.string.time_hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
    else stringResource(Res.string.time_minutes, minutes.toInt())
}

/** The month's own name in the interface's language ("March", "март"), capitalised. */
private fun monthName(month: Int): String {
    val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.MONTH, month) }
    return SimpleDateFormat("LLLL", Locale.getDefault()).format(cal.time)
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/** A card for the home screen in December and January: the year in music is ready. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YearReviewCard(year: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.primary)
            .cornerDecor(MaterialShapes.Sunny.toShape(), colors.onPrimary.copy(alpha = 0.1f), size = 150.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.year_review_title, year), style = MaterialTheme.typography.titleLargeEmphasized, color = colors.onPrimary)
            Text(stringResource(Res.string.year_review_card), style = MaterialTheme.typography.bodyMedium, color = colors.onPrimary.copy(alpha = 0.85f))
        }
        Icon(PlayerIcons.Sparkle, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(32.dp))
    }
}
