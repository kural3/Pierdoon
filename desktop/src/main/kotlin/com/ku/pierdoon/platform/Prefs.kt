package com.ku.pierdoon.platform

import java.io.File
import java.util.Properties

/**
 * A tiny key-value file, the desktop stand-in for SharedPreferences: read once, kept in memory,
 * written whole (atomically) on every change. Meant for a few dozen small values.
 */
class Prefs(name: String) {
    private val file = File(AppDirs.files, "$name.properties")
    private val props = Properties().apply {
        if (recoverAtomicWrite(file)) runCatching { file.reader(Charsets.UTF_8).use { load(it) } }
    }

    @Synchronized fun getString(key: String, default: String?): String? = props.getProperty(key) ?: default
    @Synchronized fun getLong(key: String, default: Long): Long = props.getProperty(key)?.toLongOrNull() ?: default
    @Synchronized fun getInt(key: String, default: Int): Int = props.getProperty(key)?.toIntOrNull() ?: default
    @Synchronized fun getFloat(key: String, default: Float): Float = props.getProperty(key)?.toFloatOrNull() ?: default
    @Synchronized fun getBoolean(key: String, default: Boolean): Boolean =
        props.getProperty(key)?.toBooleanStrictOrNull() ?: default
    @Synchronized fun contains(key: String): Boolean = props.containsKey(key)
    @Synchronized fun keys(): Set<String> = props.stringPropertyNames()

    /** Applies every change made in [block] with a single write. */
    @Synchronized
    fun edit(block: Editor.() -> Unit) {
        Editor().block()
        val text = java.io.StringWriter().also { props.store(it, null) }.toString()
        writeAtomically(file, text)
    }

    inner class Editor {
        fun putString(key: String, value: String?) { if (value == null) props.remove(key) else props.setProperty(key, value) }
        fun putLong(key: String, value: Long) { props.setProperty(key, value.toString()) }
        fun putInt(key: String, value: Int) { props.setProperty(key, value.toString()) }
        fun putFloat(key: String, value: Float) { props.setProperty(key, value.toString()) }
        fun putBoolean(key: String, value: Boolean) { props.setProperty(key, value.toString()) }
        fun remove(key: String) { props.remove(key) }
    }
}
