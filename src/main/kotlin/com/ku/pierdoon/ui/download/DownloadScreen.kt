package com.ku.pierdoon.ui.download

import com.ku.pierdoon.ui.desktop.Files
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.resources.*
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.download.DownloadGroup
import com.ku.pierdoon.data.download.SearchHit
import com.ku.pierdoon.data.download.SearchSource
import com.ku.pierdoon.ui.components.RemoteImage
import com.ku.pierdoon.ui.formatDuration
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val settings by SettingsStore.settings.collectAsState()
    val tasks by Downloader.tasks.collectAsState()
    val version by Downloader.engineVersion.collectAsState()
    val engineUpdate by Downloader.engineUpdate.collectAsState()
    var link by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf(SearchSource.YouTube) }
    var search by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) { Downloader.warmUp() }

    val url = findUrl(link)
    fun runSearch() {
        val query = link.trim().takeIf { it.isNotEmpty() } ?: return
        searchJob?.cancel()
        search = SearchState.Loading
        searchJob = scope.launch {
            search = try {
                SearchState.Found(Downloader.search(query, source))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchState.Failed
            }
        }
    }
    // A link downloads; anything else is looked up on the chosen site.
    fun start() {
        if (link.isBlank()) return
        focus.clearFocus()
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (url != null) {
            Downloader.enqueue(url, settings.downloadFormat)
            link = ""
        } else {
            runSearch()
        }
    }

    AppScaffold(title = stringResource(Res.string.downloads), onBack = onBack) { inner ->
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
                        stringResource(Res.string.download_unsupported_os),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                return@LazyColumn
            }

            item(key = "link") {
                Group(stringResource(Res.string.download_what)) {
                    listOf<@Composable (Shape) -> Unit>({ shape ->
                        ItemContainer(shape) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                            ) {
                                OutlinedTextField(
                                    value = link,
                                    onValueChange = { link = it },
                                    label = { Text(stringResource(Res.string.download_link)) },
                                    supportingText = { Text(stringResource(Res.string.download_link_hint)) },
                                    singleLine = true,
                                    trailingIcon = {
                                        if (link.isEmpty()) {
                                            IconButton(onClick = {
                                                val text = Files.clipboardText()
                                                text?.let { findUrl(it.toString()) }?.let { link = it }
                                            }) {
                                                Icon(PlayerIcons.Paste, contentDescription = stringResource(Res.string.download_paste))
                                            }
                                        } else {
                                            IconButton(onClick = { link = "" }) {
                                                Icon(PlayerIcons.Close, contentDescription = stringResource(Res.string.cancel))
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = if (url != null) ImeAction.Go else ImeAction.Search),
                                    keyboardActions = KeyboardActions(onGo = { start() }, onSearch = { start() }),
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                val ready = link.isNotBlank()
                                ExpressiveIconButton(
                                    onClick = ::start,
                                    icon = if (url != null || !ready) PlayerIcons.Download else PlayerIcons.Search,
                                    contentDescription = stringResource(if (url != null) Res.string.download_start else Res.string.download_search),
                                    containerColor = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp).size(56.dp),
                                )
                            }
                            AnimatedVisibility(
                                visible = url == null,
                                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
                            ) {
                                ConnectedChoice(
                                    options = listOf(
                                        stringResource(Res.string.download_source_youtube),
                                        stringResource(Res.string.download_source_soundcloud),
                                    ),
                                    selected = source.ordinal,
                                    onSelect = { i ->
                                        source = SearchSource.entries[i]
                                        if (search != SearchState.Idle) runSearch()
                                    },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                )
                            }
                        }
                    })
                }
            }

            if (search != SearchState.Idle) {
                item(key = "results") {
                    Group(stringResource(Res.string.download_results)) {
                        when (val st = search) {
                            SearchState.Loading -> listOf<@Composable (Shape) -> Unit>({ shape ->
                                SearchNote(shape, stringResource(Res.string.download_searching), loading = true)
                            })
                            SearchState.Failed -> listOf<@Composable (Shape) -> Unit>({ shape ->
                                SearchNote(shape, stringResource(Res.string.download_search_failed))
                            })
                            is SearchState.Found -> if (st.hits.isEmpty()) {
                                listOf<@Composable (Shape) -> Unit>({ shape ->
                                    SearchNote(shape, stringResource(Res.string.download_search_empty))
                                })
                            } else {
                                st.hits.map<SearchHit, @Composable (Shape) -> Unit> { hit ->
                                    { shape ->
                                        HitRow(
                                            hit = hit,
                                            shape = shape,
                                            queued = tasks.any { it.url == hit.url },
                                            onDownload = {
                                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                                Downloader.enqueue(hit.url, settings.downloadFormat, hit.title, hit.artist.ifBlank { null })
                                            },
                                        )
                                    }
                                }
                            }
                            SearchState.Idle -> emptyList()
                        }
                    }
                }
            }

            item(key = "format") {
                Group(stringResource(Res.string.download_format)) {
                    listOf<@Composable (Shape) -> Unit>({ shape ->
                        ItemContainer(shape) {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                ConnectedChoice(
                                    options = listOf(
                                        stringResource(Res.string.download_format_m4a),
                                        stringResource(Res.string.download_format_opus),
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
                                            if (f == DownloadFormat.M4a) Res.string.download_format_m4a_desc
                                            else Res.string.download_format_opus_desc
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
                    Group(stringResource(Res.string.download_queue)) {
                        // Newest first, but a playlist's own tracks keep their order under its header.
                        queueRuns(tasks).asReversed().flatMap { run ->
                            val group = run.first().group
                            val header = group?.let { g ->
                                listOf<@Composable (Shape) -> Unit>({ shape -> GroupHeader(g, run, shape) })
                            }.orEmpty()
                            header + run.map<DownloadTask, @Composable (Shape) -> Unit> { task ->
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
                    }
                    AnimatedVisibility(
                        visible = tasks.any { !it.active },
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            TextButton(onClick = Downloader::clearFinished) { Text(stringResource(Res.string.download_clear)) }
                        }
                    }
                }
            }

            item(key = "about") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                    Text(
                        stringResource(Res.string.download_where),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            version?.let { stringResource(Res.string.download_engine, it) }
                                ?: stringResource(Res.string.download_engine_unknown),
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
                                        EngineUpdate.Idle -> Res.string.download_engine_update
                                        EngineUpdate.Running -> Res.string.download_engine_updating
                                        EngineUpdate.Updated -> Res.string.download_engine_updated
                                        EngineUpdate.UpToDate -> Res.string.download_engine_latest
                                        EngineUpdate.Failed -> Res.string.download_engine_failed
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

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data object Failed : SearchState
    data class Found(val hits: List<SearchHit>) : SearchState
}

/** Consecutive tasks of one playlist stay together; every other task is a run of its own. */
private fun queueRuns(tasks: List<DownloadTask>): List<List<DownloadTask>> {
    val runs = mutableListOf<MutableList<DownloadTask>>()
    for (t in tasks) {
        val last = runs.lastOrNull()
        if (t.group != null && last != null && last.first().group?.id == t.group.id) last += t else runs += mutableListOf(t)
    }
    return runs
}

@Composable
private fun SearchNote(shape: Shape, text: String, loading: Boolean = false) {
    ItemContainer(shape) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loading) {
                Spacer(Modifier.height(8.dp))
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, shape: Shape, queued: Boolean, onDownload: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ItemContainer(shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !queued, onClick = onDownload)
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        ) {
            RemoteImage(hit.thumbnail, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hit.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    hit.artist.ifBlank { null },
                    hit.durationSec?.let { formatDuration(it * 1000L) },
                    if (queued) stringResource(Res.string.download_in_queue) else null,
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ExpressiveIconButton(
                onClick = { if (!queued) onDownload() },
                icon = if (queued) PlayerIcons.Check else PlayerIcons.Download,
                contentDescription = stringResource(if (queued) Res.string.download_in_queue else Res.string.download_start),
                containerColor = if (queued) colors.secondaryContainer else Color.Transparent,
                contentColor = if (queued) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun GroupHeader(group: DownloadGroup, tasks: List<DownloadTask>, shape: Shape) {
    val colors = MaterialTheme.colorScheme
    val done = tasks.count { it.status is DownloadStatus.Done }
    val finished = tasks.none { it.active }
    ItemContainer(shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(colors.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PlayerIcons.Playlist, contentDescription = null, tint = colors.onTertiaryContainer, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(Res.string.download_group, group.name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        if (finished && done > 0) Res.string.download_group_done else Res.string.download_group_progress,
                        done,
                        tasks.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
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
                    contentDescription = stringResource(Res.string.retry),
                    containerColor = Color.Transparent,
                    contentColor = colors.onSurfaceVariant,
                    modifier = Modifier.size(44.dp),
                )
            }
            ExpressiveIconButton(
                onClick = onRemove,
                icon = PlayerIcons.Close,
                contentDescription = stringResource(if (task.active) Res.string.download_cancel else Res.string.delete),
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
        DownloadStatus.Queued -> stringResource(Res.string.download_queued)
        DownloadStatus.Preparing -> stringResource(Res.string.download_preparing)
        is DownloadStatus.Downloading ->
            s.progress?.let { stringResource(Res.string.download_progress, (it * 100).toInt()) }
                ?: stringResource(Res.string.download_running)
        DownloadStatus.Saving -> stringResource(Res.string.download_saving)
        is DownloadStatus.Done -> stringResource(Res.string.download_done)
        is DownloadStatus.Failed -> {
            val reason = stringResource(
                when (s.reason) {
                    FailReason.Unsupported -> Res.string.download_fail_unsupported
                    FailReason.Unavailable -> Res.string.download_fail_unavailable
                    FailReason.Drm -> Res.string.download_fail_drm
                    FailReason.NoAudio -> Res.string.download_fail_no_audio
                    FailReason.Network -> Res.string.download_fail_network
                    FailReason.Save -> Res.string.download_fail_save
                    FailReason.Other -> Res.string.download_fail_other
                }
            )
            return if (s.reason == FailReason.Other && s.detail != null) "$reason: ${s.detail}" else reason
        }
    }
    return if (artist != null && task.status !is DownloadStatus.Failed) "$artist · $state" else state
}
