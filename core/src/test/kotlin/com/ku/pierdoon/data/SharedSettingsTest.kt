package com.ku.pierdoon.data

import com.ku.pierdoon.data.download.DownloadFormat
import com.ku.pierdoon.data.metadata.SaveTarget
import com.ku.pierdoon.playback.EqPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedSettingsTest {

    /** A settings file kept in a map, values stored as text the way the PC's properties file does. */
    private class MapFile : SettingsReader, SettingsWriter {
        val values = mutableMapOf<String, String>()
        override fun getString(key: String, default: String?) = values[key] ?: default
        override fun getInt(key: String, default: Int) = values[key]?.toIntOrNull() ?: default
        override fun getFloat(key: String, default: Float) = values[key]?.toFloatOrNull() ?: default
        override fun getBoolean(key: String, default: Boolean) = values[key]?.toBooleanStrictOrNull() ?: default
        override fun putString(key: String, value: String?) { if (value == null) values -= key else values[key] = value }
        override fun putInt(key: String, value: Int) { values[key] = value.toString() }
        override fun putFloat(key: String, value: Float) { values[key] = value.toString() }
        override fun putBoolean(key: String, value: Boolean) { values[key] = value.toString() }
    }

    @Test
    fun everySettingComesBackAsWritten() {
        val changed = CommonSettings(
            theme = ThemeMode.Dark,
            colorSource = ColorSource.Custom,
            seedColor = 0xFF112233.toInt(),
            pureBlack = true,
            haptics = false,
            smoothAnimations = false,
            glass = false,
            wavyProgress = false,
            nothingStyle = true,
            font = AppFont.IbmPlexMono,
            lyricsWifiOnly = true,
            lyricsSyncControls = true,
            skipSilence = true,
            crossfadeSeconds = 5,
            playbackSpeed = 1.25f,
            crossfadeAlbums = true,
            cassetteMode = true,
            eqEnabled = true,
            eqGains = EqPreset.Rock.gains!!,
            eqPreset = EqPreset.Rock,
            editTarget = SaveTarget.File,
            checkUpdates = false,
            downloadFormat = DownloadFormat.Opus,
            songSort = SongSort.DateAdded,
            songSortDescending = true,
        )
        val file = MapFile()
        changed.write(file)
        assertEquals(changed, CommonSettings.read(file, CommonSettings()))
    }

    @Test
    fun theKeysStayAsTheAppsSavedThem() {
        val file = MapFile()
        CommonSettings().write(file)
        // Renaming any of these would lose every user's choice on update.
        assertEquals(
            setOf(
                "theme", "color_source", "seed_color", "pure_black", "haptics", "smooth_animations", "glass",
                "wavy_progress", "nothing_style", "font", "lyrics_wifi_only", "lyrics_sync_controls", "skip_silence",
                "crossfade_seconds", "playback_speed", "crossfade_albums", "cassette_mode", "equalizer",
                "equalizer_gains", "equalizer_preset", "edit_target", "check_updates", "download_format",
                "song_sort", "song_sort_desc",
            ),
            file.values.keys,
        )
    }

    @Test
    fun missingOrBrokenValuesFallBackToThePlatformDefaults() {
        val file = MapFile()
        file.values["theme"] = "Sepia"
        file.values["crossfade_seconds"] = "99"
        val defaults = CommonSettings(colorSource = ColorSource.Wallpaper, haptics = false)
        val read = CommonSettings.read(file, defaults)
        assertEquals(ThemeMode.System, read.theme)
        assertEquals(ColorSource.Wallpaper, read.colorSource)
        assertEquals(false, read.haptics)
        assertEquals(MAX_CROSSFADE_SECONDS, read.crossfadeSeconds)
    }
}
