package com.ku.pierdoon.data.update

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.BuildConfig
import com.ku.pierdoon.data.USER_AGENT
import com.ku.pierdoon.platform.Prefs
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
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.system.exitProcess

/** A published release that has a Windows installer attached. */
@Immutable
data class AppRelease(
    val version: String,
    val notes: String,
    val installerUrl: String,
    val installerSize: Long,
    /** Hex SHA-256 GitHub lists for the installer; null on releases from before it did. */
    val installerSha256: String? = null,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object CheckFailed : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    /** [progress] is 0..1, or null while the size is still unknown. */
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateState
    /** Handed to the Windows installer; the app quits so it can be replaced. */
    data class Installing(val release: AppRelease) : UpdateState
    data class InstallFailed(val release: AppRelease) : UpdateState
}

/**
 * Updates straight from the GitHub releases page: the app is not in a store, so nothing else
 * would tell its users that a new version is out.
 *
 * Releases are checked at most twice a day; the newest one with an .msi attached is offered.
 * The installer is downloaded on request, started, and the app quits so it can be replaced.
 */
object Updater {
    /** One repository for both apps (desktop-… tags): only releases with an .msi attached count. */
    private const val RELEASES = "https://api.github.com/repos/kural3/Pierdoon/releases?per_page=30"
    private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    private const val K_LAST_CHECK = "last_check"
    private const val K_SKIPPED = "skipped_version"

    private val prefs by lazy { Prefs("updates") }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var download: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    const val currentVersion: String = BuildConfig.VERSION_NAME

    /** Set by the app: saves what needs saving and exits, so the installer can replace the files. */
    var onQuitForUpdate: (() -> Unit)? = null

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

    /** Downloads [release], starts its installer and quits. */
    fun install(release: AppRelease) {
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

    fun cancel() {
        download?.cancel()
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
            val msi = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".msi", ignoreCase = true) } ?: continue
            return AppRelease(
                version = o.optString("tag_name").removePrefix("desktop-").removePrefix("v"),
                notes = o.optString("body"),
                installerUrl = msi.getString("browser_download_url"),
                installerSize = msi.optLong("size"),
                installerSha256 = sha256Of(msi.optString("digest")),
            )
        }
        return null
    }

    /**
     * Cancellable between chunks: the "Cancel" button stops it within one read. Checked against
     * the SHA-256 GitHub lists for it before it is ever started.
     */
    private suspend fun downloadInstaller(release: AppRelease, onProgress: (Float?) -> Unit): File {
        // In %TEMP%, never under the install folder: the upgrade wipes that folder while the
        // installer still reads from it, which aborts it after the old version is gone.
        val dir = File(System.getProperty("java.io.tmpdir"), "Pier.doon-update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Pierdoon-${release.version}.msi")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(release.installerUrl, "application/octet-stream")
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.installerSize.takeIf { it > 0 }
            var read = 0L
            var lastReported: Int? = -1
            DigestInputStream(conn.inputStream, digest).use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        val percent = total?.let { (read * 100 / it).toInt() }
                        // Unknown size: reported once as null, not on every chunk.
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(total?.let { read.toFloat() / it })
                        }
                    }
                }
            }
            if (total != null && read != total) throw IOException("Truncated download")
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (release.installerSha256 != null && actual != release.installerSha256) {
                throw IOException("Installer checksum mismatch")
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        return file
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

/** The hex out of "sha256:<hex>", as GitHub gives an asset's digest; null for anything else. */
internal fun sha256Of(digest: String): String? =
    digest.removePrefix("sha256:").lowercase()
        .takeIf { digest.startsWith("sha256:") && it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
