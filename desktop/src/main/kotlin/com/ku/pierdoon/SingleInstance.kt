package com.ku.pierdoon

import com.ku.pierdoon.platform.AppDirs
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import javax.swing.SwingUtilities

/**
 * Keeps Pier.doon to one process. The first one holds a lock file and listens on a loopback
 * port written next to it; a second launch sends its arguments there and quits, and the
 * running window comes to the front (and downloads a link if one was passed).
 */
object SingleInstance {
    private val lockFile = File(AppDirs.files, "instance.lock")
    private val portFile = File(AppDirs.files, "instance.port")
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    /** Called on the UI thread when another launch asked for the window. */
    @Volatile var onActivate: (() -> Unit)? = null
    @Volatile var window: java.awt.Window? = null

    /** @return true if this process is the one to run; false after handing [args] to the running one. */
    fun claim(args: Array<String>): Boolean {
        val channel = runCatching { RandomAccessFile(lockFile, "rw").channel }.getOrNull() ?: return true
        val held = runCatching { channel.tryLock() }.getOrNull()
        if (held == null) {
            // Someone has it: pass the arguments on. If that fails, run anyway rather than nothing.
            return !notifyRunning(args)
        }
        lock = held
        listen()
        return true
    }

    private fun listen() {
        val s = runCatching { ServerSocket(0, 4, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        server = s
        portFile.writeText(s.localPort.toString())
        Thread({
            while (!s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                runCatching {
                    client.use { c ->
                        val args = c.getInputStream().bufferedReader(Charsets.UTF_8).readLines().toTypedArray()
                        SwingUtilities.invokeLater {
                            handleArgs(args)
                            onActivate?.invoke()
                        }
                    }
                }
            }
        }, "single-instance").apply { isDaemon = true; start() }
    }

    private fun notifyRunning(args: Array<String>): Boolean = runCatching {
        val port = portFile.readText().trim().toInt()
        Socket(InetAddress.getLoopbackAddress(), port).use { s ->
            s.getOutputStream().bufferedWriter(Charsets.UTF_8).use { w ->
                args.forEach { w.write(it); w.newLine() }
            }
        }
        true
    }.getOrDefault(false)

    fun bringToFront() {
        val w = window ?: return
        SwingUtilities.invokeLater {
            (w as? java.awt.Frame)?.let { if (it.extendedState and java.awt.Frame.ICONIFIED != 0) it.extendedState = it.extendedState and java.awt.Frame.ICONIFIED.inv() }
            w.toFront()
            w.requestFocus()
        }
    }

    fun release() {
        runCatching { server?.close() }
        runCatching { lock?.release() }
        portFile.delete()
    }
}
