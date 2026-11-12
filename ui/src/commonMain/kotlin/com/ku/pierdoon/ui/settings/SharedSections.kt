package com.ku.pierdoon.ui.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import com.ku.pierdoon.data.download.EngineUpdate
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.download.DownloadQueue
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import com.ku.pierdoon.data.CustomFonts
import com.ku.pierdoon.data.AppFont
import com.ku.pierdoon.data.ProgressStyle
import com.ku.pierdoon.data.ThemeMode
import com.ku.pierdoon.data.ColorSource
import java.io.InputStream
import java.io.File
import com.ku.pierdoon.ui.PlatformStrings
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import com.ku.pierdoon.data.CommonSettings
import com.ku.pierdoon.data.SharedSettings
import com.ku.pierdoon.data.update.UpdateState
import com.ku.pierdoon.playback.EqPreset
import com.ku.pierdoon.ui.components.rememberToast
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** A change to the settings both apps have. */
typealias SharedUpdate = (CommonSettings.() -> CommonSettings) -> Unit

// The settings pages that are the same on the phone and the computer. Each app's SettingsScreen
// lists them among its own and passes what only it knows (a sound output row, where files are).

fun LazyListScope.playbackSection(
    settings: SharedSettings,
    onUpdate: SharedUpdate,
    /** Rows above the shared ones (the computer's sound output). */
    before: List<@Composable (Shape) -> Unit> = emptyList(),
) {
    item(key = "sound") {
        Group(stringResource(Res.string.settings_sound)) {
            listOf<@Composable (Shape) -> Unit>(
                *before.toTypedArray(),
                { shape -> SpeedItem(shape, settings.playbackSpeed) { v -> onUpdate { copy(playbackSpeed = v) } } },
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_silence),
                        subtitle = stringResource(Res.string.settings_silence_desc),
                        checked = settings.skipSilence,
                        onChange = { v -> onUpdate { copy(skipSilence = v) } },
                    )
                },
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_normalize),
                        subtitle = stringResource(Res.string.settings_normalize_desc),
                        checked = settings.normalizeVolume,
                        onChange = { v -> onUpdate { copy(normalizeVolume = v) } },
                    )
                },
            )
        }
    }
    item(key = "transitions") {
        Group(stringResource(Res.string.settings_transitions)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    CrossfadeItem(shape, settings.crossfadeSeconds) { s -> onUpdate { copy(crossfadeSeconds = s) } }
                },
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_crossfade_albums),
                        subtitle = stringResource(Res.string.settings_crossfade_albums_desc),
                        checked = settings.crossfadeAlbums,
                        enabled = settings.crossfadeSeconds > 0,
                        onChange = { v -> onUpdate { copy(crossfadeAlbums = v) } },
                    )
                },
            )
        }
    }
}

fun LazyListScope.equalizerSection(settings: SharedSettings, onUpdate: SharedUpdate) {
    item(key = "equalizer") {
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_equalizer),
                        subtitle = stringResource(Res.string.settings_equalizer_desc),
                        checked = settings.eqEnabled,
                        onChange = { v -> onUpdate { copy(eqEnabled = v) } },
                    )
                },
                { shape ->
                    EqualizerItem(
                        shape = shape,
                        enabled = settings.eqEnabled,
                        preset = settings.eqPreset,
                        gains = settings.eqGains,
                        onPreset = { p -> onUpdate { copy(eqPreset = p, eqGains = p.gains ?: eqGains) } },
                        onBand = { band, db ->
                            onUpdate { copy(eqGains = eqGains.toMutableList().also { g -> g[band] = db }, eqPreset = EqPreset.Custom) }
                        },
                    )
                },
            )
        }
    }
}

