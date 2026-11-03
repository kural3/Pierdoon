package com.ku.pierdoon.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.download.DownloadLogEntry
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.formatDateTime
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.download_fail_bot
import com.ku.pierdoon.ui.resources.download_fail_drm
import com.ku.pierdoon.ui.resources.download_fail_network
import com.ku.pierdoon.ui.resources.download_fail_no_audio
import com.ku.pierdoon.ui.resources.download_fail_other
import com.ku.pierdoon.ui.resources.download_fail_save
import com.ku.pierdoon.ui.resources.download_fail_unavailable
import com.ku.pierdoon.ui.resources.download_fail_unsupported
import com.ku.pierdoon.ui.resources.download_log
import com.ku.pierdoon.ui.resources.download_log_clear
import com.ku.pierdoon.ui.resources.download_log_empty
import com.ku.pierdoon.ui.resources.download_log_errors
import com.ku.pierdoon.ui.resources.download_log_hint
import com.ku.pierdoon.ui.resources.download_log_ok
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

/**
 * The Downloads page's journal: how each download ended, newest first. A failed one shows its
 * reason; tapped, it opens to the link and yt-dlp's own words, which can be selected and copied.
 */
fun LazyListScope.downloadLog(log: StateFlow<List<DownloadLogEntry>>, onClear: () -> Unit) {
    item(key = "download_log") {
        val entries by log.collectAsState()
        var errorsOnly by rememberSaveable { mutableStateOf(false) }
        val shown = if (errorsOnly) entries.filter { !it.ok } else entries
        Group(stringResource(Res.string.download_log)) {
            buildList<@Composable (Shape) -> Unit> {
                add { shape ->
                    ItemContainer(shape) {
                        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = errorsOnly,
                                    onClick = { errorsOnly = !errorsOnly },
                                    label = { Text(stringResource(Res.string.download_log_errors)) },
                                    leadingIcon = if (errorsOnly) {
                                        { Icon(PlayerIcons.Check, contentDescription = null, Modifier.size(18.dp)) }
                                    } else null,
                                )
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = onClear, enabled = entries.isNotEmpty()) {
                                    Text(stringResource(Res.string.download_log_clear))
                                }
                            }
                            Text(
                                stringResource(if (entries.isEmpty()) Res.string.download_log_empty else Res.string.download_log_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                }
                shown.take(SHOWN_MAX).forEach { entry -> add { shape -> LogEntry(shape, entry) } }
            }
        }
    }
}

private const val SHOWN_MAX = 100

@Composable
private fun LogEntry(shape: Shape, entry: DownloadLogEntry) {
    val colors = MaterialTheme.colorScheme
    var open by rememberSaveable(entry.time, entry.url) { mutableStateOf(false) }
    ItemContainer(shape) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (entry.ok) PlayerIcons.Check else PlayerIcons.Close,
                    contentDescription = null,
                    tint = if (entry.ok) colors.primary else colors.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.title ?: entry.url,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(entry.artist, formatDateTime(entry.time)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                if (entry.ok) stringResource(Res.string.download_log_ok) else reasonText(entry.reason),
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.ok) colors.onSurfaceVariant else colors.error,
                modifier = Modifier.padding(start = 34.dp, top = 4.dp),
            )
            AnimatedVisibility(open) {
                SelectionContainer {
                    Column(
                        Modifier.padding(start = 34.dp, top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(entry.url, style = MaterialTheme.typography.bodySmall, color = colors.primary)
                        entry.message?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = colors.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.surfaceContainerHighest, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun reasonText(reason: String?): String = stringResource(
    when (reason) {
        "Unsupported" -> Res.string.download_fail_unsupported
        "Unavailable" -> Res.string.download_fail_unavailable
        "Bot" -> Res.string.download_fail_bot
        "Drm" -> Res.string.download_fail_drm
        "NoAudio" -> Res.string.download_fail_no_audio
        "Network" -> Res.string.download_fail_network
        "Save" -> Res.string.download_fail_save
        else -> Res.string.download_fail_other
    },
)
