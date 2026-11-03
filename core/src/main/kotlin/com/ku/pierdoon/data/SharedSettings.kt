package com.ku.pierdoon.data

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.metadata.SaveTarget
import com.ku.pierdoon.playback.EQ_FLAT
import com.ku.pierdoon.playback.EqPreset
import com.ku.pierdoon.playback.PlaybackSpeed
import com.ku.pierdoon.playback.formatEqGains
import com.ku.pierdoon.playback.parseEqGains

enum class ThemeMode { System, Light, Dark }

/** Where the palette comes from: the wallpaper (the Windows accent colour on a PC), current album art, or a chosen colour. */
enum class ColorSource { Wallpaper, AlbumArt, Custom }

/** Typeface for the whole app; [Custom] is a font file the user brought (see [SharedSettings.customFontFile]). */
enum class AppFont { System, SystemMono, JetBrainsMono, IbmPlexMono, Custom }

const val DEFAULT_SEED: Int = 0xFF6750A4.toInt()

/**
 * The settings both apps have, kept under the same keys. Each app's `AppSettings` is one of these
 * (delegating to a [CommonSettings]) plus what only that app has; a shared one is added here once.
 */
interface SharedSettings {
    val theme: ThemeMode
    val colorSource: ColorSource
    val seedColor: Int
    /** True black surfaces in dark theme: OLED pixels switch off and save power. */
    val pureBlack: Boolean
    /** Haptics on the phone; on a PC a soft click on presses. */
    val haptics: Boolean
    /** Ask the display for its top refresh rate while animating. */
    val smoothAnimations: Boolean
    val glass: Boolean
    val wavyProgress: Boolean
    /** Nothing-style look: monochrome with a red accent, dot-matrix headings, dotted progress. */
    val nothingStyle: Boolean
    val font: AppFont
    /** The user's own font: a file name in the app's fonts folder, copied there when picked. */
    val customFontFile: String?
    /** That font's name, as shown in the font list. */
    val customFontName: String?
    /** Lyrics downloaded on Wi-Fi only (on a PC: from the cache only). */
    val lyricsWifiOnly: Boolean
    /** Buttons that shift synced lyrics earlier or later; most tracks never need them. */
    val lyricsSyncControls: Boolean
    val skipSilence: Boolean
    /** Seconds one track fades into the next; 0 for none. */
    val crossfadeSeconds: Int
    /** Playback speed, pitch following it; one of [PlaybackSpeed.options]. */
    val playbackSpeed: Float
    /** Fade between tracks of one album too; off keeps albums gapless, as recorded. */
    val crossfadeAlbums: Boolean
    /** The queue as a tape: its sound, a cassette in the player, two sides, the deck's keys and spins. */
    val cassetteMode: Boolean
    val eqEnabled: Boolean
    /** Gain per band in dB, [com.ku.pierdoon.playback.EQ_BANDS] long; a preset copies its own in. */
    val eqGains: List<Float>
    /** The preset [eqGains] came from, or [EqPreset.Custom] once a band was moved by hand. */
    val eqPreset: EqPreset
    /** Last choice on the track editor: the app's records or the file itself. */
    val editTarget: SaveTarget
    /** Look for a new release on GitHub now and then (at most twice a day). */
    val checkUpdates: Boolean
    val downloadFormat: DownloadFormat
    val songSort: SongSort
    val songSortDescending: Boolean
}