fun LazyListScope.lyricsSection(
    settings: SharedSettings,
    onUpdate: SharedUpdate,
    lyricsCacheSize: suspend () -> Long,
    onClearLyricsCache: () -> Unit,
) {
    item(key = "lyrics") {
        val toast = rememberToast()
        var cacheRefresh by remember { mutableIntStateOf(0) }
        val cacheBytes by produceState(0L, cacheRefresh) { value = lyricsCacheSize() }
        val cleared = stringResource(Res.string.settings_cache_cleared)
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(PlatformStrings.lyricsWifiOnlyTitle),
                        subtitle = stringResource(PlatformStrings.lyricsWifiOnlyDesc),
                        checked = settings.lyricsWifiOnly,
                        onChange = { v -> onUpdate { copy(lyricsWifiOnly = v) } },
                    )
                },
                { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_sync_controls),
                        subtitle = stringResource(Res.string.settings_sync_controls_desc),
                        checked = settings.lyricsSyncControls,
                        onChange = { v -> onUpdate { copy(lyricsSyncControls = v) } },
                    )
                },
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_clear_cache),
                        subtitle = stringResource(Res.string.settings_cache_size, fileSize(cacheBytes)),
                        onClick = {
                            onClearLyricsCache()
                            cacheRefresh++
                            toast(cleared)
                        },
                    )
                },
            )
        }
    }
}

fun LazyListScope.updatesSection(
    settings: SharedSettings,
    onUpdate: SharedUpdate,
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
                        onChange = { v -> onUpdate { copy(checkUpdates = v) } },
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

fun LazyListScope.betaSection(settings: SharedSettings, onUpdate: SharedUpdate, onForgetDislikes: () -> Unit) {
    item(key = "beta") {
        BetaGroups(
            settings = settings,
            onRadio = { on -> onUpdate { copy(radioEnabled = on) } },
            onNovelty = { n -> onUpdate { copy(radioNovelty = n) } },
            onBuffer = { n -> onUpdate { copy(radioBuffer = n) } },
            onForgetDislikes = onForgetDislikes,
            onCassette = { v -> onUpdate { copy(cassetteMode = v) } },
        )
    }
}

/**
 * Theme, colours, look and text. [colorSources] are the ones this system has (no wallpaper colours
 * before Android 12); [styleRows] go under the shared look switches, [languageRow] under the font.
 */
fun LazyListScope.appearanceSection(
    settings: SharedSettings,
    onUpdate: SharedUpdate,
    colorSources: List<ColorSource>,
    /** The picked font file, when there is one (in [fontsDir]). */
    fontsDir: File,
    styleRows: List<GroupRow> = emptyList(),
    languageRow: @Composable (Shape) -> Unit,
) {
    item(key = "theme") {
        FadingGroup(stringResource(Res.string.settings_theme)) {
            buildList<GroupRow> {
                add(GroupRow { shape ->
                    ChoiceRowItem(
                        shape = shape,
                        options = listOf(
                            stringResource(Res.string.theme_system),
                            stringResource(Res.string.theme_light),
                            stringResource(Res.string.theme_dark),
                        ),
                        selected = settings.theme.ordinal,
                        onSelect = { i -> onUpdate { copy(theme = ThemeMode.entries[i]) } },
                    )
                })
                // The Nothing style brings its own palette.
                add(GroupRow(visible = !settings.nothingStyle) { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_black),
                        subtitle = stringResource(PlatformStrings.pureBlackDesc),
                        checked = settings.pureBlack,
                        onChange = { v -> onUpdate { copy(pureBlack = v) } },
                    )
                })
            }
        }
    }
    item(key = "colors") {
        Group(stringResource(Res.string.settings_colors)) {
            buildList<@Composable (Shape) -> Unit> {
                // A special theme brings its own colours; the picker stays, locked, until it's off.
                add { shape ->
                    ColorsItem(
                        shape = shape,
                        sources = colorSources,
                        source = settings.colorSource,
                        seed = settings.seedColor,
                        onSource = { s -> onUpdate { copy(colorSource = s) } },
                        onSeed = { seed -> onUpdate { copy(seedColor = seed) } },
                        wallpaperDescription = stringResource(PlatformStrings.wallpaperColorsDesc),
                        enabled = !settings.nothingStyle,
                    )
                }
                add { shape ->
                    DropdownItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_special),
                        options = listOf(false, true),
                        selected = settings.nothingStyle,
                        label = { on -> stringResource(if (on) Res.string.special_nothing else Res.string.special_none) },
                        onSelect = { on -> onUpdate { copy(nothingStyle = on) } },
                    )
                }
            }
        }
    }
    item(key = "style") {
        FadingGroup(stringResource(Res.string.settings_style)) {
            buildList<GroupRow> {
                // The Nothing style has no glass and its own dotted progress bar.
                add(GroupRow(visible = !settings.nothingStyle) { shape ->
                    SwitchItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_glass),
                        subtitle = stringResource(PlatformStrings.glassDesc),
                        checked = settings.glass,
                        onChange = { v -> onUpdate { copy(glass = v) } },
                    )
                })
                add(GroupRow(visible = !settings.nothingStyle) { shape ->
                    ChoiceRowItem(
                        shape = shape,
                        title = stringResource(Res.string.settings_progress_style),
                        options = listOf(
                            stringResource(Res.string.progress_wavy),
                            stringResource(Res.string.progress_line),
                            stringResource(Res.string.progress_dots),
                            stringResource(Res.string.progress_thick),
                        ),
                        selected = settings.progressStyle.ordinal,
                        onSelect = { i -> onUpdate { copy(progressStyle = ProgressStyle.entries[i]) } },
                    )
                })
                addAll(styleRows)
            }
        }
    }
    item(key = "text") {
        val pickFont = rememberFontPicker(fontsDir, onUpdate)
        Group(stringResource(Res.string.settings_text)) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    FontItem(
                        shape = shape,
                        font = settings.font,
                        customFontPath = settings.customFontFile?.let { File(fontsDir, it).path },
                        customFontName = settings.customFontName,
                        onSelect = { f -> onUpdate { copy(font = f) } },
                        onPickFile = pickFont,
                    )
                },
                languageRow,
            )
        }
    }
}

