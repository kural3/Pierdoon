package com.ku.pierdoon.ui.player

import com.ku.pierdoon.ui.components.MenuAction
import com.ku.pierdoon.ui.components.AppMenu
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.displayCutoutPadding
import com.ku.pierdoon.playback.SleepTimer
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.ku.pierdoon.playback.QueueState
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.BlurredArtwork
import com.ku.pierdoon.ui.theme.LocalUiPrefs
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.ExpressiveToggleButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.morphClip
import com.ku.pierdoon.ui.components.scaleBy
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What fills the space above the controls. */
private enum class PlayerPanel { Art, Lyrics, Queue }

private val ArtworkMorph by lazy { Morph(MaterialShapes.Square, MaterialShapes.Cookie12Sided) }

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
    modifier: Modifier = Modifier,
    /** Swiping the art back: the track before, where the button may restart this one instead. */
    onPreviousTrack: () -> Unit = onPrevious,
    /** The expanding sheet owns the vertical drag (swipe down shrinks the player back). */
    dragModifier: Modifier = Modifier,
    /** The phone and the paired computer, to switch the player between; null without a computer. */
    devices: PlayerDevices? = null,
) {
    val colors = MaterialTheme.colorScheme
    // Nothing style: flat black page, no frosted art, no immersive lyrics.
    val nothing = LocalUiPrefs.current.nothing

    // Matte-glass tints over the frosted art: translucent containers pick up its colour.
    val glassButton = if (nothing) colors.surfaceContainerHigh else colors.surfaceContainerHighest.copy(alpha = 0.45f)
    val sleepTimer by SleepTimer.state.collectAsStateWithLifecycle()
    var showSleepSheet by remember { mutableStateOf(false) }
    if (showSleepSheet) SleepTimerSheet(sleepTimer, onDismiss = { showSleepSheet = false })

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

        val spatialSize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
        val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val spatialF = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

        val header: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ExpressiveIconButton(
                    onClick = onCollapse,
                    icon = PlayerIcons.ExpandMore,
                    contentDescription = stringResource(R.string.collapse),
                    containerColor = glassButton,
                    contentColor = colors.onSurface,
                    modifier = Modifier.size(48.dp),
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(
                            when {
                                showQueue -> R.string.queue
                                showLyrics -> R.string.lyrics
                                else -> R.string.now_playing
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    if (devices != null) {
                        DeviceSwitch(devices, glassButton)
                    } else {
                        sleepTimerLabel(sleepTimer)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                // The sleep timer stops the phone's player; it has nothing to do with the computer.
                if (devices?.onPc != true) {
                    SleepTimerButton(active = sleepTimer != null, onClick = { showSleepSheet = true }, glass = glassButton)
                    Spacer(Modifier.width(8.dp))
                }
                FavoriteButton(
                    isFavorite = isFavorite,
                    onClick = onToggleFavorite,
                    containerColor = glassButton,
                )
            }
        }

        val panel: @Composable (Modifier) -> Unit = { panelModifier ->
            AnimatedContent(
                targetState = when {
                    showQueue -> PlayerPanel.Queue
                    showLyrics -> PlayerPanel.Lyrics
                    else -> PlayerPanel.Art
                },
                transitionSpec = {
                    (fadeIn(fade) + scaleIn(spatialF, initialScale = 0.92f))
                        .togetherWith(fadeOut(fade) + scaleOut(spatialF, targetScale = 0.92f))
                },
                label = "artOrLyrics",
                modifier = panelModifier,
            ) { p ->
                when (p) {
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
                                onNext = onNext,
                                onPrevious = onPreviousTrack,
                                modifier = Modifier
                                    .widthIn(max = minOf(maxWidth, maxHeight))
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }
            }
        }

        // Title, seek bar, transport and the row of toggles. [short] squeezes them into a
        // landscape phone's height.
        val controls: @Composable (short: Boolean) -> Unit = { short ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(if (short) 4.dp else 12.dp))
                Column(Modifier.fillMaxWidth()) {
                    key(nowPlaying.songId) {
                        Text(
                            text = nowPlaying.title,
                            style = if (short) MaterialTheme.typography.headlineSmallEmphasized
                            else MaterialTheme.typography.headlineMediumEmphasized,
                            color = colors.onSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = 2),
                        )
                    }
                    val artist = nowPlaying.artist
                    Text(
                        text = artist.ifEmpty { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            // Pulled back by its own padding so the text still lines up with the title.
                            .offset(x = (-6).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                enabled = artist.isNotEmpty(),
                                onClickLabel = stringResource(R.string.open_artist),
                            ) { onOpenArtist(artist) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(Modifier.height(if (short) 8.dp else 16.dp))
                WavySeekBar(
                    position = position,
                    durationMs = state.durationMs,
                    isPlaying = state.isPlaying,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(if (short) 8.dp else 16.dp))
                PlayerControls(
                    isPlaying = state.isPlaying,
                    onPrevious = onPrevious,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    sideColor = glassButton,
                    // The outer height wins over the group's own.
                    modifier = Modifier.fillMaxWidth().then(if (short) Modifier.height(68.dp) else Modifier),
                )
                Spacer(Modifier.height(if (short) 12.dp else 20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val toggleSize = if (short) Modifier.size(width = 64.dp, height = 44.dp) else Modifier.size(width = 72.dp, height = 56.dp)
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
                        checked = showQueue,
                        onClick = onToggleQueue,
                        icon = PlayerIcons.Queue,
                        contentDescription = stringResource(R.string.queue),
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
                Spacer(Modifier.height(if (short) 0.dp else 8.dp))
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            if (maxWidth > maxHeight) {
                val short = maxHeight < SHORT_HEIGHT
                // Landscape: the art (or lyrics, or the queue) on the left, everything else on the right.
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    panel(Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(32.dp))
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        header()
                        controls(short)
                    }
                }
                return@BoxWithConstraints
            }
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(
                    visible = !immersive,
                    enter = expandVertically(spatialSize) + fadeIn(fade),
                    exit = shrinkVertically(spatialSize) + fadeOut(fade),
                ) {
                    header()
                }

                panel(Modifier.weight(1f).fillMaxWidth())

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
                        controls(false)
                    }
                }
            }
        }
    }
}

