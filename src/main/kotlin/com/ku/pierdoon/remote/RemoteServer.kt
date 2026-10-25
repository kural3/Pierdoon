package com.ku.pierdoon.remote

import com.ku.pierdoon.BuildConfig
import com.ku.pierdoon.LibraryState
import com.ku.pierdoon.MainViewModel
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.platform.ErrorLog
import com.ku.pierdoon.platform.Images
import com.ku.pierdoon.platform.LruCache
import com.ku.pierdoon.platform.Prefs
import com.ku.pierdoon.playback.PlaybackController
import com.ku.pierdoon.playback.Player
import com.ku.pierdoon.ui.components.ArtworkCache
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import javax.swing.SwingUtilities

/** What the settings page shows about the phone connection. */
data class RemoteStatus(
    val running: Boolean = false,
    /** This computer's addresses on the local network. */
    val addresses: List<String> = emptyList(),
    val port: Int = RemoteServer.PORT,
    /** The code a phone types in to pair; changes after every use. */
    val pairingCode: String = "",
    val devices: List<RemoteDevice> = emptyList(),
    /** Why it couldn't start (the port is taken, say). */
    val error: String? = null,
)

data class RemoteDevice(val id: String, val name: String, val pairedAt: Long)

/**
 * Lets the Pier.doon phone app drive this player over the local network: what's playing, the
 * queue, lyrics, covers and the library, and every transport command. The protocol is in
 * docs/remote-api.md.
 *
 * Plain HTTP on the LAN; changes reach the phone as server-sent events on one long response,
 * so nothing polls. A phone pairs once with the code shown in the settings and then sends the
 * token it got with every request. Off unless switched on in the settings.
 */
object RemoteServer {
    const val PORT = 47231
    const val API_VERSION = 1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy { Prefs("remote") }
    private val random = SecureRandom()

    private val _status = MutableStateFlow(RemoteStatus())
    val status: StateFlow<RemoteStatus> = _status.asStateFlow()

    private var vm: MainViewModel? = null
    private var server: HttpServer? = null
    private var watchers: Job? = null
    private val clients = CopyOnWriteArrayList<LinkedBlockingQueue<String>>()
    private var failedPairings = 0
    private var libraryVersion = 0

    private val player: Player get() = PlaybackController.player

    /** Starts or stops the server as the setting says, for as long as the app runs. */
    fun bind(viewModel: MainViewModel) {
        vm = viewModel
        _status.update { it.copy(devices = loadDevices(), pairingCode = newCode()) }
        scope.launch {
            SettingsStore.settings.map { it.remoteControl }.distinctUntilChanged().collect { on ->
                if (on) start() else stop()
            }
        }
    }

    fun stop() {
        watchers?.cancel()
        watchers = null
        server?.stop(0)
        server = null
        RemoteDiscovery.stop()
        clients.forEach { it.offer(CLOSE) }
        clients.clear()
        _status.update { it.copy(running = false, error = null) }
    }

    fun forget(device: RemoteDevice) {
        prefs.edit { remove(DEVICE + device.id) }
        _status.update { it.copy(devices = loadDevices()) }
    }

    private fun start() {
        if (server != null) return
        val s = try {
            HttpServer.create(InetSocketAddress(PORT), 16)
        } catch (e: IOException) {
            _status.update { it.copy(running = false, error = e.message ?: e.javaClass.simpleName) }
            return
        }
        s.executor = Executors.newCachedThreadPool { r -> Thread(r, "remote-http").apply { isDaemon = true } }
        s.createContext("/api/") { ex ->
            try {
                route(ex)
            } catch (e: Exception) {
                ErrorLog.record("remote ${ex.requestURI.path}", e)
                runCatching { send(ex, 500, JSONObject().put("error", "internal")) }
            } finally {
                ex.close()
            }
        }
        s.start()
        server = s
        RemoteDiscovery.start(PORT)
        _status.update { it.copy(running = true, addresses = localAddresses(), error = null) }
        watch()
    }

    // Pushing changes -----------------------------------------------------------------------

