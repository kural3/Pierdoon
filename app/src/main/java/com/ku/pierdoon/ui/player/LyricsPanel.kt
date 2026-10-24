package com.ku.pierdoon.ui.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.platform.LocalWindowInfo
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.LyricsState
import com.ku.pierdoon.PositionSnapshot
import com.ku.pierdoon.R
import com.ku.pierdoon.data.lyrics.Lyrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val USER_SCROLL_HOLD_MS = 3_000L

/** Wait for a resize animation to end before re-centring (avoids chasing every frame). */
private const val RESIZE_SETTLE_MS = 120L

/** Scrolls so that line [index] sits exactly at the vertical centre of the viewport. */
private suspend fun LazyListState.centerOn(index: Int, spec: AnimationSpec<Float>) {
    fun visible() = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (visible() == null) scrollToItem(index) // far away (e.g. after a seek): jump near it first
    val item = visible() ?: return
    val info = layoutInfo
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    animateScrollBy(item.offset + item.size / 2f - viewportCenter, spec)
}

/** Lines change slightly ahead of the beat, which reads as "in sync" to the ear. */
private const val LEAD_MS = 150L

@Composable
fun LyricsPanel(
    state: LyricsState,
    position: () -> PositionSnapshot,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { fadeIn(effects).togetherWith(fadeOut(effects)) },
        label = "lyricsState",
        modifier = modifier,
    ) { s ->
        when (s) {
            LyricsState.Idle, LyricsState.Loading -> Centered { LoadingIndicator(Modifier.size(56.dp)) }
            LyricsState.Error -> Centered {
                Message(stringResource(R.string.lyrics_error))
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            is LyricsState.Ready -> when (val l = s.lyrics) {
                is Lyrics.Synced -> SyncedLyrics(l, position, onSeek)
                is Lyrics.Plain -> PlainLyrics(l)
                Lyrics.Instrumental -> Centered { Message(stringResource(R.string.lyrics_instrumental)) }
                Lyrics.NotFound -> Centered { Message(stringResource(R.string.lyrics_not_found)) }
            }
            LyricsState.WifiOnly -> Centered { Message(stringResource(R.string.lyrics_wifi_only)) }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lyrics: Lyrics.Synced,
    position: () -> PositionSnapshot,
    onSeek: (Long) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var current by remember(lyrics) { mutableIntStateOf(-1) }
    val currentPosition by rememberUpdatedState(position)

    // Event-driven sync: after each position sample, sleep exactly until the next line is due.
    // No per-frame work and no fast polling — one wakeup per lyric line.
    LaunchedEffect(lyrics) {
        snapshotFlow { currentPosition() }.collectLatest { snap ->
            while (true) {
                val now = snap.estimate(SystemClock.elapsedRealtime()) + LEAD_MS
                val idx = lyrics.indexAt(now)
                current = idx
                if (!snap.playing) break
                val next = lyrics.lines.getOrNull(idx + 1)?.timeMs ?: break
                delay((next - now).coerceAtLeast(16L))
            }
        }
    }

    // Auto-scroll keeps the active line centred, but yields to the user's finger.
    var userScrolledAt by remember { mutableLongStateOf(0L) }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) userScrolledAt = SystemClock.elapsedRealtime()
                return Offset.Zero
            }
        }
    }

    // Padding comes from the window height, not the panel's: the panel resizes on every frame
    // of the immersive transition, and padding derived from it made long lists re-measure
    // (and stutter) all the way through. Half a window lets the first and last lines centre.
    val halfWindow = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height / 2).toDp() }
    val centerSpring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    fun userHolding() = SystemClock.elapsedRealtime() - userScrolledAt < USER_SCROLL_HOLD_MS

    // The active line glides to the vertical centre of the panel.
    LaunchedEffect(current) {
        if (current >= 0 && !userHolding()) listState.centerOn(current, centerSpring)
    }
    // After the panel resizes (immersive in/out), re-centre once, when the size has settled.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }.collectLatest {
            delay(RESIZE_SETTLE_MS)
            if (current >= 0 && !userHolding()) listState.centerOn(current, centerSpring)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = halfWindow),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(connection)
                .fadingEdges(),
        ) {
            itemsIndexed(lyrics.lines, key = { i, _ -> i }) { index, line ->
                LyricLineItem(
                    text = line.text.ifBlank { "♪" },
                    active = index == current,
                    past = index < current,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        userScrolledAt = 0L
                        onSeek(line.timeMs)
                    },
                )
            }
            item(key = "source") { SourceLabel() }
        }
    }
}

@Composable
private fun LyricLineItem(text: String, active: Boolean, past: Boolean, onClick: () -> Unit) {
    val emphasis by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "lineEmphasis",
    )
    val restAlpha = if (past) 0.5f else 0.35f
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmallEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            // Alpha and scale in the layer phase: the active line "lifts" without relayout.
            .graphicsLayer {
                alpha = restAlpha + (1f - restAlpha) * emphasis
                val s = 0.94f + 0.06f * emphasis
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
    )
}

@Composable
private fun PlainLyrics(lyrics: Lyrics.Plain) {
    val listState: LazyListState = rememberLazyListState()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 24.dp),
        modifier = Modifier.fillMaxSize().fadingEdges(),
    ) {
        itemsIndexed(lyrics.lines, key = { i, _ -> i }) { _, line ->
            Text(
                text = line,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        item(key = "source") { SourceLabel() }
    }
}

@Composable
private fun SourceLabel() {
    Text(
        text = stringResource(R.string.lyrics_source),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Soft fade at top and bottom. Offscreen compositing only costs while the list moves. */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.12f to Color.Black,
                0.85f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
