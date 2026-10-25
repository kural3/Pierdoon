package com.ku.pierdoon.remote

import com.ku.pierdoon.data.download.SearchHit
import com.ku.pierdoon.data.download.SearchSource
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.LruCache
import androidx.core.content.edit
import com.ku.pierdoon.data.USER_AGENT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

/**
 * The phone as a remote for Pier.doon on a computer in the same network: finds it, pairs with
 * the code it shows, then mirrors its player, queue and library and sends it commands. The
 * protocol is docs/remote-api.md in the Pierdoon4Desktop repository.
 *
 * While [active], the app's own player shows and drives the computer's playback instead of the
 * phone's. The event stream is open only while something on screen needs it ([acquire] and
 * [release]), so the phone isn't kept awake by it.
 */
object PcRemote {

    sealed interface Connection {
        /** No computer paired yet. */
        data object NotPaired : Connection
        data object Connecting : Connection
        data object Connected : Connection
        /** Paired, but the computer doesn't answer: off, asleep, another network. */
        data object Offline : Connection
        /** The computer forgot this phone (unpaired there): pair again. */
        data object Revoked : Connection
    }

    data class Paired(val name: String, val host: String, val port: Int, val token: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: SharedPreferences
    private lateinit var deviceName: String

    private val _paired = MutableStateFlow<Paired?>(null)
    val paired: StateFlow<Paired?> = _paired.asStateFlow()

    private val _connection = MutableStateFlow<Connection>(Connection.NotPaired)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private val _state = MutableStateFlow(PcState())
    val state: StateFlow<PcState> = _state.asStateFlow()

    private val _queue = MutableStateFlow(PcQueue())
    val queue: StateFlow<PcQueue> = _queue.asStateFlow()

    private val _library = MutableStateFlow(PcLibrary())
    val library: StateFlow<PcLibrary> = _library.asStateFlow()

    /** Sync offsets changed on either side since the lyrics were fetched, by song. */
    private val _lyricsOffsets = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val lyricsOffsets: StateFlow<Map<Long, Long>> = _lyricsOffsets.asStateFlow()

    private val lyricsCache = LruCache<Long, PcLyrics>(24)

    private val _downloads = MutableStateFlow<List<PcDownload>>(emptyList())
    /** The computer's latest downloads, newest first. Empty from a Pier.doon that doesn't download for the phone. */
    val downloads: StateFlow<List<PcDownload>> = _downloads.asStateFlow()

    /** A link shared into the app while a computer is paired: the UI asks where to download it. */
    val shareRequest = MutableStateFlow<String?>(null)

    /** A link on its way to the computer's downloads: the computer screen fills it in. */
    val pendingDownload = MutableStateFlow<String?>(null)

    private val _active = MutableStateFlow(false)
    /** The phone's player shows the computer: set by playing something there, cleared by playing here. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** The player on screen is the computer's: hardware volume keys go to it. Set by the UI. */
    @Volatile var inPlayer = false

    private var holders = 0

    private var stream: Job? = null
    @Volatile private var openConnection: HttpURLConnection? = null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("pc_remote", Context.MODE_PRIVATE)
        deviceName = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: Build.MODEL
        val token = prefs.getString(K_TOKEN, null)
        val host = prefs.getString(K_HOST, null)
        if (token != null && host != null) {
            _paired.value = Paired(prefs.getString(K_NAME, null).orEmpty(), host, prefs.getInt(K_PORT, DEFAULT_PORT), token)
            _connection.value = Connection.Offline
            _active.value = prefs.getBoolean(K_ACTIVE, false)
        }
    }

    fun setActive(on: Boolean) {
        if (_active.value == on) return
        _active.value = on
        prefs.edit { putBoolean(K_ACTIVE, on) }
    }

    /** Something on screen wants the live connection; pair with [release]. */
    @Synchronized
    fun acquire() {
        holders++
        start()
    }

