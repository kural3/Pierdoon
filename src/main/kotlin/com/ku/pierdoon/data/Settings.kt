package com.ku.pierdoon.data

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.metadata.SaveTarget
import com.ku.pierdoon.platform.AppDirs
import com.ku.pierdoon.platform.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }

/** Where the palette comes from: the Windows accent colour, current album art, or a chosen colour. */
enum class ColorSource { Wallpaper, AlbumArt, Custom }

/** Typeface for the whole app. */
enum class AppFont { System, SystemMono, JetBrainsMono, IbmPlexMono }

const val DEFAULT_SEED: Int = 0xFF6750A4.toInt()

/** Narrowest navigation rail: a column of icons. */
const val NAV_MIN_WIDTH = 88f
/** Widest: the pills just fit the longest label. */
const val NAV_MAX_WIDTH = 232f

@Immutable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val colorSource: ColorSource = ColorSource.AlbumArt,
    val seedColor: Int = DEFAULT_SEED,
    /** True black surfaces in dark theme. */
    val pureBlack: Boolean = false,
    /** A soft click on presses (the desktop's stand-in for haptics). Off by default. */
    val haptics: Boolean = false,
    val smoothAnimations: Boolean = true,
    val glass: Boolean = true,
    /** Width of the navigation rail in dp, dragged by its edge: [NAV_MIN_WIDTH] shows icons only. */
    val navWidth: Float = NAV_MAX_WIDTH,
    val wavyProgress: Boolean = true,
    /** Nothing-style look: monochrome with a red accent, dot-matrix headings, dotted progress. */
    val nothingStyle: Boolean = false,
    val font: AppFont = AppFont.System,
    /** Lyrics from the cache only, never downloaded. */
    val lyricsWifiOnly: Boolean = false,
    /** Buttons that shift synced lyrics earlier or later; most tracks never need them. */
    val lyricsSyncControls: Boolean = false,
    val skipSilence: Boolean = false,
    /** Unused on the desktop; kept so the settings screens read the same. */
    val audioOffload: Boolean = false,
    /** Last choice on the track editor: the app's records or the file itself. */
    val editTarget: SaveTarget = SaveTarget.App,
    /** Look for a new release on GitHub now and then (at most twice a day). */
    val checkUpdates: Boolean = true,
    val downloadFormat: DownloadFormat = DownloadFormat.M4a,
    val songSort: SongSort = SongSort.Title,
    val songSortDescending: Boolean = false,
    /** Folders scanned for music, subfolders included. */
    val musicFolders: List<String> = listOf(AppDirs.music.absolutePath),
    val volume: Float = 1f,
    val muted: Boolean = false,
    /** Closing the window hides it to the tray while music keeps playing. */
    val closeToTray: Boolean = true,
    /** Audio output by mixer name; null = the system default. */
    val outputDevice: String? = null,
)

/**
 * App-wide settings, shared by the UI and the player. Backed by a small properties file.
 *
 * Writes are put off briefly: dragging the volume or the rail's edge changes a setting every
 * frame, and one write when the hand stops is enough.
 */
