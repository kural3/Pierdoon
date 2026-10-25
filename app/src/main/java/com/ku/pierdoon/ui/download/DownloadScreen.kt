package com.ku.pierdoon.ui.download

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ku.pierdoon.R
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.download.DownloadStatus
import com.ku.pierdoon.data.download.DownloadTask
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.data.download.EngineUpdate
import com.ku.pierdoon.data.download.FailReason
import com.ku.pierdoon.data.download.findUrl
import com.ku.pierdoon.ui.components.ConnectedChoice
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.library.AppScaffold
import com.ku.pierdoon.ui.library.listBottomPadding
import com.ku.pierdoon.ui.settings.Group
import com.ku.pierdoon.ui.settings.ItemContainer

/** A link in, a track in the library out: yt-dlp does the work, this page shows how it's going. */
@Composable
fun DownloadScreen(onBack: () -> Unit, onPlay: (Long) -> Unit, bottomInset: Dp) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val settings by SettingsStore.settings.collectAsStateWithLifecycle()
    val tasks by Downloader.tasks.collectAsStateWithLifecycle()
    val version by Downloader.engineVersion.collectAsStateWithLifecycle()
    val engineUpdate by Downloader.engineUpdate.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { Downloader.warmUp() }

    val url = findUrl(link)
    fun start() {
        val u = url ?: return
        focus.clearFocus()
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        Downloader.enqueue(u, settings.downloadFormat)
        link = ""
    }

    AppScaffold(title = stringResource(R.string.downloads), onBack = onBack) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = listBottomPadding(bottomInset),
            ),
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            if (!Downloader.supported) {
                item(key = "unsupported") {
                    Text(
                        stringResource(R.string.download_unsupported_os),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                return@LazyColumn
            }

            item(key = "link") {
                Group(stringResource(R.string.download_link)) {
                    listOf<@Composable (Shape) -> Unit>({ shape ->
                        ItemContainer(shape) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                            ) {
                                OutlinedTextField(
                                    value = link,
                                    onValueChange = { link = it },
                                    label = { Text(stringResource(R.string.download_link)) },
                                    supportingText = { Text(stringResource(R.string.download_link_hint)) },
                                    singleLine = true,
                                    trailingIcon = {
                                        if (link.isEmpty()) {
                                            IconButton(onClick = {
                                                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                                                val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
                                                text?.let { findUrl(it.toString()) }?.let { link = it }
                                            }) {
                                                Icon(PlayerIcons.Paste, contentDescription = stringResource(R.string.download_paste))
                                            }
                                        } else {
                                            IconButton(onClick = { link = "" }) {
                                                Icon(PlayerIcons.Close, contentDescription = stringResource(R.string.cancel))
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(onGo = { start() }),
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                ExpressiveIconButton(
                                    onClick = ::start,
                                    icon = PlayerIcons.Download,
                                    contentDescription = stringResource(R.string.download_start),
                                    containerColor = if (url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (url != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp).size(56.dp),
                                )
                            }
                        }
                    })
                }
            }

            item(key = "format") {
                Group(stringResource(R.string.download_format)) {
                    listOf<@Composable (Shape) -> Unit>({ shape ->
                        ItemContainer(shape) {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                ConnectedChoice(
                                    options = listOf(
                                        stringResource(R.string.download_format_m4a),
                                        stringResource(R.string.download_format_opus),
                                    ),
                                    selected = settings.downloadFormat.ordinal,
                                    onSelect = { i -> SettingsStore.update { it.copy(downloadFormat = DownloadFormat.entries[i]) } },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                                Spacer(Modifier.height(12.dp))
                                val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                                val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                                AnimatedContent(
                                    targetState = settings.downloadFormat,
                                    transitionSpec = { fadeIn(fadeInSpec).togetherWith(fadeOut(fadeOutSpec)) },
                                    label = "formatDesc",
                                ) { f ->
                                    Text(
                                        stringResource(
                                            if (f == DownloadFormat.M4a) R.string.download_format_m4a_desc
                                            else R.string.download_format_opus_desc
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    })
                }
            }

            if (tasks.isNotEmpty()) {
                item(key = "queue") {
                    Group(stringResource(R.string.download_queue)) {
                        tasks.asReversed().map<DownloadTask, @Composable (Shape) -> Unit> { task ->
                            { shape ->
                                TaskRow(
                                    task = task,
                                    shape = shape,
                                    onPlay = onPlay,
                                    onRemove = { Downloader.remove(task.id) },
                                    onRetry = { Downloader.retry(task.id) },
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = tasks.any { !it.active },
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            TextButton(onClick = Downloader::clearFinished) { Text(stringResource(R.string.download_clear)) }
                        }
                    }
                }
            }

            item(key = "about") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                    Text(
                        stringResource(R.string.download_where),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            version?.let { stringResource(R.string.download_engine, it) }
                                ?: stringResource(R.string.download_engine_unknown),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = Downloader::updateEngine,
                            enabled = engineUpdate != EngineUpdate.Running,
                        ) {
                            Text(
                                stringResource(
                                    when (engineUpdate) {
                                        EngineUpdate.Idle -> R.string.download_engine_update
                                        EngineUpdate.Running -> R.string.download_engine_updating
                                        EngineUpdate.Updated -> R.string.download_engine_updated
                                        EngineUpdate.UpToDate -> R.string.download_engine_latest
                                        EngineUpdate.Failed -> R.string.download_engine_failed
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: DownloadTask,
    shape: Shape,
    onPlay: (Long) -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val status = task.status
    val failed = status is DownloadStatus.Failed
    ItemContainer(shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = status is DownloadStatus.Done) { (status as? DownloadStatus.Done)?.let { onPlay(it.songId) } }
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (failed) colors.errorContainer else colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (status) {
                        is DownloadStatus.Done -> PlayerIcons.Play
                        is DownloadStatus.Failed -> PlayerIcons.Close
                        else -> PlayerIcons.Download
                    },
                    contentDescription = null,
                    tint = if (failed) colors.onErrorContainer else colors.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    task.title ?: task.url,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    statusText(task),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) colors.error else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when (status) {
                    is DownloadStatus.Downloading -> {
                        Spacer(Modifier.height(4.dp))
                        if (status.progress != null) {
                            LinearWavyProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    DownloadStatus.Preparing, DownloadStatus.Saving -> {
                        Spacer(Modifier.height(4.dp))
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    }
                    else -> Unit
                }
            }
            if (failed) {
                ExpressiveIconButton(
                    onClick = onRetry,
                    icon = PlayerIcons.Restore,
                    contentDescription = stringResource(R.string.retry),
                    containerColor = Color.Transparent,
                    contentColor = colors.onSurfaceVariant,
                    modifier = Modifier.size(44.dp),
                )
            }
            ExpressiveIconButton(
                onClick = onRemove,
                icon = PlayerIcons.Close,
                contentDescription = stringResource(if (task.active) R.string.download_cancel else R.string.delete),
                containerColor = Color.Transparent,
                contentColor = colors.onSurfaceVariant,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun statusText(task: DownloadTask): String {
    val artist = task.artist
    val state = when (val s = task.status) {
        DownloadStatus.Queued -> stringResource(R.string.download_queued)
        DownloadStatus.Preparing -> stringResource(R.string.download_preparing)
        is DownloadStatus.Downloading ->
            s.progress?.let { stringResource(R.string.download_progress, (it * 100).toInt()) }
                ?: stringResource(R.string.download_running)
        DownloadStatus.Saving -> stringResource(R.string.download_saving)
        is DownloadStatus.Done -> stringResource(R.string.download_done)
        is DownloadStatus.Failed -> {
            val reason = stringResource(
                when (s.reason) {
                    FailReason.Unsupported -> R.string.download_fail_unsupported
                    FailReason.Unavailable -> R.string.download_fail_unavailable
                    FailReason.NoAudio -> R.string.download_fail_no_audio
                    FailReason.Network -> R.string.download_fail_network
                    FailReason.Save -> R.string.download_fail_save
                    FailReason.Other -> R.string.download_fail_other
                }
            )
            return if (s.reason == FailReason.Other && s.detail != null) "$reason: ${s.detail}" else reason
        }
    }
    return if (artist != null && task.status !is DownloadStatus.Failed) "$artist · $state" else state
}
