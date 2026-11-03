package com.ku.pierdoon.radio

import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.normalizeTag
import com.ku.pierdoon.data.splitArtists

/** What a radio starts from: [seeds] set the mood, the first [openWith] of them play first. */
data class RadioStart(val title: String, val seeds: List<Song>, val openWith: Int) {
    companion object {
        /** From one track: it plays, then what goes with it. */
        fun track(song: Song) = RadioStart(song.title, listOf(song), openWith = 1)

        /** From an artist: one of theirs, then the artist's world. */
        fun artist(name: String, library: List<Song>): RadioStart {
            val key = normalizeTag(name)
            val theirs = library.filter { s -> splitArtists(s.artist).any { normalizeTag(it) == key } }.shuffled()
            return RadioStart(name, theirs, openWith = 1)
        }

        /** "My wave": from the playlist of the day, which already knows what's played most and loved. */
        fun wave(daily: List<Song>) = RadioStart("", daily.shuffled(), openWith = 1)
    }
}
