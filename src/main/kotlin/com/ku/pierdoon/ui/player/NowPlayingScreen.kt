package com.ku.pierdoon.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.LyricsState
import com.ku.pierdoon.PositionSnapshot
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.playback.Player
import com.ku.pierdoon.playback.PlayerState
import com.ku.pierdoon.playback.QueueState
import com.ku.pierdoon.playback.SleepTimer
import com.ku.pierdoon.resources.*
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.ui.components.BlurredArtwork
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.ExpressiveToggleButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.theme.LocalUiPrefs
import org.jetbrains.compose.resources.stringResource

/**
 * The open player on a wide window: art and controls on the left, lyrics or the queue on the
 * right, over the same frosted album art as on the phone. A narrow window gets the phone's
 * [FullPlayer] as it is.
 */
@Composable
fun NowPlayingScreen(
    nowPlaying: NowPlaying,
    state: PlayerState,
    position: () -> PositionSnapshot,
    lyrics: LyricsState,
    showLyrics: Boolean,
    queue: QueueState,
    showQueue: Boolean,
    isFavorite: Boolean,
    volume: Float,
    muted: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onPlayQueueEntry: (String) -> Unit,
    onRemoveQueueEntry: (String) -> Unit,
    onMoveQueueEntry: (String, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onRetryLyrics: () -> Unit,
    lyricsOffsetMs: Long,
    onShiftLyrics: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth < WIDE) {
            FullPlayer(
                nowPlaying = nowPlaying,
                state = state,
                position = position,
                lyrics = lyrics,
                showLyrics = showLyrics,
                queue = queue,
                showQueue = showQueue,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onToggleLyrics = onToggleLyrics,
                onToggleQueue = onToggleQueue,
                onPlayQueueEntry = onPlayQueueEntry,
                onRemoveQueueEntry = onRemoveQueueEntry,
                onMoveQueueEntry = onMoveQueueEntry,
                onSongLongPress = onSongLongPress,
                onRetryLyrics = onRetryLyrics,
                lyricsOffsetMs = lyricsOffsetMs,
                onShiftLyrics = onShiftLyrics,
                onResetLyricsOffset = onResetLyricsOffset,
                onTogglePlay = onTogglePlay,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeek = onSeek,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onOpenArtist = onOpenArtist,
                onCollapse = onCollapse,
            )
        } else {
            WideNowPlaying(
                nowPlaying, state, position, lyrics, showLyrics, queue, showQueue, isFavorite, volume, muted,
                onToggleFavorite, onToggleLyrics, onToggleQueue, onPlayQueueEntry, onRemoveQueueEntry,
                onMoveQueueEntry, onSongLongPress, onRetryLyrics, lyricsOffsetMs, onShiftLyrics,
                onResetLyricsOffset, onTogglePlay, onPrevious, onNext, onSeek, onToggleShuffle,
                onCycleRepeat, onVolume, onToggleMute, onOpenArtist, onCollapse,
            )
        }
    }
}

