package com.ku.pierdoon.ui.settings

import com.ku.pierdoon.data.download.EngineUpdate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import com.ku.pierdoon.ui.library.CONTENT_MAX_WIDTH
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import com.ku.pierdoon.BuildConfig
import com.ku.pierdoon.playback.AudioEngine
import com.ku.pierdoon.ui.desktop.Files
import com.ku.pierdoon.ui.desktop.Toast
import com.ku.pierdoon.ui.desktop.formatFileSize
import org.jetbrains.compose.resources.StringResource
import java.io.File
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.resources.*
import androidx.compose.material3.Slider
import com.ku.pierdoon.data.AppFont
import com.ku.pierdoon.data.AppLanguage
import com.ku.pierdoon.data.AppSettings
import com.ku.pierdoon.data.MAX_CROSSFADE_SECONDS
import kotlin.math.roundToInt
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.NAV_MAX_WIDTH
import com.ku.pierdoon.data.NAV_MIN_WIDTH
import com.ku.pierdoon.data.ThemeMode
import com.ku.pierdoon.data.update.UpdateState
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.playback.DiscordPresence
import com.ku.pierdoon.playback.PlaybackSpeed
import com.ku.pierdoon.ui.components.ConnectedChoice
import com.ku.pierdoon.ui.components.PlayerIcons
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

/** A page of settings, opened from the list on the Settings tab; [music] ones come first, under their own heading. */
enum class SettingsSection(val music: Boolean) {
    Library(true), Downloads(true), Playback(true), Lyrics(true),
    Appearance(false), Window(false), Integrations(false), Updates(false),
}

private typealias Update = ((AppSettings) -> AppSettings) -> Unit

/**
 * The Settings tab: the list of sections when [section] is null, otherwise that section's page
 * with a back arrow. Both live here so they share the same state and callbacks.
 */
@Composable
fun SettingsScreen(
    section: SettingsSection?,
    onOpen: (SettingsSection) -> Unit,
    onBack: () -> Unit,
    settings: AppSettings,
    onUpdate: Update,
    trackCount: Int,
    lyricsCacheSize: suspend () -> Long,
    onClearLyricsCache: () -> Unit,
    onRescan: () -> Unit,
    onAddFolder: (File) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onExcludeFolder: (File) -> Unit,
    onUnexcludeFolder: (String) -> Unit,
    onDownloadFolder: (File?) -> Unit,
    updateState: UpdateState,
    onCheckUpdates: () -> Unit,
    onShowUpdate: () -> Unit,
    bottomInset: Dp,
) {
    val version = BuildConfig.VERSION_NAME
    val updateStatus = when (updateState) {
        UpdateState.Checking -> stringResource(Res.string.update_checking)
        UpdateState.UpToDate -> stringResource(Res.string.update_up_to_date, version)
        UpdateState.CheckFailed -> stringResource(Res.string.update_check_failed)
        is UpdateState.Available -> stringResource(Res.string.update_available, updateState.release.version)
        is UpdateState.Downloading -> stringResource(Res.string.update_downloading)
        is UpdateState.Installing -> stringResource(Res.string.update_confirm)
        is UpdateState.InstallFailed -> stringResource(Res.string.update_failed)
        UpdateState.Idle -> stringResource(Res.string.update_current, version)
    }

    AppScaffold(
        title = stringResource(section?.title() ?: Res.string.nav_settings),
        onBack = if (section != null) onBack else null,
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = listBottomPadding(bottomInset),
            ),
            // A readable width on a wide window, lined up under the title.
            modifier = Modifier.fillMaxHeight().widthIn(max = CONTENT_MAX_WIDTH),
        ) {
            when (section) {
                null -> sectionList(onOpen, trackCount, updateStatus, version)
                SettingsSection.Appearance -> appearance(settings, onUpdate)
                SettingsSection.Window -> window(settings, onUpdate)
                SettingsSection.Playback -> playback(settings, onUpdate)
                SettingsSection.Lyrics -> lyrics(settings, onUpdate, lyricsCacheSize, onClearLyricsCache)
                SettingsSection.Library -> library(settings, trackCount, onRescan, onAddFolder, onRemoveFolder, onExcludeFolder, onUnexcludeFolder)
                SettingsSection.Downloads -> downloads(settings, onUpdate, onDownloadFolder)
                SettingsSection.Integrations -> integrations(settings, onUpdate)
                SettingsSection.Updates -> updates(settings, onUpdate, updateState, updateStatus, onCheckUpdates, onShowUpdate)
            }
        }
    }
}

