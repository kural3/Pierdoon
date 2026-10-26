package com.ku.pierdoon.ui.settings

import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.data.download.EngineUpdate
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.R
import com.ku.pierdoon.data.AppFont
import com.ku.pierdoon.data.AppSettings
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.ThemeMode
import com.ku.pierdoon.data.update.UpdateState
import com.ku.pierdoon.ui.components.ConnectedChoice
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.remote.PcRemote
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ku.pierdoon.ui.library.AppScaffold
import com.ku.pierdoon.ui.library.listBottomPadding
import com.ku.pierdoon.ui.theme.typographyFor

/** Shapes for grouped items: large outer corners, tight inner ones (Android 16 style). */
internal fun groupShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/** A page of settings, opened from the list on the Settings tab. */
enum class SettingsSection { Appearance, Feedback, Playback, Lyrics, Library, Downloads, Updates }

private typealias Update = ((AppSettings) -> AppSettings) -> Unit

/**
 * The Settings tab: the list of sections when [section] is null, otherwise that section's page
 * with a back arrow. Both live here so they share the same state and callbacks.
 */
@Composable
fun SettingsScreen(
    section: SettingsSection?,
    onOpen: (SettingsSection) -> Unit,
    onOpenComputer: () -> Unit,
    onBack: () -> Unit,
    settings: AppSettings,
    onUpdate: Update,
    trackCount: Int,
    lyricsCacheSize: suspend () -> Long,
    onClearLyricsCache: () -> Unit,
    onRescan: () -> Unit,
    updateState: UpdateState,
    onCheckUpdates: () -> Unit,
    onShowUpdate: () -> Unit,
    bottomInset: Dp,
) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val updateStatus = when (updateState) {
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_up_to_date, version)
        UpdateState.CheckFailed -> stringResource(R.string.update_check_failed)
        is UpdateState.Available -> stringResource(R.string.update_available, updateState.release.version)
        is UpdateState.Downloading -> stringResource(R.string.update_downloading)
        is UpdateState.Installing -> stringResource(R.string.update_confirm)
        is UpdateState.InstallFailed -> stringResource(R.string.update_failed)
        UpdateState.Idle -> stringResource(R.string.update_current, version)
    }

    AppScaffold(
        title = stringResource(section?.title() ?: R.string.nav_settings),
        onBack = if (section != null) onBack else null,
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = listBottomPadding(bottomInset),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (section) {
                null -> sectionList(onOpen, onOpenComputer, trackCount, updateStatus, version, settings.downloadFormat)
                SettingsSection.Appearance -> appearance(settings, onUpdate)
                SettingsSection.Feedback -> feedback(settings, onUpdate)
                SettingsSection.Playback -> playback(settings, onUpdate)
                SettingsSection.Lyrics -> lyrics(settings, onUpdate, lyricsCacheSize, onClearLyricsCache)
                SettingsSection.Library -> library(trackCount, onRescan)
                SettingsSection.Downloads -> downloads(settings, onUpdate)
                SettingsSection.Updates -> updates(settings, onUpdate, updateState, updateStatus, onCheckUpdates, onShowUpdate)
            }
        }
    }
}

private fun SettingsSection.title(): Int = when (this) {
    SettingsSection.Appearance -> R.string.settings_appearance
    SettingsSection.Feedback -> R.string.settings_feedback
    SettingsSection.Playback -> R.string.settings_playback
    SettingsSection.Lyrics -> R.string.settings_lyrics
    SettingsSection.Library -> R.string.settings_library
    SettingsSection.Downloads -> R.string.settings_downloads
    SettingsSection.Updates -> R.string.settings_updates
}

private fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.Appearance -> PlayerIcons.Palette
    SettingsSection.Feedback -> PlayerIcons.Vibration
    SettingsSection.Playback -> PlayerIcons.Play
    SettingsSection.Lyrics -> PlayerIcons.Lyrics
    SettingsSection.Library -> PlayerIcons.Library
    SettingsSection.Downloads -> PlayerIcons.Download
    SettingsSection.Updates -> PlayerIcons.Refresh
}

