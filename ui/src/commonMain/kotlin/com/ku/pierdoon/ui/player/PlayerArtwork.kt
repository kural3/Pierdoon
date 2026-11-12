package com.ku.pierdoon.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.ku.pierdoon.PositionSnapshot
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.playback.TapeMotion
import com.ku.pierdoon.playback.TapeSides
import com.ku.pierdoon.playback.cassetteRewindMs
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayPauseButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.morphClip
import com.ku.pierdoon.ui.components.rememberSmoothProgress
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.ui.resources.Res
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import com.ku.pierdoon.ui.library.CoverFlowers

/** Immersive-lyrics bar: thin progress, track info and small transport buttons. */
@Composable
fun CompactControls(
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
                    text = nowPlaying.artist.ifEmpty { stringResource(Res.string.unknown_artist) },
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
                contentDescription = stringResource(Res.string.previous),
                containerColor = glassButton,
                contentColor = colors.onSurface,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(6.dp))
            PlayPauseButton(
                isPlaying = isPlaying,
                onClick = onTogglePlay,
                playLabel = stringResource(Res.string.play),
                pauseLabel = stringResource(Res.string.pause),
                iconSize = 26.dp,
                modifier = Modifier.size(width = 64.dp, height = 52.dp),
            )
            Spacer(Modifier.width(6.dp))
            ExpressiveIconButton(
                onClick = onNext,
                icon = PlayerIcons.Next,
                contentDescription = stringResource(Res.string.next),
                containerColor = glassButton,
                contentColor = colors.onSurface,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

/**
 * Album art that relaxes into a rounded square while playing and curls into a scalloped
 * cookie, or with [flowers] a different flower each time (and shrinks a little), when paused. One-shot springs, no idle animation.
 *
 * Swipe sideways for the next or previous track, where [onNext] and [onPrevious] are given.
 *
 * Tap toggles playback. Double-tap the left or right third to skip 10 s back or forward,
 * YouTube Music style: further taps on that side while the hint is up add 10 s each.
 * The middle third never waits for a second tap, so play/pause there stays instant.
 */
@Composable
fun PlayerArtwork(
    nowPlaying: NowPlaying,
    isPlaying: Boolean,
    position: () -> PositionSnapshot,
    durationMs: Long,
    onTap: () -> Unit,
    onSeek: (Long) -> Unit,
    /** Cassette mode: where the track is on the tape; a cassette takes the cover's place. */
    tape: TapeSides?,
    modifier: Modifier = Modifier,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    /** A new flower on every pause instead of the one cookie. */
    flowers: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val swipe = rememberTrackSwipeState()
    val source = remember { MutableInteractionSource() }
    val press = animatePressAsState(source, 0.94f)
    val slowSpatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val morph = animateFloatAsState(if (isPlaying) 0f else 1f, slowSpatial, label = "artMorph")
    // Each pause curls the cover into a different flower; picked while it is still square, so the swap never shows.
    var flower by remember { mutableStateOf(if (flowers) CoverFlowers.random() else MaterialShapes.Cookie12Sided) }
    LaunchedEffect(isPlaying) { if (flowers && !isPlaying && morph.value < 0.05f) flower = CoverFlowers.random() }
    val artMorph = remember(flower) { Morph(MaterialShapes.Square, flower) }
    val tapeMotion by TapeMotion.current.collectAsState()
    // The player is paused through a rewind or a turn, but the tape is still moving.
    val restScale = animateFloatAsState(if (isPlaying || tapeMotion != TapeMotion.Still) 1f else 0.86f, slowSpatial, label = "artScale")
    val scale = remember { derivedStateOf { press.value * restScale.value } }

    // Current skip streak: side (-1 back, 1 forward, 0 none), seconds so far, and where it got to.
    // The target is tracked here because the player's reported position lags rapid taps.
    var seekSide by remember { mutableIntStateOf(0) }
    var seekSeconds by remember { mutableIntStateOf(0) }
    var seekTarget by remember { mutableLongStateOf(0L) }
    var seekTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekTick) {
        if (seekTick == 0) return@LaunchedEffect
        delay(SEEK_STREAK_MS)
        seekSide = 0
        seekSeconds = 0
    }
    LaunchedEffect(nowPlaying.songId) { seekSide = 0; seekSeconds = 0 }

    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentPosition by rememberUpdatedState(position)
    val currentDuration by rememberUpdatedState(durationMs)

    fun toggle() {
        haptics.performHapticFeedback(
            if (currentIsPlaying) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
        )
        currentOnTap()
    }

    fun skip(side: Int) {
        val base = if (seekSide == side) seekTarget else currentPosition().estimate()
        val end = currentDuration.takeIf { it > 0 } ?: Long.MAX_VALUE
        seekTarget = (base + side * SEEK_STEP_MS).coerceIn(0L, end)
        seekSeconds = if (seekSide == side) seekSeconds + SEEK_STEP_S else SEEK_STEP_S
        seekSide = side
        seekTick++
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        currentOnSeek(seekTarget)
    }

    val backLabel = stringResource(Res.string.seek_back)
    val forwardLabel = stringResource(Res.string.seek_forward)
    val swiping = if (onNext != null && onPrevious != null) Modifier.trackSwipe(swipe, onNext, onPrevious) else Modifier
    Box(modifier.then(swiping), contentAlignment = Alignment.Center) {
        val gestures = Modifier
                .semantics {
                    role = Role.Button
                    onClick { toggle(); true }
                    customActions = listOf(
                        CustomAccessibilityAction(backLabel) { skip(-1); true },
                        CustomAccessibilityAction(forwardLabel) { skip(1); true },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val pressed = PressInteraction.Press(down.position)
                        source.tryEmit(pressed)
                        val up = waitForUpOrCancellation()
                        source.tryEmit(if (up != null) PressInteraction.Release(pressed) else PressInteraction.Cancel(pressed))
                        if (up == null) return@awaitEachGesture

                        val third = size.width / 3f
                        val side = when {
                            down.position.x < third -> -1
                            down.position.x > size.width - third -> 1
                            else -> 0
                        }
                        when {
                            side == 0 -> toggle()
                            // Mid-streak: every tap on a side counts at once.
                            seekSide != 0 -> skip(side)
                            else -> {
                                val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                    awaitFirstDown()
                                }
                                if (second == null) toggle() else {
                                    skip(side)
                                    waitForUpOrCancellation()
                                }
                            }
                        }
                    }
                }
        if (tape != null) {
            CassetteDeck(
                title = nowPlaying.title,
                artist = nowPlaying.artist,
                sideB = tape.onSideB,
                isPlaying = isPlaying,
                motion = tapeMotion,
                rewindMs = cassetteRewindMs(tape.sideLengthMs),
                sideProgress = {
                    if (tape.sideLengthMs <= 0) 0f else (tape.elapsedMs + position().estimate()).toFloat() / tape.sideLengthMs
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(CASSETTE_SHAPE).trackSwipeOffset(swipe).scaleBy(scale).then(gestures),
            ) { cover ->
                Artwork(nowPlaying.songId, nowPlaying.albumId, placeholderIconSize = 24.dp, modifier = cover)
            }
        } else {
            Artwork(
                songId = nowPlaying.songId,
                albumId = nowPlaying.albumId,
                large = true,
                placeholderIconSize = 96.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .scaleBy(scale)
                    .trackSwipeOffset(swipe)
                    .morphClip(artMorph, morph)
                    .then(gestures),
            )
        }
        SeekHint(side = -1, activeSide = seekSide, seconds = seekSeconds, Modifier.align(Alignment.CenterStart))
        SeekHint(side = 1, activeSide = seekSide, seconds = seekSeconds, Modifier.align(Alignment.CenterEnd))
    }
}

/** "« 20 с" pill over one side of the art while a skip streak on that side is running. */
@Composable
private fun SeekHint(side: Int, activeSide: Int, seconds: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedVisibility(
        visible = activeSide == side,
        enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.7f),
        exit = fadeOut(effects) + scaleOut(spatial, targetScale = 0.85f),
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceContainerHighest.copy(alpha = 0.85f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            val icon = if (side < 0) PlayerIcons.FastRewind else PlayerIcons.FastForward
            if (side < 0) Icon(icon, null, tint = colors.onSurface, modifier = Modifier.size(22.dp))
            Text(
                text = stringResource(Res.string.seek_seconds, seconds),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            if (side > 0) Icon(icon, null, tint = colors.onSurface, modifier = Modifier.size(22.dp))
        }
    }
}

private const val CASSETTE_SHAPE = 1.58f
private const val SEEK_STEP_S = 10
private const val SEEK_STEP_MS = SEEK_STEP_S * 1000L
/** How long after the last skip further single taps keep adding to it. */
private const val SEEK_STREAK_MS = 800L
