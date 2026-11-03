package com.ku.pierdoon.ui.settings

import com.ku.pierdoon.ui.library.TWO_PANE_WIDTH
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.RectangleShape
import com.ku.pierdoon.data.CookieBrowser
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.BuildConfig
import com.ku.pierdoon.radio.RadioMemory
import com.ku.pierdoon.platform.AppDirs
import com.ku.pierdoon.data.AppFont
import com.ku.pierdoon.data.AppLanguage
import com.ku.pierdoon.data.AppSettings
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.CustomFonts
import com.ku.pierdoon.data.NAV_MAX_WIDTH
import com.ku.pierdoon.data.NAV_MIN_WIDTH
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.ThemeMode
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.data.download.EngineUpdate
import com.ku.pierdoon.data.update.UpdateState
import com.ku.pierdoon.playback.AudioEngine
import com.ku.pierdoon.playback.DiscordPresence
import com.ku.pierdoon.playback.EqPreset
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.desktop.Files
import com.ku.pierdoon.ui.desktop.Toast
import com.ku.pierdoon.ui.desktop.formatFileSize
import com.ku.pierdoon.ui.library.CONTENT_MAX_WIDTH
import com.ku.pierdoon.ui.library.AppScaffold
import com.ku.pierdoon.ui.library.listBottomPadding
import com.ku.pierdoon.ui.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** The headings the sections are listed under. */
enum class SectionGroup(val title: StringResource) {
    Music(Res.string.settings_group_music),
    Sound(Res.string.settings_group_sound),
    App(Res.string.settings_group_app),
}

/** A page of settings, opened from the list on the Settings tab, in the order they're listed. */
enum class SettingsSection(val group: SectionGroup) {
    Library(SectionGroup.Music), Lyrics(SectionGroup.Music), Downloads(SectionGroup.Music),
    Playback(SectionGroup.Sound), Equalizer(SectionGroup.Sound),
    Appearance(SectionGroup.App), System(SectionGroup.App), Updates(SectionGroup.App), Beta(SectionGroup.App), About(SectionGroup.App),
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

    // One page's items, whichever layout holds them.
    val page: LazyListScope.(SettingsSection) -> Unit = { s ->
        when (s) {
            SettingsSection.Appearance -> appearance(settings, onUpdate)
            SettingsSection.System -> system(settings, onUpdate)
            SettingsSection.Playback -> playback(settings, onUpdate)
            SettingsSection.Equalizer -> equalizer(settings, onUpdate)
            SettingsSection.Lyrics -> lyrics(settings, onUpdate, lyricsCacheSize, onClearLyricsCache)
            SettingsSection.Library -> library(settings, trackCount, onRescan, onAddFolder, onRemoveFolder, onExcludeFolder, onUnexcludeFolder)
            SettingsSection.Downloads -> downloads(settings, onUpdate, onDownloadFolder)
            SettingsSection.Updates -> updates(settings, onUpdate, updateState, updateStatus, onCheckUpdates, onShowUpdate)
            SettingsSection.Beta -> beta(settings, onUpdate)
            SettingsSection.About -> item(key = "about") { AboutContent(version) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TWO_PANE_WIDTH) {
            // Wide: the sections stay listed on the left and the one picked opens beside them.
            var picked by remember(section) { mutableStateOf(section ?: SettingsSection.entries.first()) }
            AppScaffold(
                title = stringResource(Res.string.nav_settings),
                onBack = null,
            ) { inner ->
                val padding = PaddingValues(top = inner.calculateTopPadding(), bottom = listBottomPadding(bottomInset))
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    LazyColumn(contentPadding = padding, modifier = Modifier.width(SECTION_LIST_WIDTH).fillMaxHeight()) {
                        sectionList({ picked = it }, settings, trackCount, updateStatus, version, selected = picked)
                    }
                    LazyColumn(contentPadding = padding, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        item(key = "pageTitle_${picked.name}") {
                            Text(
                                stringResource(picked.title()),
                                style = MaterialTheme.typography.headlineMediumEmphasized,
                                modifier = Modifier.padding(start = 20.dp, top = 4.dp),
                            )
                        }
                        page(picked)
                    }
                }
            }
            return@BoxWithConstraints
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
                modifier = Modifier.fillMaxSize(),
            ) {
                if (section == null) sectionList(onOpen, settings, trackCount, updateStatus, version) else page(section)
            }
        }
    }
}

/** The list of sections beside an open one on a wide window. */
private val SECTION_LIST_WIDTH = 400.dp

