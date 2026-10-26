package com.ku.pierdoon.playback

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
