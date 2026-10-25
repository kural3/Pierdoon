package com.ku.pierdoon.remote

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/** A track in the computer's library. [art] changes whenever its cover does. */
@Immutable
data class PcSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val art: String,
)

enum class PcRepeat { Off, One, All }

@Immutable
data class PcState(
    val song: PcSong? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: PcRepeat = PcRepeat.Off,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val favorite: Boolean = false,
    /** elapsedRealtime when this arrived: the position is extrapolated from here while playing. */
    val receivedAt: Long = 0,
)

@Immutable
data class PcQueueEntry(val key: String, val song: PcSong)

@Immutable
data class PcQueue(
    val current: PcQueueEntry? = null,
    val upcoming: List<PcQueueEntry> = emptyList(),
    val canReorder: Boolean = true,
)

@Immutable
data class PcAlbum(val id: Long, val title: String, val artist: String, val songIds: List<Long>)

@Immutable
data class PcArtist(val name: String, val albumCount: Int, val songIds: List<Long>)

@Immutable
data class PcPlaylist(val id: Long, val name: String, val songIds: List<Long>)

@Immutable
data class PcLibrary(
    val version: Long = -1,
    val songs: List<PcSong> = emptyList(),
    val albums: List<PcAlbum> = emptyList(),
    val artists: List<PcArtist> = emptyList(),
    val playlists: List<PcPlaylist> = emptyList(),
    val favorites: List<Long> = emptyList(),
) {
    val byId: Map<Long, PcSong> by lazy { songs.associateBy { it.id } }

    fun resolve(ids: List<Long>): List<PcSong> = ids.mapNotNull(byId::get)
}

sealed interface PcLyrics {
    data class Synced(val lines: List<Line>, val offsetMs: Long) : PcLyrics
    data class Plain(val lines: List<String>) : PcLyrics
    data object Instrumental : PcLyrics
    data object None : PcLyrics
    data object Error : PcLyrics

    @Immutable
    data class Line(val timeMs: Long, val text: String)
}

/** A download on the computer, as it last told. */
@Immutable
data class PcDownload(
    val id: Long,
    val url: String,
    val title: String?,
    val artist: String?,
    val status: Status,
    /** 0..1 while [Status.Downloading], when known. */
    val progress: Float?,
) {
    enum class Status { Queued, Preparing, Downloading, Saving, Done, Failed }

    val active: Boolean get() = status != Status.Done && status != Status.Failed
}

/** A computer that answered the discovery broadcast, or one typed in by hand. */
@Immutable
data class FoundPc(val name: String, val host: String, val port: Int, val version: String)

// JSON -------------------------------------------------------------------------------------------

internal fun JSONObject.toSong() = PcSong(
    id = getLong("id"),
    title = optString("title"),
    artist = optString("artist"),
    album = optString("album"),
    albumId = optLong("albumId"),
    durationMs = optLong("durationMs"),
    art = optString("art"),
)

internal fun JSONObject.toState(receivedAt: Long) = PcState(
    song = optJSONObject("song")?.toSong(),
    playing = optBoolean("playing"),
    positionMs = optLong("positionMs"),
    durationMs = optLong("durationMs"),
    shuffle = optBoolean("shuffle"),
    repeat = when (optString("repeat")) {
        "one" -> PcRepeat.One
        "all" -> PcRepeat.All
        else -> PcRepeat.Off
    },
    volume = optDouble("volume", 1.0).toFloat(),
    muted = optBoolean("muted"),
    favorite = optBoolean("favorite"),
    receivedAt = receivedAt,
)

internal fun JSONObject.toQueue(): PcQueue {
    fun entry(o: JSONObject) = PcQueueEntry(o.getString("key"), o.getJSONObject("song").toSong())
    return PcQueue(
        current = optJSONObject("current")?.let(::entry),
        upcoming = optJSONArray("upcoming").objects().map(::entry),
        canReorder = optBoolean("canReorder", true),
    )
}

internal fun JSONObject.toLibrary() = PcLibrary(
    version = optLong("version"),
    songs = optJSONArray("songs").objects().map { it.toSong() },
    albums = optJSONArray("albums").objects().map {
        PcAlbum(it.optLong("id"), it.optString("title"), it.optString("artist"), it.optJSONArray("songIds").longs())
    },
    artists = optJSONArray("artists").objects().map {
        PcArtist(it.optString("name"), it.optInt("albumCount"), it.optJSONArray("songIds").longs())
    },
    playlists = optJSONArray("playlists").objects().map {
        PcPlaylist(it.optLong("id"), it.optString("name"), it.optJSONArray("songIds").longs())
    },
    favorites = optJSONArray("favorites").longs(),
)

internal fun JSONObject.toLyrics(): PcLyrics = when (optString("type")) {
    "synced" -> PcLyrics.Synced(
        lines = optJSONArray("lines").objects().map { PcLyrics.Line(it.optLong("timeMs"), it.optString("text")) },
        offsetMs = optLong("offsetMs"),
    )
    "plain" -> PcLyrics.Plain(optJSONArray("lines").let { a -> List(a?.length() ?: 0) { a!!.optString(it) } })
    "instrumental" -> PcLyrics.Instrumental
    "none" -> PcLyrics.None
    else -> PcLyrics.Error
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { getJSONObject(it) }

private fun JSONArray?.longs(): List<Long> =
    if (this == null) emptyList() else List(length()) { getLong(it) }

internal fun JSONObject.toDownloads(): List<PcDownload> = optJSONArray("tasks").objects().map {
    PcDownload(
        id = it.optLong("id"),
        url = it.optString("url"),
        title = it.optString("title").takeIf { t -> !it.isNull("title") && t.isNotBlank() },
        artist = it.optString("artist").takeIf { a -> !it.isNull("artist") && a.isNotBlank() },
        status = when (it.optString("status")) {
            "preparing" -> PcDownload.Status.Preparing
            "downloading" -> PcDownload.Status.Downloading
            "saving" -> PcDownload.Status.Saving
            "done" -> PcDownload.Status.Done
            "failed" -> PcDownload.Status.Failed
            else -> PcDownload.Status.Queued
        },
        progress = if (it.isNull("progress")) null else it.optDouble("progress").toFloat().takeIf { p -> !p.isNaN() },
    )
}
