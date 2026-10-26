package com.ku.pierdoon.remote

import com.ku.pierdoon.BuildConfig
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * Answers the phone's "is there a Pier.doon here?" broadcast, so it finds the computer without
 * anyone typing an address. The phone sends [QUESTION] to UDP [PORT] on the broadcast address;
 * every running Pier.doon answers with its name and HTTP port.
 */
object RemoteDiscovery {
    const val PORT = 47232
    const val QUESTION = "PIERDOON_DISCOVER"

    @Volatile private var socket: DatagramSocket? = null

    fun start(httpPort: Int) {
        if (socket != null) return
        val s = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
            }
        }.getOrNull() ?: return
        socket = s
        Thread({
            val buf = ByteArray(512)
            while (!s.isClosed) {
                val packet = DatagramPacket(buf, buf.size)
                runCatching { s.receive(packet) }.onFailure { return@Thread }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim()
                if (text != QUESTION) continue
                val answer = JSONObject()
                    .put("app", "Pier.doon")
                    .put("name", RemoteServer.computerName())
                    .put("port", httpPort)
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("api", RemoteServer.API_VERSION)
                    .toString().toByteArray(Charsets.UTF_8)
                runCatching { s.send(DatagramPacket(answer, answer.size, packet.socketAddress)) }
            }
        }, "remote-discovery").apply { isDaemon = true; start() }
    }

    fun stop() {
        socket?.close()
        socket = null
    }
}
