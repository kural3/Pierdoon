package com.ku.pierdoon.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ku.pierdoon.data.AppLanguage
import com.ku.pierdoon.data.AppSettings
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.DEFAULT_SAVE_FOLDER
import com.ku.pierdoon.data.InterfaceLanguage
import com.ku.pierdoon.data.MusicFolder
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.data.update.UpdateState
import com.ku.pierdoon.remote.PcRemote
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.library.AppScaffold
import com.ku.pierdoon.ui.library.listBottomPadding
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.radio.RadioMemory
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
    musicFolders: suspend () -> List<MusicFolder>,
    updateState: UpdateState,
    onCheckUpdates: () -> Unit,
    onShowUpdate: () -> Unit,
    onFindDuplicates: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    bottomInset: Dp,
) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
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
            modifier = Modifier.fillMaxSize(),
        ) {
            // Pages both apps have change only the settings both have.
            val shared: SharedUpdate = { change -> onUpdate { it.shared(change) } }
            when (section) {
                null -> sectionList(onOpen, onOpenComputer, trackCount, updateStatus, version, settings)
                SettingsSection.Appearance -> {
                    appearanceSection(
                        settings = settings,
                        onUpdate = shared,
                        // "Wallpaper" only exists on Android 12+, so the options adapt to the device.
                        colorSources = buildList {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(ColorSource.Wallpaper)
                            add(ColorSource.AlbumArt)
                            add(ColorSource.Custom)
                        },
                        fontsDir = SettingsStore.fontsDir,
                        languageRow = { shape -> LanguageItem(shape) },
                    )
                    feedback(settings, onUpdate)
                }
                SettingsSection.Playback -> playbackSection(settings, shared)
                SettingsSection.Equalizer -> equalizerSection(settings, shared)
                SettingsSection.Lyrics -> lyricsSection(settings, shared, lyricsCacheSize, onClearLyricsCache)
                SettingsSection.Library -> {
                    library(settings, onUpdate, trackCount, onRescan, musicFolders)
                    duplicatesItem(onFindDuplicates)
                }
                SettingsSection.System -> error("not on this platform")
                SettingsSection.Sync -> item(key = "sync") { PcSyncGroup(null, onOpenComputer) }
                SettingsSection.Backup -> backupItems(onExportBackup, onImportBackup)
                SettingsSection.Downloads -> downloads(settings, onUpdate)
                SettingsSection.Updates -> updatesSection(settings, shared, updateState, updateStatus, onCheckUpdates, onShowUpdate)
                SettingsSection.Beta -> betaSection(settings, shared, onForgetDislikes = { RadioMemory.get(context.filesDir).clear() })
                SettingsSection.About -> item(key = "about") { AboutContent(version) }
            }
        }
    }
}

private fun SettingsSection.title(): StringResource = when (this) {
    SettingsSection.Appearance -> Res.string.settings_appearance
    SettingsSection.System -> error("not on this platform")
    SettingsSection.Sync -> Res.string.settings_sync
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
    SettingsSection.System -> error("not on this platform")
    SettingsSection.Sync -> PlayerIcons.Sync
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
    onOpenComputer: () -> Unit,
    trackCount: Int,
    updateStatus: String,
    version: String,
    settings: AppSettings,
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
            // Downloads need Android 10; before that the section would only say so.
            SettingsSection.phone
                .filter { it.group == group && (it != SettingsSection.Downloads || Downloader.supported) }
                .map { s ->
                    { shape ->
                        SectionItem(
                            shape = shape,
                            icon = s.icon(),
                            title = stringResource(s.title()),
                            summary = sectionSummary(s, settings, trackCount, updateStatus),
                            onClick = { onOpen(s) },
                            tone = sectionTone(group.ordinal),
                            iconShape = sectionIconShape(s.iconIndex),
                        )
                    }
                }
        }
        // The computer belongs with the music: it's where the rest of it is.
        if (group == SectionGroup.Music) {
            val paired by PcRemote.paired.collectAsStateWithLifecycle()
            Spacer(Modifier.height(2.dp))
            SectionItem(
                shape = groupShape(0, 1),
                icon = PlayerIcons.Desktop,
                title = stringResource(Res.string.pc_open),
                summary = paired?.name ?: stringResource(Res.string.pc_summary_none),
                onClick = onOpenComputer,
                tone = sectionTone(group.ordinal),
                iconShape = sectionIconShape(SettingsSection.phone.size),
            )
        }
    }
}

/** What's inside a section, or how it's set, under its name on the list. */
@Composable
private fun sectionSummary(s: SettingsSection, settings: AppSettings, trackCount: Int, updateStatus: String): String = when (s) {
    SettingsSection.Appearance -> stringResource(Res.string.settings_appearance_summary)
    SettingsSection.System -> error("not on this platform")
    SettingsSection.Sync -> syncSummary()
    SettingsSection.Backup -> stringResource(Res.string.settings_backup_summary)
    SettingsSection.Playback -> playbackSummary(settings)
    SettingsSection.Equalizer -> equalizerSummary(settings)
    SettingsSection.Lyrics -> stringResource(Res.string.settings_lyrics_summary)
    SettingsSection.Library -> listOfNotNull(
        stringResource(Res.string.tracks_count, trackCount),
        settings.excludedFolders.size.takeIf { it > 0 }?.let { stringResource(Res.string.settings_folders_hidden, it) },
    ).joinToString(" · ")
    SettingsSection.Downloads -> downloadsSummary(settings)
    SettingsSection.Updates -> updateStatus
    SettingsSection.Beta -> stringResource(Res.string.settings_beta_summary)
    SettingsSection.About -> stringResource(Res.string.about_tagline)
}

