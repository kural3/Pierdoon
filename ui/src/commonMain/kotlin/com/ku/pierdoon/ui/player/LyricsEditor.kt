package com.ku.pierdoon.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ku.pierdoon.PositionSnapshot
import com.ku.pierdoon.data.lyrics.EditLine
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.data.lyrics.LyricsText
import com.ku.pierdoon.ui.components.ConnectedChoice
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayPauseButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.elapsedRealtime
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.ui.resources.Res
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val NUDGE_MS = 100L
private const val BACK_MS = 3_000L

/**
 * Writing lyrics for the track playing, or fixing the ones it has. Two views of the same lines:
 * the text, with optional `[mm:ss.xx]` stamps, and a sync view that stamps them as the song plays.
 */
@Composable
fun LyricsEditorDialog(
    search: LyricsSearch,
    /** What the track shows now; the starting point when the user hasn't written their own. */
    current: Lyrics?,
    own: Boolean,
    position: () -> PositionSnapshot,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val save = search.saveOwn ?: return
    // The track playing when the editor opened; a track change meanwhile won't take the lyrics along.
    val songId = remember { search.songId() } ?: return
    var loaded by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<EditLine>() }
    var original by remember { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val start = if (own) search.ownText(songId)?.let(LyricsText::lines) ?: LyricsText.from(current)
        else LyricsText.from(current)
        text = LyricsText.text(start)
        original = text
        loaded = true
    }

    fun result(): String = if (tab == 0) text else LyricsText.text(lines)
    fun close() {
        if (loaded && LyricsText.text(LyricsText.lines(result())) != LyricsText.text(LyricsText.lines(original))) {
            confirmDiscard = true
        } else onDismiss()
    }
    fun switchTo(next: Int) {
        if (next == tab) return
        if (next == 1) {
            lines.clear()
            lines.addAll(LyricsText.lines(text))
        } else text = LyricsText.text(lines)
        tab = next
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // A phone gets the whole screen; a wide window, a tall card in the middle.
            val wide = maxWidth > 840.dp
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = if (wide) RoundedCornerShape(28.dp) else RoundedCornerShape(0.dp),
                modifier = if (wide) Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight().padding(vertical = 32.dp)
                else Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::close) {
                            Icon(PlayerIcons.Close, contentDescription = stringResource(Res.string.cancel))
                        }
                        Text(
                            stringResource(Res.string.lyrics_editor),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        Button(
                            onClick = { save(songId, result()); onDismiss() },
                            enabled = loaded,
                        ) { Text(stringResource(Res.string.save)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    ConnectedChoice(
                        options = listOf(stringResource(Res.string.lyrics_tab_text), stringResource(Res.string.lyrics_tab_sync)),
                        selected = tab,
                        onSelect = ::switchTo,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            !loaded -> LoadingIndicator(Modifier.size(48.dp).align(Alignment.Center))
                            tab == 0 -> TextTab(
                                text = text,
                                onText = { text = it },
                                own = own,
                                onDelete = { search.removeOwn(songId); onDismiss() },
                            )
                            else -> SyncTab(lines, position, onSeek, search.togglePlay, showKeys = wide)
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(Res.string.lyrics_discard_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text(stringResource(Res.string.lyrics_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(Res.string.lyrics_keep_editing)) }
            },
        )
    }
}

@Composable
private fun TextTab(text: String, onText: (String) -> Unit, own: Boolean, onDelete: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (text.isEmpty()) focus.requestFocus() }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            placeholder = { Text(stringResource(Res.string.lyrics_text_placeholder)) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().focusRequester(focus),
        )
        Text(
            stringResource(Res.string.lyrics_text_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        )
        if (own) {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.lyrics_delete_own)) }
        }
    }
}

