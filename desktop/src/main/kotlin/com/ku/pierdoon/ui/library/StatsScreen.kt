package com.ku.pierdoon.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.history.ListeningStats
import com.ku.pierdoon.data.history.Play
import com.ku.pierdoon.data.history.StatsPeriod
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.resources.*
import com.ku.pierdoon.ui.components.ConnectedChoice
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.navigation.Route
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What was listened to: time, counts, a bar per day or month, the top artists and tracks. */
@Composable
fun StatsScreen(
    plays: List<Play>?,
    songs: List<Song>,
    currentSongId: Long?,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onOpen: (Route) -> Unit,
    onClear: () -> Unit,
    bottomInset: Dp,
) {
    var period by rememberSaveable { mutableStateOf(StatsPeriod.Week) }
    var confirmClear by remember { mutableStateOf(false) }
    val stats = remember(plays, songs, period) {
        plays?.let { ListeningStats.from(it, songs.associateBy { s -> s.id }, period, System.currentTimeMillis()) }
    }
    val topSongs = remember(stats) { stats?.topTracks?.map { it.song }.orEmpty() }

    AppScaffold(
        title = stringResource(Res.string.stats),
        onBack = onBack,
        actions = {
            if (!plays.isNullOrEmpty()) {
                OverflowMenu(listOf(listOf(MenuEntry(stringResource(Res.string.stats_clear), PlayerIcons.Delete) { confirmClear = true })))
            }
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = listBottomPadding(bottomInset)),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "period") { PeriodPicker(period) { period = it } }
            if (stats == null) return@LazyColumn
            if (stats.plays == 0) {
                item(key = "empty") { EmptyMessage(stringResource(Res.string.stats_empty)) }
                return@LazyColumn
            }
            item(key = "summary") { Summary(stats) }
            item(key = "timeline") { Timeline(stats.timeline) }
            item(key = "artistsTitle") { WideSectionTitle(stringResource(Res.string.stats_top_artists)) }
            item(key = "artists") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth(),
                ) {
                    itemsIndexed(stats.topArtists, key = { _, a -> a.name }) { _, a ->
                        CoverCard(
                            title = a.name,
                            subtitle = pluralStringResource(Res.plurals.plays_count, a.plays, a.plays),
                            songId = a.cover.id,
                            albumId = a.cover.albumId,
                            shape = CircleShape,
                            width = 120.dp,
                            onClick = { onOpen(Route.ArtistDetail(a.name)) },
                        )
                    }
                }
            }
            item(key = "tracksTitle") { WideSectionTitle(stringResource(Res.string.stats_top_tracks)) }
            itemsIndexed(stats.topTracks, key = { _, t -> t.song.id }, contentType = { _, _ -> "song" }) { index, t ->
                SongRow(
                    song = t.song,
                    isCurrent = t.song.id == currentSongId,
                    onClick = { onPlay(topSongs, index) },
                    onLongClick = { onSongLongPress(t.song) },
                    modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH),
                    leading = {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(24.dp),
                        )
                    },
                    trailing = {
                        Text(
                            pluralStringResource(Res.plurals.plays_count, t.plays, t.plays),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(Res.string.stats_clear_title)) },
            text = { Text(stringResource(Res.string.stats_clear_text)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(Res.string.stats_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PeriodPicker(period: StatsPeriod, onPick: (StatsPeriod) -> Unit) {
    ConnectedChoice(
        options = listOf(
            stringResource(Res.string.stats_week),
            stringResource(Res.string.stats_month),
            stringResource(Res.string.stats_year),
            stringResource(Res.string.stats_all),
        ),
        selected = period.ordinal,
        onSelect = { onPick(StatsPeriod.entries[it]) },
        modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun Summary(stats: ListeningStats) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(
            listenedTime(stats.listenedMs),
            style = MaterialTheme.typography.displayMediumEmphasized,
            color = colors.primary,
        )
        Text(
            stringResource(Res.string.stats_listened),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stats.plays, Res.string.stats_plays, Modifier.weight(1f))
            StatTile(stats.tracks, Res.string.stats_tracks, Modifier.weight(1f))
            StatTile(stats.artists, Res.string.stats_artists, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(value: Int, label: org.jetbrains.compose.resources.StringResource, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("$value", style = MaterialTheme.typography.headlineSmallEmphasized, color = colors.onSurface)
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A bar per day or month; the latest one in full colour. */
@Composable
private fun Timeline(values: List<Long>) {
    val colors = MaterialTheme.colorScheme
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Canvas(
        Modifier
            .widthIn(max = CONTENT_MAX_WIDTH)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .height(120.dp),
    ) {
        // Thin bars spread over the width: a week of seven wide blocks read as tiles, not a chart.
        val step = size.width / values.size
        val barWidth = minOf(step * 0.7f, 28.dp.toPx())
        val radius = CornerRadius(minOf(barWidth / 2, 8.dp.toPx()))
        val floor = 4.dp.toPx()
        values.forEachIndexed { i, v ->
            val h = if (v == 0L) floor else (floor + (size.height - floor) * v / max)
            drawRoundRect(
                color = if (i == values.lastIndex) colors.primary else colors.primary.copy(alpha = if (v == 0L) 0.15f else 0.45f),
                topLeft = Offset(i * step + (step - barWidth) / 2, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun WideSectionTitle(text: String) {
    Column(Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth()) { SectionTitle(text) }
}

/** "12 h 5 min", or minutes alone below an hour. */
@Composable
fun listenedTime(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) stringResource(Res.string.time_hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
    else stringResource(Res.string.time_minutes, minutes.toInt())
}
