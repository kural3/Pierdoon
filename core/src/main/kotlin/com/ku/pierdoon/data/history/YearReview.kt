package com.ku.pierdoon.data.history

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.splitArtists
import java.util.Calendar

/** When in the day most of the listening happens. */
enum class DayPart { Night, Morning, Afternoon, Evening }

/**
 * A year of listening told as a story: how much, who and what most, the month and the day that
 * stood out, the artists heard for the first time, the longest run of days in a row.
 * Plays of tracks no longer in the library count for the time, not for the tops.
 */
@Immutable
data class YearReview(
    val year: Int,
    val listenedMs: Long,
    val plays: Int,
    val tracks: Int,
    val artists: Int,
    val topTracks: List<TrackCount>,
    val topArtists: List<ArtistCount>,
    /** Time listened per month, January first. */
    val months: List<Long>,
    /** The day listened to most: its start (epoch ms) and how long. */
    val topDayMs: Long,
    val topDayListenedMs: Long,
    /** Artists whose first play ever was this year. */
    val newArtists: Int,
    /** The one of them played most this year, if any. */
    val topNewArtist: ArtistCount?,
    /** Most days in a row with some listening. */
    val longestStreakDays: Int,
    val dayPart: DayPart,
    /** The track that opened the year. */
    val firstSong: Song?,
) {
    /** The month (0 = January) listened to most. */
    val topMonth: Int get() = months.indices.maxByOrNull { months[it] } ?: 0

    companion object {
        private const val TOP = 5

        /** Years with plays in them, latest first. */
        fun years(plays: List<Play>): List<Int> {
            val cal = Calendar.getInstance()
            return plays.mapTo(HashSet()) { cal.timeInMillis = it.atMs; cal.get(Calendar.YEAR) }.sortedDescending()
        }

        /**
         * The review shows from December 1st, for the year ending, until the end of January, for
         * the year just gone; null outside those weeks or with nothing to tell.
         */
        fun seasonYear(plays: List<Play>, nowMs: Long): Int? {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
            val year = when (cal.get(Calendar.MONTH)) {
                Calendar.DECEMBER -> cal.get(Calendar.YEAR)
                Calendar.JANUARY -> cal.get(Calendar.YEAR) - 1
                else -> return null
            }
            return year.takeIf { it in years(plays) }
        }

        fun from(plays: List<Play>, byId: Map<Long, Song>, year: Int): YearReview {
            val cal = Calendar.getInstance()
            fun yearOf(ms: Long): Int { cal.timeInMillis = ms; return cal.get(Calendar.YEAR) }
            val inYear = plays.filter { yearOf(it.atMs) == year }.sortedBy { it.atMs }

            val months = LongArray(12)
            val perDay = HashMap<Long, Long>()
            val parts = LongArray(4)
            for (p in inYear) {
                cal.timeInMillis = p.atMs
                months[cal.get(Calendar.MONTH)] += p.listenedMs
                parts[cal.get(Calendar.HOUR_OF_DAY) / 6] += p.listenedMs
                perDay.merge(dayStart(cal, p.atMs), p.listenedMs, Long::plus)
            }
            val topDay = perDay.maxByOrNull { it.value }

            // Days in a row: walk the listened days in order, a calendar day apart.
            var longest = 0
            var run = 0
            var prev: Long? = null
            for (day in perDay.keys.sorted()) {
                run = if (prev != null && nextDay(cal, prev) == day) run + 1 else 1
                longest = maxOf(longest, run)
                prev = day
            }

            val known = inYear.filter { it.songId in byId }
            val stats = ListeningStats.from(known, byId, StatsPeriod.All, 0L, top = Int.MAX_VALUE)

            // Artists first heard this year: none of their tracks played before it began.
            val heardBefore = HashSet<String>()
            for (p in plays) {
                if (yearOf(p.atMs) >= year) continue
                val song = byId[p.songId] ?: continue
                splitArtists(song.artist).forEach { heardBefore += it.lowercase() }
            }
            val fresh = stats.topArtists.filter { it.name.lowercase() !in heardBefore }

            return YearReview(
                year = year,
                listenedMs = inYear.sumOf { it.listenedMs },
                plays = inYear.size,
                tracks = stats.tracks,
                artists = stats.artists,
                topTracks = stats.topTracks.take(TOP),
                topArtists = stats.topArtists.take(TOP),
                months = months.toList(),
                topDayMs = topDay?.key ?: 0L,
                topDayListenedMs = topDay?.value ?: 0L,
                newArtists = fresh.size,
                topNewArtist = fresh.firstOrNull(),
                longestStreakDays = longest,
                dayPart = DayPart.entries[parts.indices.maxByOrNull { parts[it] } ?: 0],
                firstSong = known.firstOrNull()?.let { byId[it.songId] },
            )
        }

        private fun dayStart(cal: Calendar, ms: Long): Long {
            cal.timeInMillis = ms
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        private fun nextDay(cal: Calendar, dayStartMs: Long): Long {
            cal.timeInMillis = dayStartMs
            cal.add(Calendar.DAY_OF_MONTH, 1)
            return dayStart(cal, cal.timeInMillis)
        }
    }
}