private const val IMMERSIVE_DELAY_MS = 5_000L

/** Below this a landscape player trims its spacing and buttons. */
private val SHORT_HEIGHT = 420.dp

/** Where the player plays: the phone itself or the paired computer. */
@Immutable
class PlayerDevices(
    val pcName: String,
    /** The player shows the computer. */
    val onPc: Boolean,
    /** What each side has on, to tell them apart in the menu; null when it has nothing to play. */
    val phoneTrack: String?,
    val pcTrack: String?,
    val pcOnline: Boolean,
    val onSwitch: (toPc: Boolean) -> Unit,
    val onOpenComputer: () -> Unit,
)

/** Under the header: the device playing, as a pill that opens the choice between the two. */
@Composable
private fun DeviceSwitch(devices: PlayerDevices, glass: Color) {
    var menu by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (menu) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "deviceArrow")
    val colors = MaterialTheme.colorScheme
    val phoneName = stringResource(R.string.player_device_phone)
    val nothing = stringResource(R.string.player_device_idle)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(CircleShape)
                .background(glass)
                .clickable(onClickLabel = stringResource(R.string.player_device_switch)) { menu = true }
                .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            AnimatedContent(
                targetState = devices.onPc,
                transitionSpec = {
                    // The computer comes from below, the phone from above.
                    val dir = if (targetState) 1 else -1
                    (fadeIn(fadeInSpec) + slideInVertically(slide) { it * dir })
                        .togetherWith(fadeOut(fadeOutSpec) + slideOutVertically(slide) { -it * dir })
                        .using(SizeTransform(clip = false))
                },
                label = "device",
            ) { onPc ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (onPc) PlayerIcons.Desktop else PlayerIcons.Smartphone,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (onPc) devices.pcName else phoneName,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                }
            }
            Icon(
                PlayerIcons.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
            )
        }
        AppMenu(
            expanded = menu,
            onDismiss = { menu = false },
            groups = listOf(
                listOf(
                    MenuAction(
                        label = phoneName,
                        icon = PlayerIcons.Smartphone,
                        supporting = devices.phoneTrack ?: nothing,
                        selected = !devices.onPc,
                        enabled = devices.onPc && devices.phoneTrack != null,
                    ) { devices.onSwitch(false) },
                    MenuAction(
                        label = devices.pcName,
                        icon = PlayerIcons.Desktop,
                        supporting = when {
                            !devices.pcOnline -> stringResource(R.string.player_device_offline)
                            else -> devices.pcTrack ?: nothing
                        },
                        selected = devices.onPc,
                        enabled = !devices.onPc && devices.pcTrack != null,
                    ) { devices.onSwitch(true) },
                ),
                listOf(
                    MenuAction(stringResource(R.string.pc_open), PlayerIcons.Library, onClick = devices.onOpenComputer),
                ),
            ),
        )
    }
}

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
 *
 * Tap toggles playback. Double-tap the left or right third to skip 10 s back or forward,
 * YouTube Music style: further taps on that side while the hint is up add 10 s each.
 * The middle third never waits for a second tap, so play/pause there stays instant.
 * Swipe sideways for the next or previous track.
 */
@Composable
private fun PlayerArtwork(
    nowPlaying: NowPlaying,
    isPlaying: Boolean,
    position: () -> PositionSnapshot,
    durationMs: Long,
    onTap: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val swipe = rememberTrackSwipeState()
    val source = remember { MutableInteractionSource() }
    val press = animatePressAsState(source, 0.94f)
    val slowSpatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val morph = animateFloatAsState(if (isPlaying) 0f else 1f, slowSpatial, label = "artMorph")
    val restScale = animateFloatAsState(if (isPlaying) 1f else 0.86f, slowSpatial, label = "artScale")
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

    val backLabel = stringResource(R.string.seek_back)
    val forwardLabel = stringResource(R.string.seek_forward)
    Box(modifier.trackSwipe(swipe, onNext, onPrevious), contentAlignment = Alignment.Center) {
        Artwork(
            songId = nowPlaying.songId,
            albumId = nowPlaying.albumId,
            large = true,
            placeholderIconSize = 96.dp,
            modifier = Modifier
                .fillMaxSize()
                .trackSwipeOffset(swipe)
                .scaleBy(scale)
                .morphClip(ArtworkMorph, morph)
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
                },
        )
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
                text = stringResource(R.string.seek_seconds, seconds),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            if (side > 0) Icon(icon, null, tint = colors.onSurface, modifier = Modifier.size(22.dp))
        }
    }
}

private const val SEEK_STEP_S = 10
private const val SEEK_STEP_MS = SEEK_STEP_S * 1000L
/** How long after the last skip further single taps keep adding to it. */
private const val SEEK_STREAK_MS = 800L
