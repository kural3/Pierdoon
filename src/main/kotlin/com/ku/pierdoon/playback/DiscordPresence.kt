package com.ku.pierdoon.playback

import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.metadata.MetadataSearch
import com.ku.pierdoon.platform.ErrorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Executors

/**
 * "Listening to Pier.doon" on the user's Discord profile: title, artist, a cover and the time
 * bar, cleared while paused. Off unless switched on in the settings.
 *
 * Talks to the Discord app on this computer through its local IPC pipe (`discord-ipc-N`), the
 * same way Discord's own SDK does, so nothing goes through the internet but the cover lookup.
 * Discord can only show pictures from the web; covers come from Deezer when it knows the track,
 * otherwise the app icon stands in.
 */
object DiscordPresence {
    /**
     * Application ID of the "Pier.doon" application in Discord's developer portal; its name is
     * what Discord shows. Empty: the status stays off.
     */
    private const val CLIENT_ID = ""
    private const val APP_ICON = "https://raw.githubusercontent.com/kural3/Pierdoon4Desktop/main/icons/pierdoon.png"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** The pipe is used from one thread only: Windows serialises I/O on it anyway. */
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "discord").apply { isDaemon = true } }
    private val pipeDispatcher = io.asCoroutineDispatcher()

    private var pipe: RandomAccessFile? = null
    private val coverUrls = HashMap<Long, String?>()

    private data class Shown(val song: Song?, val playing: Boolean, val startMs: Long)

    private val state = MutableStateFlow(Shown(null, false, 0L))

    /** False until the app has an ID in Discord's portal; the settings hide the switch till then. */
    val available: Boolean get() = CLIENT_ID.isNotEmpty()

    fun start() {
        if (!available) return
        val player = PlaybackController.player
        player.addListener { p, _ ->
            val song = p.currentMediaItem?.song
            // Where the track began, by the wall clock: Discord draws the time bar from it.
            state.value = Shown(song, p.isPlaying, System.currentTimeMillis() - p.currentPosition)
        }
        scope.launch {
            combine(SettingsStore.settings.map { it.discordPresence }.distinctUntilChanged(), state) { on, s -> on to s }
                // A seek moves the start a little; only a real jump is worth telling Discord about.
                .distinctUntilChanged { (onA, a), (onB, b) ->
                    onA == onB && a.song?.id == b.song?.id && a.playing == b.playing && kotlin.math.abs(a.startMs - b.startMs) < 2_000
                }
                .collectLatest { (on, s) ->
                    // Discord allows a handful of updates a minute: skipping through tracks sends the last.
                    delay(UPDATE_DELAY_MS)
                    withContext(pipeDispatcher) {
                        runCatching { if (on) show(s) else clear(disconnect = true) }
                            .onFailure { closePipe() }
                    }
                }
        }
    }

    fun shutdown() {
        runCatching { io.submit { runCatching { clear(disconnect = true) } }.get(1, java.util.concurrent.TimeUnit.SECONDS) }
    }

    private fun show(s: Shown) {
        val song = s.song
        if (song == null || !s.playing) return clear(disconnect = false)
        val cover = coverUrls.getOrPut(song.id) { MetadataSearch.coverUrl(song.title, song.artist) } ?: APP_ICON
        val activity = JSONObject()
            .put("type", ACTIVITY_LISTENING)
            // The member list shows the track's name instead of "Pier.doon".
            .put("status_display_type", STATUS_DISPLAY_DETAILS)
            .put("details", fit(song.title))
            .put("state", fit(song.artist.ifBlank { song.album }))
            .put("assets", JSONObject().put("large_image", cover).apply {
                if (song.album.isNotBlank()) put("large_text", fit(song.album))
            })
        if (song.durationMs > 0) {
            activity.put("timestamps", JSONObject().put("start", s.startMs).put("end", s.startMs + song.durationMs))
        }
        command(activity)
    }

    private fun clear(disconnect: Boolean) {
        if (pipe == null) return
        command(null)
        if (disconnect) closePipe()
    }

    private fun command(activity: JSONObject?) {
        val p = pipe ?: connect() ?: return
        val args = JSONObject().put("pid", ProcessHandle.current().pid()).put("activity", activity ?: JSONObject.NULL)
        write(p, OP_FRAME, JSONObject().put("cmd", "SET_ACTIVITY").put("args", args).put("nonce", UUID.randomUUID().toString()))
        val reply = read(p)
        if (reply.optString("evt") == "ERROR") {
            ErrorLog.record("discord", IOException(reply.optJSONObject("data")?.optString("message") ?: reply.toString()))
        }
    }

    /** The first pipe Discord answers on; null when it isn't running. */
    private fun connect(): RandomAccessFile? {
        for (i in 0..9) {
            val p = runCatching { RandomAccessFile("\\\\.\\pipe\\discord-ipc-$i", "rw") }.getOrNull() ?: continue
            val ok = runCatching {
                write(p, OP_HANDSHAKE, JSONObject().put("v", 1).put("client_id", CLIENT_ID))
                read(p).optString("evt") == "READY"
            }.getOrDefault(false)
            if (ok) {
                pipe = p
                return p
            }
            runCatching { p.close() }
        }
        return null
    }

    private fun closePipe() {
        runCatching { pipe?.close() }
        pipe = null
    }

    /** A frame: opcode and length, little-endian, then the JSON. */
    private fun write(p: RandomAccessFile, op: Int, json: JSONObject) {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.size).array()
        p.write(header + body)
    }

    private fun read(p: RandomAccessFile): JSONObject {
        val header = ByteArray(8).also(p::readFully)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val op = b.int
        val length = b.int
        if (length !in 0..MAX_FRAME) throw IOException("Bad frame")
        val body = ByteArray(length).also(p::readFully)
        if (op == OP_CLOSE) throw IOException("Discord closed: " + String(body, Charsets.UTF_8))
        return JSONObject(String(body, Charsets.UTF_8))
    }

    /** Discord wants 2–128 characters. */
    private fun fit(s: String): String = s.take(128).let { if (it.length < 2) it.padEnd(2, ' ') else it }

    private const val OP_HANDSHAKE = 0
    private const val OP_FRAME = 1
    private const val OP_CLOSE = 2
    private const val MAX_FRAME = 1 shl 20
    private const val ACTIVITY_LISTENING = 2
    private const val STATUS_DISPLAY_DETAILS = 2
    private const val UPDATE_DELAY_MS = 1_500L
}
