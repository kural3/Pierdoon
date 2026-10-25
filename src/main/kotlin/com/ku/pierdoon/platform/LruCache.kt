package com.ku.pierdoon.platform

/** The few bits of android.util.LruCache the app uses: size-bounded, least recently used out first. */
open class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>(0, 0.75f, true)
    private var size = 0

    protected open fun sizeOf(key: K, value: V): Int = 1

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? {
        val previous = map.put(key, value)
        size += sizeOf(key, value)
        if (previous != null) size -= sizeOf(key, previous)
        trim()
        return previous
    }

    @Synchronized
    fun remove(key: K): V? = map.remove(key)?.also { size -= sizeOf(key, it) }

    @Synchronized
    fun evictAll() {
        map.clear()
        size = 0
    }

    @Synchronized
    fun snapshot(): Map<K, V> = LinkedHashMap(map)

    private fun trim() {
        val it = map.entries.iterator()
        while (size > maxSize && it.hasNext()) {
            val e = it.next()
            size -= sizeOf(e.key, e.value)
            it.remove()
        }
    }
}
