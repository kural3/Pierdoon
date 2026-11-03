package com.ku.pierdoon.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ku.pierdoon.data.AppFont
import com.ku.pierdoon.data.AppLanguage
import com.ku.pierdoon.data.AppSettings
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.CustomFonts
import com.ku.pierdoon.data.DEFAULT_SAVE_FOLDER
import com.ku.pierdoon.data.InterfaceLanguage
import com.ku.pierdoon.data.MusicFolder
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.ThemeMode
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.data.download.EngineUpdate
import com.ku.pierdoon.data.update.UpdateState
import com.ku.pierdoon.playback.EqPreset
import com.ku.pierdoon.playback.PlaybackSpeed
import com.ku.pierdoon.remote.PcRemote
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.library.AppScaffold
import com.ku.pierdoon.ui.library.listBottomPadding
import com.ku.pierdoon.ui.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
    Appearance(SectionGroup.App), Updates(SectionGroup.App),
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
            when (section) {
                null -> sectionList(onOpen, onOpenComputer, trackCount, updateStatus, version, settings)
                SettingsSection.Appearance -> appearance(settings, onUpdate)
                SettingsSection.Playback -> playback(settings, onUpdate)
                SettingsSection.Equalizer -> equalizer(settings, onUpdate)
                SettingsSection.Lyrics -> lyrics(settings, onUpdate, lyricsCacheSize, onClearLyricsCache)
                SettingsSection.Library -> library(settings, onUpdate, trackCount, onRescan, musicFolders)
                SettingsSection.Downloads -> downloads(settings, onUpdate)
                SettingsSection.Updates -> updates(settings, onUpdate, updateState, updateStatus, onCheckUpdates, onShowUpdate)
            }
        }
    }
}

private fun SettingsSection.title(): StringResource = when (this) {
    SettingsSection.Appearance -> Res.string.settings_appearance
    SettingsSection.Playback -> Res.string.settings_playback
    SettingsSection.Equalizer -> Res.string.settings_equalizer
    SettingsSection.Lyrics -> Res.string.settings_lyrics
    SettingsSection.Library -> Res.string.settings_library
    SettingsSection.Downloads -> Res.string.settings_downloads
    SettingsSection.Updates -> Res.string.settings_updates
}

private fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.Appearance -> PlayerIcons.Palette
    SettingsSection.Playback -> PlayerIcons.Play
    SettingsSection.Equalizer -> PlayerIcons.Equalizer
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
    settings: AppSettings,
) {
    for (group in SectionGroup.entries) item(key = "sections_${group.name}") {
        Group(stringResource(group.title)) {
            // Downloads need Android 10; before that the section would only say so.
            SettingsSection.entries
                .filter { it.group == group && (it != SettingsSection.Downloads || Downloader.supported) }
                .map { s ->
                    { shape ->
                        SectionItem(
                            shape = shape,
                            icon = s.icon(),
                            title = stringResource(s.title()),
                            summary = sectionSummary(s, settings, trackCount, updateStatus),
                            onClick = { onOpen(s) },
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
            )
        }
    }
    item(key = "about") { About(version) }
}

/** What's inside a section, or how it's set, under its name on the list. */
@Composable
private fun sectionSummary(s: SettingsSection, settings: AppSettings, trackCount: Int, updateStatus: String): String = when (s) {
    SettingsSection.Appearance -> stringResource(Res.string.settings_appearance_summary)
    SettingsSection.Playback -> playbackSummary(settings)
    SettingsSection.Equalizer -> equalizerSummary(settings)
    SettingsSection.Lyrics -> stringResource(Res.string.settings_lyrics_summary)
    SettingsSection.Library -> listOfNotNull(
        stringResource(Res.string.tracks_count, trackCount),
        settings.excludedFolders.size.takeIf { it > 0 }?.let { stringResource(Res.string.settings_folders_hidden, it) },
    ).joinToString(" · ")
    SettingsSection.Downloads -> downloadsSummary(settings)
    SettingsSection.Updates -> updateStatus
}

@Composable
private fun About(version: String) {
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
        // "Wallpaper" only exists on Android 12+, so the options adapt to the device.
        val sources = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(ColorSource.Wallpaper)
            add(ColorSource.AlbumArt)
            add(ColorSource.Custom)
        }
        Group(stringResource(Res.string.settings_colors)) {
            listOf<@Composable (Shape) -> Unit>({ shape ->
                ColorsItem(
                    shape = shape,
                    sources = sources,
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
                        checked = settings.floatingNav,
                        onChange = { v -> onUpdate { it.copy(floatingNav = v) } },
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
                { shape -> LanguageItem(shape) },
            )
        }
    }
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

/**
 * Opens the system file picker for a font; the file is copied into the app and becomes the font.
 * Anything that turns out not to be a font is turned away with a message.
 */
@Composable
private fun rememberFontPicker(onUpdate: Update): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(Res.string.font_custom_failed)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.let { input ->
                        CustomFonts.import(input, displayName(context, uri) ?: "font.ttf", SettingsStore.fontsDir)
                    }
                }.getOrNull()
            }
            if (imported == null) {
                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            } else {
                onUpdate { it.shared { copy(font = AppFont.Custom, customFontFile = imported.file, customFontName = imported.name) } }
            }
        }
    }
    // File managers label fonts every which way (font/ttf, application/x-font-ttf, octet-stream),
    // so any file is offered and checked once picked.
    return { launcher.launch(arrayOf("*/*")) }
}

private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
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

private fun LazyListScope.playback(settings: AppSettings, onUpdate: Update) {
    item(key = "sound") {
        Group(stringResource(Res.string.settings_sound)) {
            listOf<@Composable (Shape) -> Unit>(
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
    item(key = "modes") {
        Group(stringResource(Res.string.settings_modes)) {
            listOf<@Composable (Shape) -> Unit>({ shape ->
                SwitchItem(
                    shape = shape,
                    title = stringResource(Res.string.settings_cassette_mode),
                    subtitle = stringResource(Res.string.settings_cassette_mode_desc),
                    checked = settings.cassetteMode,
                    onChange = { v -> onUpdate { it.shared { copy(cassetteMode = v) } } },
                )
            })
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
        val context = LocalContext.current
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
                        subtitle = stringResource(
                            Res.string.settings_cache_size,
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
        val version by Downloader.engineVersion.collectAsStateWithLifecycle()
        val engineUpdate by Downloader.engineUpdate.collectAsStateWithLifecycle()
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
