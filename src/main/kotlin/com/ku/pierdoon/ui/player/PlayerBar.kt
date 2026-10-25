package com.ku.pierdoon.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.PositionSnapshot
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.playback.Player
import com.ku.pierdoon.playback.PlayerState
import com.ku.pierdoon.playback.SleepTimer
import com.ku.pierdoon.resources.*
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.Backdrop
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.ExpressiveToggleButton
import com.ku.pierdoon.ui.components.PlayPauseButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.glass
import org.jetbrains.compose.resources.stringResource

/**
 * The desktop's mini player: a frosted bar along the bottom of the window. Track on the left,
 * transport and the wavy seek bar in the middle, lyrics, queue and volume on the right.
 */
@Composable
fun PlayerBar(
    nowPlaying: NowPlaying,
    state: PlayerState,
    position: () -> PositionSnapshot,
    isFavorite: Boolean,
    volume: Float,
    muted: Boolean,
    showLyrics: Boolean,
    showQueue: Boolean,
    backdrop: Backdrop,
    onToggleFavorite: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    val glassButton = colors.surfaceContainerHighest.copy(alpha = 0.55f)
    val sleepTimer by SleepTimer.state.collectAsState()
    var showSleepSheet by remember { mutableStateOf(false) }
    if (showSleepSheet) SleepTimerSheet(sleepTimer, onDismiss = { showSleepSheet = false })

    Surface(
        shape = shape,
        color = Color.Transparent,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.08f)),
        modifier = modifier.glass(
            backdrop = backdrop,
            shape = shape,
            tint = colors.surfaceContainerHigh.copy(alpha = 0.72f),
            fallback = colors.surfaceContainerHigh,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Track ---------------------------------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(0.3f),
            ) {
                Artwork(
                    songId = nowPlaying.songId,
                    albumId = nowPlaying.albumId,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onExpand),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        nowPlaying.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onExpand),
                    )
                    Text(
                        nowPlaying.artist.ifEmpty { stringResource(Res.string.unknown_artist) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (nowPlaying.artist.isNotEmpty()) {
                            Modifier.clickable { onOpenArtist(nowPlaying.artist) }
                        } else {
                            Modifier
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                ExpressiveIconButton(
                    onClick = onToggleFavorite,
                    icon = if (isFavorite) PlayerIcons.Favorite else PlayerIcons.FavoriteBorder,
                    contentDescription = stringResource(if (isFavorite) Res.string.remove_from_favorites else Res.string.add_to_favorites),
                    containerColor = Color.Transparent,
                    contentColor = if (isFavorite) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            }

            // Transport -----------------------------------------------------------------------
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(0.4f).widthIn(min = 320.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveToggleButton(
                        checked = state.shuffle,
                        onClick = onToggleShuffle,
                        icon = PlayerIcons.Shuffle,
                        contentDescription = stringResource(Res.string.shuffle),
                        iconSize = 20.dp,
                        uncheckedColor = Color.Transparent,
                        modifier = Modifier.size(40.dp),
                    )
                    ExpressiveIconButton(
                        onClick = onPrevious,
                        icon = PlayerIcons.Previous,
                        contentDescription = stringResource(Res.string.previous),
                        containerColor = glassButton,
                        contentColor = colors.onSurface,
                        modifier = Modifier.size(44.dp),
                    )
                    PlayPauseButton(
                        isPlaying = state.isPlaying,
                        onClick = onTogglePlay,
                        playLabel = stringResource(Res.string.play),
                        pauseLabel = stringResource(Res.string.pause),
                        iconSize = 28.dp,
                        modifier = Modifier.size(width = 64.dp, height = 48.dp),
                    )
                    ExpressiveIconButton(
                        onClick = onNext,
                        icon = PlayerIcons.Next,
                        contentDescription = stringResource(Res.string.next),
                        containerColor = glassButton,
                        contentColor = colors.onSurface,
                        modifier = Modifier.size(44.dp),
                    )
                    ExpressiveToggleButton(
                        checked = state.repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = onCycleRepeat,
                        icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) PlayerIcons.RepeatOne else PlayerIcons.Repeat,
                        contentDescription = stringResource(Res.string.repeat),
                        iconSize = 20.dp,
                        uncheckedColor = Color.Transparent,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                // Draws the elapsed and total time under itself.
                WavySeekBar(
                    position = position,
                    durationMs = state.durationMs,
                    isPlaying = state.isPlaying,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                )
            }

            // Extras --------------------------------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                modifier = Modifier.weight(0.3f),
            ) {
                SleepTimerButton(active = sleepTimer != null, onClick = { showSleepSheet = true }, glass = Color.Transparent)
                ExpressiveToggleButton(
                    checked = showLyrics,
                    onClick = onToggleLyrics,
                    icon = PlayerIcons.Lyrics,
                    contentDescription = stringResource(Res.string.lyrics),
                    iconSize = 20.dp,
                    uncheckedColor = Color.Transparent,
                    modifier = Modifier.size(40.dp),
                )
                ExpressiveToggleButton(
                    checked = showQueue,
                    onClick = onToggleQueue,
                    icon = PlayerIcons.Queue,
                    contentDescription = stringResource(Res.string.queue),
                    iconSize = 20.dp,
                    uncheckedColor = Color.Transparent,
                    modifier = Modifier.size(40.dp),
                )
                VolumeControl(volume, muted, onVolume, onToggleMute)
                ExpressiveIconButton(
                    onClick = onExpand,
                    icon = PlayerIcons.OpenInFull,
                    contentDescription = stringResource(Res.string.open_full_player),
                    containerColor = Color.Transparent,
                    contentColor = colors.onSurfaceVariant,
                    iconSize = 20.dp,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

/** Mute button and slider; the mouse wheel over either nudges the volume. */
@Composable
fun VolumeControl(volume: Float, muted: Boolean, onVolume: (Float) -> Unit, onToggleMute: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shown = if (muted) 0f else volume
    val wheel = rememberScrollableState { delta ->
        onVolume((volume - delta / WHEEL_PX_PER_FULL).coerceIn(0f, 1f))
        delta
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.scrollable(wheel, Orientation.Vertical),
    ) {
        ExpressiveIconButton(
            onClick = onToggleMute,
            icon = when {
                shown == 0f -> PlayerIcons.VolumeOff
                shown < 0.5f -> PlayerIcons.VolumeDown
                else -> PlayerIcons.VolumeUp
            },
            contentDescription = stringResource(Res.string.mute),
            containerColor = Color.Transparent,
            contentColor = colors.onSurfaceVariant,
            iconSize = 20.dp,
            modifier = Modifier.size(40.dp),
        )
        Slider(
            value = shown,
            onValueChange = onVolume,
            colors = SliderDefaults.colors(inactiveTrackColor = colors.surfaceContainerHighest),
            modifier = Modifier.width(110.dp).height(32.dp),
        )
    }
}

private const val WHEEL_PX_PER_FULL = 1200f