    private val playerListener = Player.Listener { _, _ -> broadcast("state", stateJson()) }

    /** Sends the phone whatever changed: player state, the queue, the library. */
    private fun watch() {
        val model = vm ?: return
        player.addListener(playerListener)
        watchers = scope.launch {
            launch {
                SettingsStore.settings.map { it.volume to it.muted }.distinctUntilChanged().drop(1)
                    .collect { broadcast("state", stateJson()) }
            }
            launch { model.queue.drop(1).collect { broadcast("queue", queueJson()) } }
            launch {
                combine(model.library, model.userData) { _, _ -> Unit }.drop(1).collect {
                    libraryVersion++
                    broadcast("library", JSONObject().put("version", libraryVersion))
                }
            }
            launch {
                model.lyricsOffsetsVersion.drop(1).collect {
                    val id = player.currentMediaItem?.song?.id ?: return@collect
                    broadcast("lyricsOffset", JSONObject().put("songId", id).put("offsetMs", model.lyricsOffsetFor(id)))
                }
            }
        }.also { job -> job.invokeOnCompletion { player.removeListener(playerListener) } }
    }

    private fun broadcast(event: String, data: JSONObject) {
        if (clients.isEmpty()) return
        val message = "event: $event\ndata: $data\n\n"
        clients.forEach { it.offer(message) }
    }

    // Requests ------------------------------------------------------------------------------

    private fun route(ex: HttpExchange) {
        val path = ex.requestURI.path.removePrefix("/api")
        val method = ex.requestMethod
        when {
            path == "/info" && method == "GET" -> return send(ex, 200, infoJson())
            path == "/pair" && method == "POST" -> return pair(ex)
        }
        if (!authorized(ex)) return send(ex, 401, JSONObject().put("error", "unauthorized"))
        when {
            path == "/events" && method == "GET" -> events(ex)
            path == "/state" && method == "GET" -> send(ex, 200, onUi { stateJson() })
            path == "/queue" && method == "GET" -> send(ex, 200, onUi { queueJson() })
            path == "/library" && method == "GET" -> send(ex, 200, libraryJson())
            path == "/lyrics" && method == "GET" -> lyrics(ex)
            path.startsWith("/cover/") && method == "GET" -> cover(ex, path.removePrefix("/cover/"))
            path == "/command" && method == "POST" -> command(ex)
            path == "/unpair" && method == "POST" -> {
                tokenOf(ex)?.let { token -> prefs.edit { remove(DEVICE + hash(token)) } }
                _status.update { it.copy(devices = loadDevices()) }
                send(ex, 200, JSONObject().put("ok", true))
            }
            else -> send(ex, 404, JSONObject().put("error", "not found"))
        }
    }

    private fun infoJson() = JSONObject()
        .put("app", "Pier.doon")
        .put("version", BuildConfig.VERSION_NAME)
        .put("api", API_VERSION)
        .put("name", computerName())

    private fun pair(ex: HttpExchange) {
        val body = readJson(ex)
        val code = body.optString("code").filter(Char::isDigit)
        val ok = synchronized(this) {
            if (code.isNotEmpty() && code == _status.value.pairingCode) {
                failedPairings = 0
                true
            } else {
                // Guessing a six-digit code takes more than a handful of tries: after five, a new one.
                if (++failedPairings >= MAX_FAILED_PAIRINGS) {
                    failedPairings = 0
                    _status.update { it.copy(pairingCode = newCode()) }
                }
                false
            }
        }
        if (!ok) return send(ex, 403, JSONObject().put("error", "wrong code"))
        val token = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val name = body.optString("name").take(60).ifBlank { "Телефон" }
        prefs.edit { putString(DEVICE + hash(token), "${System.currentTimeMillis()}|$name") }
        _status.update { it.copy(devices = loadDevices(), pairingCode = newCode()) }
        send(ex, 200, JSONObject().put("token", token).put("name", computerName()))
    }

    private fun authorized(ex: HttpExchange): Boolean {
        val token = tokenOf(ex) ?: return false
        return prefs.contains(DEVICE + hash(token))
    }

