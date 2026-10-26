package com.ku.pierdoon.playback

import android.content.Context
import android.util.AtomicFile
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
class QueueStore(context: Context) {

    private val path = File(context.filesDir, "queue.json")
    private val file = AtomicFile(path)

    fun read(): SavedQueue? {
        recoverOldWrite()
        return runCatching {
            val o = JSONObject(file.readFully().toString(Charsets.UTF_8))
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

    /** Atomic: a crash mid-write leaves the previous queue intact (AtomicFile keeps a backup). */
    fun write(q: SavedQueue) {
        val o = JSONObject()
            .put("version", 1)
            .put("ids", JSONArray(q.songIds))
            .put("index", q.index)
            .put("position", q.positionMs)
            .put("shuffle", q.shuffle)
            .put("repeat", q.repeatMode)
        q.shuffleOrder?.let { o.put("order", JSONArray(it)) }
        runCatching {
            val out = file.startWrite()
            try {
                out.write(o.toString().toByteArray(Charsets.UTF_8))
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        }
    }

    /**
     * Up to 1.6 a save could stop between deleting queue.json and renaming its temp file over
     * it, leaving only the complete temp file behind. Puts it back.
     */
    private fun recoverOldWrite() {
        val tmp = File(path.parentFile, path.name + ".tmp")
        if (!tmp.exists()) return
        if (path.exists()) tmp.delete() else tmp.renameTo(path)
    }
}