@Immutable
data class CommonSettings(
    override val theme: ThemeMode = ThemeMode.System,
    override val colorSource: ColorSource = ColorSource.AlbumArt,
    override val seedColor: Int = DEFAULT_SEED,
    override val pureBlack: Boolean = false,
    override val haptics: Boolean = true,
    override val smoothAnimations: Boolean = true,
    override val glass: Boolean = true,
    override val wavyProgress: Boolean = true,
    override val nothingStyle: Boolean = false,
    override val font: AppFont = AppFont.System,
    override val customFontFile: String? = null,
    override val customFontName: String? = null,
    override val lyricsWifiOnly: Boolean = false,
    override val lyricsSyncControls: Boolean = false,
    override val skipSilence: Boolean = false,
    override val crossfadeSeconds: Int = 0,
    override val playbackSpeed: Float = 1f,
    override val crossfadeAlbums: Boolean = false,
    override val cassetteMode: Boolean = false,
    override val eqEnabled: Boolean = false,
    override val eqGains: List<Float> = EQ_FLAT,
    override val eqPreset: EqPreset = EqPreset.Flat,
    override val editTarget: SaveTarget = SaveTarget.App,
    override val checkUpdates: Boolean = true,
    override val downloadFormat: DownloadFormat = DownloadFormat.M4a,
    override val songSort: SongSort = SongSort.Title,
    override val songSortDescending: Boolean = false,
) : SharedSettings {

    /** Written into a settings file, under the keys both apps use. */
    fun write(to: SettingsWriter) = with(to) {
        putString(K_THEME, theme.name)
        putString(K_SOURCE, colorSource.name)
        putInt(K_SEED, seedColor)
        putBoolean(K_BLACK, pureBlack)
        putBoolean(K_HAPTICS, haptics)
        putBoolean(K_SMOOTH, smoothAnimations)
        putBoolean(K_GLASS, glass)
        putBoolean(K_WAVY, wavyProgress)
        putBoolean(K_NOTHING, nothingStyle)
        putString(K_FONT, font.name)
        putString(K_CUSTOM_FONT_FILE, customFontFile)
        putString(K_CUSTOM_FONT_NAME, customFontName)
        putBoolean(K_WIFI, lyricsWifiOnly)
        putBoolean(K_SYNC_CONTROLS, lyricsSyncControls)
        putBoolean(K_SILENCE, skipSilence)
        putInt(K_CROSSFADE, crossfadeSeconds)
        putFloat(K_SPEED, playbackSpeed)
        putBoolean(K_CROSSFADE_ALBUMS, crossfadeAlbums)
        putBoolean(K_CASSETTE_MODE, cassetteMode)
        putBoolean(K_EQ, eqEnabled)
        putString(K_EQ_GAINS, formatEqGains(eqGains))
        putString(K_EQ_PRESET, eqPreset.name)
        putString(K_EDIT_TARGET, editTarget.name)
        putBoolean(K_UPDATES, checkUpdates)
        putString(K_DOWNLOAD_FORMAT, downloadFormat.name)
        putString(K_SONG_SORT, songSort.name)
        putBoolean(K_SONG_SORT_DESC, songSortDescending)
    }

    companion object {
        /** Read from a settings file; anything missing or unreadable takes its value from [defaults]. */
        fun read(from: SettingsReader, defaults: CommonSettings): CommonSettings = with(from) {
            val d = defaults
            CommonSettings(
                theme = enumOr(getString(K_THEME, null), d.theme),
                colorSource = enumOr(getString(K_SOURCE, null), d.colorSource),
                seedColor = getInt(K_SEED, d.seedColor),
                pureBlack = getBoolean(K_BLACK, d.pureBlack),
                haptics = getBoolean(K_HAPTICS, d.haptics),
                smoothAnimations = getBoolean(K_SMOOTH, d.smoothAnimations),
                glass = getBoolean(K_GLASS, d.glass),
                wavyProgress = getBoolean(K_WAVY, d.wavyProgress),
                nothingStyle = getBoolean(K_NOTHING, d.nothingStyle),
                font = enumOr(getString(K_FONT, null), d.font),
                customFontFile = getString(K_CUSTOM_FONT_FILE, d.customFontFile),
                customFontName = getString(K_CUSTOM_FONT_NAME, d.customFontName),
                lyricsWifiOnly = getBoolean(K_WIFI, d.lyricsWifiOnly),
                lyricsSyncControls = getBoolean(K_SYNC_CONTROLS, d.lyricsSyncControls),
                skipSilence = getBoolean(K_SILENCE, d.skipSilence),
                crossfadeSeconds = getInt(K_CROSSFADE, d.crossfadeSeconds).coerceIn(0, MAX_CROSSFADE_SECONDS),
                playbackSpeed = PlaybackSpeed.nearest(getFloat(K_SPEED, d.playbackSpeed)),
                crossfadeAlbums = getBoolean(K_CROSSFADE_ALBUMS, d.crossfadeAlbums),
                cassetteMode = getBoolean(K_CASSETTE_MODE, d.cassetteMode),
                eqEnabled = getBoolean(K_EQ, d.eqEnabled),
                eqGains = parseEqGains(getString(K_EQ_GAINS, null)),
                eqPreset = enumOr(getString(K_EQ_PRESET, null), d.eqPreset),
                editTarget = enumOr(getString(K_EDIT_TARGET, null), d.editTarget),
                checkUpdates = getBoolean(K_UPDATES, d.checkUpdates),
                downloadFormat = enumOr(getString(K_DOWNLOAD_FORMAT, null), d.downloadFormat),
                songSort = enumOr(getString(K_SONG_SORT, null), d.songSort),
                songSortDescending = getBoolean(K_SONG_SORT_DESC, d.songSortDescending),
            )
        }

        /** The key the colour source is kept under, for a migration that needs to know it was never set. */
        const val COLOR_SOURCE_KEY = "color_source"

        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

        private const val K_THEME = "theme"
        private const val K_SOURCE = COLOR_SOURCE_KEY
        private const val K_SEED = "seed_color"
        private const val K_BLACK = "pure_black"
        private const val K_HAPTICS = "haptics"
        private const val K_SMOOTH = "smooth_animations"
        private const val K_GLASS = "glass"
        private const val K_WAVY = "wavy_progress"
        private const val K_NOTHING = "nothing_style"
        private const val K_FONT = "font"
        private const val K_CUSTOM_FONT_FILE = "custom_font_file"
        private const val K_CUSTOM_FONT_NAME = "custom_font_name"
        private const val K_WIFI = "lyrics_wifi_only"
        private const val K_SYNC_CONTROLS = "lyrics_sync_controls"
        private const val K_SILENCE = "skip_silence"
        private const val K_CROSSFADE = "crossfade_seconds"
        private const val K_SPEED = "playback_speed"
        private const val K_CROSSFADE_ALBUMS = "crossfade_albums"
        private const val K_CASSETTE_MODE = "cassette_mode"
        private const val K_EQ = "equalizer"
        private const val K_EQ_GAINS = "equalizer_gains"
        private const val K_EQ_PRESET = "equalizer_preset"
        private const val K_EDIT_TARGET = "edit_target"
        private const val K_UPDATES = "check_updates"
        private const val K_DOWNLOAD_FORMAT = "download_format"
        private const val K_SONG_SORT = "song_sort"
        private const val K_SONG_SORT_DESC = "song_sort_desc"
    }
}

/** A settings file to read: SharedPreferences on the phone, a properties file on the PC. */
interface SettingsReader {
    fun getString(key: String, default: String?): String?
    fun getInt(key: String, default: Int): Int
    fun getFloat(key: String, default: Float): Float
    fun getBoolean(key: String, default: Boolean): Boolean
}

/** A settings file being written. */
interface SettingsWriter {
    fun putString(key: String, value: String?)
    fun putInt(key: String, value: Int)
    fun putFloat(key: String, value: Float)
    fun putBoolean(key: String, value: Boolean)
}