private fun LazyListScope.sectionList(
    onOpen: (SettingsSection) -> Unit,
    onOpenComputer: () -> Unit,
    trackCount: Int,
    updateStatus: String,
    version: String,
    downloadFormat: DownloadFormat,
) {
    item(key = "sections") {
        Group(null) {
            // Downloads need Android 10; before that the section would only say so.
            SettingsSection.entries.filter { it != SettingsSection.Downloads || Downloader.supported }.map { s ->
                { shape ->
                    SectionItem(
                        shape = shape,
                        icon = s.icon(),
                        title = stringResource(s.title()),
                        summary = when (s) {
                            SettingsSection.Appearance -> stringResource(R.string.settings_appearance_summary)
                            SettingsSection.Feedback -> stringResource(R.string.settings_feedback_summary)
                            SettingsSection.Playback -> stringResource(R.string.settings_playback_summary)
                            SettingsSection.Lyrics -> stringResource(R.string.settings_lyrics_summary)
                            SettingsSection.Library -> stringResource(R.string.tracks_count, trackCount)
                            SettingsSection.Downloads -> downloadsSummary(downloadFormat)
                            SettingsSection.Updates -> updateStatus
                        },
                        onClick = { onOpen(s) },
                    )
                }
            }
        }
    }
    item(key = "computer") {
        val paired by PcRemote.paired.collectAsStateWithLifecycle()
        Group(null) {
            listOf<@Composable (Shape) -> Unit>({ shape ->
                SectionItem(
                    shape = shape,
                    icon = PlayerIcons.Desktop,
                    title = stringResource(R.string.pc_open),
                    summary = paired?.name ?: stringResource(R.string.pc_summary_none),
                    onClick = onOpenComputer,
                )
            })
        }
    }
    item(key = "about") {
        Group(stringResource(R.string.settings_about)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    ItemContainer(shape) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.settings_version, version),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.settings_lyrics_source),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }
    }
}

private fun LazyListScope.appearance(settings: AppSettings, onUpdate: Update) {
    item(key = "theme") {
        Group(stringResource(R.string.settings_theme)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    ItemContainer(shape) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            ConnectedChoice(
                                options = listOf(
                                    stringResource(R.string.theme_system),
                                    stringResource(R.string.theme_light),
                                    stringResource(R.string.theme_dark),
                                ),
                                selected = settings.theme.ordinal,
                                onSelect = { i -> onUpdate { it.copy(theme = ThemeMode.entries[i]) } },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }
                },
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(R.string.settings_nothing),
                        subtitle = stringResource(R.string.settings_nothing_desc),
                        checked = settings.nothingStyle,
                        onChange = { v -> onUpdate { it.copy(nothingStyle = v) } },
                    )
                },
            )
        }
    }
    item(key = "font") {
        Group(stringResource(R.string.settings_font)) {
            AppFont.entries.map { font ->
                { shape ->
                    FontItem(
                        shape = shape,
                        font = font,
                        selected = settings.font == font,
                        onClick = { onUpdate { it.copy(font = font) } },
                    )
                }
            }
        }
    }
    // The Nothing style brings its own palette and progress bar, and no glass.
    if (!settings.nothingStyle) {
        item(key = "colors") {
            Group(stringResource(R.string.settings_colors)) {
                listOf<@Composable (Shape) -> Unit>(
                    { shape -> ColorsItem(shape, settings, onUpdate) },
                    { shape ->
                        SwitchItem(
                            shape = shape,
                            title = stringResource(R.string.settings_black),
                            subtitle = stringResource(R.string.settings_black_desc),
                            checked = settings.pureBlack,
                            onChange = { v -> onUpdate { it.copy(pureBlack = v) } },
                        )
                    },
                )
            }
        }
    }
    item(key = "interface") {
        Group(stringResource(R.string.settings_interface)) {
            buildList<@Composable (Shape) -> Unit> {
                if (!settings.nothingStyle) add { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(R.string.settings_glass),
                        subtitle = stringResource(R.string.settings_glass_desc),
                        checked = settings.glass,
                        onChange = { v -> onUpdate { it.copy(glass = v) } },
                    )
                }
                add { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(R.string.settings_floating_nav),
                        subtitle = stringResource(R.string.settings_floating_nav_desc),
                        checked = settings.floatingNav,
                        onChange = { v -> onUpdate { it.copy(floatingNav = v) } },
                    )
                }
                if (!settings.nothingStyle) add { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(R.string.settings_wavy),
                        subtitle = stringResource(R.string.settings_wavy_desc),
                        checked = settings.wavyProgress,
                        onChange = { v -> onUpdate { it.copy(wavyProgress = v) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorsItem(shape: Shape, settings: AppSettings, onUpdate: Update) {
    // "Wallpaper" only exists on Android 12+, so the options adapt to the device.
    val sources = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(ColorSource.Wallpaper)
        add(ColorSource.AlbumArt)
        add(ColorSource.Custom)
    }
    val source = settings.colorSource.takeIf { it in sources } ?: ColorSource.Custom
    ItemContainer(shape) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                stringResource(
                    when (source) {
                        ColorSource.Wallpaper -> R.string.settings_colors_wallpaper_desc
                        ColorSource.AlbumArt -> R.string.settings_colors_album_desc
                        ColorSource.Custom -> R.string.settings_colors_custom_desc
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ConnectedChoice(
                options = sources.map {
                    stringResource(
                        when (it) {
                            ColorSource.Wallpaper -> R.string.colors_wallpaper
                            ColorSource.AlbumArt -> R.string.colors_album
                            ColorSource.Custom -> R.string.colors_custom
                        }
                    )
                },
                selected = sources.indexOf(source),
                onSelect = { i -> onUpdate { it.copy(colorSource = sources[i]) } },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            if (source != ColorSource.Wallpaper) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(
                        if (source == ColorSource.AlbumArt) R.string.settings_colors_fallback
                        else R.string.settings_colors_pick
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SwatchRow(
                    selected = settings.seedColor,
                    onSelect = { seed -> onUpdate { it.copy(seedColor = seed) } },
                )
                Spacer(Modifier.height(12.dp))
                HuePicker(
                    seed = settings.seedColor,
                    onSeed = { seed -> onUpdate { it.copy(seedColor = seed) } },
                )
            }
            Spacer(Modifier.height(16.dp))
            PalettePreview()
        }
    }
}

private fun LazyListScope.feedback(settings: AppSettings, onUpdate: Update) {
    item(key = "feedback") {
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(R.string.settings_haptics),
                        subtitle = stringResource(R.string.settings_haptics_desc),
                        checked = settings.haptics,
                        onChange = { v -> onUpdate { it.copy(haptics = v) } },
                    )
                },
                { shape ->
                    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
                    SwitchItem(
                        shape = shape,
                        title = stringResource(R.string.settings_smooth),
                        subtitle = stringResource(
                            if (supported) R.string.settings_smooth_desc else R.string.settings_smooth_unavailable
                        ),
                        checked = settings.smoothAnimations && supported,
                        enabled = supported,
                        onChange = { v -> onUpdate { it.copy(smoothAnimations = v) } },
                    )
                },
            )
        }
    }
}

