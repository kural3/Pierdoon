package com.ku.pierdoon.data

import com.ku.pierdoon.data.history.Play
import com.ku.pierdoon.data.history.YearReview
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SmartAndDuplicatesTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = Calendar.getInstance().apply { set(2026, Calendar.JUNE, 15, 12, 0, 0) }.timeInMillis

    private fun song(id: Long, title: String, artist: String = "A", ms: Long = 200_000, addedDaysAgo: Int = 100) =
        Song(id, title, artist, "Album", 1, ms, dateAdded = (now - addedDaysAgo * day) / 1000)

    @Test
    fun smartRulesAllAndAny() {
        val songs = listOf(song(1, "One", addedDaysAgo = 3), song(2, "Two", "B"), song(3, "Three", "B", addedDaysAgo = 1))
        val plays = listOf(Play(2, now - day, 60_000), Play(2, now - 2 * day, 60_000))
        val recentB = SmartPlaylist(1, "x", listOf(SmartRule(SmartField.Artist, "b"), SmartRule(SmartField.AddedWithinDays, number = 7)), sort = SmartSort.Title)
        assertEquals(listOf(3L), recentB.evaluate(songs, plays, emptySet(), now).map { it.id })
        val either = recentB.copy(matchAll = false)
        assertEquals(listOf(1L, 3L, 2L), either.evaluate(songs, plays, emptySet(), now).map { it.id })
        val neverPlayed = SmartPlaylist(2, "y", listOf(SmartRule(SmartField.PlayedAtMost, number = 0)), sort = SmartSort.Title)
        assertEquals(listOf(1L, 3L), neverPlayed.evaluate(songs, plays, emptySet(), now).map { it.id })
        val notFav = SmartPlaylist(3, "z", listOf(SmartRule(SmartField.Favorite, negate = true)), sort = SmartSort.MostPlayed, limit = 1)
        assertEquals(listOf(2L), notFav.evaluate(songs, plays, setOf(1L), now).map { it.id })
    }

    @Test
    fun smartSurvivesJson() {
        val p = SmartPlaylist(5, "Name", listOf(SmartRule(SmartField.Title, "love", negate = true)), false, SmartSort.Artist, 20)
        assertEquals(p, SmartPlaylist.fromJson(JSONObject(p.toJson().toString())))
    }

    @Test
    fun duplicatesGroupAndOrder() {
        val songs = listOf(
            song(1, "Song", ms = 200_000),
            song(2, "Song (Official Video)", ms = 201_500),
            song(3, "Song", artist = "Someone else"),
            song(4, "Other"),
            song(5, "song", ms = 260_000),
        )
        val groups = Duplicates.find(songs, favorites = setOf(2L))
        assertEquals(1, groups.size)
        assertEquals(listOf(2L, 1L), groups.single().songs.map { it.id })
    }

    @Test
    fun yearReview() {
        val cal = Calendar.getInstance()
        fun at(month: Int, dayOfMonth: Int, hour: Int) = cal.apply { set(2025, month, dayOfMonth, hour, 0, 0) }.timeInMillis
        val byId = listOf(song(1, "One", "Old"), song(2, "Two", "New")).associateBy { it.id }
        val plays = listOf(
            Play(1, cal.apply { set(2024, Calendar.MAY, 1, 10, 0, 0) }.timeInMillis, 60_000),
            Play(1, at(Calendar.MARCH, 1, 21), 100_000),
            Play(2, at(Calendar.MARCH, 2, 22), 200_000),
            Play(2, at(Calendar.MARCH, 3, 20), 200_000),
            Play(2, at(Calendar.JULY, 9, 9), 50_000),
        )
        val r = YearReview.from(plays, byId, 2025)
        assertEquals(4, r.plays)
        assertEquals(550_000L, r.listenedMs)
        assertEquals(Calendar.MARCH, r.topMonth)
        assertEquals(3, r.longestStreakDays)
        assertEquals(1, r.newArtists)
        assertEquals("New", r.topNewArtist?.name)
        assertEquals("Two", r.topTracks.first().song.title)
        assertEquals(1L, r.firstSong?.id)
        assertEquals(listOf(2025, 2024), YearReview.years(plays))
        assertTrue(r.dayPart == com.ku.pierdoon.data.history.DayPart.Evening)
    }
}