    @Synchronized
    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders == 0) stop()
    }

    // Finding and pairing ---------------------------------------------------------------------

    /** Asks the network who runs Pier.doon; whoever answers within [DISCOVERY_MS]. */
    suspend fun discover(): List<FoundPc> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, FoundPc>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 300
            val message = DISCOVER.toByteArray()
            val targets = (broadcastAddresses() + InetAddress.getByName("255.255.255.255")).distinct()
            val deadline = SystemClock.elapsedRealtime() + DISCOVERY_MS
            var nextSend = 0L
            val buffer = ByteArray(2048)
            while (SystemClock.elapsedRealtime() < deadline) {
                // A datagram can get lost; ask a few times while listening.
                if (SystemClock.elapsedRealtime() >= nextSend) {
                    targets.forEach { runCatching { socket.send(DatagramPacket(message, message.size, it, DISCOVERY_PORT)) } }
                    nextSend = SystemClock.elapsedRealtime() + 700
                }
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val reply = runCatching { JSONObject(String(packet.data, 0, packet.length, Charsets.UTF_8)) }.getOrNull() ?: continue
                if (reply.optString("app") != "Pier.doon") continue
                val host = packet.address.hostAddress ?: continue
                found[host] = FoundPc(reply.optString("name", host), host, reply.optInt("port", DEFAULT_PORT), reply.optString("version"))
            }
        }
        found.values.toList()
    }

    /** A computer typed in by hand: "192.168.1.5" or "192.168.1.5:47231". Null if nothing answers there. */
    suspend fun probe(address: String): FoundPc? = withContext(Dispatchers.IO) {
        val (host, port) = address.trim().let { a ->
            val i = a.lastIndexOf(':')
            if (i > 0) a.substring(0, i) to (a.substring(i + 1).toIntOrNull() ?: DEFAULT_PORT) else a to DEFAULT_PORT
        }
        if (host.isBlank()) return@withContext null
        runCatching {
            val info = request("GET", host, port, "/info", token = null, timeoutMs = 4_000).json()
            FoundPc(info.optString("name", host), host, port, info.optString("version"))
        }.getOrNull()
    }

    sealed interface PairResult {
        data object Ok : PairResult
        data object WrongCode : PairResult
        data object Unreachable : PairResult
    }

    suspend fun pair(pc: FoundPc, code: String): PairResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("code", code).put("name", deviceName)
        val reply = try {
            request("POST", pc.host, pc.port, "/pair", token = null, body = body)
        } catch (_: IOException) {
            return@withContext PairResult.Unreachable
        }
        if (reply.code == 403) return@withContext PairResult.WrongCode
        if (reply.code != 200) return@withContext PairResult.Unreachable
        val json = reply.json()
        val paired = Paired(json.optString("name", pc.name), pc.host, pc.port, json.getString("token"))
        save(paired)
        _paired.value = paired
        _connection.value = Connection.Connecting
        setActive(true)
        if (holders > 0) start()
        PairResult.Ok
    }

    /** Forgets the computer here and asks it to forget this phone too. */
    fun forget() {
        val p = _paired.value
        stop()
        prefs.edit { clear() }
        _active.value = false
        _paired.value = null
        _connection.value = Connection.NotPaired
        _state.value = PcState()
        _queue.value = PcQueue()
        _library.value = PcLibrary()
        _downloads.value = emptyList()
        lyricsCache.evictAll()
        if (p != null) scope.launch { runCatching { request("POST", p.host, p.port, "/unpair", p.token, timeoutMs = 3_000) } }
    }

    // The live connection -----------------------------------------------------------------------

    /** Opens the event stream (and keeps reopening it) until [stop]. */
    private fun start() {
        if (_paired.value == null || stream?.isActive == true) return
        stream = scope.launch {
            var failures = 0
            while (isActive) {
                val p = _paired.value ?: break
                if (_connection.value != Connection.Connected) _connection.value = Connection.Connecting
                val result = runCatching { listen(p) }
                if (!isActive) break
                if (result.exceptionOrNull() is RevokedException) {
                    _connection.value = Connection.Revoked
                    break
                }
                failures++
                _connection.value = Connection.Offline
                // The router may have handed the computer a new address: look for it by name.
                if (failures % 2 == 0) relocate(p)
                delay((failures * 2_000L).coerceAtMost(10_000L))
            }
        }
    }

    private fun stop() {
        stream?.cancel()
        stream = null
        runCatching { openConnection?.disconnect() }
        openConnection = null
        if (_connection.value == Connection.Connected || _connection.value == Connection.Connecting) {
            _connection.value = if (_paired.value != null) Connection.Offline else Connection.NotPaired
        }
    }

    private class RevokedException : IOException("revoked")

    private fun listen(p: Paired) {
        val conn = open("GET", p.host, p.port, "/events", p.token, timeoutMs = 5_000)
        conn.readTimeout = STREAM_TIMEOUT_MS
        openConnection = conn
        try {
            if (conn.responseCode == 401) throw RevokedException()
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            _connection.value = Connection.Connected
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var event = "message"
                val data = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: throw IOException("closed")
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) dispatch(p, event, data.toString())
                            event = "message"
                            data.clear()
                        }
                        line.startsWith(":") -> Unit
                        line.startsWith("event:") -> event = line.substring(6).trim()
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.substring(5).trimStart())
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
            if (openConnection === conn) openConnection = null
        }
    }

    private fun dispatch(p: Paired, event: String, data: String) {
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return
        when (event) {
            "state" -> _state.value = json.toState(SystemClock.elapsedRealtime())
            "queue" -> _queue.value = json.toQueue()
            "library" -> {
                val version = json.optLong("version", -2)
                if (version != _library.value.version) scope.launch { runCatching { fetchLibrary(p) } }
            }
            "lyricsOffset" -> {
                val songId = json.optLong("songId")
                _lyricsOffsets.update { it + (songId to json.optLong("offsetMs")) }
            }
            "downloads" -> _downloads.value = json.toDownloads()
        }
    }

    private fun fetchLibrary(p: Paired) {
        val reply = request("GET", p.host, p.port, "/library", p.token, timeoutMs = 20_000)
        if (reply.code == 200) _library.value = reply.json().toLibrary()
    }

    private suspend fun relocate(p: Paired) {
        val found = runCatching { discover() }.getOrNull()?.firstOrNull { it.name == p.name && it.host != p.host } ?: return
        val moved = p.copy(host = found.host, port = found.port)
        save(moved)
        _paired.value = moved
    }

    // Commands ----------------------------------------------------------------------------------

    fun command(action: String, args: JSONObject.() -> Unit = {}) {
        val p = _paired.value ?: return
        val body = JSONObject().put("action", action).apply(args)
        scope.launch { runCatching { request("POST", p.host, p.port, "/command", p.token, body) } }
    }

    fun toggle() {
        setActive(true)
        command("toggle")
    }
    fun next() = command("next")
    fun previous() = command("previous")
    fun seek(positionMs: Long) {
        // Shown at once; the computer's state event confirms it.
        _state.update { it.copy(positionMs = positionMs, receivedAt = SystemClock.elapsedRealtime()) }
        command("seek") { put("positionMs", positionMs) }
    }
    /** A slider moves every frame: only the latest value goes out, one request at a time. */
    fun setVolume(value: Float) {
        _state.update { it.copy(volume = value, muted = false) }
        pendingVolume = value
        if (volumeSender?.isActive == true) return
        volumeSender = scope.launch {
            var sent: Float? = null
            while (true) {
                val v = pendingVolume
                if (v == sent) break
                val p = _paired.value ?: break
                runCatching { request("POST", p.host, p.port, "/command", p.token, JSONObject().put("action", "volume").put("value", v.toDouble())) }
                sent = v
            }
        }
    }

    @Volatile private var pendingVolume = 1f
    private var volumeSender: Job? = null
    fun toggleShuffle() = command("shuffle")
    fun cycleRepeat() = command("repeat")
    fun toggleFavorite(songId: Long) = command("favorite") { put("songId", songId) }
    fun shuffleAll() {
        setActive(true)
        command("shuffleAll")
    }
    fun playSongs(ids: List<Long>, index: Int) {
        setActive(true)
        command("playSongs") {
            put("ids", JSONArray(ids))
            put("index", index)
        }
    }
    fun playNext(ids: List<Long>) = command("playNext") { put("ids", JSONArray(ids)) }
    fun addToQueue(ids: List<Long>) = command("addToQueue") { put("ids", JSONArray(ids)) }
    fun playQueueEntry(key: String) {
        setActive(true)
        command("playQueueEntry") { put("key", key) }
    }
    /** [position] 0 = right after the current track, as in the phone's own queue. */
    fun moveQueueEntry(key: String, position: Int) {
        _queue.update { q ->
            val entry = q.upcoming.firstOrNull { it.key == key } ?: return@update q
            val rest = q.upcoming - entry
            q.copy(upcoming = rest.toMutableList().apply { add(position.coerceIn(0, rest.size), entry) })
        }
        command("moveQueueEntry") {
            put("key", key)
            put("position", position)
        }
    }
    fun removeQueueEntry(key: String) {
        _queue.update { q -> q.copy(upcoming = q.upcoming.filterNot { it.key == key }) }
        command("removeQueueEntry") { put("key", key) }
    }
    fun shiftLyrics(deltaMs: Long) = command("lyricsOffset") { put("deltaMs", deltaMs) }
    fun resetLyricsOffset() = command("lyricsOffset") { put("reset", true) }

    /** A press of a hardware volume key, while the computer is in the player. */
    fun stepVolume(up: Boolean) {
        val s = _state.value
        val current = if (s.muted) 0f else s.volume
        setVolume((current + if (up) VOLUME_STEP else -VOLUME_STEP).coerceIn(0f, 1f))
    }

    // Downloads on the computer -----------------------------------------------------------------

    enum class SendResult { Ok, Outdated, Unreachable }

    /** Has the computer download [url] into its own music folder, in its own format. */
    suspend fun download(url: String, title: String? = null, artist: String? = null): SendResult = withContext(Dispatchers.IO) {
        val p = _paired.value ?: return@withContext SendResult.Unreachable
        val body = JSONObject().put("action", "download").put("url", url)
        title?.let { body.put("title", it) }
        artist?.let { body.put("artist", it) }
        val code = runCatching { request("POST", p.host, p.port, "/command", p.token, body).code }.getOrNull()
        when (code) {
            200 -> SendResult.Ok
            // Pier.doon before 1.0.5 has no such command.
            400 -> SendResult.Outdated
            else -> SendResult.Unreachable
        }
    }

    sealed interface Search {
        data class Found(val hits: List<SearchHit>) : Search
        data object Outdated : Search
        data object Failed : Search
    }

    /** Looks tracks up by name on the computer (it has yt-dlp at hand); takes a few seconds. */
    suspend fun searchDownloads(query: String, source: SearchSource): Search = withContext(Dispatchers.IO) {
        val p = _paired.value ?: return@withContext Search.Failed
        val src = if (source == SearchSource.SoundCloud) "soundcloud" else "youtube"
        val reply = runCatching {
            request("GET", p.host, p.port, "/downloads/search?q=${enc(query)}&source=$src", p.token, timeoutMs = 40_000)
        }.getOrNull() ?: return@withContext Search.Failed
        when (reply.code) {
            200 -> runCatching {
                Search.Found(JSONObject(reply.body).optJSONArray("hits").let { a ->
                    List(a?.length() ?: 0) { i ->
                        val h = a!!.getJSONObject(i)
                        SearchHit(
                            url = h.optString("url"),
                            title = h.optString("title"),
                            artist = h.optString("artist"),
                            durationSec = if (h.isNull("durationSec")) null else h.optInt("durationSec"),
                            thumbnail = if (h.isNull("thumbnail")) null else h.optString("thumbnail"),
                        )
                    }
                })
            }.getOrDefault(Search.Failed)
            404 -> Search.Outdated
            else -> Search.Failed
        }
    }

    // Lyrics and covers ------------------------------------------------------------------------

    /** The computer looks lyrics up on LRCLIB if it hasn't yet, so the first call can take a while. */
    suspend fun lyrics(songId: Long): PcLyrics = withContext(Dispatchers.IO) {
        lyricsCache.get(songId)?.let { return@withContext it }
        val p = _paired.value ?: return@withContext PcLyrics.Error
        val result = runCatching {
            request("GET", p.host, p.port, "/lyrics?songId=$songId", p.token, timeoutMs = 20_000).json().toLyrics()
        }.getOrDefault(PcLyrics.Error)
        if (result != PcLyrics.Error) lyricsCache.put(songId, result)
        result
    }

    /** The cover of a track the computer told about (playing, queued or in its library), by id. */
    fun coverUrl(songId: Long, size: Int): String? {
        val song = _state.value.song?.takeIf { it.id == songId }
            ?: _queue.value.let { q -> (listOfNotNull(q.current) + q.upcoming).firstOrNull { it.song.id == songId }?.song }
            ?: _library.value.byId[songId]
            ?: return null
        return coverUrl(song, size)
    }

    /** The cover's address; [art] in it makes a changed cover a new address for the image cache. */
    fun coverUrl(song: PcSong, size: Int): String? {
        val p = _paired.value ?: return null
        return "http://${hostPart(p.host)}:${p.port}/api/cover/${song.id}?size=$size&v=${enc(song.art)}&token=${p.token}"
    }

    // Plumbing --------------------------------------------------------------------------------

    private class Reply(val code: Int, val body: String) {
        fun json(): JSONObject {
            if (code != 200) throw IOException("HTTP $code")
            return JSONObject(body)
        }
    }

    private fun open(method: String, host: String, port: Int, path: String, token: String?, timeoutMs: Int): HttpURLConnection {
        val conn = URL("http://${hostPart(host)}:$port/api$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", USER_AGENT)
        if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
        return conn
    }

    private fun request(
        method: String,
        host: String,
        port: Int,
        path: String,
        token: String?,
        body: JSONObject? = null,
        timeoutMs: Int = 6_000,
    ): Reply {
        val conn = open(method, host, port, path, token, timeoutMs)
        try {
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code == 401 && token != null) {
                _connection.value = Connection.Revoked
            }
            return Reply(code, text)
        } finally {
            conn.disconnect()
        }
    }

    private fun save(p: Paired) = prefs.edit {
        putString(K_NAME, p.name)
        putString(K_HOST, p.host)
        putInt(K_PORT, p.port)
        putString(K_TOKEN, p.token)
    }

    private fun broadcastAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .filter { it.address is Inet4Address }
            .mapNotNull { it.broadcast }
    }.getOrDefault(emptyList())

    /** IPv6 literals go in brackets in a URL. */
    private fun hostPart(host: String) = if (':' in host) "[$host]" else host

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    const val DEFAULT_PORT = 47231
    private const val DISCOVERY_PORT = 47232
    private const val DISCOVER = "PIERDOON_DISCOVER"
    private const val DISCOVERY_MS = 2_500L
    /** Three keepalives missed. */
    private const val STREAM_TIMEOUT_MS = 45_000

    private const val K_NAME = "name"
    private const val K_HOST = "host"
    private const val K_PORT = "port"
    private const val K_TOKEN = "token"
    private const val K_ACTIVE = "active"
    private const val VOLUME_STEP = 0.05f
}
