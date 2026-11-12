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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.ku.pierdoon.data.AppLanguage
import com.ku.pierdoon.data.AppSettings
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.NAV_MAX_WIDTH
import com.ku.pierdoon.data.NAV_MIN_WIDTH
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.data.update.UpdateState
import com.ku.pierdoon.playback.AudioEngine
import com.ku.pierdoon.playback.DiscordPresence
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.desktop.Files
import com.ku.pierdoon.ui.desktop.Toast
import com.ku.pierdoon.ui.library.AppScaffold
import com.ku.pierdoon.ui.library.listBottomPadding
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

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
    onFindDuplicates: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
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
        // Pages both apps have change only the settings both have.
        val shared: SharedUpdate = { change -> onUpdate { it.shared(change) } }
        when (s) {
            SettingsSection.Appearance -> appearanceSection(
                settings = settings,
                onUpdate = shared,
                // "Wallpaper" here is Windows' accent colour.
                colorSources = ColorSource.entries,
                fontsDir = SettingsStore.fontsDir,
                styleRows = listOf(floatingNavRow(settings, onUpdate)),
                languageRow = languageRow(settings, onUpdate),
            )
            SettingsSection.Sync -> error("not on this platform")
            SettingsSection.System -> system(settings, onUpdate)
            SettingsSection.Playback -> playbackSection(settings, shared, before = listOf(outputDeviceRow(settings, onUpdate)))
            SettingsSection.Equalizer -> equalizerSection(settings, shared)
            SettingsSection.Lyrics -> lyricsSection(settings, shared, lyricsCacheSize, onClearLyricsCache)
            SettingsSection.Library -> {
                library(settings, trackCount, onRescan, onAddFolder, onRemoveFolder, onExcludeFolder, onUnexcludeFolder)
                duplicatesItem(onFindDuplicates)
            }
            SettingsSection.Backup -> backupItems(onExportBackup, onImportBackup)
            SettingsSection.Downloads -> downloads(settings, onUpdate, onDownloadFolder)
            SettingsSection.Updates -> updatesSection(settings, shared, updateState, updateStatus, onCheckUpdates, onShowUpdate)
            SettingsSection.Beta -> betaSection(settings, shared, onForgetDislikes = { RadioMemory.get(AppDirs.files).clear() })
            SettingsSection.About -> item(key = "about") { AboutContent(version) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TWO_PANE_WIDTH) {
            // Wide: the sections stay listed on the left and the one picked opens beside them.
            var picked by remember(section) { mutableStateOf(section ?: SettingsSection.computer.first()) }
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
    SettingsSection.Sync -> error("not on this platform")
    SettingsSection.System -> Res.string.settings_system
    SettingsSection.Backup -> Res.string.settings_backup
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
    SettingsSection.Sync -> error("not on this platform")
    SettingsSection.System -> PlayerIcons.Desktop
    SettingsSection.Backup -> PlayerIcons.Restore
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
            SettingsSection.computer.filter { it.group == group }.map { s ->
                { shape ->
                    SectionItem(
                        shape = shape,
                        icon = s.icon(),
                        title = stringResource(s.title()),
                        summary = when (s) {
                            SettingsSection.Appearance -> stringResource(Res.string.settings_appearance_summary)
                            SettingsSection.Sync -> error("not on this platform")
                            SettingsSection.System -> stringResource(Res.string.settings_system_summary)
                            SettingsSection.Backup -> stringResource(Res.string.settings_backup_summary)
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
                        iconShape = sectionIconShape(s.iconIndex),
                    )
                }
            }
        }
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
    downloadFormatAndEngine(settings, { change -> onUpdate { it.shared(change) } }, Downloader)
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

/** Which sound card plays; "the system's default" unless one is picked. */
private fun outputDeviceRow(settings: AppSettings, onUpdate: Update): @Composable (Shape) -> Unit = { shape ->
    // Listing the outputs asks every sound driver; once per visit is plenty.
    val devices = remember { AudioEngine.outputDevices() }
    val default = stringResource(Res.string.output_default)
    DropdownItem(
        shape = shape,
        title = stringResource(Res.string.output_device),
        options = listOf<String?>(null) + devices,
        // A device that was unplugged plays through the default one.
        selected = settings.outputDevice?.takeIf { it in devices },
        label = { it ?: default },
        onSelect = { name -> onUpdate { it.copy(outputDevice = name) } },
    )
}

/** Icons only, or icons with their names: the rail's width, also dragged by its edge. */
private fun floatingNavRow(settings: AppSettings, onUpdate: Update) = GroupRow { shape ->
    SwitchItem(
        shape = shape,
        title = stringResource(Res.string.settings_floating_nav),
        subtitle = stringResource(Res.string.settings_floating_nav_desc),
        checked = settings.navWidth <= NAV_MIN_WIDTH,
        onChange = { v -> onUpdate { it.copy(navWidth = if (v) NAV_MIN_WIDTH else NAV_MAX_WIDTH) } },
    )
}

/** The interface language, kept by the app itself on Windows. */
private fun languageRow(settings: AppSettings, onUpdate: Update): @Composable (Shape) -> Unit = { shape ->
    DropdownItem(
        shape = shape,
        title = stringResource(Res.string.settings_language),
        options = AppLanguage.entries,
        selected = settings.language,
        label = { languageLabel(it) },
        onSelect = { l -> onUpdate { it.copy(language = l) } },
    )
}
