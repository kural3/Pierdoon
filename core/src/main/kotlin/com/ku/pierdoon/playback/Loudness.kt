package com.ku.pierdoon.playback

import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.recoverAtomicWrite
import com.ku.pierdoon.data.writeAtomically
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Each track's loudness, measured once and kept in [file], and the measuring itself: one track
 * at a time in the background, the ones about to play first. [measure] decodes a track the way
 * its platform can; null if it can't be.
 */
class Loudness(
    private val file: File,
    private val scope: CoroutineScope,
    private val measure: (Song) -> TrackLoudness?,
) {
    private val known = HashMap<Long, TrackLoudness>()
    private val failed = HashSet<Long>()
    private val soon = ArrayDeque<Song>()
    private val later = ArrayDeque<Song>()
    private var worker: Job? = null
    private var unsaved = 0
    /** The level the library is evened out to; follows it as tracks are measured. */
    private var target = TrackLoudness.TARGET_LUFS

    /** Bumped with every new measurement, so what's playing can take it up. */
    private val _measured = MutableStateFlow(0)
    val measured: StateFlow<Int> = _measured.asStateFlow()

    init {
        if (recoverAtomicWrite(file)) runCatching {
            val o = JSONObject(file.readText())
            o.keys().forEach { k ->
                val a = o.optJSONArray(k) ?: return@forEach
                val id = k.toLongOrNull() ?: return@forEach
                known[id] = TrackLoudness(a.optDouble(0).toFloat(), a.optDouble(1).toFloat())
            }
        }
        target = TrackLoudness.targetFor(known.values.map { it.lufs })
    }

    /** The factor [song] plays at; 1 until it's measured. */
    fun gain(songId: Long?): Float = synchronized(this) { songId?.let { known[it] }?.gain(target) } ?: 1f

    fun of(songId: Long): TrackLoudness? = synchronized(this) { known[songId] }

    /** Measures [songs] ahead of everything else waiting (the playing track and what follows). */
    fun prioritize(songs: List<Song>) {
        synchronized(this) {
            val todo = songs.filter { it.id !in known && it.id !in failed }
            if (todo.isEmpty()) return
            soon.removeAll { s -> todo.any { it.id == s.id } }
            todo.asReversed().forEach { soon.addFirst(it) }
        }
        start()
    }

    /** Measures whatever of the library isn't yet, slowly. */
    fun scanLibrary(songs: List<Song>) {
        synchronized(this) {
            later.clear()
            songs.filterTo(later) { it.id !in known && it.id !in failed }
        }
        start()
    }

    /** Stops measuring (the setting went off); what was measured stays. */
    fun stop() {
        synchronized(this) {
            soon.clear()
            later.clear()
        }
        worker?.cancel()
        worker = null
        save()
    }

    private fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val song = synchronized(this@Loudness) { soon.removeFirstOrNull() ?: later.removeFirstOrNull() }
                    ?: break
                if (synchronized(this@Loudness) { song.id in known }) continue
                val result = runCatching { measure(song) }.getOrNull()
                synchronized(this@Loudness) {
                    if (result != null) {
                        known[song.id] = result
                        target = TrackLoudness.targetFor(known.values.map { it.lufs })
                        unsaved++
                    } else {
                        failed += song.id
                    }
                }
                if (result != null) _measured.value++
                if (unsaved >= SAVE_EVERY) save()
                // Playback and the screen come first.
                yield()
            }
            save()
        }
    }

    private fun save() {
        val text = synchronized(this) {
            if (unsaved == 0) return
            unsaved = 0
            JSONObject().apply {
                known.forEach { (id, l) -> put(id.toString(), JSONArray().put(l.lufs.toDouble()).put(l.peak.toDouble())) }
            }.toString()
        }
        writeAtomically(file, text)
    }

    private companion object {
        const val SAVE_EVERY = 20
    }
}