    /** "Authorization: Bearer …", or ?token=… for image loaders that can't set headers. */
    private fun tokenOf(ex: HttpExchange): String? =
        ex.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() }
            ?: query(ex)["token"]

    /** One long response: the current state at once, then every change as it happens. */
    private fun events(ex: HttpExchange) {
        val queue = LinkedBlockingQueue<String>()
        ex.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
        ex.responseHeaders.add("Cache-Control", "no-cache")
        ex.sendResponseHeaders(200, 0)
        val (state, q) = onUi { stateJson() to queueJson() }
        queue.offer("event: state\ndata: $state\n\n")
        queue.offer("event: queue\ndata: $q\n\n")
        queue.offer("event: library\ndata: ${JSONObject().put("version", libraryVersion)}\n\n")
        clients += queue
        try {
            val out = ex.responseBody
            while (true) {
                // A comment now and then keeps NATs and the phone's own timeout from closing it.
                val message = queue.poll(KEEPALIVE_S, TimeUnit.SECONDS) ?: ": keepalive\n\n"
                if (message === CLOSE) break
                out.write(message.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        } catch (_: IOException) {
            // The phone went away.
        } finally {
            clients -= queue
        }
    }

    private fun lyrics(ex: HttpExchange) {
        val model = vm ?: return send(ex, 503, JSONObject())
        val songId = query(ex)["songId"]?.toLongOrNull()
            ?: return send(ex, 400, JSONObject().put("error", "songId"))
        val lyrics = runBlocking { model.lyricsFor(songId) }
        val json = JSONObject().put("songId", songId).put("offsetMs", model.lyricsOffsetFor(songId))
        when (lyrics) {
            is Lyrics.Synced -> json.put("type", "synced").put(
                "lines",
                JSONArray(lyrics.lines.map { JSONObject().put("timeMs", it.timeMs).put("text", it.text) }),
            )
            is Lyrics.Plain -> json.put("type", "plain").put("lines", JSONArray(lyrics.lines))
            Lyrics.Instrumental -> json.put("type", "instrumental")
            Lyrics.NotFound -> json.put("type", "none")
            null -> json.put("type", "error")
        }
        send(ex, 200, json)
    }

    private val covers = object : LruCache<String, ByteArray>(COVER_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    /** /cover/{songId}?size=… : the track's cover as JPEG, at most size px on a side. */
    private fun cover(ex: HttpExchange, id: String) {
        val model = vm ?: return send(ex, 503, JSONObject())
        val song = id.toLongOrNull()?.let(model::song) ?: return send(ex, 404, JSONObject())
        val size = query(ex)["size"]?.toIntOrNull()?.coerceIn(64, 1200) ?: 600
        val key = "${artKey(song)}@$size"
        val jpeg = covers.get(key) ?: ArtworkCache.coverBytes(song.id, song.albumId)
            ?.let(Images::decode)?.let { Images.jpeg(Images.scaled(it, size), 88) }
            ?.also { covers.put(key, it) }
            ?: return send(ex, 404, JSONObject().put("error", "no cover"))
        ex.responseHeaders.add("Content-Type", "image/jpeg")
        // The url carries the art's version (artKey), so it can be kept for long.
        ex.responseHeaders.add("Cache-Control", "max-age=31536000, immutable")
        ex.sendResponseHeaders(200, jpeg.size.toLong())
        ex.responseBody.use { it.write(jpeg) }
    }

    private fun command(ex: HttpExchange) {
        val model = vm ?: return send(ex, 503, JSONObject())
        val c = readJson(ex)
        fun songs(): List<Song> = c.optJSONArray("ids")?.let { a -> model.resolve(List(a.length()) { a.getLong(it) }) }.orEmpty()
        val ok = onUi {
            when (c.optString("action")) {
                "toggle" -> model.togglePlay()
                "play" -> if (!player.isPlaying) model.togglePlay()
                "pause" -> player.pause()
                "next" -> model.next()
                "previous" -> model.previous()
                "seek" -> model.seekTo(c.optLong("positionMs"))
                "seekBy" -> model.seekBy(c.optLong("deltaMs"))
                "volume" -> model.setVolume(c.optDouble("value", 1.0).toFloat())
                "mute" -> model.toggleMute()
                "shuffle" -> model.toggleShuffle()
                "repeat" -> model.cycleRepeat()
                "shuffleAll" -> model.shuffleAll()
                "playSongs" -> songs().takeIf { it.isNotEmpty() }?.let {
                    model.playSongs(it, c.optInt("index").coerceIn(0, it.lastIndex), c.optBoolean("shuffle"))
                } ?: return@onUi false
                "playNext" -> model.playNext(songs())
                "addToQueue" -> model.addToQueue(songs())
                "playQueueEntry" -> model.playQueueEntry(c.optString("key"))
                "removeQueueEntry" -> model.removeQueueEntry(c.optString("key"))
                "moveQueueEntry" -> model.moveQueueEntry(c.optString("key"), c.optInt("position"))
                "favorite" -> model.toggleFavorite(c.optLong("songId"))
                "lyricsOffset" -> if (c.optBoolean("reset")) model.resetLyricsOffset() else model.shiftLyrics(c.optLong("deltaMs"))
                else -> return@onUi false
            }
            true
        }
        send(ex, if (ok) 200 else 400, JSONObject().put("ok", ok))
    }

    // JSON ----------------------------------------------------------------------------------

    /** Called on the UI thread: the player belongs to it. */
    private fun stateJson(): JSONObject {
        val s = SettingsStore.settings.value
        val item = player.currentMediaItem
        val song = item?.song?.let { vm?.song(it.id) ?: it }
        return JSONObject()
            .put("song", song?.let(::songJson) ?: JSONObject.NULL)
            .put("playing", player.isPlaying)
            .put("positionMs", player.currentPosition)
            .put("durationMs", song?.durationMs ?: 0L)
            .put("shuffle", player.shuffleModeEnabled)
            .put("repeat", when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> "one"
                Player.REPEAT_MODE_ALL -> "all"
                else -> "off"
            })
            .put("volume", s.volume.toDouble())
            .put("muted", s.muted)
            .put("favorite", song != null && vm?.userData?.value?.favorites?.contains(song.id) == true)
            .put("serverTimeMs", System.currentTimeMillis())
    }

    private fun queueJson(): JSONObject {
        val q = vm?.queue?.value ?: return JSONObject()
        fun entry(e: com.ku.pierdoon.playback.QueueEntry) = JSONObject().put("key", e.key).put("song", songJson(e.song))
        return JSONObject()
            .put("current", q.current?.let(::entry) ?: JSONObject.NULL)
            .put("upcoming", JSONArray(q.upcoming.map(::entry)))
            .put("canReorder", q.canReorder)
    }

    /** The whole library in one go; gzip makes even a big one small. */
    private fun libraryJson(): JSONObject {
        val model = vm ?: return JSONObject()
        val songs = (model.library.value as? LibraryState.Ready)?.songs.orEmpty()
        val collections = model.collections.value
        val user = model.userData.value
        return JSONObject()
            .put("version", libraryVersion)
            .put("songs", JSONArray(songs.map(::songJson)))
            .put("albums", JSONArray(collections.albums.map { a ->
                JSONObject().put("id", a.id).put("title", a.title).put("artist", a.artist)
                    .put("songIds", JSONArray(a.songs.map { it.id }))
            }))
            .put("artists", JSONArray(collections.artists.map { a ->
                JSONObject().put("name", a.name).put("albumCount", a.albumCount)
                    .put("songIds", JSONArray(a.songs.map { it.id }))
            }))
            .put("playlists", JSONArray(user.playlists.map { p ->
                JSONObject().put("id", p.id).put("name", p.name).put("songIds", JSONArray(p.songIds))
            }))
            .put("favorites", JSONArray(user.favorites.toList()))
    }

    private fun songJson(s: Song) = JSONObject()
        .put("id", s.id)
        .put("title", s.title)
        .put("artist", s.artist)
        .put("album", s.album)
        .put("albumId", s.albumId)
        .put("durationMs", s.durationMs)
        .put("art", artKey(s))

    /** Changes when the cover does, so the phone can cache covers by it. */
    private fun artKey(s: Song): String =
        "${s.id}-${CoverStore.songVersion(s.id) ?: 0}-${ArtworkCache.albumRevision(s.albumId)}"

    // Plumbing ------------------------------------------------------------------------------

    private fun <T> onUi(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        val f = CompletableFuture<T>()
        SwingUtilities.invokeLater { runCatching(block).fold(f::complete, f::completeExceptionally) }
        return f.get(5, TimeUnit.SECONDS)
    }

    private fun send(ex: HttpExchange, code: Int, json: JSONObject) {
        val raw = json.toString().toByteArray(Charsets.UTF_8)
        val gzip = raw.size > GZIP_MIN_BYTES && ex.requestHeaders.getFirst("Accept-Encoding")?.contains("gzip") == true
        val body = if (gzip) {
            java.io.ByteArrayOutputStream().also { b -> GZIPOutputStream(b).use { it.write(raw) } }.toByteArray()
        } else {
            raw
        }
        ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        if (gzip) ex.responseHeaders.add("Content-Encoding", "gzip")
        ex.sendResponseHeaders(code, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    private fun readJson(ex: HttpExchange): JSONObject {
        val text = ex.requestBody.use { it.readNBytes(MAX_BODY_BYTES).toString(Charsets.UTF_8) }
        return runCatching { JSONObject(text) }.getOrDefault(JSONObject())
    }

    private fun query(ex: HttpExchange): Map<String, String> =
        ex.requestURI.rawQuery.orEmpty().split('&').filter { '=' in it }.associate {
            val (k, v) = it.split('=', limit = 2)
            java.net.URLDecoder.decode(k, Charsets.UTF_8) to java.net.URLDecoder.decode(v, Charsets.UTF_8)
        }

    private fun loadDevices(): List<RemoteDevice> = prefs.keys().filter { it.startsWith(DEVICE) }.mapNotNull { key ->
        val (time, name) = prefs.getString(key, null)?.split('|', limit = 2)?.takeIf { it.size == 2 } ?: return@mapNotNull null
        RemoteDevice(key.removePrefix(DEVICE), name, time.toLongOrNull() ?: 0L)
    }.sortedBy { it.pairedAt }

    private fun newCode(): String = (100_000 + random.nextInt(900_000)).toString()

    /** Only hashes are kept: the settings file alone isn't enough to pose as a paired phone. */
    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

    fun computerName(): String =
        System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
            ?: runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("PC")

    /** IPv4 addresses of the interfaces that are up, skipping loopback and virtual adapters. */
    private fun localAddresses(): List<String> = runCatching {
        NetworkInterface.networkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .filterNot { n -> VIRTUAL.any { n.displayName.contains(it, ignoreCase = true) } || TUNNEL.containsMatchIn(n.displayName) }
            .flatMap { n -> n.inetAddresses().toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress }
    }.getOrDefault(emptyList())

    private const val DEVICE = "device."
    private const val CLOSE = "\u0000close"
    private const val KEEPALIVE_S = 15L
    private const val MAX_FAILED_PAIRINGS = 5
    private const val GZIP_MIN_BYTES = 4096
    private const val MAX_BODY_BYTES = 256 * 1024
    private const val COVER_CACHE_BYTES = 16 * 1024 * 1024
    /** VPN and proxy adapters (Throne's "throne-tun", WireGuard, Wintun…): the phone can't reach them. */
    private val TUNNEL = Regex("""(?i)(^tun|-tun|wintun|wireguard|tunnel|vpn)""")
    private val VIRTUAL = listOf("VirtualBox", "VMware", "Hyper-V", "vEthernet", "WSL", "Npcap", "TAP", "Tailscale", "ZeroTier")
}
