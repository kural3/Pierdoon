package com.ku.pierdoon.ui.player

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.ku.pierdoon.ui.components.PlayPauseButton
import com.ku.pierdoon.ui.components.rememberSmoothProgress
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.media3.common.Player
import com.ku.pierdoon.LyricsState
import com.ku.pierdoon.PositionSnapshot
import com.ku.pierdoon.R
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.playback.PlayerState
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.BlurredArtwork
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.ExpressiveToggleButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.morphClip
import com.ku.pierdoon.ui.components.scaleBy
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ArtworkMorph by lazy { Morph(MaterialShapes.Square, MaterialShapes.Cookie12Sided) }

@Composable
fun FullPlayer(
    nowPlaying: NowPlaying,
    state: PlayerState,
    position: () -> PositionSnapshot,
    lyrics: LyricsState,
    showLyrics: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleLyrics: () -> Unit,
    onRetryLyrics: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    /** The expanding sheet owns the vertical drag (swipe down shrinks the player back). */
    dragModifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    // Matte-glass tints over the frosted art: translucent containers pick up its colour.
    val glassButton = colors.surfaceContainerHighest.copy(alpha = 0.45f)

    // Immersive lyrics: after a few idle seconds with lyrics open, the chrome steps aside and
    // the controls fold into a compact bar so the lyrics get the screen. Before that, touches
    // restart the countdown; once immersive, touches (e.g. tapping a line to seek) keep it, and
    // only a new track, closing the lyrics or tapping the track title brings the controls back.
    // Idle = one pending delay, no polling.
    var touchTick by remember { mutableIntStateOf(0) }
    var immersive by remember { mutableStateOf(false) }
    val immersiveNow by rememberUpdatedState(immersive)
    val lyricsReady = lyrics is LyricsState.Ready
    LaunchedEffect(showLyrics, lyricsReady, touchTick, nowPlaying.songId) {
        immersive = false
        if (showLyrics && lyricsReady) {
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
                    contentDescription = stringResource(R.string.collapse),
                    containerColor = glassButton,
                    contentColor = colors.onSurface,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(if (showLyrics) R.string.lyrics else R.string.now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                FavoriteButton(
                    isFavorite = isFavorite,
                    onClick = onToggleFavorite,
                    containerColor = glassButton,
                )
            }
            }

            val spatialF = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = {
                    (fadeIn(effects) + scaleIn(spatialF, initialScale = 0.92f))
                        .togetherWith(fadeOut(effects) + scaleOut(spatialF, targetScale = 0.92f))
                },
                label = "artOrLyrics",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { lyricsShown ->
                if (lyricsShown) {
                    LyricsPanel(
                        state = lyrics,
                        position = position,
                        onSeek = onSeek,
                        onRetry = onRetryLyrics,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PlayerArtwork(
                            nowPlaying = nowPlaying,
                            isPlaying = state.isPlaying,
                            onTap = onTogglePlay,
                            modifier = Modifier
                                .widthIn(max = minOf(maxWidth, maxHeight))
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
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
                    Column(Modifier.fillMaxWidth()) {
                        key(nowPlaying.songId) {
                            Text(
                                text = nowPlaying.title,
                                style = MaterialTheme.typography.headlineMediumEmphasized,
                                color = colors.onSurface,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(iterations = 2),
                            )
                        }
                        Text(
                            text = nowPlaying.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val toggleSize = Modifier.size(width = 72.dp, height = 56.dp)
                        ExpressiveToggleButton(
                            checked = state.shuffle,
                            onClick = onToggleShuffle,
                            icon = PlayerIcons.Shuffle,
                            contentDescription = stringResource(R.string.shuffle),
                            uncheckedColor = glassButton,
                            modifier = toggleSize,
                        )
                        ExpressiveToggleButton(
                            checked = showLyrics,
                            onClick = onToggleLyrics,
                            icon = PlayerIcons.Lyrics,
                            contentDescription = stringResource(R.string.lyrics),
                            uncheckedColor = glassButton,
                            modifier = toggleSize,
                        )
                        ExpressiveToggleButton(
                            checked = state.repeatMode != Player.REPEAT_MODE_OFF,
                            onClick = onCycleRepeat,
                            icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) PlayerIcons.RepeatOne else PlayerIcons.Repeat,
                            contentDescription = stringResource(R.string.repeat),
                            uncheckedColor = glassButton,
                            modifier = toggleSize,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

private const val IMMERSIVE_DELAY_MS = 2_500L

/** Immersive-lyrics bar: thin progress, track info and small transport buttons. */
@Composable
private fun CompactControls(
    nowPlaying: NowPlaying,
    isPlaying: Boolean,
    position: () -> PositionSnapshot,
    durationMs: Long,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    glassButton: Color,
    onShowControls: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var barWidthPx by remember { mutableIntStateOf(0) }
    val progress by rememberSmoothProgress(position, durationMs, barWidthPx)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            color = colors.primary,
            trackColor = colors.onSurface.copy(alpha = 0.12f),
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .onSizeChanged { barWidthPx = it.width },
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onShowControls)
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = nowPlaying.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = nowPlaying.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            ExpressiveIconButton(
                onClick = onPrevious,
                icon = PlayerIcons.Previous,
                contentDescription = stringResource(R.string.previous),
                containerColor = glassButton,
                contentColor = colors.onSurface,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(6.dp))
            PlayPauseButton(
                isPlaying = isPlaying,
                onClick = onTogglePlay,
                playLabel = stringResource(R.string.play),
                pauseLabel = stringResource(R.string.pause),
                iconSize = 26.dp,
                modifier = Modifier.size(width = 64.dp, height = 52.dp),
            )
            Spacer(Modifier.width(6.dp))
            ExpressiveIconButton(
                onClick = onNext,
                icon = PlayerIcons.Next,
                contentDescription = stringResource(R.string.next),
                containerColor = glassButton,
                contentColor = colors.onSurface,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

/** Heart toggle that "pops" (overshoots and settles) when a track is added to favourites. */
@Composable
private fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit, containerColor: Color) {
    val pop = remember { Animatable(1f) }
    val popSpec = remember { spring<Float>(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(isFavorite) {
        if (first) { first = false; return@LaunchedEffect }
        if (isFavorite) {
            pop.snapTo(1.35f)
            pop.animateTo(1f, popSpec)
        }
    }
    val tint by animateColorAsState(
        if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "heart",
    )
    ExpressiveIconButton(
        onClick = onClick,
        icon = if (isFavorite) PlayerIcons.Favorite else PlayerIcons.FavoriteBorder,
        contentDescription = stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites),
        containerColor = containerColor,
        contentColor = tint,
        haptic = if (isFavorite) HapticFeedbackType.ToggleOff else HapticFeedbackType.Confirm,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
    )
}

/**
 * Album art that relaxes into a rounded square while playing and curls into a scalloped
 * cookie (and shrinks a little) when paused. One-shot springs, no idle animation.
 */
@Composable
private fun PlayerArtwork(
    nowPlaying: NowPlaying,
    isPlaying: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val press = animatePressAsState(source, 0.94f)
    val slowSpatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val morph = animateFloatAsState(if (isPlaying) 0f else 1f, slowSpatial, label = "artMorph")
    val restScale = animateFloatAsState(if (isPlaying) 1f else 0.86f, slowSpatial, label = "artScale")
    val scale = remember { derivedStateOf { press.value * restScale.value } }

    Artwork(
        songId = nowPlaying.songId,
        albumId = nowPlaying.albumId,
        large = true,
        placeholderIconSize = 96.dp,
        modifier = modifier
            .scaleBy(scale)
            .morphClip(ArtworkMorph, morph)
            .clickable(interactionSource = source, indication = null) {
                haptics.performHapticFeedback(
                    if (isPlaying) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                )
                onTap()
            },
    )
}