private fun SettingsSection.title(): StringResource = when (this) {
    SettingsSection.Appearance -> Res.string.settings_appearance
    SettingsSection.System -> Res.string.settings_system
    SettingsSection.Playback -> Res.string.settings_playback
    SettingsSection.Equalizer -> Res.string.settings_equalizer
    SettingsSection.Lyrics -> Res.string.settings_lyrics
    SettingsSection.Library -> Res.string.settings_library
    SettingsSection.Downloads -> Res.string.settings_downloads
    SettingsSection.Updates -> Res.string.settings_updates
    SettingsSection.Beta -> Res.string.settings_beta
    SettingsSection.About -> Res.string.settings_about
}

private fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.Appearance -> PlayerIcons.Palette
    SettingsSection.System -> PlayerIcons.Desktop
    SettingsSection.Playback -> PlayerIcons.Play
    SettingsSection.Equalizer -> PlayerIcons.Equalizer
    SettingsSection.Lyrics -> PlayerIcons.Lyrics
    SettingsSection.Library -> PlayerIcons.Library
    SettingsSection.Downloads -> PlayerIcons.Download
    SettingsSection.Updates -> PlayerIcons.Refresh
    SettingsSection.Beta -> PlayerIcons.Science
    SettingsSection.About -> PlayerIcons.Info
}

private fun LazyListScope.sectionList(
    onOpen: (SettingsSection) -> Unit,
    settings: AppSettings,
    trackCount: Int,
    updateStatus: String,
    version: String,
    selected: SettingsSection? = null,
) {
    item(key = "hero") {
        SettingsHero(
            name = stringResource(Res.string.app_name),
            version = stringResource(Res.string.settings_version, version),
            status = updateStatus,
            onStatusClick = { onOpen(SettingsSection.Updates) },
            onLogoClick = { onOpen(SettingsSection.About) },
        )
    }
    for (group in SectionGroup.entries) item(key = "sections_${group.name}") {
        Group(stringResource(group.title)) {
            SettingsSection.entries.filter { it.group == group }.map { s ->
                { shape ->
                    SectionItem(
                        shape = shape,
                        icon = s.icon(),
                        title = stringResource(s.title()),
                        summary = when (s) {
                            SettingsSection.Appearance -> stringResource(Res.string.settings_appearance_summary)
                            SettingsSection.System -> stringResource(Res.string.settings_system_summary)
                            SettingsSection.Playback -> playbackSummary(settings)
                            SettingsSection.Equalizer -> equalizerSummary(settings)
                            SettingsSection.Lyrics -> stringResource(Res.string.settings_lyrics_summary)
                            SettingsSection.Library -> stringResource(
                                Res.string.settings_library_summary,
                                stringResource(Res.string.tracks_count, trackCount),
                            )
                            SettingsSection.Downloads -> stringResource(Res.string.settings_downloads_summary)
                            SettingsSection.Updates -> updateStatus
                            SettingsSection.Beta -> stringResource(Res.string.settings_beta_summary)
                            SettingsSection.About -> stringResource(Res.string.about_tagline)
                        },
                        onClick = { onOpen(s) },
                        selected = s == selected,
                        chevron = selected == null,
                        tone = sectionTone(group.ordinal),
                        iconShape = sectionIconShape(s.ordinal),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.appearance(settings: AppSettings, onUpdate: Update) {
    item(key = "theme") {
        Group(stringResource(Res.string.settings_theme)) {
            buildList<@Composable (Shape) -> Unit> {
                add { shape ->
                    ChoiceRowItem(
                        shape = shape,
                        options = listOf(
                            stringResource(Res.string.theme_system),
                            stringResource(Res.string.theme_light),
                            stringResource(Res.string.theme_dark),
                        ),
                        selected = settings.theme.ordinal,
                        onSelect = { i -> onUpdate { it.shared { copy(theme = ThemeMode.entries[i]) } } },
                    )
                }
                // The Nothing style brings its own palette.
                if (!settings.nothingStyle) add { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_black),
                        subtitle = stringResource(Res.string.settings_black_desc),
                        checked = settings.pureBlack,
                        onChange = { v -> onUpdate { it.shared { copy(pureBlack = v) } } },
                    )
                }
            }
        }
    }
    if (!settings.nothingStyle) item(key = "colors") {
        Group(stringResource(Res.string.settings_colors)) {
            listOf<@Composable (Shape) -> Unit>({ shape ->
                ColorsItem(
                    shape = shape,
                    // "Wallpaper" here is Windows' accent colour.
                    sources = ColorSource.entries,
                    source = settings.colorSource,
                    seed = settings.seedColor,
                    onSource = { s -> onUpdate { it.shared { copy(colorSource = s) } } },
                    onSeed = { seed -> onUpdate { it.shared { copy(seedColor = seed) } } },
                    wallpaperDescription = stringResource(Res.string.settings_colors_wallpaper_desc),
                )
            })
        }
    }
    item(key = "style") {
        Group(stringResource(Res.string.settings_style)) {
            buildList<@Composable (Shape) -> Unit> {
                add { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_nothing),
                        subtitle = stringResource(Res.string.settings_nothing_desc),
                        checked = settings.nothingStyle,
                        onChange = { v -> onUpdate { it.shared { copy(nothingStyle = v) } } },
                    )
                }
                // The Nothing style has no glass and its own dotted progress bar.
                if (!settings.nothingStyle) add { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_glass),
                        subtitle = stringResource(Res.string.settings_glass_desc),
                        checked = settings.glass,
                        onChange = { v -> onUpdate { it.shared { copy(glass = v) } } },
                    )
                }
                if (!settings.nothingStyle) add { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_wavy),
                        subtitle = stringResource(Res.string.settings_wavy_desc),
                        checked = settings.wavyProgress,
                        onChange = { v -> onUpdate { it.shared { copy(wavyProgress = v) } } },
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
            }
        }
    }
    item(key = "text") {
        val pickFont = rememberFontPicker(onUpdate)
        Group(stringResource(Res.string.settings_text)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    FontItem(
                        shape = shape,
                        font = settings.font,
                        customFontPath = settings.customFontPath,
                        customFontName = settings.customFontName,
                        onSelect = { f -> onUpdate { it.shared { copy(font = f) } } },
                        onPickFile = pickFont,
                    )
                },
                { shape ->
                    DropdownItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_language),
                        options = AppLanguage.entries,
                        selected = settings.language,
                        label = { languageLabel(it) },
                        onSelect = { l -> onUpdate { it.copy(language = l) } },
                    )
                },
            )
        }
    }
}

