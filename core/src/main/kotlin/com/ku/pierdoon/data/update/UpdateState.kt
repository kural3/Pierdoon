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
