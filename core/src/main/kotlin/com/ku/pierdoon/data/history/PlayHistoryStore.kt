package com.ku.pierdoon.data.history

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The play log on disk, read once and then kept in memory. Every file access happens on one
 * background thread, in order: a play added before the log was read lands after what it holds.
 * [onChange] gets each new list, on that thread; the platforms turn it into a flow.
 */
class PlayHistoryStore(private val file: File, private val onChange: (List<Play>) -> Unit) {

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "play-history").apply { isDaemon = true } }
    @Volatile private var plays: List<Play> = emptyList()

    init {
        io.execute {
            val read = runCatching { if (file.exists()) PlayLogFormat.parse(file.readText()) else emptyList() }
                .getOrDefault(emptyList())
            plays = if (read.size > PlayLogFormat.MAX_PLAYS) {
                read.takeLast(PlayLogFormat.MAX_PLAYS).also(::rewrite)
            } else read
            onChange(plays)
        }
    }

    fun add(play: Play) = io.execute {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(PlayLogFormat.line(play))
        }
        plays = plays + play
        onChange(plays)
    }

    /** Waits a moment for what's queued to reach the disk; for quitting. */
    fun awaitWrites() {
        runCatching { io.submit {}.get(1, TimeUnit.SECONDS) }
    }

    fun clear() = io.execute {
        file.delete()
        plays = emptyList()
        onChange(plays)
    }

    private fun rewrite(list: List<Play>) {
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(list.joinToString("") { PlayLogFormat.line(it) })
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
