package com.ku.pierdoon.ui.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.ku.pierdoon.LyricsState
import com.ku.pierdoon.PositionSnapshot
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.lyrics.LyricLine
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.playback.NowPlaying
import com.ku.pierdoon.playback.PlayerState
import com.ku.pierdoon.playback.QueueEntry
import com.ku.pierdoon.playback.QueueState
import com.ku.pierdoon.remote.PcLyrics
import com.ku.pierdoon.remote.PcRemote
import com.ku.pierdoon.remote.PcRepeat
import com.ku.pierdoon.remote.PcSong

/** The computer's playback in the shapes the phone's player screens take. */
@Immutable
class PcPlayback(
    val name: String,
    val nowPlaying: NowPlaying,
    val state: PlayerState,
    val position: PositionSnapshot,
    val queue: QueueState,
    val lyrics: LyricsState,
    val lyricsOffsetMs: Long,
    val favorite: Boolean,
)

/**
 * What the player should show of the computer, or null when it shows the phone: the computer
 * isn't [PcRemote.active], isn't paired, or plays nothing. Keeps the connection open while it is
 * on screen, or while [peek]ing: the open player tells what plays there before switching to it.
 * Lyrics are fetched only while [lyricsVisible].
 */
@Composable
fun rememberPcPlayback(lyricsVisible: Boolean, peek: Boolean = false): PcPlayback? {
    val active by PcRemote.active.collectAsStateWithLifecycle()
    val paired by PcRemote.paired.collectAsStateWithLifecycle()
    val connection by PcRemote.connection.collectAsStateWithLifecycle()
    val state by PcRemote.state.collectAsStateWithLifecycle()
    val queue by PcRemote.queue.collectAsStateWithLifecycle()
    val offsets by PcRemote.lyricsOffsets.collectAsStateWithLifecycle()

    val wanted = (active || peek) && paired != null
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, wanted) {
        if (!wanted) return@DisposableEffect onDispose { }
        var held = false
        fun hold(on: Boolean) {
            if (on == held) return
            held = on
            if (on) PcRemote.acquire() else PcRemote.release()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> hold(true)
                Lifecycle.Event.ON_STOP -> hold(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            hold(false)
        }
    }

    val song = state.song
    val lyrics by produceState<Pair<LyricsState, Long>>(LyricsState.Idle to 0L, song?.id, lyricsVisible) {
        val id = song?.id
        if (id == null || !lyricsVisible) {
            value = LyricsState.Idle to 0L
            return@produceState
        }
        value = LyricsState.Loading to 0L
        value = when (val l = PcRemote.lyrics(id)) {
            is PcLyrics.Synced -> LyricsState.Ready(Lyrics.Synced(l.lines.map { LyricLine(it.timeMs, it.text) })) to l.offsetMs
            is PcLyrics.Plain -> LyricsState.Ready(Lyrics.Plain(l.lines)) to 0L
            PcLyrics.Instrumental -> LyricsState.Ready(Lyrics.Instrumental) to 0L
            PcLyrics.None -> LyricsState.Ready(Lyrics.NotFound) to 0L
            PcLyrics.Error -> LyricsState.Error to 0L
        }
    }

    val p = paired
    if (!active || p == null || song == null) return null
    if (connection == PcRemote.Connection.NotPaired || connection == PcRemote.Connection.Revoked) return null

    val nowPlaying = NowPlaying(song.id, song.albumId, song.title, song.artist)
    return PcPlayback(
        name = p.name,
        nowPlaying = nowPlaying,
        state = PlayerState(
            connected = true,
            nowPlaying = nowPlaying,
            isPlaying = state.playing,
            durationMs = state.durationMs.takeIf { it > 0 } ?: song.durationMs,
            shuffle = state.shuffle,
            repeatMode = when (state.repeat) {
                PcRepeat.Off -> Player.REPEAT_MODE_OFF
                PcRepeat.One -> Player.REPEAT_MODE_ONE
                PcRepeat.All -> Player.REPEAT_MODE_ALL
            },
        ),
        position = PositionSnapshot(state.positionMs, state.receivedAt, state.playing),
        queue = QueueState(
            current = queue.current?.let { QueueEntry(it.key, it.song.toSong()) },
            upcoming = queue.upcoming.map { QueueEntry(it.key, it.song.toSong()) },
            canReorder = queue.canReorder,
        ),
        lyrics = lyrics.first,
        lyricsOffsetMs = offsets[song.id] ?: lyrics.second,
        favorite = state.favorite,
    )
}

private fun PcSong.toSong() = Song(id, title, artist, album, albumId, durationMs)
