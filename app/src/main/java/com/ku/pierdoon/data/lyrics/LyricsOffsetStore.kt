package com.ku.pierdoon.data.lyrics

import android.content.Context
import androidx.core.content.edit

/** [LyricsOffsets] kept in SharedPreferences ("lyrics_offsets"). */
fun LyricsOffsets(context: Context): LyricsOffsets {
    val prefs = context.getSharedPreferences("lyrics_offsets", Context.MODE_PRIVATE)
    return LyricsOffsets(object : LyricsOffsets.Store {
        override fun get(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null
        override fun set(key: String, value: Long?) = prefs.edit { if (value == null) remove(key) else putLong(key, value) }
    })
}
