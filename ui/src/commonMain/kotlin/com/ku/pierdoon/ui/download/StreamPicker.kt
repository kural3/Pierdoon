package com.ku.pierdoon.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.SharedSettingsStore
import com.ku.pierdoon.data.download.AudioQuality
import com.ku.pierdoon.data.download.DownloadMode
import com.ku.pierdoon.data.download.AudioStream
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.download.DownloadQueue
import com.ku.pierdoon.ui.components.ConnectedChoice
import com.ku.pierdoon.ui.components.FormatBadge
import com.ku.pierdoon.data.AudioTier
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.ui.settings.fileSize
import com.ku.pierdoon.ui.settings.label
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource

/** A link waiting for its quality to be picked, with the name a search already knows. */
data class PendingDownload(val url: String, val title: String? = null, val artist: String? = null)

/**
 * Links to ask about from outside the download page (shared from another app): it opens the
 * picker for them. Set only under [com.ku.pierdoon.data.download.DownloadMode.Ask].
 */
object DownloadAsk {
    val pending = MutableStateFlow<PendingDownload?>(null)
}

/**
 * Starts [url] the way Settings say: at once in the set format, or, under "choose each time",
 * by putting it up for the picker on the download page (which the caller opens).
 */
fun DownloadQueue.offer(url: String, title: String? = null, artist: String? = null) {
    val settings = SharedSettingsStore.settings.value
    if (settings.downloadMode == DownloadMode.Ask) DownloadAsk.pending.value = PendingDownload(url, title, artist)
    else enqueue(url, settings.downloadFormat, title, artist)
}

/**
 * Asked before a download, under "choose each time": the audio streams the link has, best first,
 * and, where FFmpeg is at hand, what to save it as. A link whose streams can't be read (a playlist,
 * an odd site) offers the formats instead.
 */
@Composable
fun StreamPickerDialog(pending: PendingDownload, downloads: DownloadQueue, onDismiss: () -> Unit) {
    var streams by remember(pending.url) { mutableStateOf<List<AudioStream>?>(null) }
    var loaded by remember(pending.url) { mutableStateOf(false) }
    LaunchedEffect(pending.url) {
        streams = downloads.streams(pending.url)?.takeIf { it.isNotEmpty() }
        loaded = true
    }
    var picked by remember(pending.url) { mutableStateOf(0) }
    // What the chosen stream is saved as: as is, or as MP3 for old players. Never FLAC: lossy
    // audio in a lossless file is only bigger; a lossless stream is saved as is anyway.
    val saveAs = remember(downloads.canConvert) {
        listOf(DownloadFormat.Best) + if (downloads.canConvert) listOf(DownloadFormat.Mp3) else emptyList()
    }
    var convert by remember { mutableStateOf(0) }
    // Without streams: the formats to pick from.
    val formats = remember(downloads.canConvert) { DownloadFormat.entries.filter { downloads.canConvert || !it.converts } }
    var format by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(Res.string.download_pick_title))
                pending.title?.let {
                    Text(
                        listOfNotNull(pending.artist, it).joinToString(" — "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val list = streams
                when {
                    !loaded -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                        LoadingIndicator(Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(Res.string.download_pick_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                    list != null -> {
                        Text(
                            stringResource(Res.string.download_pick_streams),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            list.forEachIndexed { i, s ->
                                StreamRow(s, best = i == 0, selected = i == picked, onClick = { picked = i })
                            }
                        }
                        if (saveAs.size > 1) {
                            Text(
                                stringResource(Res.string.download_pick_save_as),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            )
                            ConnectedChoice(
                                options = saveAs.map {
                                    if (it == DownloadFormat.Best) stringResource(Res.string.download_pick_as_is) else stringResource(it.label())
                                },
                                selected = convert,
                                onSelect = { convert = it },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }
                    else -> {
                        Text(
                            stringResource(Res.string.download_pick_no_streams),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        ConnectedChoice(
                            options = formats.map { stringResource(it.label()) },
                            selected = format,
                            onSelect = { format = it },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = loaded,
                onClick = {
                    val list = streams
                    if (list != null) {
                        downloads.enqueue(pending.url, saveAs[convert], pending.title, pending.artist, streamId = list[picked].id)
                    } else {
                        downloads.enqueue(pending.url, formats[format], pending.title, pending.artist)
                    }
                    onDismiss()
                },
            ) { Text(stringResource(Res.string.download_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** "Opus · 135 kbit/s · 3.2 MB", a radio dot, and "best" on the first one. */
@Composable
private fun StreamRow(stream: AudioStream, best: Boolean, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerHighest)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(20.dp).clip(CircleShape).background(if (selected) colors.primary else colors.outlineVariant),
        ) {
            if (selected) Icon(PlayerIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                qualityText(AudioQuality(stream.codec, stream.kbps)) ?: stream.id,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            )
            stream.bytes?.let {
                Text(fileSize(it), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        if (stream.lossless) {
            FormatBadge(stringResource(Res.string.download_pick_lossless), AudioTier.Lossless, Modifier.padding(end = 6.dp))
        }
        if (best) {
            Text(
                stringResource(Res.string.download_pick_best),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onTertiaryContainer,
                modifier = Modifier.clip(CircleShape).background(colors.tertiaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
