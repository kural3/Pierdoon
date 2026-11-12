package com.ku.pierdoon.data.download

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow

/** The download queue as the download screen sees it; each app's Downloader runs yt-dlp its own way. */
interface DownloadQueue {
    /** False where yt-dlp can't run (Android before 10). */
    val supported: Boolean

    /** Everything queued, running and finished this session. */
    val tasks: StateFlow<List<DownloadTask>>

    /** Gets yt-dlp ready before the first link, so the first download starts sooner. */
    fun warmUp()

    fun enqueue(url: String, format: DownloadFormat, title: String? = null, artist: String? = null)
    fun remove(id: Long)
    fun retry(id: Long)

    /** Drops the finished and failed downloads from the list. */
    fun clearFinished()

    /** yt-dlp's version, once it's known. */
    val engineVersion: StateFlow<String?>

    /** How a check for a newer yt-dlp went. */
    val engineUpdate: StateFlow<EngineUpdate>

    /** Fetches the newest yt-dlp now, not waiting for the daily check. */
    fun updateEngine()
    suspend fun search(query: String, source: SearchSource): List<SearchHit>
}

enum class FailReason { Unsupported, Unavailable, Bot, Drm, NoAudio, Network, Save, Other }

sealed interface DownloadStatus {
    data object Queued : DownloadStatus

    /** Starting yt-dlp and reading the page. */
    data object Preparing : DownloadStatus

    /** [progress] 0..1, or null while unknown. */
    data class Downloading(val progress: Float?) : DownloadStatus

    /** Tags, cover and the copy into the music folder. */
    data object Saving : DownloadStatus

    data class Done(val songId: Long) : DownloadStatus
    data class Failed(val reason: FailReason, val detail: String?) : DownloadStatus
}

@Immutable
data class DownloadTask(
    val id: Long,
    val url: String,
    val format: DownloadFormat,
    val title: String? = null,
    val artist: String? = null,
    val status: DownloadStatus = DownloadStatus.Queued,
    /** Set on the tracks of a playlist or album link; they end up in a Pier.doon playlist together. */
    val group: DownloadGroup? = null,
) {
    val active: Boolean get() = status !is DownloadStatus.Done && status !is DownloadStatus.Failed
}

@Immutable
data class DownloadGroup(val id: Long, val name: String)

enum class SearchSource(val prefix: String) { YouTube("ytsearch"), SoundCloud("scsearch") }

/** One search result: enough to recognise the track, and the link to download it. */
@Immutable
data class SearchHit(val url: String, val title: String, val artist: String, val durationSec: Int?, val thumbnail: String?)

sealed interface EngineUpdate {
    data object Idle : EngineUpdate
    data object Running : EngineUpdate
    data object UpToDate : EngineUpdate
    data object Updated : EngineUpdate
    data object Failed : EngineUpdate
}
