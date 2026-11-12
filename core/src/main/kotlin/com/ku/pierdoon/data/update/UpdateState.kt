package com.ku.pierdoon.data.update

import androidx.compose.runtime.Immutable

/** A published release with this app's own file attached: the APK, or the Windows installer. */
@Immutable
data class AppRelease(
    val version: String,
    val notes: String,
    val fileUrl: String,
    val fileSize: Long,
    /** Hex SHA-256 GitHub lists for the file; null on releases from before it did. */
    val sha256: String? = null,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object CheckFailed : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    /** [progress] is 0..1, or null while the size is still unknown. */
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateState
    /** Handed to the system's installer: Android asks the user, Windows' quits the app to replace it. */
    data class Installing(val release: AppRelease) : UpdateState
    data class InstallFailed(val release: AppRelease) : UpdateState
}

/**
 * Each app's updater: the same GitHub releases, a different file (an APK, an .msi) and a
 * different way to install it.
 */
interface AppUpdater {
    val state: kotlinx.coroutines.flow.StateFlow<UpdateState>

    /** The last check was long enough ago for a quiet one on launch. */
    fun isDue(): Boolean

    /** The user put [release] off with "Later". */
    fun isSkipped(release: AppRelease): Boolean

    /** "Later" on a release: the automatic check stays quiet about it; a manual one still shows it. */
    fun skip(release: AppRelease)

    /** Asks GitHub for the latest release. Never interrupts a download or install under way. */
    suspend fun check(): UpdateState

    /** Downloads [release] and hands it to the system's installer. */
    fun install(release: AppRelease)

    /** Stops a download under way. */
    fun cancel()
}
