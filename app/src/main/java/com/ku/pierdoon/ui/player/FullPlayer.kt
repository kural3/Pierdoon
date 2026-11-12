package com.ku.pierdoon.ui.player

import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import com.ku.pierdoon.ui.library.MenuEntry
import com.ku.pierdoon.ui.library.EntryMenu
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ku.pierdoon.LyricsState
import com.ku.pierdoon.PositionSnapshot
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.playback.PlayerState
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.playback.QueueState
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.BlurredArtwork
import com.ku.pierdoon.ui.theme.LocalUiPrefs
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons

/** What fills the space above the controls. */
private enum class PlayerPanel { Art, Lyrics, Queue }

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
    /** False where the artist line leads to the same place whoever is chosen. */
    pickArtist: Boolean = true,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    /** Swiping the art back: the track before, where the button may restart this one instead. */
    onPreviousTrack: () -> Unit = onPrevious,
    /** The expanding sheet owns the vertical drag (swipe down shrinks the player back). */
    dragModifier: Modifier = Modifier,
    /** The phone and the paired computer, to switch the player between; null without a computer. */
    devices: PlayerDevices? = null,
    /** The radio playing on the phone (beta), or null. */
    radio: PlayerRadio? = null,
) {
    val colors = MaterialTheme.colorScheme
    // Nothing style: flat black page, no frosted art, no immersive lyrics.
    val nothing = LocalUiPrefs.current.nothing
    // No tape on the radio: an endless queue has no sides (the service leaves the deck out too).
    val cassetteMode = SettingsStore.settings.collectAsStateWithLifecycle().value.cassetteMode && radio == null

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
                    contentDescription = stringResource(Res.string.collapse),
                    containerColor = glassButton,
                    contentColor = colors.onSurface,
                    modifier = Modifier.size(48.dp),
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    // One line on top, whatever plays: the radio's name takes "Now playing"'s place.
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
                    SpeedButton(glassButton)
                    Spacer(Modifier.width(8.dp))
                    SleepTimerButton(active = sleepTimer != null, onClick = { showSleepSheet = true }, glass = glassButton)
                } else {
                    // Keeps the label centred against the collapse button.
                    Spacer(Modifier.width(48.dp))
                }
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
                                onNext = onNext,
                                onPrevious = onPreviousTrack,
                                tape = queue.tape.takeIf { cassetteMode },
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    key(nowPlaying.songId) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // A radio's track the library hasn't got: marked where the eye already is.
                            if (radio?.fresh == true) {
                                NewBadge()
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                text = nowPlaying.title,
                                style = if (short) MaterialTheme.typography.headlineSmallEmphasized
                                else MaterialTheme.typography.headlineMediumEmphasized,
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
                        pick = pickArtist,
                    )
                }
                Spacer(Modifier.width(12.dp))
                FavoriteButton(
                    isFavorite = isFavorite,
                    onClick = onToggleFavorite,
                    containerColor = glassButton,
                    size = if (short) 48.dp else 56.dp,
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
                    height = if (short) 52.dp else 64.dp,
                )
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
    val phoneName = stringResource(Res.string.player_device_phone)
    val nothing = stringResource(Res.string.player_device_idle)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(CircleShape)
                .background(glass)
                .clickable(onClickLabel = stringResource(Res.string.player_device_switch)) { menu = true }
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
        EntryMenu(
            expanded = menu,
            onDismiss = { menu = false },
            groups = listOf(
                listOf(
                    MenuEntry(
                        label = phoneName,
                        icon = PlayerIcons.Smartphone,
                        supporting = devices.phoneTrack ?: nothing,
                        selected = !devices.onPc,
                        enabled = devices.onPc && devices.phoneTrack != null,
                    ) { devices.onSwitch(false) },
                    MenuEntry(
                        label = devices.pcName,
                        icon = PlayerIcons.Desktop,
                        supporting = when {
                            !devices.pcOnline -> stringResource(Res.string.player_device_offline)
                            else -> devices.pcTrack ?: nothing
                        },
                        selected = devices.onPc,
                        enabled = !devices.onPc && devices.pcTrack != null,
                    ) { devices.onSwitch(true) },
                ),
                listOf(
                    MenuEntry(stringResource(Res.string.pc_open), PlayerIcons.Library, onClick = devices.onOpenComputer),
                ),
            ),
        )
    }
}
