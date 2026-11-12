package com.ku.pierdoon.radio

import com.ku.pierdoon.data.keepDamagedCopy
import com.ku.pierdoon.data.normalizeTag
import com.ku.pierdoon.data.recoverAtomicWrite
import com.ku.pierdoon.data.writeAtomically
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What the radio was told it got wrong, kept between sessions: tracks never to play again, and
 * artists turned down often enough to leave out altogether.
 */
class RadioMemory private constructor(private val file: File) {
    private val videos = LinkedHashSet<String>()
    private val songs = LinkedHashSet<Long>()
    private val artistStrikes = LinkedHashMap<String, Int>()

    init {
        if (recoverAtomicWrite(file)) {
            // A broken entry costs only itself; anything skipped would be gone with the next
            // dislike, so the file as it was is put aside first.
            var damaged = false
            val o = runCatching { JSONObject(file.readText()) }.getOrNull()
            if (o == null) {
                damaged = true
            } else {
                o.optJSONArray(K_VIDEOS)?.let { a ->
                    repeat(a.length()) { i -> (a.opt(i) as? String)?.let { videos += it } ?: run { damaged = true } }
                }
                o.optJSONArray(K_SONGS)?.let { a ->
                    repeat(a.length()) { i -> (a.opt(i) as? Number)?.let { songs += it.toLong() } ?: run { damaged = true } }
                }
                o.optJSONObject(K_ARTISTS)?.let { a ->
                    a.keys().forEach { k -> (a.opt(k) as? Number)?.let { artistStrikes[k] = it.toInt() } ?: run { damaged = true } }
                }
            }
            if (damaged) keepDamagedCopy(file)
        }
    }

    @Synchronized fun isDisliked(track: RadioTrack): Boolean =
        track.videoId in videos || (artistStrikes[normalizeTag(track.artist)] ?: 0) >= ARTIST_STRIKES

    @Synchronized fun isDisliked(songId: Long): Boolean = songId in songs

    /** "Not this": the track goes, and its artist after [ARTIST_STRIKES] of these. */
    @Synchronized fun dislike(track: RadioTrack) {
        videos += track.videoId
        val artist = normalizeTag(track.artist)
        if (artist.isNotEmpty()) artistStrikes[artist] = (artistStrikes[artist] ?: 0) + 1
        save()
    }

    /** A library track the radio shouldn't bring up again (it still plays when picked by hand). */
    @Synchronized fun dislike(songId: Long) {
        songs += songId
        save()
    }

    @Synchronized fun isEmpty(): Boolean = videos.isEmpty() && songs.isEmpty() && artistStrikes.isEmpty()

    @Synchronized fun clear() {
        videos.clear()
        songs.clear()
        artistStrikes.clear()
        save()
    }

    private fun save() {
        // The oldest go first once it grows long: tastes change.
        while (videos.size > MAX_ENTRIES) videos.remove(videos.first())
        while (songs.size > MAX_ENTRIES) songs.remove(songs.first())
        val o = JSONObject()
            .put(K_VIDEOS, JSONArray(videos.toList()))
            .put(K_SONGS, JSONArray(songs.toList()))
            .put(K_ARTISTS, JSONObject(artistStrikes as Map<*, *>))
        runCatching { writeAtomically(file, o.toString()) }
    }

    companion object {
        /** Turned down this many times, an artist is left out of new tracks. */
        const val ARTIST_STRIKES = 2
        private const val MAX_ENTRIES = 2_000
        private const val K_VIDEOS = "videos"
        private const val K_SONGS = "songs"
        private const val K_ARTISTS = "artists"

        private val open = HashMap<String, RadioMemory>()

        /** The one kept in [dir] (the app's files folder). */
        fun get(dir: File): RadioMemory = synchronized(open) {
            open.getOrPut(dir.absolutePath) { RadioMemory(File(dir, "radio.json")) }
        }
    }
}
