package com.ku.pierdoon.data.lyrics

import com.ku.pierdoon.platform.Prefs

/** [LyricsOffsets] kept in lyrics_offsets.properties. */
fun LyricsOffsets(): LyricsOffsets {
    val prefs = Prefs("lyrics_offsets")
    return LyricsOffsets(object : LyricsOffsets.Store {
        override fun get(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null
        override fun set(key: String, value: Long?) = prefs.edit { if (value == null) remove(key) else putLong(key, value) }
    })
}
