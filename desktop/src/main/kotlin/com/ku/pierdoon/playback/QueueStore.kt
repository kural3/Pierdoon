package com.ku.pierdoon.playback

import com.ku.pierdoon.platform.AppDirs
import com.ku.pierdoon.platform.recoverAtomicWrite
import com.ku.pierdoon.platform.writeAtomically
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedQueue(
    val songIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    /** The shuffled play order, as indices into [songIds]; null when it wasn't saved. */
    val shuffleOrder: List<Int>? = null,
)

/** A saved queue reduced to the tracks that still exist: what to play, from where. */
data class RestorePlan(
    val songIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    /** The saved play order without the dropped tracks, as indices into [songIds]. */
    val shuffleOrder: List<Int>? = null,
)

/**
 * Drops the ids [exists] rejects (files deleted since). If the current track is gone, the one
 * after it starts from the top, or the last one if nothing follows. Null when nothing is left.
 */
fun SavedQueue.restorePlan(exists: (Long) -> Boolean): RestorePlan? {
    val ids = ArrayList<Long>(songIds.size)
    /** Old index to new, for the tracks that are kept. */
    val kept = HashMap<Int, Int>()
    var index = 0
    var current = false
    songIds.forEachIndexed { i, id ->
        if (!exists(id)) return@forEachIndexed
        if (i < this.index) index++ else if (i == this.index) current = true
        kept[i] = ids.size
        ids += id
    }
    if (ids.isEmpty()) return null
    val order = shuffleOrder
        ?.takeIf { it.size == songIds.size && it.toSet() == songIds.indices.toSet() }
        ?.mapNotNull { kept[it] }
    return RestorePlan(ids, index.coerceAtMost(ids.lastIndex), if (current) positionMs else 0L, order)
}

/**
 * The last queue, so playback picks up where it left off after the process dies.
 * Only ids are stored; titles and art come from MediaStore again on restore.
 */
class QueueStore {

    private val file = File(AppDirs.files, "queue.json")

    fun read(): SavedQueue? {
        if (!recoverAtomicWrite(file)) return null
        return runCatching {
            val o = JSONObject(file.readText())
            val ids = o.getJSONArray("ids").let { arr -> List(arr.length()) { arr.getLong(it) } }
            SavedQueue(
                songIds = ids,
                index = o.optInt("index"),
                positionMs = o.optLong("position"),
                shuffle = o.optBoolean("shuffle"),
                repeatMode = o.optInt("repeat"),
                shuffleOrder = o.optJSONArray("order")?.let { arr -> List(arr.length()) { arr.getInt(it) } },
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
        q.shuffleOrder?.let { o.put("order", JSONArray(it)) }
        writeAtomically(file, o.toString())
    }
}
