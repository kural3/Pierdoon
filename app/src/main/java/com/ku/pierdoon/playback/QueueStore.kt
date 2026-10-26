package com.ku.pierdoon.playback

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