private fun LazyListScope.playback(settings: AppSettings, onUpdate: Update) {
    item(key = "playback") {
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(R.string.settings_offload),
                        subtitle = stringResource(R.string.settings_offload_desc),
                        checked = settings.audioOffload,
                        onChange = { v -> onUpdate { it.copy(audioOffload = v) } },
                    )
                },
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(R.string.settings_silence),
                        subtitle = stringResource(R.string.settings_silence_desc),
                        checked = settings.skipSilence,
                        onChange = { v -> onUpdate { it.copy(skipSilence = v) } },
                    )
                },
            )
        }
    }
}

private fun LazyListScope.lyrics(
    settings: AppSettings,
    onUpdate: Update,
    lyricsCacheSize: suspend () -> Long,
    onClearLyricsCache: () -> Unit,
) {
    item(key = "lyrics") {
        val context = LocalContext.current
        var cacheRefresh by remember { mutableIntStateOf(0) }
        val cacheBytes by produceState(0L, cacheRefresh) { value = lyricsCacheSize() }
        val cleared = stringResource(R.string.settings_cache_cleared)
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(R.string.settings_wifi_only),
                        subtitle = stringResource(R.string.settings_wifi_only_desc),
                        checked = settings.lyricsWifiOnly,
                        onChange = { v -> onUpdate { it.copy(lyricsWifiOnly = v) } },
                    )
                },
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(R.string.settings_sync_controls),
                        subtitle = stringResource(R.string.settings_sync_controls_desc),
                        checked = settings.lyricsSyncControls,
                        onChange = { v -> onUpdate { it.copy(lyricsSyncControls = v) } },
                    )
                },
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(R.string.settings_clear_cache),
                        subtitle = stringResource(
                            R.string.settings_cache_size,
                            Formatter.formatShortFileSize(context, cacheBytes),
                        ),
                        onClick = {
                            onClearLyricsCache()
                            cacheRefresh++
                            Toast.makeText(context, cleared, Toast.LENGTH_SHORT).show()
                        },
                    )
                },
            )
        }
    }
}

private fun LazyListScope.library(trackCount: Int, onRescan: () -> Unit) {
    item(key = "library") {
        val context = LocalContext.current
        val done = stringResource(R.string.settings_rescan_done)
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(R.string.settings_rescan),
                        subtitle = stringResource(R.string.settings_rescan_desc) + " · " +
                            stringResource(R.string.tracks_count, trackCount),
                        onClick = {
                            onRescan()
                            Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                        },
                    )
                },
            )
        }
    }
}