private fun SettingsSection.title(): StringResource = when (this) {
    SettingsSection.Appearance -> Res.string.settings_appearance
    SettingsSection.Window -> Res.string.settings_desktop
    SettingsSection.Playback -> Res.string.settings_playback
    SettingsSection.Lyrics -> Res.string.settings_lyrics
    SettingsSection.Library -> Res.string.settings_library
    SettingsSection.Downloads -> Res.string.settings_downloads
    SettingsSection.Integrations -> Res.string.settings_integrations
    SettingsSection.Updates -> Res.string.settings_updates
}

private fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.Appearance -> PlayerIcons.Palette
    SettingsSection.Window -> PlayerIcons.Desktop
    SettingsSection.Playback -> PlayerIcons.Play
    SettingsSection.Lyrics -> PlayerIcons.Lyrics
    SettingsSection.Library -> PlayerIcons.Library
    SettingsSection.Downloads -> PlayerIcons.Download
    SettingsSection.Integrations -> PlayerIcons.Extension
    SettingsSection.Updates -> PlayerIcons.Refresh
}

private fun LazyListScope.sectionList(
    onOpen: (SettingsSection) -> Unit,
    trackCount: Int,
    updateStatus: String,
    version: String,
) {
    for (music in listOf(true, false)) item(key = if (music) "sections_music" else "sections_app") {
        Group(stringResource(if (music) Res.string.settings_group_music else Res.string.settings_group_app)) {
            SettingsSection.entries.filter { it.music == music }.map { s ->
                { shape ->
                    SectionItem(
                        shape = shape,
                        icon = s.icon(),
                        title = stringResource(s.title()),
                        summary = when (s) {
                            SettingsSection.Appearance -> stringResource(Res.string.settings_appearance_summary)
                            SettingsSection.Window -> stringResource(Res.string.settings_desktop_summary)
                            SettingsSection.Playback -> stringResource(Res.string.settings_playback_summary)
                            SettingsSection.Lyrics -> stringResource(Res.string.settings_lyrics_summary)
                            SettingsSection.Library -> stringResource(
                                Res.string.settings_library_summary,
                                stringResource(Res.string.tracks_count, trackCount),
                            )
                            SettingsSection.Downloads -> stringResource(Res.string.settings_downloads_summary)
                            SettingsSection.Integrations -> stringResource(Res.string.settings_integrations_summary)
                            SettingsSection.Updates -> updateStatus
                        },
                        onClick = { onOpen(s) },
                    )
                }
            }
        }
    }
    item(key = "about") {
        Group(stringResource(Res.string.settings_about)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    ItemContainer(shape) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(Res.string.settings_version, version),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(Res.string.settings_lyrics_source),
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
        Group(stringResource(Res.string.settings_theme)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    ItemContainer(shape) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            ConnectedChoice(
                                options = listOf(
                                    stringResource(Res.string.theme_system),
                                    stringResource(Res.string.theme_light),
                                    stringResource(Res.string.theme_dark),
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
                        title = stringResource(Res.string.settings_nothing),
                        subtitle = stringResource(Res.string.settings_nothing_desc),
                        checked = settings.nothingStyle,
                        onChange = { v -> onUpdate { it.copy(nothingStyle = v) } },
                    )
                },
            )
        }
    }
    item(key = "language") {
        Group(stringResource(Res.string.settings_language)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape -> LanguageItem(shape, settings.language) { l -> onUpdate { it.copy(language = l) } } },
            )
        }
    }
    item(key = "font") {
        Group(stringResource(Res.string.settings_font)) {
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
            Group(stringResource(Res.string.settings_colors)) {
                listOf<@Composable (Shape) -> Unit>(
                    { shape -> ColorsItem(shape, settings, onUpdate) },
                    { shape ->
                        SwitchItem(
                            shape = shape,
                            title = stringResource(Res.string.settings_black),
                            subtitle = stringResource(Res.string.settings_black_desc),
                            checked = settings.pureBlack,
                            onChange = { v -> onUpdate { it.copy(pureBlack = v) } },
                        )
                    },
                )
            }
        }
    }
    item(key = "interface") {
        Group(stringResource(Res.string.settings_interface)) {
            buildList<@Composable (Shape) -> Unit> {
                if (!settings.nothingStyle) add { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_glass),
                        subtitle = stringResource(Res.string.settings_glass_desc),
                        checked = settings.glass,
                        onChange = { v -> onUpdate { it.copy(glass = v) } },
                    )
                }
                add { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_floating_nav),
                        subtitle = stringResource(Res.string.settings_floating_nav_desc),
                        checked = settings.navWidth <= NAV_MIN_WIDTH,
                        onChange = { v -> onUpdate { it.copy(navWidth = if (v) NAV_MIN_WIDTH else NAV_MAX_WIDTH) } },
                    )
                }
                if (!settings.nothingStyle) add { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_wavy),
                        subtitle = stringResource(Res.string.settings_wavy_desc),
                        checked = settings.wavyProgress,
                        onChange = { v -> onUpdate { it.copy(wavyProgress = v) } },
                    )
                }
            }
        }
    }
}