/**
 * Opens the Windows file dialog for a font; the file is copied into the app's data and becomes
 * the font. Anything that turns out not to be a font is turned away with a message.
 */
@Composable
private fun rememberFontPicker(onUpdate: Update): () -> Unit {
    val scope = rememberCoroutineScope()
    val title = stringResource(Res.string.font_custom_title)
    val failed = stringResource(Res.string.font_custom_failed)
    return {
        Files.pickFont(title)?.let { file ->
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    runCatching { CustomFonts.import(file.inputStream(), file.name, SettingsStore.fontsDir) }.getOrNull()
                }
                if (imported == null) {
                    Toast.show(failed)
                } else {
                    onUpdate { it.shared { copy(font = AppFont.Custom, customFontFile = imported.file, customFontName = imported.name) } }
                }
            }
        }
    }
}

private fun LazyListScope.beta(settings: AppSettings, onUpdate: Update) {
    item(key = "beta") {
        BetaGroups(
            settings = settings,
            onRadio = { on -> onUpdate { it.shared { copy(radioEnabled = on) } } },
            onNovelty = { n -> onUpdate { it.shared { copy(radioNovelty = n) } } },
            onBuffer = { n -> onUpdate { it.shared { copy(radioBuffer = n) } } },
            onForgetDislikes = { RadioMemory.get(AppDirs.files).clear() },
            onCassette = { v -> onUpdate { it.shared { copy(cassetteMode = v) } } },
        )
    }
}

