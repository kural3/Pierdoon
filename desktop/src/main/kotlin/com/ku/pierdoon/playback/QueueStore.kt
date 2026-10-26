package com.ku.pierdoon.playback

import com.ku.pierdoon.platform.AppDirs
import com.ku.pierdoon.platform.recoverAtomicWrite
import com.ku.pierdoon.platform.writeAtomically
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