/** System, Russian, English: each language under its own name, so it can be found from the other one. */
@Composable
private fun LanguageItem(shape: Shape, language: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    ItemContainer(shape) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            ConnectedChoice(
                options = listOf(
                    stringResource(Res.string.language_system),
                    stringResource(Res.string.language_russian),
                    stringResource(Res.string.language_english),
                ),
                selected = language.ordinal,
                onSelect = { i -> onSelect(AppLanguage.entries[i]) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

/** Slowed, as recorded or nightcore: the speed, pitch going along with it. */
@Composable
private fun SpeedItem(shape: Shape, speed: Float, onChange: (Float) -> Unit) {
    ItemContainer(shape) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                stringResource(Res.string.speed_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ConnectedChoice(
                options = PlaybackSpeed.options.map(PlaybackSpeed::label),
                selected = PlaybackSpeed.options.indexOf(speed),
                onSelect = { i -> onChange(PlaybackSpeed.options[i]) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

/** The crossfade length: off, or 1 to [MAX_CROSSFADE_SECONDS] seconds. */
@Composable
private fun CrossfadeItem(shape: Shape, seconds: Int, onChange: (Int) -> Unit) {
    // Follows the drag; the setting (and the player) only takes the value on release.
    var dragged by remember(seconds) { mutableIntStateOf(seconds) }
    ItemContainer(shape) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.settings_crossfade_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    if (dragged == 0) stringResource(Res.string.crossfade_off)
                    else stringResource(Res.string.crossfade_seconds, dragged),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = dragged.toFloat(),
                onValueChange = { dragged = it.roundToInt() },
                onValueChangeFinished = { onChange(dragged) },
                valueRange = 0f..MAX_CROSSFADE_SECONDS.toFloat(),
                steps = MAX_CROSSFADE_SECONDS - 1,
            )
        }
    }
}

@Composable
private fun ColorsItem(shape: Shape, settings: AppSettings, onUpdate: Update) {
    // "Wallpaper" here is Windows' accent colour.
    val sources = buildList {
        add(ColorSource.Wallpaper)
        add(ColorSource.AlbumArt)
        add(ColorSource.Custom)
    }
    val source = settings.colorSource.takeIf { it in sources } ?: ColorSource.Custom
    ItemContainer(shape) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                stringResource(
                    when (source) {
                        ColorSource.Wallpaper -> Res.string.settings_colors_wallpaper_desc
                        ColorSource.AlbumArt -> Res.string.settings_colors_album_desc
                        ColorSource.Custom -> Res.string.settings_colors_custom_desc
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
                            ColorSource.Wallpaper -> Res.string.colors_wallpaper
                            ColorSource.AlbumArt -> Res.string.colors_album
                            ColorSource.Custom -> Res.string.colors_custom
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
                        if (source == ColorSource.AlbumArt) Res.string.settings_colors_fallback
                        else Res.string.settings_colors_pick
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

private fun LazyListScope.window(settings: AppSettings, onUpdate: Update) {
    item(key = "window") {
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.close_to_tray),
                        subtitle = stringResource(Res.string.close_to_tray_desc),
                        checked = settings.closeToTray,
                        onChange = { v -> onUpdate { it.copy(closeToTray = v) } },
                    )
                },
            )
        }
    }
}

private fun LazyListScope.playback(settings: AppSettings, onUpdate: Update) {
    item(key = "output") {
        // Listing the outputs asks every sound driver; once per visit is plenty.
        val devices = remember { AudioEngine.outputDevices() }
        val options = listOf<String?>(null) + devices
        Group(stringResource(Res.string.output_device)) {
            options.map { name ->
                { shape ->
                    ChoiceItem(
                        shape = shape,
                        title = name ?: stringResource(Res.string.output_default),
                        selected = settings.outputDevice == name || (name == null && settings.outputDevice !in devices),
                        onClick = { onUpdate { it.copy(outputDevice = name) } },
                    )
                }
            }
        }
    }
    item(key = "speed") {
        Group(stringResource(Res.string.speed)) {
            listOf<@Composable (Shape) -> Unit>({ shape -> SpeedItem(shape, settings.playbackSpeed) { v -> onUpdate { it.copy(playbackSpeed = v) } } })
        }
    }
    item(key = "crossfade") {
        Group(stringResource(Res.string.settings_crossfade)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    CrossfadeItem(shape, settings.crossfadeSeconds) { s -> onUpdate { it.copy(crossfadeSeconds = s) } }
                },
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_crossfade_albums),
                        subtitle = stringResource(Res.string.settings_crossfade_albums_desc),
                        checked = settings.crossfadeAlbums,
                        enabled = settings.crossfadeSeconds > 0,
                        onChange = { v -> onUpdate { it.copy(crossfadeAlbums = v) } },
                    )
                },
            )
        }
    }
    item(key = "playback") {
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_silence),
                        subtitle = stringResource(Res.string.settings_silence_desc),
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
        var cacheRefresh by remember { mutableIntStateOf(0) }
        val cacheBytes by produceState(0L, cacheRefresh) { value = lyricsCacheSize() }
        val cleared = stringResource(Res.string.settings_cache_cleared)
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_wifi_only),
                        subtitle = stringResource(Res.string.settings_wifi_only_desc),
                        checked = settings.lyricsWifiOnly,
                        onChange = { v -> onUpdate { it.copy(lyricsWifiOnly = v) } },
                    )
                },
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_sync_controls),
                        subtitle = stringResource(Res.string.settings_sync_controls_desc),
                        checked = settings.lyricsSyncControls,
                        onChange = { v -> onUpdate { it.copy(lyricsSyncControls = v) } },
                    )
                },
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_clear_cache),
                        subtitle = stringResource(
                            Res.string.settings_cache_size,
                            formatFileSize(cacheBytes),
                        ),
                        onClick = {
                            onClearLyricsCache()
                            cacheRefresh++
                            Toast.show(cleared)
                        },
                    )
                },
            )
        }
    }
}

