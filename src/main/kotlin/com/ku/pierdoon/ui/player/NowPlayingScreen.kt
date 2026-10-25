package com.ku.pierdoon.ui.player

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.LyricsState
import com.ku.pierdoon.data.lyrics.Lyrics
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
import com.ku.pierdoon.ui.components.PlayPauseButton
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
    /** The expanding sheet's drag: pulling the player down shrinks it back into the bar. */
    dragModifier: Modifier = Modifier,
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
                dragModifier = dragModifier,
            )
        } else {
            WideNowPlaying(
                nowPlaying, state, position, lyrics, showLyrics, queue, showQueue, isFavorite, volume, muted,
                onToggleFavorite, onToggleLyrics, onToggleQueue, onPlayQueueEntry, onRemoveQueueEntry,
                onMoveQueueEntry, onSongLongPress, onRetryLyrics, lyricsOffsetMs, onShiftLyrics,
                onResetLyricsOffset, onTogglePlay, onPrevious, onNext, onSeek, onToggleShuffle,
                onCycleRepeat, onVolume, onToggleMute, onOpenArtist, onCollapse, dragModifier,
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
    dragModifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val nothing = LocalUiPrefs.current.nothing
    val glassButton = if (nothing) colors.surfaceContainerHigh else colors.surfaceContainerHighest.copy(alpha = 0.45f)
    val sleepTimer by SleepTimer.state.collectAsState()
    var showSleepSheet by remember { mutableStateOf(false) }
    if (showSleepSheet) SleepTimerSheet(sleepTimer, onDismiss = { showSleepSheet = false })
    val panel = if (showQueue) PlayerPanel.Queue else PlayerPanel.Lyrics

    // Immersive lyrics, as on the phone: after a few idle seconds with lyrics showing, the top
    // bar and the tabs fold away, the controls shrink to a compact bar and the art grows. Clicks
    // restart the countdown until then; afterwards a new track, switching to the queue or a
    // click on the compact bar's title brings everything back.
    var touchTick by remember { mutableIntStateOf(0) }
    var immersive by remember { mutableStateOf(false) }
    val immersiveNow by rememberUpdatedState(immersive)
    // Only real lyrics: "not found" leaves nothing to read, so the controls stay.
    val lyricsReady = lyrics is LyricsState.Ready && lyrics.lyrics != Lyrics.NotFound
    LaunchedEffect(panel, lyricsReady, touchTick, nowPlaying.songId, nothing) {
        immersive = false
        if (panel == PlayerPanel.Lyrics && lyricsReady && !nothing) {
            delay(IMMERSIVE_DELAY_MS)
            immersive = true
        }
    }
    val spatialSize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // 0 = normal art, 1 = the larger immersive art. Read only in the layout phase, so the
    // transition re-places the art and controls without recomposing the whole screen.
    val grow = remember { Animatable(if (immersive) 1f else 0f) }
    LaunchedEffect(immersive) { grow.animateTo(if (immersive) 1f else 0f, spatial) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
            // Observe (never consume) every press, before children handle it.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && !immersiveNow) touchTick++
                    }
                }
            }
            .then(dragModifier)
    ) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth
        if (!nothing) {
            BlurredArtwork(nowPlaying.songId, nowPlaying.albumId, Modifier.fillMaxSize())
            // An even veil, a little heavier behind the lyrics, so text reads over any cover.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to colors.surface.copy(alpha = 0.45f),
                        1f to colors.surface.copy(alpha = 0.7f),
                    )
                )
            )
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = SCREEN_PADDING)) {
            // Top bar ----------------------------------------------------------------------
            AnimatedVisibility(
                visible = !immersive,
                enter = expandVertically(spatialSize) + fadeIn(fade),
                exit = shrinkVertically(spatialSize) + fadeOut(fade),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ExpressiveIconButton(
                        onClick = onCollapse,
                        icon = PlayerIcons.ExpandMore,
                        contentDescription = stringResource(Res.string.collapse),
                        containerColor = glassButton,
                        contentColor = colors.onSurface,
                        modifier = Modifier.size(44.dp),
                    )
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        Text(stringResource(Res.string.now_playing), style = MaterialTheme.typography.titleSmall)
                        sleepTimerLabel(sleepTimer)?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = colors.primary)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    SleepTimerButton(active = sleepTimer != null, onClick = { showSleepSheet = true }, glass = glassButton)
                }
            }

            // Sized from the whole screen, not from what the folding top bar leaves: measuring
            // the area itself would recompose everything on every frame of the fold.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // The art grows with the window but leaves room under it for the controls; with
                // the controls folded away it takes most of that room too.
                val area = screenHeight - SCREEN_PADDING * 2
                val width = screenWidth - 56.dp
                val normal = (area - TOP_BAR_HEIGHT - 250.dp).coerceIn(220.dp, 480.dp).coerceAtMost(width * 0.42f)
                val roomy = (area - 120.dp).coerceIn(220.dp, 600.dp).coerceAtMost(width * 0.45f)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Art and controls ---------------------------------------------------------
                    // Never narrower than the row of transport buttons.
                    Column(Modifier.lerpWidth(maxOf(normal, CONTROLS_WIDTH), maxOf(roomy, CONTROLS_WIDTH)) { grow.value }) {
                        PlayerArtwork(
                            nowPlaying = nowPlaying,
                            isPlaying = state.isPlaying,
                            position = position,
                            durationMs = state.durationMs,
                            onTap = onTogglePlay,
                            onSeek = onSeek,
                            modifier = Modifier.align(Alignment.CenterHorizontally).scaledSquare(normal, roomy) { grow.value },
                        )
                        Spacer(Modifier.height(20.dp))
                        // Full controls or the compact bar; the size change springs, like the phone's.
                        AnimatedContent(
                            targetState = immersive,
                            transitionSpec = {
                                (fadeIn(fade) + slideInVertically(spatialOffset) { it / 3 })
                                    .togetherWith(fadeOut(fade) + slideOutVertically(spatialOffset) { it / 3 })
                                    .using(SizeTransform(clip = false) { _, _ -> spatialSize })
                            },
                            label = "immersiveControls",
                        ) { compact ->
                            if (compact) {
                                CompactControls(
                                    nowPlaying = nowPlaying,
                                    isPlaying = state.isPlaying,
                                    position = position,
                                    durationMs = state.durationMs,
                                    onPrevious = onPrevious,
                                    onTogglePlay = onTogglePlay,
                                    onNext = onNext,
                                    glassButton = glassButton,
                                    onShowControls = { touchTick++ },
                                )
                            } else {
                                Column(Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TrackTitle(nowPlaying, onOpenArtist, Modifier.weight(1f))
                                        FavoriteButton(isFavorite = isFavorite, onClick = onToggleFavorite, containerColor = glassButton)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    WavySeekBar(
                                        position = position,
                                        durationMs = state.durationMs,
                                        isPlaying = state.isPlaying,
                                        onSeek = onSeek,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        ExpressiveToggleButton(
                                            checked = state.shuffle,
                                            onClick = onToggleShuffle,
                                            icon = PlayerIcons.Shuffle,
                                            contentDescription = stringResource(Res.string.shuffle),
                                            iconSize = 20.dp,
                                            uncheckedColor = Color.Transparent,
                                            modifier = Modifier.size(44.dp),
                                        )
                                        ExpressiveIconButton(
                                            onClick = onPrevious,
                                            icon = PlayerIcons.Previous,
                                            contentDescription = stringResource(Res.string.previous),
                                            containerColor = glassButton,
                                            contentColor = colors.onSurface,
                                            iconSize = 26.dp,
                                            modifier = Modifier.size(56.dp),
                                        )
                                        PlayPauseButton(
                                            isPlaying = state.isPlaying,
                                            onClick = onTogglePlay,
                                            playLabel = stringResource(Res.string.play),
                                            pauseLabel = stringResource(Res.string.pause),
                                            iconSize = 32.dp,
                                            modifier = Modifier.size(width = 88.dp, height = 64.dp),
                                        )
                                        ExpressiveIconButton(
                                            onClick = onNext,
                                            icon = PlayerIcons.Next,
                                            contentDescription = stringResource(Res.string.next),
                                            containerColor = glassButton,
                                            contentColor = colors.onSurface,
                                            iconSize = 26.dp,
                                            modifier = Modifier.size(56.dp),
                                        )
                                        ExpressiveToggleButton(
                                            checked = state.repeatMode != Player.REPEAT_MODE_OFF,
                                            onClick = onCycleRepeat,
                                            icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) PlayerIcons.RepeatOne else PlayerIcons.Repeat,
                                            contentDescription = stringResource(Res.string.repeat),
                                            iconSize = 20.dp,
                                            uncheckedColor = Color.Transparent,
                                            modifier = Modifier.size(44.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        VolumeControl(volume, muted, onVolume, onToggleMute, sliderWidth = 160.dp)
                                    }
                                }
                            }
                        }
                    }

                    // Lyrics or queue ------------------------------------------------------------
                    Column(Modifier.weight(1f, fill = false).widthIn(max = 640.dp).fillMaxHeight()) {
                        AnimatedVisibility(
                            visible = !immersive,
                            enter = expandVertically(spatialSize) + fadeIn(fade),
                            exit = shrinkVertically(spatialSize) + fadeOut(fade),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                            ) {
                                PanelTab(
                                    selected = panel == PlayerPanel.Lyrics,
                                    icon = PlayerIcons.Lyrics,
                                    label = stringResource(Res.string.lyrics),
                                    onClick = { if (panel != PlayerPanel.Lyrics) onToggleLyrics() },
                                )
                                PanelTab(
                                    selected = panel == PlayerPanel.Queue,
                                    icon = PlayerIcons.Queue,
                                    label = stringResource(Res.string.queue),
                                    onClick = { if (panel != PlayerPanel.Queue) onToggleQueue() },
                                )
                            }
                        }
                        AnimatedContent(
                            targetState = panel,
                            transitionSpec = {
                                (fadeIn(fade) + scaleIn(spatial, initialScale = 0.92f))
                                    .togetherWith(fadeOut(fade) + scaleOut(spatial, targetScale = 0.92f))
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
    }
}

/** Title and artist; a new track slides in from the side the way the art changes. */
@Composable
private fun TrackTitle(nowPlaying: NowPlaying, onOpenArtist: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = nowPlaying,
        contentKey = { it.songId },
        transitionSpec = {
            (fadeIn(fade) + slideInHorizontally(spatialOffset) { it / 4 })
                .togetherWith(fadeOut(fade) + slideOutHorizontally(spatialOffset) { -it / 4 })
        },
        label = "trackTitle",
        modifier = modifier,
    ) { np ->
        Column {
            Text(
                text = np.title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val artist = np.artist
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
    }
}

/** A pill with an icon and a label, filled when selected; switches the right-hand panel. */
@Composable
private fun PanelTab(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        if (selected) colors.secondaryContainer else colors.surfaceContainerHighest.copy(alpha = 0.45f), effects, label = "tabBg"
    )
    val content by animateColorAsState(if (selected) colors.onSecondaryContainer else colors.onSurface, effects, label = "tabFg")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

private val WIDE = 880.dp
private val CONTROLS_WIDTH = 340.dp
/** As on the phone: idle time with lyrics showing before the controls fold away. */
private const val IMMERSIVE_DELAY_MS = 5_000L
private val SCREEN_PADDING = 20.dp
/** The top bar's row: its 44dp collapse button is the tallest thing in it. */
private val TOP_BAR_HEIGHT = 44.dp

/** A width between [from] and [to], read in the layout phase so animating it never recomposes. */
private fun Modifier.lerpWidth(from: Dp, to: Dp, fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val w = lerp(from, to, fraction()).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(w, placeable.height) { placeable.place(0, 0) }
}

/**
 * A square between [from] and [to]. The content is measured once at the larger size and only
 * scaled in its layer, so the art never re-measures or re-decodes while it grows.
 */
private fun Modifier.scaledSquare(from: Dp, to: Dp, fraction: () -> Float): Modifier = layout { measurable, _ ->
    val full = maxOf(from, to).roundToPx()
    val side = lerp(from, to, fraction()).roundToPx()
    val placeable = measurable.measure(Constraints.fixed(full, full))
    layout(side, side) {
        placeable.placeWithLayer(0, 0) {
            val s = side.toFloat() / full
            scaleX = s
            scaleY = s
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}
