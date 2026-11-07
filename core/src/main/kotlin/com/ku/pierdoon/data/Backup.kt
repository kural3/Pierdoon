package com.ku.pierdoon.data

import org.json.JSONObject

/**
 * A backup file: the settings both apps have, and the favourites, playlists, smart playlists and
 * play history with the tags of every track they name, so it restores on the same device, after
 * a reinstall, on a new phone or on the computer. The music itself isn't in it.
 *
 * JSON, gzipped by the apps when written. Restoring adds what's missing (see [LibrarySync.merge]
 * without a base) and takes the settings over.
 */
data class Backup(
    val createdAtMs: Long,
    /** "android" or "windows". */
    val platform: String,
    val appVersion: String,
    val settings: CommonSettings?,
    val library: LibrarySnapshot,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("createdAt", createdAtMs)
        .put("platform", platform)
        .put("appVersion", appVersion)
        .apply { settings?.let { put("settings", JsonSettings().also(it::write).json) } }
        .put("library", library.toJson())

    /** The file's bytes: the JSON, gzipped. */
    fun encode(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(out).use { it.write(toJson().toString().toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    companion object {
        /** A backup file's bytes, gzipped or not; null when it isn't one. */
        fun decode(bytes: ByteArray, settingsDefaults: CommonSettings): Backup? {
            val gzipped = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
            val text = runCatching {
                if (gzipped) java.util.zip.GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
                else bytes.toString(Charsets.UTF_8)
            }.getOrNull() ?: return null
            return parse(text, settingsDefaults)
        }

        /** "Pier.doon 2026-11-04", the name a new backup is offered under. */
        fun suggestedName(nowMs: Long): String =
            "Pier.doon " + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(nowMs))

        const val FORMAT = "pierdoon-backup"
        const val VERSION = 1
        const val EXTENSION = "pierdoon"

        /** Null when the text isn't a backup, or one from a newer version than this. */
        fun parse(text: String, settingsDefaults: CommonSettings): Backup? = runCatching {
            val o = JSONObject(text)
            if (o.optString("format") != FORMAT || o.optInt("version") > VERSION) return null
            Backup(
                createdAtMs = o.optLong("createdAt"),
                platform = o.optString("platform"),
                appVersion = o.optString("appVersion"),
                settings = o.optJSONObject("settings")?.let { s ->
                    CommonSettings.read(JsonSettings(s), settingsDefaults).let { read ->
                        // A font file of the user's own isn't in the backup: the font stays as it is here.
                        if (read.font == AppFont.Custom) {
                            read.copy(
                                font = settingsDefaults.font,
                                customFontFile = settingsDefaults.customFontFile,
                                customFontName = settingsDefaults.customFontName,
                            )
                        } else {
                            read.copy(customFontFile = settingsDefaults.customFontFile, customFontName = settingsDefaults.customFontName)
                        }
                    }
                },
                library = LibrarySnapshot.fromJson(o.getJSONObject("library")),
            )
        }.getOrNull()
    }
}

/** Settings kept in a JSON object, for the backup file. */
private class JsonSettings(val json: JSONObject = JSONObject()) : SettingsReader, SettingsWriter {
    override fun getString(key: String, default: String?): String? = if (json.has(key) && !json.isNull(key)) json.optString(key) else default
    override fun getInt(key: String, default: Int): Int = if (json.has(key)) json.optInt(key, default) else default
    override fun getFloat(key: String, default: Float): Float = if (json.has(key)) json.optDouble(key, default.toDouble()).toFloat() else default
    override fun getBoolean(key: String, default: Boolean): Boolean = if (json.has(key)) json.optBoolean(key, default) else default
    override fun putString(key: String, value: String?) { json.put(key, value ?: JSONObject.NULL) }
    override fun putInt(key: String, value: Int) { json.put(key, value) }
    override fun putFloat(key: String, value: Float) { json.put(key, value.toDouble()) }
    override fun putBoolean(key: String, value: Boolean) { json.put(key, value) }
}