@Composable
private fun WideNowPlaying(
    nowPlaying: NowPlaying,
    state: PlayerState,
    position: () -> PositionSnapshot,
    lyrics: LyricsState,
    showLyrics: Boolean,
    queue: QueueState,
    showQueue: Boolean,
    isFavorite: Boolean,
    volume: Float,
    muted: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onPlayQueueEntry: (String) -> Unit,
    onRemoveQueueEntry: (String) -> Unit,
    onMoveQueueEntry: (String, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onRetryLyrics: () -> Unit,
    lyricsOffsetMs: Long,
    onShiftLyrics: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onCollapse: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val nothing = LocalUiPrefs.current.nothing
    val glassButton = if (nothing) colors.surfaceContainerHigh else colors.surfaceContainerHighest.copy(alpha = 0.45f)
    val sleepTimer by SleepTimer.state.collectAsState()
    var showSleepSheet by remember { mutableStateOf(false) }
    if (showSleepSheet) SleepTimerSheet(sleepTimer, onDismiss = { showSleepSheet = false })

    Box(Modifier.fillMaxSize().background(colors.surface)) {
        if (!nothing) {
            BlurredArtwork(nowPlaying.songId, nowPlaying.albumId, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to colors.surface.copy(alpha = 0.35f),
                        0.5f to colors.surface.copy(alpha = 0.55f),
                        1f to colors.surface.copy(alpha = 0.75f),
                    )
                )
            )
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
            // Art and controls --------------------------------------------------------------
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(0.45f).widthIn(max = 560.dp).fillMaxHeight(),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ExpressiveIconButton(
                        onClick = onCollapse,
                        icon = PlayerIcons.ExpandMore,
                        contentDescription = stringResource(Res.string.collapse),
                        containerColor = glassButton,
                        contentColor = colors.onSurface,
                        modifier = Modifier.size(48.dp),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(Res.string.now_playing), style = MaterialTheme.typography.labelLarge)
                        sleepTimerLabel(sleepTimer)?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = colors.primary)
                        }
                    }
                    SleepTimerButton(active = sleepTimer != null, onClick = { showSleepSheet = true }, glass = glassButton)
                    Spacer(Modifier.width(8.dp))
                    FavoriteButton(isFavorite = isFavorite, onClick = onToggleFavorite, containerColor = glassButton)
                }
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayerArtwork(
                        nowPlaying = nowPlaying,
                        isPlaying = state.isPlaying,
                        position = position,
                        durationMs = state.durationMs,
                        onTap = onTogglePlay,
                        onSeek = onSeek,
                        modifier = Modifier
                            .widthIn(max = minOf(maxWidth, maxHeight))
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                }
                Column(Modifier.fillMaxWidth()) {
                    key(nowPlaying.songId) {
                        Text(
                            text = nowPlaying.title,
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = 2),
                        )
                    }
                    val artist = nowPlaying.artist
                    Text(
                        text = artist.ifEmpty { stringResource(Res.string.unknown_artist) },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .offset(x = (-6).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = artist.isNotEmpty()) { onOpenArtist(artist) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                WavySeekBar(
                    position = position,
                    durationMs = state.durationMs,
                    isPlaying = state.isPlaying,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                PlayerControls(
                    isPlaying = state.isPlaying,
                    onPrevious = onPrevious,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    sideColor = glassButton,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val toggleSize = Modifier.size(width = 64.dp, height = 52.dp)
                    ExpressiveToggleButton(
                        checked = state.shuffle,
                        onClick = onToggleShuffle,
                        icon = PlayerIcons.Shuffle,
                        contentDescription = stringResource(Res.string.shuffle),
                        uncheckedColor = glassButton,
                        modifier = toggleSize,
                    )
                    ExpressiveToggleButton(
                        checked = state.repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = onCycleRepeat,
                        icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) PlayerIcons.RepeatOne else PlayerIcons.Repeat,
                        contentDescription = stringResource(Res.string.repeat),
                        uncheckedColor = glassButton,
                        modifier = toggleSize,
                    )
                    Spacer(Modifier.weight(1f))
                    VolumeControl(volume, muted, onVolume, onToggleMute)
                }
            }

            Spacer(Modifier.width(40.dp))

            // Lyrics or queue ---------------------------------------------------------------
            Column(Modifier.weight(0.55f).fillMaxHeight()) {
                val panel = if (showQueue) PlayerPanel.Queue else PlayerPanel.Lyrics
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExpressiveToggleButton(
                        checked = panel == PlayerPanel.Lyrics,
                        onClick = { if (panel != PlayerPanel.Lyrics) onToggleLyrics() },
                        icon = PlayerIcons.Lyrics,
                        contentDescription = stringResource(Res.string.lyrics),
                        uncheckedColor = glassButton,
                        modifier = Modifier.size(width = 64.dp, height = 48.dp),
                    )
                    ExpressiveToggleButton(
                        checked = panel == PlayerPanel.Queue,
                        onClick = { if (panel != PlayerPanel.Queue) onToggleQueue() },
                        icon = PlayerIcons.Queue,
                        contentDescription = stringResource(Res.string.queue),
                        uncheckedColor = glassButton,
                        modifier = Modifier.size(width = 64.dp, height = 48.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (panel == PlayerPanel.Queue) Res.string.queue else Res.string.lyrics),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                AnimatedContent(
                    targetState = panel,
                    transitionSpec = {
                        (fadeIn(effects) + scaleIn(spatial, initialScale = 0.96f))
                            .togetherWith(fadeOut(effects) + scaleOut(spatial, targetScale = 0.96f))
                    },
                    label = "widePanel",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { p ->
                    when (p) {
                        PlayerPanel.Queue -> QueuePanel(
                            queue = queue,
                            onPlay = onPlayQueueEntry,
                            onRemove = onRemoveQueueEntry,
                            onMove = onMoveQueueEntry,
                            onLongPress = onSongLongPress,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> LyricsPanel(
                            state = lyrics,
                            position = position,
                            onSeek = onSeek,
                            onRetry = onRetryLyrics,
                            offsetMs = lyricsOffsetMs,
                            onShift = onShiftLyrics,
                            onResetOffset = onResetLyricsOffset,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

private val WIDE = 880.dp
