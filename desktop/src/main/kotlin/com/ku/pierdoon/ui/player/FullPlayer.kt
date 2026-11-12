package com.ku.pierdoon.ui.player

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.playback.Player
import com.ku.pierdoon.LyricsState
import com.ku.pierdoon.PositionSnapshot
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.playback.PlayerState
import com.ku.pierdoon.playback.QueueState
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.BlurredArtwork
import com.ku.pierdoon.ui.theme.LocalUiPrefs
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons

/** What fills the space above the controls. */
internal enum class PlayerPanel { Art, Lyrics, Queue }

@Composable
fun FullPlayer(
    nowPlaying: NowPlaying,
    state: PlayerState,
    position: () -> PositionSnapshot,
    lyrics: LyricsState,
    showLyrics: Boolean,
    queue: QueueState,
    showQueue: Boolean,
    isFavorite: Boolean,
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
    onOpenArtist: (String) -> Unit,
    onCollapse: () -> Unit,
    onOpenDelicious: () -> Unit,
    modifier: Modifier = Modifier,
    /** The expanding sheet owns the vertical drag (swipe down shrinks the player back). */
    dragModifier: Modifier = Modifier,
    /** The radio playing (beta), or null. */
    radio: PlayerRadio? = null,
) {
    val colors = MaterialTheme.colorScheme
    // Nothing style: flat black page, no frosted art, no immersive lyrics.
    val nothing = LocalUiPrefs.current.nothing
    // No tape on the radio: an endless queue has no sides.
    val cassetteMode = SettingsStore.settings.collectAsState().value.cassetteMode && radio == null

    // Matte-glass tints over the frosted art: translucent containers pick up its colour.
    val glassButton = if (nothing) colors.surfaceContainerHigh else colors.surfaceContainerHighest.copy(alpha = 0.45f)

    // Immersive lyrics: after a few idle seconds with lyrics open, the chrome steps aside and
    // the controls fold into a compact bar so the lyrics get the screen. Before that, touches
    // restart the countdown; once immersive, touches (e.g. tapping a line to seek) keep it, and
    // only a new track, closing the lyrics or tapping the track title brings the controls back.
    // Idle = one pending delay, no polling.
    var touchTick by remember { mutableIntStateOf(0) }
    var immersive by remember { mutableStateOf(false) }
    val immersiveNow by rememberUpdatedState(immersive)
    val lyricsReady = lyrics is LyricsState.Ready
    LaunchedEffect(showLyrics, lyricsReady, touchTick, nowPlaying.songId, nothing) {
        immersive = false
        if (showLyrics && lyricsReady && !nothing) {
            delay(IMMERSIVE_DELAY_MS)
            immersive = true
        }
    }

    Box(
        modifier = modifier
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
            .then(dragModifier),
    ) {
        if (!nothing) {
            BlurredArtwork(nowPlaying.songId, nowPlaying.albumId, Modifier.fillMaxSize())
            // Frosted scrim: keeps text readable while the art's colour glows through.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to colors.surface.copy(alpha = 0.30f),
                            0.5f to colors.surface.copy(alpha = 0.55f),
                            1f to colors.surface.copy(alpha = 0.85f),
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val spatialSize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
            val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
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
                    modifier = Modifier.size(48.dp),
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    // The radio's name takes "Now playing"'s place.
                    if (radio != null && !showQueue && !showLyrics) {
                        RadioTitle(radio)
                    } else {
                        Text(
                            text = stringResource(
                                when {
                                    showQueue -> Res.string.queue
                                    showLyrics -> Res.string.lyrics
                                    else -> Res.string.now_playing
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                SpeedButton(glassButton)
                Spacer(Modifier.width(8.dp))
                ExpressiveIconButton(
                    onClick = onOpenDelicious,
                    icon = PlayerIcons.Sparkle,
                    contentDescription = stringResource(Res.string.delicious_open),
                    containerColor = glassButton,
                    contentColor = colors.onSurface,
                    modifier = Modifier.size(48.dp),
                )
            }
            }

            val spatialF = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = when {
                    showQueue -> PlayerPanel.Queue
                    showLyrics -> PlayerPanel.Lyrics
                    else -> PlayerPanel.Art
                },
                transitionSpec = {
                    (fadeIn(effects) + scaleIn(spatialF, initialScale = 0.92f))
                        .togetherWith(fadeOut(effects) + scaleOut(spatialF, targetScale = 0.92f))
                },
                label = "artOrLyrics",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { panel ->
                when (panel) {
                PlayerPanel.Lyrics -> {
                    LyricsPanel(
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
                PlayerPanel.Queue -> {
                    QueuePanel(
                        queue = queue,
                        onPlay = onPlayQueueEntry,
                        onRemove = onRemoveQueueEntry,
                        onMove = onMoveQueueEntry,
                        onLongPress = onSongLongPress,
                        tapeMode = cassetteMode,
                        // The column's side padding is for the art; rows bring their own inset.
                        modifier = Modifier.horizontalBleed(12.dp),
                    )
                }
                PlayerPanel.Art -> {
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PlayerArtwork(
                            nowPlaying = nowPlaying,
                            isPlaying = state.isPlaying,
                            position = position,
                            durationMs = state.durationMs,
                            onTap = onTogglePlay,
                            onSeek = onSeek,
                            tape = queue.tape.takeIf { cassetteMode },
                            flowers = true,
                            modifier = Modifier
                                .widthIn(max = minOf(maxWidth, maxHeight))
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
                    }
                }
                }
            }

            // Height change animates on a spring, so the lyrics above grow/shrink smoothly.
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
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        key(nowPlaying.songId) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // A radio's track the library hasn't got.
                                if (radio?.fresh == true) {
                                    NewBadge()
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    text = nowPlaying.title,
                                    style = MaterialTheme.typography.headlineMediumEmphasized,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false).basicMarquee(iterations = 2),
                                )
                            }
                        }
                        ArtistLink(
                            artist = nowPlaying.artist,
                            style = MaterialTheme.typography.titleMedium,
                            onOpen = onOpenArtist,
                            // Pulled back by its own padding so the text still lines up with the title.
                            modifier = Modifier.offset(x = (-6).dp).clip(RoundedCornerShape(8.dp)),
                            padding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    FavoriteButton(isFavorite = isFavorite, onClick = onToggleFavorite, containerColor = glassButton, size = 56.dp)
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
                    Spacer(Modifier.height(20.dp))
                    PlayerToggles(
                        shuffle = state.shuffle,
                        repeatOn = state.repeatMode != Player.REPEAT_MODE_OFF,
                        repeatOne = state.repeatMode == Player.REPEAT_MODE_ONE,
                        showLyrics = showLyrics,
                        showQueue = showQueue,
                        onShuffle = onToggleShuffle,
                        onRepeat = onCycleRepeat,
                        onLyrics = onToggleLyrics,
                        onQueue = onToggleQueue,
                        radio = radio,
                        glass = glassButton,
                    )
                    Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

private const val IMMERSIVE_DELAY_MS = 5_000L
