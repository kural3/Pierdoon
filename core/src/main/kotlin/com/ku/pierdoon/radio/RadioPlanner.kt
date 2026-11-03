package com.ku.pierdoon.radio

import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.TrackKey
import com.ku.pierdoon.data.TrackMatcher
import com.ku.pierdoon.data.bareTitle
import com.ku.pierdoon.data.normalizeTag
import com.ku.pierdoon.data.splitArtists
import kotlin.math.abs
import kotlin.random.Random

/**
 * Decides what the radio plays next: a new track from the mixes or one of the library's, as often
 * as [novelty] says, never the same twice and never one it was told not to.
 *
 * New tracks come from YouTube's mixes ([add]), less the ones the library has already: those count
 * as a hint instead, the library's own track going to the front of its picks. The library's picks
 * lean to the artists the radio has met (the seed's and the mixes'), so they stay near the mood.
 */
class RadioPlanner(
    library: List<Song>,
    seeds: List<Song>,
    /** How many of [seeds] play first, in order: the track picked, say. */
    openWith: Int,
    private val memory: RadioMemory?,
    novelty: Int,
    private val random: Random = Random.Default,
) {
    /** 0: only the library. 100: only new tracks. */
    var novelty: Int = novelty.coerceIn(0, 100)
        set(value) { field = value.coerceIn(0, 100) }

    private val songs = library.filter { !RadioTrack.isRadio(it.id) }
    private val byKey = songs.associateBy { TrackKey(it.title, it.artist, it.durationMs) }
    private val matcher = TrackMatcher(byKey.keys)
    /** The library's keys with bare titles, back to the keys themselves. */
    private val bareKeys = byKey.keys.associateBy { it.copy(title = bareTitle(it.title)) }
    private val bareMatcher = TrackMatcher(bareKeys.keys)
    private val byBareTitle = byKey.keys.groupBy { normalizeTag(bareTitle(it.title)) }

    private val pool = ArrayDeque<RadioTrack>()
    private val hinted = ArrayDeque<Song>()
    private val seen = HashSet<String>()
    private val played = HashSet<Long>()
    private val near = HashSet<String>()
    private var credit = 0f

    private val opening = ArrayDeque(seeds.filter { !RadioTrack.isRadio(it.id) }.take(openWith))
    /** Tracks to find mixes for, the seeds first. */
    private val searchFrom = ArrayDeque(seeds.filter { !RadioTrack.isRadio(it.id) })

    init {
        seeds.forEach { meet(it.artist) }
    }

    sealed interface Pick {
        data class New(val track: RadioTrack) : Pick
        data class Known(val song: Song) : Pick
    }

    val hasLibrary: Boolean get() = songs.isNotEmpty()

    /** New tracks waiting. Below a few the session asks for another mix. */
    val waiting: Int get() = pool.size

    /** Takes in a mix; returns how many of its tracks are new to the radio. */
    fun add(mix: List<RadioTrack>): Int {
        var added = 0
        for (track in mix) {
            if (!seen.add(track.videoId)) continue
            meet(track.artist)
            val length = track.durationSec
            val known = known(track)
            if (known != null) {
                byKey[known]?.takeIf { it.id !in played && memory?.isDisliked(it.id) != true }?.let { hinted += it }
                continue
            }
            if (memory?.isDisliked(track) == true) continue
            if (length != null && length !in MIN_SECONDS..MAX_SECONDS) continue
            pool += track
            added++
        }
        return added
    }

    /**
     * The library's track [track] is, if it has it. A video's title often carries "(prod. X)" or
     * "[Official Video]" a file's tags don't: tried bare too, either side.
     */
    private fun known(track: RadioTrack): TrackKey? {
        val ms = (track.durationSec ?: 0) * 1000L
        return matcher.find(TrackKey(track.title, track.artist, ms))
            ?: bareMatcher.find(TrackKey(bareTitle(track.title), track.artist, ms))?.let { bareKeys[it] }
            ?: sharedArtist(track, ms)
    }

    /**
     * A mix often names only the channel's artist ("A") where the file has them all ("A, B"): the
     * same bare title, one artist in common and, when both are known, the same length.
     */
    private fun sharedArtist(track: RadioTrack, ms: Long): TrackKey? {
        val artists = artistsOf(track.artist).ifEmpty { return null }
        return byBareTitle[normalizeTag(bareTitle(track.title))].orEmpty().firstOrNull { key ->
            val close = ms <= 0 || key.durationMs <= 0 || abs(ms - key.durationMs) <= TrackMatcher.DURATION_SLACK_MS
            close && artistsOf(key.artist).any { it in artists }
        }
    }

    private fun artistsOf(tag: String) = splitArtists(tag).map(::normalizeTag).filter { it.isNotEmpty() }.toSet()

    /** A video a search found for one of the library's tracks: that track, never a new one. */
    fun owns(videoId: String) {
        seen += videoId
    }

    /**
     * What to play next, or null when nothing is left: no library to fall back on and no new
     * tracks until another mix comes in.
     */
    fun next(): Pick? {
        opening.removeFirstOrNull()?.let { played += it.id; return Pick.Known(it) }
        // Capped, or an hour offline would be paid back as an hour of only new tracks.
        credit = (credit + novelty / 100f).coerceAtMost(2f)
        val wantsNew = credit >= 1f || !hasLibrary
        if (wantsNew) takeNew()?.let { credit = (credit - 1f).coerceAtLeast(0f); return it }
        takeKnown()?.let { return it }
        // The library ran out of tracks not yet played: new ones, if there are any, whatever the slider says.
        return if (novelty > 0) takeNew() else null
    }

    /** A new track that couldn't be fetched: the next one takes its turn. */
    fun skipped(track: RadioTrack) {
        pool.remove(track)
    }

    /** The library's track to find a mix for when none of the new ones has played yet. */
    fun seedSong(): Song? = searchFrom.removeFirstOrNull()
        ?: (songs.filter { meetsNear(it) }.takeIf { it.isNotEmpty() } ?: songs).randomOrNull(random)

    private fun takeNew(): Pick.New? {
        while (true) {
            val track = pool.removeFirstOrNull() ?: return null
            // Disliked since it came in (another of its artist's, say).
            if (memory?.isDisliked(track) == true) continue
            return Pick.New(track)
        }
    }

    private fun takeKnown(): Pick.Known? {
        while (true) {
            val hint = hinted.removeFirstOrNull() ?: break
            if (hint.id !in played) { played += hint.id; return Pick.Known(hint) }
        }
        val left = songs.filter { it.id !in played && memory?.isDisliked(it.id) != true }
        if (left.isEmpty()) return null
        val song = (left.filter { meetsNear(it) }.takeIf { it.isNotEmpty() } ?: left).random(random)
        played += song.id
        return Pick.Known(song)
    }

    private fun meetsNear(song: Song) = splitArtists(song.artist).any { normalizeTag(it) in near }

    private fun meet(artist: String) {
        splitArtists(artist).forEach { name -> normalizeTag(name).takeIf { it.isNotEmpty() }?.let { near += it } }
    }

    companion object {
        /** Shorter is a skit or a jingle; longer, an hour-long mix or a whole album in one video. */
        const val MIN_SECONDS = 60
        const val MAX_SECONDS = 600
    }
}
