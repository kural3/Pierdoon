package com.ku.pierdoon.data.history

import com.ku.pierdoon.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningTest {

    private fun song(id: Long, artist: String, duration: Long = 200_000) =
        Song(id, "T$id", artist, "Al", albumId = 1, durationMs = duration)

    @Test
    fun trackerCountsWhatWasHeard() {
        val plays = mutableListOf<Play>()
        val t = ListeningTracker { plays += it }
        t.update(1, 200_000, playing = true, nowMs = 0, wallMs = 1000)
        t.update(1, 200_000, playing = false, nowMs = 20_000, wallMs = 0)
        t.update(1, 200_000, playing = true, nowMs = 50_000, wallMs = 0)
        // Skipped to 2 after 40 s heard in all: 1 counts.
        t.update(2, 200_000, playing = true, nowMs = 70_000, wallMs = 9000)
        // 2 was skipped after 10 s: it doesn't.
        t.update(3, 40_000, playing = true, nowMs = 80_000, wallMs = 0)
        // A 40 s track counts after half of it.
        t.flush(nowMs = 101_000)
        assertEquals(listOf(Play(1, 1000, 40_000), Play(3, 0, 21_000)), plays)
    }

    @Test
    fun aRepeatedTrackCountsEachTime() {
        val plays = mutableListOf<Play>()
        val t = ListeningTracker { plays += it }
        t.update(1, 60_000, playing = true, nowMs = 0, wallMs = 0, newItem = true)
        t.update(1, 60_000, playing = true, nowMs = 60_000, wallMs = 60_000, newItem = true)
        t.flush(nowMs = 120_000)
        assertEquals(listOf(Play(1, 0, 60_000), Play(1, 60_000, 60_000)), plays)
    }

    @Test
    fun logRoundTrips() {
        val plays = listOf(Play(1, 2, 3), Play(Long.MAX_VALUE, 5, 6))
        assertEquals(plays, PlayLogFormat.parse(plays.joinToString("") { PlayLogFormat.line(it) } + "7 8"))
    }

    @Test
    fun statsCountEachArtistOfATrack() {
        val songs = listOf(song(1, "A, B"), song(2, "B"), song(3, "C"))
        val now = 100L * 24 * 60 * 60 * 1000
        val plays = listOf(
            Play(1, now - 1000, 60_000), Play(1, now - 2000, 60_000),
            Play(2, now - 3000, 30_000), Play(3, now - 4000, 10_000),
            Play(99, now, 10_000),
            Play(3, now - 40L * 24 * 60 * 60 * 1000, 10_000),
        )
        val s = ListeningStats.from(plays, songs.associateBy { it.id }, StatsPeriod.Week, now)
        assertEquals(4, s.plays)
        assertEquals(160_000L, s.listenedMs)
        assertEquals(listOf(1L, 2L, 3L), s.topTracks.map { it.song.id })
        assertEquals(listOf("B" to 3, "A" to 2, "C" to 1), s.topArtists.map { it.name to it.plays })
        assertEquals(7, s.timeline.size)
        assertEquals(160_000L, s.timeline.sum())
        assertEquals(5, ListeningStats.from(plays, songs.associateBy { it.id }, StatsPeriod.All, now).plays)
    }

    @Test
    fun dailyMixIsStableForADayAndFavoursTaste() {
        val songs = (1L..200L).map { song(it, "Artist ${it % 20}") }
        val day = 500L * 24 * 60 * 60 * 1000
        val plays = (1..30).map { Play(if (it % 2 == 0) 20L else 40L, day - 10L * 24 * 60 * 60 * 1000 + it, 60_000) }
        val a = DailyMix.build(songs, plays, favorites = setOf(5L), nowMs = day + 1000)
        val b = DailyMix.build(songs, plays, favorites = setOf(5L), nowMs = day + 5000)
        val next = DailyMix.build(songs, plays, favorites = setOf(5L), nowMs = day + 24L * 60 * 60 * 1000)
        assertEquals(a, b)
        assertTrue(a != next)
        assertEquals(DailyMix.SIZE, a.size)
        assertEquals(a.size, a.toSet().size)
        // Artist 0 (track 20 and 40 were played a lot) shows up more than chance alone would have it.
        assertTrue(a.count { it.artist == "Artist 0" } >= 3)
        assertTrue(5L in a.map { it.id })
        assertEquals(10, DailyMix.build(songs.take(10), emptyList(), emptySet(), day).size)
    }
}