/** "M4A · yt-dlp 2025.10.22" on the section list. */
@Composable
private fun downloadsSummary(format: DownloadFormat): String {
    val version by Downloader.engineVersion.collectAsStateWithLifecycle()
    val name = stringResource(if (format == DownloadFormat.M4a) R.string.download_format_m4a else R.string.download_format_opus)
    return listOfNotNull(name, version?.let { stringResource(R.string.download_engine, it) }).joinToString(" · ")
}

private fun LazyListScope.downloads(settings: AppSettings, onUpdate: Update) {
    item(key = "download_format") {
        Group(stringResource(R.string.download_format)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    ItemContainer(shape) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            ConnectedChoice(
                                options = listOf(
                                    stringResource(R.string.download_format_m4a),
                                    stringResource(R.string.download_format_opus),
                                ),
                                selected = settings.downloadFormat.ordinal,
                                onSelect = { i -> onUpdate { it.copy(downloadFormat = DownloadFormat.entries[i]) } },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(
                                    if (settings.downloadFormat == DownloadFormat.M4a) R.string.download_format_m4a_desc
                                    else R.string.download_format_opus_desc
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }
    }
    item(key = "download_engine") {
        LaunchedEffect(Unit) { Downloader.warmUp() }
        val version by Downloader.engineVersion.collectAsStateWithLifecycle()
        val engineUpdate by Downloader.engineUpdate.collectAsStateWithLifecycle()
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(R.string.download_engine_update),
                        subtitle = when (engineUpdate) {
                            EngineUpdate.Idle -> version?.let { stringResource(R.string.download_engine, it) }
                                ?: stringResource(R.string.download_engine_unknown)
                            EngineUpdate.Running -> stringResource(R.string.download_engine_updating)
                            EngineUpdate.Updated -> stringResource(R.string.download_engine_updated)
                            EngineUpdate.UpToDate -> stringResource(R.string.download_engine_latest)
                            EngineUpdate.Failed -> stringResource(R.string.download_engine_failed)
                        },
                        onClick = { if (engineUpdate != EngineUpdate.Running) Downloader.updateEngine() },
                    )
                },
            )
        }
    }
}

private fun LazyListScope.updates(
    settings: AppSettings,
    onUpdate: Update,
    updateState: UpdateState,
    updateStatus: String,
    onCheckUpdates: () -> Unit,
    onShowUpdate: () -> Unit,
) {
    item(key = "updates") {
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(R.string.settings_updates_auto),
                        subtitle = stringResource(R.string.settings_updates_auto_desc),
                        checked = settings.checkUpdates,
                        onChange = { v -> onUpdate { it.copy(checkUpdates = v) } },
                    )
                },
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(R.string.settings_check_updates),
                        subtitle = updateStatus,
                        onClick = {
                            when (updateState) {
                                UpdateState.Idle, UpdateState.UpToDate, UpdateState.CheckFailed -> onCheckUpdates()
                                UpdateState.Checking -> Unit
                                else -> onShowUpdate()
                            }
                        },
                    )
                },
            )
        }
    }
}

/** A titled run of items; without a title it just leaves a little room above. */
@Composable
internal fun Group(title: String?, items: () -> List<@Composable (Shape) -> Unit>) {
    val list = items()
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            list.forEachIndexed { i, item -> item(groupShape(i, list.size)) }
        }
    }
}

@Composable
internal fun ItemContainer(shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) { content() }
}

/** A section on the main settings list: icon, name, a hint of what's inside, and a chevron. */
@Composable
private fun SectionItem(shape: Shape, icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    ItemContainer(shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(PlayerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One font to pick: its name and a sample, both set in that font. */
@Composable
private fun FontItem(shape: Shape, font: AppFont, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val type = typographyFor(font)
    ItemContainer(shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                }
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when (font) {
                            AppFont.System -> R.string.font_system
                            AppFont.SystemMono -> R.string.font_system_mono
                            AppFont.JetBrainsMono -> R.string.font_jetbrains_mono
                            AppFont.IbmPlexMono -> R.string.font_ibm_plex_mono
                        }
                    ),
                    style = type.titleMedium,
                )
                Text(
                    stringResource(R.string.font_sample),
                    style = type.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Spacer(Modifier.width(16.dp))
                Icon(PlayerIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SwitchItem(
    shape: Shape,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val toggle: (Boolean) -> Unit = { v ->
        haptics.performHapticFeedback(if (v) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onChange(v)
    }
    ItemContainer(shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { toggle(!checked) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = toggle,
                enabled = enabled,
                thumbContent = if (checked) {
                    { Icon(PlayerIcons.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ActionItem(shape: Shape, title: String, subtitle: String, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    ItemContainer(shape) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                }
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