private fun LazyListScope.library(
    settings: AppSettings,
    trackCount: Int,
    onRescan: () -> Unit,
    onAddFolder: (File) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onExcludeFolder: (File) -> Unit,
    onUnexcludeFolder: (String) -> Unit,
) {
    item(key = "folders") {
        val chooseTitle = stringResource(Res.string.choose_folder)
        Group(stringResource(Res.string.music_folders)) {
            buildList<@Composable (Shape) -> Unit> {
                settings.musicFolders.forEach { path ->
                    add { shape ->
                        FolderItem(
                            shape = shape,
                            path = path,
                            // The last folder stays: with none, the library would simply be empty.
                            onRemove = if (settings.musicFolders.size > 1) ({ onRemoveFolder(path) }) else null,
                        )
                    }
                }
                add { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.add_folder),
                        subtitle = stringResource(Res.string.music_folders_desc),
                        onClick = {
                            val start = settings.musicFolders.firstOrNull()?.let(::File)
                            Files.pickFolder(chooseTitle, start)?.let(onAddFolder)
                        },
                    )
                }
            }
        }
    }
    item(key = "excluded") {
        val chooseTitle = stringResource(Res.string.choose_excluded_folder)
        Group(stringResource(Res.string.excluded_folders)) {
            buildList<@Composable (Shape) -> Unit> {
                settings.excludedFolders.forEach { path ->
                    add { shape -> FolderItem(shape = shape, path = path, onRemove = { onUnexcludeFolder(path) }) }
                }
                add { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.exclude_folder),
                        subtitle = stringResource(
                            if (settings.excludedFolders.isEmpty()) Res.string.excluded_folders_none
                            else Res.string.exclude_folder_desc
                        ),
                        onClick = {
                            val start = settings.musicFolders.firstOrNull()?.let(::File)
                            Files.pickFolder(chooseTitle, start)?.let(onExcludeFolder)
                        },
                    )
                }
            }
        }
    }
    item(key = "library") {
        val done = stringResource(Res.string.settings_rescan_done)
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_rescan),
                        subtitle = stringResource(Res.string.settings_rescan_desc) + " · " +
                            stringResource(Res.string.tracks_count, trackCount),
                        onClick = {
                            onRescan()
                            Toast.show(done)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun FolderItem(shape: Shape, path: String, onRemove: (() -> Unit)?, removeLabel: String? = null) {
    ItemContainer(shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(PlayerIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(
                path,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(PlayerIcons.Close, contentDescription = removeLabel ?: stringResource(Res.string.remove_folder))
                }
            }
        }
    }
}

