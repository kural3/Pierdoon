package com.ku.pierdoon.playback

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedQueue(
    val songIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

/** A saved queue reduced to the tracks that still exist: what to play, from where. */
data class RestorePlan(val songIds: List<Long>, val index: Int, val positionMs: Long)

/**
 * Drops the ids [exists] rejects (files deleted since). If the current track is gone, the one
 * after it starts from the top, or the last one if nothing follows. Null when nothing is left.
 */
fun SavedQueue.restorePlan(exists: (Long) -> Boolean): RestorePlan? {
    val ids = ArrayList<Long>(songIds.size)
    var index = 0
    var current = false
    songIds.forEachIndexed { i, id ->
        if (!exists(id)) return@forEachIndexed
        if (i < this.index) index++ else if (i == this.index) current = true
        ids += id
    }
    if (ids.isEmpty()) return null
    return RestorePlan(ids, index.coerceAtMost(ids.lastIndex), if (current) positionMs else 0L)
}

/**
 * The last queue, so playback picks up where it left off after the process dies.
 * Only ids are stored; titles and art come from MediaStore again on restore.
 */
class QueueStore(context: Context) {

    private val file = File(context.filesDir, "queue.json")

    fun read(): SavedQueue? {
        if (!file.exists()) return null
        return runCatching {
            val o = JSONObject(file.readText())
            val ids = o.getJSONArray("ids").let { arr -> List(arr.length()) { arr.getLong(it) } }
            SavedQueue(
                songIds = ids,
                index = o.optInt("index"),
                positionMs = o.optLong("position"),
                shuffle = o.optBoolean("shuffle"),
                repeatMode = o.optInt("repeat"),
            )
        }.getOrNull()
    }

    /** Atomic: a crash mid-write leaves the previous queue intact. */
    fun write(q: SavedQueue) {
        val o = JSONObject()
            .put("version", 1)
            .put("ids", JSONArray(q.songIds))
            .put("index", q.index)
            .put("position", q.positionMs)
            .put("shuffle", q.shuffle)
            .put("repeat", q.repeatMode)
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