@Composable
private fun SyncTab(
    lines: MutableList<EditLine>,
    position: () -> PositionSnapshot,
    onSeek: (Long) -> Unit,
    togglePlay: () -> Unit,
    /** Keyboard hints, for a window on a computer. */
    showKeys: Boolean,
) {
    if (lines.isEmpty()) {
        Text(
            stringResource(Res.string.lyrics_sync_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        )
        return
    }
    val colors = MaterialTheme.colorScheme
    val currentPosition by rememberUpdatedState(position)
    // The first line without a stamp: where a fresh text starts.
    var selected by remember { mutableIntStateOf(lines.indexOfFirst { it.timeMs == null }.coerceAtLeast(0)) }
    val listState = rememberLazyListState()
    val now by produceState(position().estimate()) {
        while (true) {
            val snap = currentPosition()
            value = snap.estimate(elapsedRealtime())
            delay(if (snap.playing) 50 else 200)
        }
    }
    val playing = position().playing
    // The line playing now, by the stamps so far.
    val live = lines.indices.lastOrNull { i -> lines[i].timeMs?.let { it <= now } == true } ?: -1

    LaunchedEffect(selected) {
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (selected < first + 1 || selected > last - 2) listState.animateScrollToItem((selected - 2).coerceAtLeast(0))
    }

    fun stamp() {
        val i = selected.coerceIn(0, lines.lastIndex)
        lines[i] = lines[i].copy(timeMs = currentPosition().estimate(elapsedRealtime()))
        if (i < lines.lastIndex) selected = i + 1
    }
    fun nudge(delta: Long) {
        val l = lines.getOrNull(selected) ?: return
        val t = l.timeMs ?: return
        lines[selected] = l.copy(timeMs = (t + delta).coerceAtLeast(0))
    }

    val keys = remember { FocusRequester() }
    LaunchedEffect(Unit) { keys.requestFocus() }
    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(keys)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter -> stamp()
                    Key.Spacebar -> togglePlay()
                    Key.DirectionUp -> selected = (selected - 1).coerceAtLeast(0)
                    Key.DirectionDown -> selected = (selected + 1).coerceAtMost(lines.lastIndex)
                    Key.Backspace, Key.Delete -> lines.getOrNull(selected)?.let { lines[selected] = it.copy(timeMs = null) }
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusable(),
    ) {
        Text(
            stringResource(Res.string.lyrics_sync_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            itemsIndexed(lines) { i, line ->
                val isSelected = i == selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) colors.secondaryContainer else colors.surfaceContainerLow)
                        .clickable { selected = i; keys.requestFocus() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    val t = line.timeMs
                    Text(
                        if (t == null) "––:––.––" else LyricsText.time(t),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                        color = when {
                            t == null -> colors.outline
                            isSelected -> colors.onSecondaryContainer
                            else -> colors.primary
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = t != null) {
                                selected = i
                                if (t != null) onSeek(t)
                                keys.requestFocus()
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                    Text(
                        line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = when {
                            i == live -> colors.primary
                            isSelected -> colors.onSecondaryContainer
                            else -> colors.onSurface
                        },
                        modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // The controls: time and fine-tuning above, the big stamp below, within thumb reach.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceContainerHigh)
                .padding(12.dp),
        ) {
            val sel = lines.getOrNull(selected)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LyricsText.time(now),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                val small = Modifier.size(40.dp)
                ExpressiveIconButton(
                    onClick = { nudge(-NUDGE_MS) },
                    icon = PlayerIcons.Remove,
                    contentDescription = stringResource(Res.string.lyrics_nudge_earlier),
                    containerColor = colors.surfaceContainerHighest,
                    contentColor = if (sel?.timeMs != null) colors.onSurface else colors.outline,
                    iconSize = 20.dp,
                    modifier = small,
                )
                Spacer(Modifier.width(6.dp))
                ExpressiveIconButton(
                    onClick = { nudge(NUDGE_MS) },
                    icon = PlayerIcons.Add,
                    contentDescription = stringResource(Res.string.lyrics_nudge_later),
                    containerColor = colors.surfaceContainerHighest,
                    contentColor = if (sel?.timeMs != null) colors.onSurface else colors.outline,
                    iconSize = 20.dp,
                    modifier = small,
                )
                Spacer(Modifier.width(6.dp))
                ExpressiveIconButton(
                    onClick = { sel?.let { lines[selected] = it.copy(timeMs = null) } },
                    icon = PlayerIcons.Delete,
                    contentDescription = stringResource(Res.string.lyrics_unstamp),
                    containerColor = colors.surfaceContainerHighest,
                    contentColor = if (sel?.timeMs != null) colors.onSurface else colors.outline,
                    iconSize = 20.dp,
                    modifier = small,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveIconButton(
                    onClick = { onSeek((currentPosition().estimate(elapsedRealtime()) - BACK_MS).coerceAtLeast(0)) },
                    icon = PlayerIcons.FastRewind,
                    contentDescription = stringResource(Res.string.lyrics_back_3s),
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(8.dp))
                PlayPauseButton(
                    isPlaying = playing,
                    onClick = togglePlay,
                    playLabel = stringResource(Res.string.play),
                    pauseLabel = stringResource(Res.string.pause),
                    iconSize = 28.dp,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { stamp(); keys.requestFocus() },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(PlayerIcons.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.lyrics_stamp), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (showKeys) Text(
                stringResource(Res.string.lyrics_sync_keys),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
    }
}
