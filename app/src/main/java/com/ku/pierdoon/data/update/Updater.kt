package com.ku.pierdoon.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.edit
import com.ku.pierdoon.BuildConfig
import com.ku.pierdoon.data.USER_AGENT
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

/**
 * Updates straight from the GitHub releases page: the app is not in a store, so nothing else
 * would tell its users that a new version is out.
 *
 * Releases are checked at most twice a day; the newest one with an APK attached is offered
 * (the Windows app publishes to the same repository). The APK is downloaded on request and
 * handed to [PackageInstaller]. Android shows its own confirmation and only accepts an APK
 * signed with the same key, so a tampered download can't replace the app.
 *
 * Process-wide like [com.ku.pierdoon.data.SettingsStore]: the install result arrives in a
 * receiver, outside any screen.
 */
object Updater : AppUpdater {
    private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    private const val K_LAST_CHECK = "last_check"
    private const val K_SKIPPED = "skipped_version"

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var download: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    const val currentVersion: String = BuildConfig.VERSION_NAME

    @Synchronized
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
    }

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
            val release = withContext(Dispatchers.IO) { GitHubReleases.latest(".apk", "android-", USER_AGENT) }
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

    /** Downloads [release] and opens the system install prompt. */
    override fun install(release: AppRelease) {
        if (download?.isActive == true) return
        download = scope.launch {
            _state.value = UpdateState.Downloading(release, null)
            val ok = try {
                val apk = withContext(Dispatchers.IO) {
                    downloadApk(release) { p -> _state.value = UpdateState.Downloading(release, p) }
                }
                withContext(Dispatchers.IO) { commit(apk) }
                true
            } catch (e: CancellationException) {
                _state.value = UpdateState.Available(release)
                throw e
            } catch (_: Exception) {
                false
            }
            _state.value = if (ok) UpdateState.Installing(release) else UpdateState.InstallFailed(release)
        }
    }

    override fun cancel() {
        download?.cancel()
    }

    /** Called by [InstallResultReceiver]. Success never arrives here: the new version replaces this process. */
    internal fun onInstallResult(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val release = (_state.value as? UpdateState.Installing)?.release ?: return
                // Cancelled in the system dialog lands here too: offer it again rather than an error.
                _state.value = if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0) == PackageInstaller.STATUS_FAILURE_ABORTED) {
                    UpdateState.Available(release)
                } else {
                    UpdateState.InstallFailed(release)
                }
            }
        }
    }

    private suspend fun downloadApk(release: AppRelease, onProgress: (Float?) -> Unit): File {
        val dir = File(app.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Pierdoon-${release.version}.apk")
        GitHubReleases.download(release, file, USER_AGENT, onProgress)
        return file
    }

    private fun commit(apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Skips the prompt where Android allows it (this app installed the previous version).
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callback = PendingIntent.getBroadcast(
                app, id, Intent(app, InstallResultReceiver::class.java), flags,
            )
            session.commit(callback.intentSender)
        }
        apk.delete()
    }

}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Updater.init(context)
        Updater.onInstallResult(context, intent)
    }
}