private fun LazyListScope.system(settings: AppSettings, onUpdate: Update) {
    item(key = "system") {
        Group(null) {
            buildList<@Composable (Shape) -> Unit> {
                add { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.close_to_tray),
                        subtitle = stringResource(Res.string.close_to_tray_desc),
                        checked = settings.closeToTray,
                        onChange = { v -> onUpdate { it.copy(closeToTray = v) } },
                    )
                }
                if (DiscordPresence.available) add { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.discord_presence),
                        subtitle = stringResource(Res.string.discord_presence_desc),
                        checked = settings.discordPresence,
                        onChange = { v -> onUpdate { it.copy(discordPresence = v) } },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.playback(settings: AppSettings, onUpdate: Update) {
    item(key = "sound") {
        // Listing the outputs asks every sound driver; once per visit is plenty.
        val devices = remember { AudioEngine.outputDevices() }
        val default = stringResource(Res.string.output_default)
        Group(stringResource(Res.string.settings_sound)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    DropdownItem(
                        shape = shape,
                        title = stringResource(Res.string.output_device),
                        options = listOf<String?>(null) + devices,
                        // A device that was unplugged plays through the default one.
                        selected = settings.outputDevice?.takeIf { it in devices },
                        label = { it ?: default },
                        onSelect = { name -> onUpdate { it.copy(outputDevice = name) } },
                    )
                },
                { shape -> SpeedItem(shape, settings.playbackSpeed) { v -> onUpdate { it.shared { copy(playbackSpeed = v) } } } },
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_silence),
                        subtitle = stringResource(Res.string.settings_silence_desc),
                        checked = settings.skipSilence,
                        onChange = { v -> onUpdate { it.shared { copy(skipSilence = v) } } },
                    )
                },
            )
        }
    }
    item(key = "transitions") {
        Group(stringResource(Res.string.settings_transitions)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    CrossfadeItem(shape, settings.crossfadeSeconds) { s -> onUpdate { it.shared { copy(crossfadeSeconds = s) } } }
                },
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_crossfade_albums),
                        subtitle = stringResource(Res.string.settings_crossfade_albums_desc),
                        checked = settings.crossfadeAlbums,
                        enabled = settings.crossfadeSeconds > 0,
                        onChange = { v -> onUpdate { it.shared { copy(crossfadeAlbums = v) } } },
                    )
                },
            )
        }
    }
}

private fun LazyListScope.equalizer(settings: AppSettings, onUpdate: Update) {
    item(key = "equalizer") {
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_equalizer),
                        subtitle = stringResource(Res.string.settings_equalizer_desc),
                        checked = settings.eqEnabled,
                        onChange = { v -> onUpdate { it.shared { copy(eqEnabled = v) } } },
                    )
                },
                { shape ->
                    EqualizerItem(
                        shape = shape,
                        enabled = settings.eqEnabled,
                        preset = settings.eqPreset,
                        gains = settings.eqGains,
                        onPreset = { p -> onUpdate { it.shared { copy(eqPreset = p, eqGains = p.gains ?: eqGains) } } },
                        onBand = { band, db ->
                            onUpdate {
                                it.shared { copy(eqGains = eqGains.toMutableList().also { g -> g[band] = db }, eqPreset = EqPreset.Custom) }
                            }
                        },
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
                        onChange = { v -> onUpdate { it.shared { copy(lyricsWifiOnly = v) } } },
                    )
                },
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_sync_controls),
                        subtitle = stringResource(Res.string.settings_sync_controls_desc),
                        checked = settings.lyricsSyncControls,
                        onChange = { v -> onUpdate { it.shared { copy(lyricsSyncControls = v) } } },
                    )
                },
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_clear_cache),
                        subtitle = stringResource(Res.string.settings_cache_size, formatFileSize(cacheBytes)),
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
                    ChoiceRowItem(
                        shape = shape,
                        options = listOf(
                            stringResource(Res.string.download_format_m4a),
                            stringResource(Res.string.download_format_opus),
                        ),
                        selected = settings.downloadFormat.ordinal,
                        onSelect = { i -> onUpdate { it.shared { copy(downloadFormat = DownloadFormat.entries[i]) } } },
                        description = stringResource(
                            if (settings.downloadFormat == DownloadFormat.M4a) Res.string.download_format_m4a_desc
                            else Res.string.download_format_opus_desc
                        ),
                    )
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
    item(key = "youtube_cookies") {
        Group(stringResource(Res.string.youtube_group)) {
            listOf<@Composable (Shape) -> Unit>({ shape ->
                ItemContainer(shape) {
                    Column {
                        DropdownItem(
                            shape = RectangleShape,
                            title = stringResource(Res.string.youtube_cookies),
                            options = CookieBrowser.entries,
                            selected = settings.cookiesBrowser,
                            label = { b -> if (b == CookieBrowser.Off) stringResource(Res.string.youtube_cookies_off) else b.label },
                            onSelect = { b -> onUpdate { it.copy(cookiesBrowser = b) } },
                        )
                        Text(
                            stringResource(Res.string.youtube_cookies_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                        )
                    }
                }
            })
        }
    }
    downloadLog(Downloader.log.entries, Downloader.log::clear)
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
                        onChange = { v -> onUpdate { it.shared { copy(checkUpdates = v) } } },
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
