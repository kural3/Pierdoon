package com.ku.pierdoon.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.ku.pierdoon.BuildConfig
import com.ku.pierdoon.data.USER_AGENT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A published release that has an APK attached. */
@Immutable
data class AppRelease(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object CheckFailed : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    /** [progress] is 0..1, or null while the size is still unknown. */
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateState
    /** Handed to the system installer; the user confirms there. */
    data class Installing(val release: AppRelease) : UpdateState
    data class InstallFailed(val release: AppRelease) : UpdateState
}

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
object Updater {
    private const val RELEASES = "https://api.github.com/repos/kural3/Pierdoon/releases?per_page=30"
    private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    private const val K_LAST_CHECK = "last_check"
    private const val K_SKIPPED = "skipped_version"

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var download: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    const val currentVersion: String = BuildConfig.VERSION_NAME

    @Synchronized
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
    }

    fun isDue(): Boolean = System.currentTimeMillis() - prefs.getLong(K_LAST_CHECK, 0L) >= CHECK_INTERVAL_MS

    fun isSkipped(release: AppRelease): Boolean = prefs.getString(K_SKIPPED, null) == release.version

    /** "Later" on a release: the automatic check stays quiet about it; a manual one still shows it. */
    fun skip(release: AppRelease) = prefs.edit { putString(K_SKIPPED, release.version) }

    /** Asks GitHub for the latest release. Never interrupts a download or install under way. */
    suspend fun check(): UpdateState {
        when (_state.value) {
            is UpdateState.Downloading, is UpdateState.Installing, UpdateState.Checking -> return _state.value
            else -> Unit
        }
        _state.value = UpdateState.Checking
        val result = try {
            val release = withContext(Dispatchers.IO) { fetchLatest() }
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
    fun install(release: AppRelease) {
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

    fun cancel() {
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

    private fun fetchLatest(): AppRelease? {
        val conn = open(RELEASES, "application/vnd.github+json")
        val releases = try {
            JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
        for (i in 0 until releases.length()) {
            val o = releases.getJSONObject(i)
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) continue
            val assets = o.optJSONArray("assets") ?: continue
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) } ?: continue
            return AppRelease(
                version = o.optString("tag_name").removePrefix("android-").removePrefix("v"),
                notes = o.optString("body"),
                apkUrl = apk.getString("browser_download_url"),
                apkSize = apk.optLong("size"),
            )
        }
        return null
    }

    /** Cancellable between chunks: the "Cancel" button stops it within one read. */
    private suspend fun downloadApk(release: AppRelease, onProgress: (Float?) -> Unit): File {
        val dir = File(app.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Pierdoon-${release.version}.apk")
        val conn = open(release.apkUrl, "application/octet-stream")
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
            var read = 0L
            var lastReported = -1
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        val percent = total?.let { (read * 100 / it).toInt() }
                        if (percent != lastReported) {
                            lastReported = percent ?: -1
                            onProgress(total?.let { read.toFloat() / it })
                        }
                    }
                }
            }
            if (total != null && read != total) throw IOException("Truncated download")
        } finally {
            conn.disconnect()
        }
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

    private fun open(url: String, accept: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("User-Agent", USER_AGENT)
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP ${conn.responseCode}")
        }
        return conn
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Updater.init(context)
        Updater.onInstallResult(context, intent)
    }
}