object SettingsStore {
    private var prefs: Prefs? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingWrite: Job? = null
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Synchronized
    fun init() {
        if (prefs != null) return
        val p = Prefs("settings")
        prefs = p
        val d = AppSettings()
        _settings.value = AppSettings(
            theme = enumOr(p.getString(K_THEME, null), d.theme),
            colorSource = enumOr(p.getString(K_SOURCE, null), d.colorSource),
            seedColor = p.getInt(K_SEED, d.seedColor),
            pureBlack = p.getBoolean(K_BLACK, d.pureBlack),
            haptics = p.getBoolean(K_HAPTICS, d.haptics),
            smoothAnimations = p.getBoolean(K_SMOOTH, d.smoothAnimations),
            glass = p.getBoolean(K_GLASS, d.glass),
            navWidth = p.getFloat(K_NAV_WIDTH, d.navWidth).coerceIn(NAV_MIN_WIDTH, NAV_MAX_WIDTH),
            wavyProgress = p.getBoolean(K_WAVY, d.wavyProgress),
            nothingStyle = p.getBoolean(K_NOTHING, d.nothingStyle),
            font = enumOr(p.getString(K_FONT, null), d.font),
            lyricsWifiOnly = p.getBoolean(K_WIFI, d.lyricsWifiOnly),
            lyricsSyncControls = p.getBoolean(K_SYNC_CONTROLS, d.lyricsSyncControls),
            skipSilence = p.getBoolean(K_SILENCE, d.skipSilence),
            audioOffload = p.getBoolean(K_OFFLOAD, d.audioOffload),
            editTarget = enumOr(p.getString(K_EDIT_TARGET, null), d.editTarget),
            checkUpdates = p.getBoolean(K_UPDATES, d.checkUpdates),
            downloadFormat = enumOr(p.getString(K_DOWNLOAD_FORMAT, null), d.downloadFormat),
            songSort = enumOr(p.getString(K_SONG_SORT, null), d.songSort),
            songSortDescending = p.getBoolean(K_SONG_SORT_DESC, d.songSortDescending),
            musicFolders = p.getString(K_FOLDERS, null)?.split(FOLDER_SEPARATOR)?.filter { it.isNotBlank() }
                ?: d.musicFolders,
            volume = p.getFloat(K_VOLUME, d.volume).coerceIn(0f, 1f),
            muted = p.getBoolean(K_MUTED, d.muted),
            closeToTray = p.getBoolean(K_TRAY, d.closeToTray),
            outputDevice = p.getString(K_OUTPUT, null),
        )
    }

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        if (s == _settings.value) return
        _settings.value = s
        pendingWrite?.cancel()
        pendingWrite = scope.launch {
            delay(WRITE_DELAY_MS)
            write(_settings.value)
        }
    }

    /** Writes a change still waiting; for quitting. */
    @Synchronized
    fun flush() {
        if (pendingWrite?.isActive != true) return
        pendingWrite?.cancel()
        write(_settings.value)
    }

    private fun write(s: AppSettings) {
        prefs?.edit {
            putString(K_THEME, s.theme.name)
            putString(K_SOURCE, s.colorSource.name)
            putInt(K_SEED, s.seedColor)
            putBoolean(K_BLACK, s.pureBlack)
            putBoolean(K_HAPTICS, s.haptics)
            putBoolean(K_SMOOTH, s.smoothAnimations)
            putBoolean(K_GLASS, s.glass)
            putFloat(K_NAV_WIDTH, s.navWidth)
            putBoolean(K_WAVY, s.wavyProgress)
            putBoolean(K_NOTHING, s.nothingStyle)
            putString(K_FONT, s.font.name)
            putBoolean(K_WIFI, s.lyricsWifiOnly)
            putBoolean(K_SYNC_CONTROLS, s.lyricsSyncControls)
            putBoolean(K_SILENCE, s.skipSilence)
            putBoolean(K_OFFLOAD, s.audioOffload)
            putString(K_EDIT_TARGET, s.editTarget.name)
            putBoolean(K_UPDATES, s.checkUpdates)
            putString(K_DOWNLOAD_FORMAT, s.downloadFormat.name)
            putString(K_SONG_SORT, s.songSort.name)
            putBoolean(K_SONG_SORT_DESC, s.songSortDescending)
            putString(K_FOLDERS, s.musicFolders.joinToString(FOLDER_SEPARATOR))
            putFloat(K_VOLUME, s.volume)
            putBoolean(K_MUTED, s.muted)
            putBoolean(K_TRAY, s.closeToTray)
            putString(K_OUTPUT, s.outputDevice)
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private const val WRITE_DELAY_MS = 500L

    /** Not a character a Windows path can hold. */
    private const val FOLDER_SEPARATOR = "|"

    private const val K_THEME = "theme"
    private const val K_SOURCE = "color_source"
    private const val K_SEED = "seed_color"
    private const val K_BLACK = "pure_black"
    private const val K_HAPTICS = "haptics"
    private const val K_SMOOTH = "smooth_animations"
    private const val K_GLASS = "glass"
    private const val K_NAV_WIDTH = "nav_width"
    private const val K_WAVY = "wavy_progress"
    private const val K_NOTHING = "nothing_style"
    private const val K_FONT = "font"
    private const val K_WIFI = "lyrics_wifi_only"
    private const val K_SYNC_CONTROLS = "lyrics_sync_controls"
    private const val K_SILENCE = "skip_silence"
    private const val K_OFFLOAD = "audio_offload"
    private const val K_EDIT_TARGET = "edit_target"
    private const val K_UPDATES = "check_updates"
    private const val K_DOWNLOAD_FORMAT = "download_format"
    private const val K_SONG_SORT = "song_sort"
    private const val K_SONG_SORT_DESC = "song_sort_desc"
    private const val K_FOLDERS = "music_folders"
    private const val K_VOLUME = "volume"
    private const val K_MUTED = "muted"
    private const val K_TRAY = "close_to_tray"
    private const val K_OUTPUT = "output_device"
}
