package com.ku.pierdoon.data.update

import com.ku.pierdoon.BuildConfig
import com.ku.pierdoon.data.USER_AGENT
import com.ku.pierdoon.platform.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

/**
 * Updates straight from the GitHub releases page: the app is not in a store, so nothing else
 * would tell its users that a new version is out.
 *
 * Releases are checked at most twice a day; the newest one with an .msi attached is offered.
 * The installer is downloaded on request, started, and the app quits so it can be replaced.
 */
object Updater : AppUpdater {
    private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    private const val K_LAST_CHECK = "last_check"
    private const val K_SKIPPED = "skipped_version"

    private val prefs by lazy { Prefs("updates") }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var download: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    const val currentVersion: String = BuildConfig.VERSION_NAME

    /** Set by the app: saves what needs saving and exits, so the installer can replace the files. */
    var onQuitForUpdate: (() -> Unit)? = null

    override fun isDue(): Boolean = System.currentTimeMillis() - prefs.getLong(K_LAST_CHECK, 0L) >= CHECK_INTERVAL_MS

    override fun isSkipped(release: AppRelease): Boolean = prefs.getString(K_SKIPPED, null) == release.version

    /** "Later" on a release: the automatic check stays quiet about it; a manual one still shows it. */
    override fun skip(release: AppRelease) = prefs.edit { putString(K_SKIPPED, release.version) }

    /** Asks GitHub for the latest release. Never interrupts a download or install under way. */
    override suspend fun check(): UpdateState {
        when (_state.value) {
            is UpdateState.Downloading, is UpdateState.Installing, UpdateState.Checking -> return _state.value
            else -> Unit
        }
        _state.value = UpdateState.Checking
        val result = try {
            val release = withContext(Dispatchers.IO) { GitHubReleases.latest(".msi", "desktop-", USER_AGENT) }
            prefs.edit { putLong(K_LAST_CHECK, System.currentTimeMillis()) }
            if (release != null && isNewerVersion(release.version, currentVersion)) {
                UpdateState.Available(release)
            } else {
                UpdateState.UpToDate
            }
        } catch (e: CancellationException) {
            _state.value = UpdateState.Idle
            throw e
        } catch (_: Exception) {
            UpdateState.CheckFailed
        }
        _state.value = result
        return result
    }

    /** Downloads [release], starts its installer and quits. */
    override fun install(release: AppRelease) {
        if (download?.isActive == true) return
        download = scope.launch {
            _state.value = UpdateState.Downloading(release, null)
            val installer = try {
                withContext(Dispatchers.IO) {
                    downloadInstaller(release) { p -> _state.value = UpdateState.Downloading(release, p) }
                }
            } catch (e: CancellationException) {
                _state.value = UpdateState.Available(release)
                throw e
            } catch (_: Exception) {
                null
            }
            if (installer == null) {
                _state.value = UpdateState.InstallFailed(release)
                return@launch
            }
            _state.value = UpdateState.Installing(release)
            val log = File(installer.parentFile, "install.log")
            val started = runCatching {
                ProcessBuilder("msiexec.exe", "/i", installer.absolutePath, "/passive", "/l*v", log.absolutePath).start()
            }.isSuccess
            if (started) {
                onQuitForUpdate?.invoke() ?: exitProcess(0)
            } else {
                _state.value = UpdateState.InstallFailed(release)
            }
        }
    }

    override fun cancel() {
        download?.cancel()
    }

    private suspend fun downloadInstaller(release: AppRelease, onProgress: (Float?) -> Unit): File {
        // In %TEMP%, never under the install folder: the upgrade wipes that folder while the
        // installer still reads from it, which aborts it after the old version is gone.
        val dir = File(System.getProperty("java.io.tmpdir"), "Pier.doon-update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Pierdoon-${release.version}.msi")
        GitHubReleases.download(release, file, USER_AGENT, onProgress)
        return file
    }

}
