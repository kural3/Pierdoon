package com.ku.pierdoon.platform

import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Where errors end up. The installed app has no console, so without this whatever went wrong
 * was gone the moment its dialog closed: `%APPDATA%\Pier.doon\logs\errors.log`.
 */
object ErrorLog {
    val file: File by lazy { File(AppDirs.files, "logs/errors.log") }

    fun install() {
        runCatching {
            file.parentFile.mkdirs()
            // Starts over once it gets long; only recent trouble matters.
            if (file.length() > MAX_BYTES) file.delete()
            // Packaged builds: stderr (library warnings, stack traces) goes to the same file.
            if (System.getProperty("jpackage.app-version") != null) {
                System.setErr(PrintStream(FileOutputStream(file, true), true, Charsets.UTF_8))
            }
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            record("uncaught on ${thread.name}", e)
            previous?.uncaughtException(thread, e)
        }
    }

    @Synchronized
    fun record(what: String, e: Throwable) {
        runCatching {
            val time = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            file.appendText("$time $what\n${e.stackTraceToString()}\n", Charsets.UTF_8)
        }
    }

    private const val MAX_BYTES = 512 * 1024L
}
