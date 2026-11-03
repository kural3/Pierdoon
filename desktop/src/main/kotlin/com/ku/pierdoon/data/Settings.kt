package com.ku.pierdoon.data

import androidx.compose.runtime.Immutable
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

/** How the Delicious mode draws the music. */
enum class DeliciousStyle { Aura, Waves, Dust, Calm }

/** Narrowest navigation rail: a column of icons. */
const val NAV_MIN_WIDTH = 88f
/** Widest: the pills just fit the longest label. */
const val NAV_MAX_WIDTH = 232f

/** The PC's defaults for what both apps have: album art colours, no click on presses. */
private val PC_DEFAULTS = CommonSettings(colorSource = ColorSource.AlbumArt, haptics = false)

/** The settings both apps have ([common]) and the PC's own. */
@Immutable
data class AppSettings(
    val common: CommonSettings = PC_DEFAULTS,
    /** Width of the navigation rail in dp, dragged by its edge: [NAV_MIN_WIDTH] shows icons only. */
    val navWidth: Float = NAV_MAX_WIDTH,
    val language: AppLanguage = AppLanguage.System,
    /** Folders scanned for music, subfolders included. */
    val musicFolders: List<String> = listOf(AppDirs.music.absolutePath),
    /** Folders inside the music folders that the library skips, subfolders included. */
    val excludedFolders: List<String> = emptyList(),
    /** Where downloads are saved; null = a Pier.doon folder in the user's Music. */
    val downloadFolder: String? = null,
    val volume: Float = 1f,
    val muted: Boolean = false,
    /** Closing the window hides it to the tray while music keeps playing. */
    val closeToTray: Boolean = true,
    /** Audio output by mixer name; null = the system default. */
    val outputDevice: String? = null,
    /** The phone app may connect over the local network (see RemoteServer). */
    val remoteControl: Boolean = false,
    /** What's playing shows on the user's Discord profile. */
    val discordPresence: Boolean = false,
    /** The picture behind the Delicious mode's lyrics. */
    val deliciousStyle: DeliciousStyle = DeliciousStyle.Aura,
    /** The browser yt-dlp takes YouTube cookies from, so YouTube doesn't take downloads for a bot. */
    val cookiesBrowser: CookieBrowser = CookieBrowser.Off,
) : SharedSettings by common {
    /** A change to the settings both apps have. */
    fun shared(change: CommonSettings.() -> CommonSettings): AppSettings = copy(common = common.change())

    /** The user's own font file, when one was picked; it lives in [SettingsStore.fontsDir]. */
    val customFontPath: String? get() = customFontFile?.let { java.io.File(SettingsStore.fontsDir, it).path }
}

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

    /** Where the user's own font is kept (see [CustomFonts]). */
    val fontsDir: java.io.File get() = java.io.File(AppDirs.files, "fonts")

    @Synchronized
    fun init() {
        if (prefs != null) return
        val p = Prefs("settings")
        prefs = p
        val d = AppSettings()
        _settings.value = AppSettings(
            common = CommonSettings.read(p, d.common),
            navWidth = p.getFloat(K_NAV_WIDTH, d.navWidth).coerceIn(NAV_MIN_WIDTH, NAV_MAX_WIDTH),
            language = AppLanguage.fromTag(p.getString(K_LANGUAGE, null)),
            musicFolders = p.getString(K_FOLDERS, null)?.split(FOLDER_SEPARATOR)?.filter { it.isNotBlank() }
                ?: d.musicFolders,
            excludedFolders = p.getString(K_EXCLUDED, null)?.split(FOLDER_SEPARATOR)?.filter { it.isNotBlank() }
                ?: d.excludedFolders,
            downloadFolder = p.getString(K_DOWNLOAD_FOLDER, null)?.takeIf { it.isNotBlank() },
            volume = p.getFloat(K_VOLUME, d.volume).coerceIn(0f, 1f),
            muted = p.getBoolean(K_MUTED, d.muted),
            closeToTray = p.getBoolean(K_TRAY, d.closeToTray),
            outputDevice = p.getString(K_OUTPUT, null),
            remoteControl = p.getBoolean(K_REMOTE, d.remoteControl),
            discordPresence = p.getBoolean(K_DISCORD, d.discordPresence),
            deliciousStyle = enumOr(p.getString(K_DELICIOUS, null), d.deliciousStyle),
            cookiesBrowser = enumOr(p.getString(K_COOKIES, null), d.cookiesBrowser),
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
            s.common.write(this)
            putFloat(K_NAV_WIDTH, s.navWidth)
            putString(K_LANGUAGE, s.language.tag)
            putString(K_FOLDERS, s.musicFolders.joinToString(FOLDER_SEPARATOR))
            putString(K_EXCLUDED, s.excludedFolders.joinToString(FOLDER_SEPARATOR))
            putString(K_DOWNLOAD_FOLDER, s.downloadFolder)
            putFloat(K_VOLUME, s.volume)
            putBoolean(K_MUTED, s.muted)
            putBoolean(K_TRAY, s.closeToTray)
            putString(K_OUTPUT, s.outputDevice)
            putBoolean(K_REMOTE, s.remoteControl)
            putBoolean(K_DISCORD, s.discordPresence)
            putString(K_DELICIOUS, s.deliciousStyle.name)
            putString(K_COOKIES, s.cookiesBrowser.name)
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private const val WRITE_DELAY_MS = 500L

    /** Not a character a Windows path can hold. */
    private const val FOLDER_SEPARATOR = "|"

    private const val K_NAV_WIDTH = "nav_width"
    private const val K_LANGUAGE = "language"
    private const val K_FOLDERS = "music_folders"
    private const val K_EXCLUDED = "excluded_folders"
    private const val K_DOWNLOAD_FOLDER = "download_folder"
    private const val K_VOLUME = "volume"
    private const val K_MUTED = "muted"
    private const val K_TRAY = "close_to_tray"
    private const val K_OUTPUT = "output_device"
    private const val K_REMOTE = "remote_control"
    private const val K_DISCORD = "discord_presence"
    private const val K_DELICIOUS = "delicious_style"
    private const val K_COOKIES = "cookies_browser"
}

/** Browsers yt-dlp can read cookies from on Windows, by its own names for them. */
enum class CookieBrowser(val ytDlpName: String?, val label: String) {
    Off(null, ""),
    Firefox("firefox", "Firefox"),
    Chrome("chrome", "Chrome"),
    Edge("edge", "Edge"),
    Brave("brave", "Brave"),
    Opera("opera", "Opera"),
    Vivaldi("vivaldi", "Vivaldi"),
}