@Composable
private fun ChoiceItem(shape: Shape, title: String, selected: Boolean, onClick: () -> Unit) {
    ItemContainer(shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (selected) {
                Spacer(Modifier.width(16.dp))
                Icon(PlayerIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun LazyListScope.downloads(settings: AppSettings, onUpdate: Update, onDownloadFolder: (File?) -> Unit) {
    item(key = "download_folder") {
        val chooseTitle = stringResource(Res.string.choose_download_folder)
        Group(stringResource(Res.string.download_folder)) {
            buildList<@Composable (Shape) -> Unit> {
                add { shape ->
                    FolderItem(
                        shape = shape,
                        path = settings.downloadFolder ?: Downloader.defaultFolder.absolutePath,
                        onRemove = if (settings.downloadFolder != null) ({ onDownloadFolder(null) }) else null,
                        removeLabel = stringResource(Res.string.download_folder_reset),
                    )
                }
                add { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.download_folder_choose),
                        subtitle = stringResource(Res.string.download_folder_choose_desc),
                        onClick = { Files.pickFolder(chooseTitle, Downloader.folder.takeIf { it.isDirectory })?.let(onDownloadFolder) },
                    )
                }
            }
        }
    }
    item(key = "download_format") {
        Group(stringResource(Res.string.download_format)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    ItemContainer(shape) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            ConnectedChoice(
                                options = listOf(
                                    stringResource(Res.string.download_format_m4a),
                                    stringResource(Res.string.download_format_opus),
                                ),
                                selected = settings.downloadFormat.ordinal,
                                onSelect = { i -> onUpdate { it.copy(downloadFormat = DownloadFormat.entries[i]) } },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(
                                    if (settings.downloadFormat == DownloadFormat.M4a) Res.string.download_format_m4a_desc
                                    else Res.string.download_format_opus_desc
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
        val version by Downloader.engineVersion.collectAsState()
        val engineUpdate by Downloader.engineUpdate.collectAsState()
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.download_engine_update),
                        subtitle = when (engineUpdate) {
                            EngineUpdate.Idle -> version?.let { stringResource(Res.string.download_engine, it) }
                                ?: stringResource(Res.string.download_engine_unknown)
                            EngineUpdate.Running -> stringResource(Res.string.download_engine_updating)
                            EngineUpdate.Updated -> stringResource(Res.string.download_engine_updated)
                            EngineUpdate.UpToDate -> stringResource(Res.string.download_engine_latest)
                            EngineUpdate.Failed -> stringResource(Res.string.download_engine_failed)
                        },
                        onClick = { if (engineUpdate != EngineUpdate.Running) Downloader.updateEngine() },
                    )
                },
            )
        }
    }
}

private fun LazyListScope.integrations(settings: AppSettings, onUpdate: Update) {
    if (DiscordPresence.available) item(key = "discord") {
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.discord_presence),
                        subtitle = stringResource(Res.string.discord_presence_desc),
                        checked = settings.discordPresence,
                        onChange = { v -> onUpdate { it.copy(discordPresence = v) } },
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
                        title = stringResource(Res.string.settings_updates_auto),
                        subtitle = stringResource(Res.string.settings_updates_auto_desc),
                        checked = settings.checkUpdates,
                        onChange = { v -> onUpdate { it.copy(checkUpdates = v) } },
                    )
                },
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_check_updates),
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
                            AppFont.System -> Res.string.font_system
                            AppFont.SystemMono -> Res.string.font_system_mono
                            AppFont.JetBrainsMono -> Res.string.font_jetbrains_mono
                            AppFont.IbmPlexMono -> Res.string.font_ibm_plex_mono
                        }
                    ),
                    style = type.titleMedium,
                )
                Text(
                    stringResource(Res.string.font_sample),
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
internal fun SwitchItem(
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
