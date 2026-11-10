package com.ku.pierdoon.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where downloads and tracks from the computer go, relative to the storage root. */
const val DEFAULT_SAVE_FOLDER = "Music/Pier.doon"

/** The phone's defaults for what both apps have: wallpaper colours where Android has them, haptics on. */
private val PHONE_DEFAULTS = CommonSettings(
    colorSource =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) ColorSource.Wallpaper
        else ColorSource.Custom,
    haptics = true,
)

/** How big the covers are in the library's album grid: the narrowest a cell may get, in dp. */
enum class AlbumGrid(val minCellDp: Int) { Large(150), Medium(108), Small(80) }

/** The settings both apps have ([common]) and the phone's own. */
@Immutable
data class AppSettings(
    val common: CommonSettings = PHONE_DEFAULTS,
    /** Rounded tab bar hovering above content instead of docked to the bottom edge. */
    /** Folders left out of the library, as [MusicFolder.path]s: only that folder, not the ones inside it. */
    val excludedFolders: Set<String> = emptySet(),
    /** The library's album grid; large covers unless picked otherwise. */
    val albumGrid: AlbumGrid = AlbumGrid.Large,
) : SharedSettings by common {
    /** A change to the settings both apps have. */
    fun shared(change: CommonSettings.() -> CommonSettings): AppSettings = copy(common = common.change())

    /** The user's own font file, when one was picked; it lives in [SettingsStore.fontsDir]. */
    val customFontPath: String? get() = customFontFile?.let { java.io.File(SettingsStore.fontsDir, it).path }
}

/**
 * App-wide settings shared by the UI and the playback service (same process).
 * Backed by SharedPreferences: tiny, already in the platform, async writes.
 */
object SettingsStore {
    private var prefs: SharedPreferences? = null
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Where the user's own font is kept (see [CustomFonts]). */
    lateinit var fontsDir: java.io.File
        private set

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        fontsDir = java.io.File(context.applicationContext.filesDir, "fonts")
        val d = AppSettings()
        // The old on/off "wallpaper colours" switch, from before there was a colour source.
        val defaults =
            if (!p.contains(CommonSettings.COLOR_SOURCE_KEY) && !p.getBoolean(K_DYNAMIC, true)) d.common.copy(colorSource = ColorSource.Custom)
            else d.common
        _settings.value = AppSettings(
            common = CommonSettings.read(Reader(p), defaults),
            excludedFolders = p.getStringSet(K_EXCLUDED, null)?.toSet() ?: d.excludedFolders,
            albumGrid = AlbumGrid.entries.firstOrNull { it.name == p.getString(K_ALBUM_GRID, null) } ?: d.albumGrid,
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        if (s == _settings.value) return
        _settings.value = s
        prefs?.edit {
            s.common.write(Writer(this))
            putStringSet(K_EXCLUDED, s.excludedFolders)
            putString(K_ALBUM_GRID, s.albumGrid.name)
        }
    }

    private class Reader(private val p: SharedPreferences) : SettingsReader {
        override fun getString(key: String, default: String?) = p.getString(key, default)
        override fun getInt(key: String, default: Int) = p.getInt(key, default)
        override fun getFloat(key: String, default: Float) = p.getFloat(key, default)
        override fun getBoolean(key: String, default: Boolean) = p.getBoolean(key, default)
    }

    private class Writer(private val e: SharedPreferences.Editor) : SettingsWriter {
        override fun putString(key: String, value: String?) { e.putString(key, value) }
        override fun putInt(key: String, value: Int) { e.putInt(key, value) }
        override fun putFloat(key: String, value: Float) { e.putFloat(key, value) }
        override fun putBoolean(key: String, value: Boolean) { e.putBoolean(key, value) }
    }

    private const val K_DYNAMIC = "dynamic_color"
    private const val K_EXCLUDED = "excluded_folders"
    private const val K_ALBUM_GRID = "album_grid"
}