/**
 * Opens the system's file picker for a font; the file is copied into [fontsDir] and becomes the
 * font. Anything that turns out not to be a font is turned away with a message.
 */
@Composable
private fun rememberFontPicker(fontsDir: File, onUpdate: SharedUpdate): () -> Unit {
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val failed = stringResource(Res.string.font_custom_failed)
    return rememberFontFilePicker { picked ->
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching { picked.open()?.let { input -> CustomFonts.import(input, picked.name, fontsDir) } }.getOrNull()
            }
            if (imported == null) {
                toast(failed)
            } else {
                onUpdate { copy(font = AppFont.Custom, customFontFile = imported.file, customFontName = imported.name) }
            }
        }
    }
}

/** A file the user picked: its name and a way to read it. */
class PickedFile(val name: String, val open: () -> InputStream?)

/** The system's Open dialog for a font file. */
@Composable
internal expect fun rememberFontFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit

/** The format downloads are saved in, and yt-dlp: its version and an update. */
fun LazyListScope.downloadFormatAndEngine(settings: SharedSettings, onUpdate: SharedUpdate, downloads: DownloadQueue) {
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
                        onSelect = { i -> onUpdate { copy(downloadFormat = DownloadFormat.entries[i]) } },
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
        LaunchedEffect(Unit) { downloads.warmUp() }
        val version by downloads.engineVersion.collectAsState()
        val engineUpdate by downloads.engineUpdate.collectAsState()
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
                        onClick = { if (engineUpdate != EngineUpdate.Running) downloads.updateEngine() },
                    )
                },
            )
        }
    }
}

/** A size on disk as the system writes it ("1.2 MB"). */
@Composable
internal expect fun fileSize(bytes: Long): String
