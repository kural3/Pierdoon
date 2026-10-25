package com.ku.pierdoon.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.metadata.SaveTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }

/** Where the palette comes from: device wallpaper, current album art, or a chosen colour. */
enum class ColorSource { Wallpaper, AlbumArt, Custom }

const val DEFAULT_SEED: Int = 0xFF6750A4.toInt()

@Immutable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val colorSource: ColorSource =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) ColorSource.Wallpaper
        else ColorSource.Custom,
    val seedColor: Int = DEFAULT_SEED,
    /** True black surfaces in dark theme: OLED pixels switch off and save power. */
    val pureBlack: Boolean = false,
    val haptics: Boolean = true,
    /** Ask the display for its top refresh rate while animating (Android 15+). */
    val smoothAnimations: Boolean = true,
    val glass: Boolean = true,
    /** Rounded tab bar hovering above content instead of docked to the bottom edge. */
    val floatingNav: Boolean = false,
    val wavyProgress: Boolean = true,
    val lyricsWifiOnly: Boolean = false,
    /** Buttons that shift synced lyrics earlier or later; most tracks never need them. */
    val lyricsSyncControls: Boolean = false,
    val skipSilence: Boolean = false,
    val audioOffload: Boolean = true,
    /** Last choice on the track editor: the app's records or the file itself. */
    val editTarget: SaveTarget = SaveTarget.App,
    /** Look for a new release on GitHub now and then (at most twice a day). */
    val checkUpdates: Boolean = true,
    val downloadFormat: DownloadFormat = DownloadFormat.M4a,
)

/**
 * App-wide settings shared by the UI and the playback service (same process).
 * Backed by SharedPreferences: tiny, already in the platform, async writes.
 */
object SettingsStore {
    private var prefs: SharedPreferences? = null
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        val d = AppSettings()
        _settings.value = AppSettings(
            theme = runCatching { ThemeMode.valueOf(p.getString(K_THEME, d.theme.name)!!) }.getOrDefault(d.theme),
            colorSource = p.getString(K_SOURCE, null)
                ?.let { runCatching { ColorSource.valueOf(it) }.getOrNull() }
                // Migrate the old on/off "wallpaper colours" switch.
                ?: if (p.getBoolean(K_DYNAMIC, true)) d.colorSource else ColorSource.Custom,
            seedColor = p.getInt(K_SEED, d.seedColor),
            pureBlack = p.getBoolean(K_BLACK, d.pureBlack),
            haptics = p.getBoolean(K_HAPTICS, d.haptics),
            smoothAnimations = p.getBoolean(K_SMOOTH, d.smoothAnimations),
            glass = p.getBoolean(K_GLASS, d.glass),
            floatingNav = p.getBoolean(K_FLOATING_NAV, d.floatingNav),
            wavyProgress = p.getBoolean(K_WAVY, d.wavyProgress),
            lyricsWifiOnly = p.getBoolean(K_WIFI, d.lyricsWifiOnly),
            lyricsSyncControls = p.getBoolean(K_SYNC_CONTROLS, d.lyricsSyncControls),
            skipSilence = p.getBoolean(K_SILENCE, d.skipSilence),
            audioOffload = p.getBoolean(K_OFFLOAD, d.audioOffload),
            editTarget = runCatching { SaveTarget.valueOf(p.getString(K_EDIT_TARGET, d.editTarget.name)!!) }
                .getOrDefault(d.editTarget),
            checkUpdates = p.getBoolean(K_UPDATES, d.checkUpdates),
            downloadFormat = runCatching { DownloadFormat.valueOf(p.getString(K_DOWNLOAD_FORMAT, d.downloadFormat.name)!!) }
                .getOrDefault(d.downloadFormat),
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        if (s == _settings.value) return
        _settings.value = s
        prefs?.edit {
            putString(K_THEME, s.theme.name)
            putString(K_SOURCE, s.colorSource.name)
            putInt(K_SEED, s.seedColor)
            putBoolean(K_BLACK, s.pureBlack)
            putBoolean(K_HAPTICS, s.haptics)
            putBoolean(K_SMOOTH, s.smoothAnimations)
            putBoolean(K_GLASS, s.glass)
            putBoolean(K_FLOATING_NAV, s.floatingNav)
            putBoolean(K_WAVY, s.wavyProgress)
            putBoolean(K_WIFI, s.lyricsWifiOnly)
            putBoolean(K_SYNC_CONTROLS, s.lyricsSyncControls)
            putBoolean(K_SILENCE, s.skipSilence)
            putBoolean(K_OFFLOAD, s.audioOffload)
            putString(K_EDIT_TARGET, s.editTarget.name)
            putBoolean(K_UPDATES, s.checkUpdates)
            putString(K_DOWNLOAD_FORMAT, s.downloadFormat.name)
        }
    }

    private const val K_THEME = "theme"
    private const val K_DYNAMIC = "dynamic_color"
    private const val K_SOURCE = "color_source"
    private const val K_SEED = "seed_color"
    private const val K_BLACK = "pure_black"
    private const val K_HAPTICS = "haptics"
    private const val K_SMOOTH = "smooth_animations"
    private const val K_GLASS = "glass"
    private const val K_FLOATING_NAV = "floating_nav"
    private const val K_WAVY = "wavy_progress"
    private const val K_WIFI = "lyrics_wifi_only"
    private const val K_SYNC_CONTROLS = "lyrics_sync_controls"
    private const val K_SILENCE = "skip_silence"
    private const val K_OFFLOAD = "audio_offload"
    private const val K_EDIT_TARGET = "edit_target"
    private const val K_UPDATES = "check_updates"
    private const val K_DOWNLOAD_FORMAT = "download_format"
}