/**
 * System, Russian, English: each language under its own name, so it can be found from the other
 * one. Not an app setting: Android keeps it (see [InterfaceLanguage]), and the screen is
 * recreated in the new language.
 */
@Composable
private fun LanguageItem(shape: Shape) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val language = remember { InterfaceLanguage.current(context) }
    DropdownItem(
        shape = shape,
        title = stringResource(Res.string.settings_language),
        options = AppLanguage.entries,
        selected = language,
        label = { languageLabel(it) },
        onSelect = { chosen -> if (chosen != language && activity != null) InterfaceLanguage.set(activity, chosen) },
    )
}

private fun LazyListScope.library(
    settings: AppSettings,
    onUpdate: Update,
    trackCount: Int,
    onRescan: () -> Unit,
    musicFolders: suspend () -> List<MusicFolder>,
) {
    item(key = "library") {
        val context = LocalContext.current
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
                            Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                        },
                    )
                },
            )
        }
    }
    item(key = "folders") {
        // Counted again whenever the library changes, so a new folder shows up here too.
        val folders by produceState<List<MusicFolder>?>(null, trackCount) { value = musicFolders() }
        val list = folders.orEmpty()
        if (list.isEmpty()) return@item
        Group(stringResource(Res.string.settings_folders)) {
            list.map<MusicFolder, @Composable (Shape) -> Unit> { f ->
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = f.path.ifEmpty { stringResource(Res.string.settings_folder_root) },
                        subtitle = stringResource(Res.string.tracks_count, f.count),
                        checked = f.path !in settings.excludedFolders,
                        onChange = { on ->
                            onUpdate { s -> s.copy(excludedFolders = if (on) s.excludedFolders - f.path else s.excludedFolders + f.path) }
                        },
                    )
                }
            }
        }
        Text(
            stringResource(Res.string.settings_folders_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
    }
}

/** "Music/Pier.doon · M4A" on the section list. */
@Composable
private fun downloadsSummary(settings: AppSettings): String {
    val format = stringResource(
        if (settings.downloadFormat == DownloadFormat.M4a) Res.string.download_format_m4a else Res.string.download_format_opus
    )
    return "$DEFAULT_SAVE_FOLDER · $format"
}

private fun LazyListScope.downloads(settings: AppSettings, onUpdate: Update) {
    downloadFormatAndEngine(settings, { change -> onUpdate { it.shared(change) } }, Downloader)
    item(key = "youtube_sign_in") {
        val context = LocalContext.current
        val signedIn by Downloader.youtubeSignedIn.collectAsStateWithLifecycle()
        Group(stringResource(Res.string.youtube_group)) {
            listOf<@Composable (Shape) -> Unit>({ shape ->
                if (signedIn) {
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.youtube_sign_out),
                        subtitle = stringResource(Res.string.youtube_signed_in),
                        onClick = Downloader::signOutOfYouTube,
                    )
                } else {
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.youtube_sign_in),
                        subtitle = stringResource(Res.string.youtube_sign_in_desc),
                        onClick = { context.startActivity(android.content.Intent(context, com.ku.pierdoon.YouTubeLoginActivity::class.java)) },
                    )
                }
            })
        }
    }
    downloadLog(Downloader.log.entries, Downloader.log::clear)
}

/** The Sync section on the list: which computer and when, or that there is none. */
@Composable
private fun syncSummary(): String {
    val paired by PcRemote.paired.collectAsStateWithLifecycle()
    val pc = paired ?: return stringResource(Res.string.pc_summary_none)
    return pc.name + " · " + (syncAgo(short = true) ?: stringResource(Res.string.sync_never))
}

/** Haptics and the refresh rate: what only a phone has. */
private fun LazyListScope.feedback(settings: AppSettings, onUpdate: Update) {
    item(key = "feedback") {
        Group(stringResource(Res.string.settings_feedback)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_haptics),
                        subtitle = stringResource(Res.string.settings_haptics_desc),
                        checked = settings.haptics,
                        onChange = { v -> onUpdate { it.shared { copy(haptics = v) } } },
                    )
                },
                { shape ->
                    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_smooth),
                        subtitle = stringResource(
                            if (supported) Res.string.settings_smooth_desc else Res.string.settings_smooth_unavailable
                        ),
                        checked = settings.smoothAnimations && supported,
                        enabled = supported,
                        onChange = { v -> onUpdate { it.shared { copy(smoothAnimations = v) } } },
                    )
                },
            )
        }
    }
}
