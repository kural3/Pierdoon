package com.ku.pierdoon.data.history

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.splitArtists
import java.util.Calendar
import java.util.Random
import java.util.TimeZone

/** One listen: [songId] began at [atMs] (epoch) and played for [listenedMs] of real time. */
@Immutable
data class Play(val songId: Long, val atMs: Long, val listenedMs: Long)

/**
 * The play log's file format: one "id at listened" line per play, appended as plays end.
 * Text rather than JSON: a year of listening is a few hundred kilobytes, and a cut-off last
 * line costs that one play only.
 */
object PlayLogFormat {
    /** Plays kept: the oldest beyond this are dropped when the log is rewritten. */
    const val MAX_PLAYS = 50_000

    fun line(p: Play): String = "${p.songId} ${p.atMs} ${p.listenedMs}\n"

    fun parse(text: String): List<Play> = text.lineSequence().mapNotNull { l ->
        val parts = l.trim().split(' ')
        if (parts.size != 3) return@mapNotNull null
        val id = parts[0].toLongOrNull() ?: return@mapNotNull null
        val at = parts[1].toLongOrNull() ?: return@mapNotNull null
        val ms = parts[2].toLongOrNull() ?: return@mapNotNull null
        Play(id, at, ms)
    }.toList()
}

/**
 * Turns the player's state into plays. The platform reports every change of track or of
 * playing/paused; a track counts once it played for [MIN_LISTEN_MS], or half of it when shorter,
 * and is written when it stops being the current one.
 */
class ListeningTracker(private val onPlay: (Play) -> Unit) {
    private var songId = -1L
    private var durationMs = 0L
    private var startedAtMs = 0L
    private var listenedMs = 0L
    /** Monotonic time playing resumed at, or -1 while paused. */
    private var resumedAt = -1L

    /**
     * [nowMs] is monotonic (for the time listened), [wallMs] the clock (for when the play began).
     * [newItem]: the player moved to an item, the same track again included (repeat one).
     */
    @Synchronized
    fun update(songId: Long?, durationMs: Long, playing: Boolean, nowMs: Long, wallMs: Long, newItem: Boolean = false) {
        val id = songId ?: -1L
        if (id != this.songId || newItem) {
            finish(nowMs)
            this.songId = id
            this.durationMs = durationMs
            startedAtMs = wallMs
        }
        if (id < 0) return
        if (playing && resumedAt < 0) resumedAt = nowMs
        if (!playing && resumedAt >= 0) {
            listenedMs += nowMs - resumedAt
            resumedAt = -1
        }
    }

    /** The app is going away: the current track counts as far as it got. */
    @Synchronized
    fun flush(nowMs: Long) {
        finish(nowMs)
        songId = -1
    }

    private fun finish(nowMs: Long) {
        val total = listenedMs + if (resumedAt >= 0) nowMs - resumedAt else 0
        listenedMs = 0
        resumedAt = -1
        if (songId < 0) return
        val needed = if (durationMs > 0) minOf(MIN_LISTEN_MS, durationMs / 2) else MIN_LISTEN_MS
        if (total >= needed) onPlay(Play(songId, startedAtMs, total))
    }

    private companion object {
        const val MIN_LISTEN_MS = 30_000L
    }
}

@Immutable
data class TrackCount(val song: Song, val plays: Int, val listenedMs: Long)

@Immutable
data class ArtistCount(val name: String, val plays: Int, val listenedMs: Long, val cover: Song)

enum class StatsPeriod { Week, Month, Year, All }

@Immutable
data class ListeningStats(
    val plays: Int,
    val listenedMs: Long,
    val tracks: Int,
    val artists: Int,
    val topTracks: List<TrackCount>,
    val topArtists: List<ArtistCount>,
    /** Time listened per day (week, month) or per month (year, all time), oldest first. */
    val timeline: List<Long>,
) {
    companion object {
        private const val TOP = 20

        /** Plays of tracks no longer in the library are left out: there'd be nothing to show for them. */
        fun from(plays: List<Play>, byId: Map<Long, Song>, period: StatsPeriod, nowMs: Long): ListeningStats {
            val since = periodStart(period, nowMs)
            val inPeriod = plays.filter { it.atMs >= since && it.songId in byId }

            val perTrack = HashMap<Long, LongArray>()
            for (p in inPeriod) perTrack.getOrPut(p.songId) { LongArray(2) }.let { it[0]++; it[1] += p.listenedMs }
            val topTracks = perTrack.entries
                .sortedWith(compareByDescending<Map.Entry<Long, LongArray>> { it.value[0] }.thenByDescending { it.value[1] })
                .take(TOP)
                .map { (id, v) -> TrackCount(byId.getValue(id), v[0].toInt(), v[1]) }

            // A track of "A, B" counts fully for each; names that differ in case are one.
            class Acc(val name: String, val cover: Song) {
                var plays = 0
                var ms = 0L
            }
            val perArtist = HashMap<String, Acc>()
            for ((id, v) in perTrack) {
                val song = byId.getValue(id)
                for (name in splitArtists(song.artist)) {
                    val acc = perArtist.getOrPut(name.lowercase()) { Acc(name, song) }
                    acc.plays += v[0].toInt()
                    acc.ms += v[1]
                }
            }
            val topArtists = perArtist.values
                .sortedWith(compareByDescending<Acc> { it.plays }.thenByDescending { it.ms })
                .take(TOP)
                .map { ArtistCount(it.name, it.plays, it.ms, it.cover) }

            return ListeningStats(
                plays = inPeriod.size,
                listenedMs = inPeriod.sumOf { it.listenedMs },
                tracks = perTrack.size,
                artists = perArtist.size,
                topTracks = topTracks,
                topArtists = topArtists,
                timeline = timeline(inPeriod, period, nowMs),
            )
        }

        fun periodStart(period: StatsPeriod, nowMs: Long): Long = when (period) {
            StatsPeriod.Week -> daysBack(nowMs, 6)
            StatsPeriod.Month -> daysBack(nowMs, 29)
            StatsPeriod.Year -> monthsBack(nowMs, 11)
            StatsPeriod.All -> 0L
        }

        private fun timeline(plays: List<Play>, period: StatsPeriod, nowMs: Long): List<Long> {
            val cal = Calendar.getInstance()
            return when (period) {
                StatsPeriod.Week, StatsPeriod.Month -> {
                    val days = if (period == StatsPeriod.Week) 7 else 30
                    val first = daysBack(nowMs, days - 1)
                    // Day boundaries from the calendar, not 24-hour steps: a clock change moves them.
                    val starts = LongArray(days) { daysBack(nowMs, days - 1 - it) }
                    val out = LongArray(days)
                    for (p in plays) {
                        if (p.atMs < first) continue
                        var i = days - 1
                        while (i > 0 && p.atMs < starts[i]) i--
                        out[i] += p.listenedMs
                    }
                    out.toList()
                }
                StatsPeriod.Year, StatsPeriod.All -> {
                    fun month(ms: Long): Int {
                        cal.timeInMillis = ms
                        return cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
                    }
                    val now = month(nowMs)
                    // The last twelve months, or since the first play, up to five years.
                    val first = if (period == StatsPeriod.Year || plays.isEmpty()) now - 11
                    else plays.minOf { month(it.atMs) }.coerceIn(now - 59, now - 11)
                    val out = LongArray(now - first + 1)
                    for (p in plays) {
                        val i = month(p.atMs) - first
                        if (i in out.indices) out[i] += p.listenedMs
                    }
                    out.toList()
                }
            }
        }

        private fun daysBack(nowMs: Long, days: Int): Long = Calendar.getInstance().run {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -days)
            timeInMillis
        }

        private fun monthsBack(nowMs: Long, months: Int): Long = Calendar.getInstance().run {
            timeInMillis = daysBack(nowMs, 0)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -months)
            timeInMillis
        }
    }
}

/**
 * The playlist of the day: what's loved, more of the artists played most, and some that
 * haven't been heard yet. The same all day long, a new one the next.
 */
object DailyMix {
    const val SIZE = 25
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val TASTE_DAYS = 60

    fun build(songs: List<Song>, plays: List<Play>, favorites: Set<Long>, nowMs: Long): List<Song> {
        if (songs.isEmpty()) return emptyList()
        val random = Random(dayNumber(nowMs) * 1_000_003 + 17)
        val byId = songs.associateBy { it.id }

        // Taste: two months of plays up to last midnight, artists counted the way the stats count
        // them. Today's plays wait for tomorrow, so the mix stays put while it's being listened to.
        val today = Calendar.getInstance().run {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }
        val since = today - TASTE_DAYS * DAY_MS
        val trackPlays = HashMap<Long, Int>()
        val artistPlays = HashMap<String, Int>()
        val lastPlayed = HashMap<Long, Long>()
        for (p in plays) {
            val song = byId[p.songId] ?: continue
            if (p.atMs >= today) continue
            lastPlayed.merge(p.songId, p.atMs, ::maxOf)
            if (p.atMs < since) continue
            trackPlays.merge(p.songId, 1, Int::plus)
            for (a in splitArtists(song.artist)) artistPlays.merge(a.lowercase(), 1, Int::plus)
        }
        // What played yesterday sits this one out.
        val justHeard = lastPlayed.filterValues { it >= today - DAY_MS }.keys
        fun artistWeight(s: Song) = splitArtists(s.artist).sumOf { artistPlays[it.lowercase()] ?: 0 }

        // Weighted shuffle: a random number raised to 1/weight puts the heavy ones first, mostly.
        fun weighted(list: List<Song>, weight: (Song) -> Double) =
            list.map { it to Math.pow(random.nextDouble(), 1.0 / weight(it)) }
                .sortedByDescending { it.second }.map { it.first }

        val picked = LinkedHashSet<Song>()
        fun take(from: List<Song>, count: Int) {
            var taken = 0
            for (s in from) {
                if (taken >= count || picked.size >= SIZE) return
                if (s.id !in justHeard && picked.add(s)) taken++
            }
        }

        val loved = songs.filter { it.id in favorites || (trackPlays[it.id] ?: 0) >= 2 }
        take(weighted(loved) { 1.0 + (trackPlays[it.id] ?: 0) + if (it.id in favorites) 3 else 0 }, SIZE * 2 / 5)
        val moreOf = songs.filter { artistWeight(it) > 0 && it !in picked }
        take(weighted(moreOf) { artistWeight(it).toDouble() }, SIZE * 2 / 5)
        take(songs.filter { it.id !in lastPlayed }.shuffled(random), SIZE - picked.size)
        // A small library or a short history: the rest from anywhere.
        take(songs.shuffled(random), SIZE - picked.size)
        return spreadArtists(picked.toList().shuffled(random))
    }

    /** Reorders so one artist's tracks don't follow each other where that can be helped. */
    private fun spreadArtists(list: List<Song>): List<Song> {
        val left = list.toMutableList()
        val out = ArrayList<Song>(list.size)
        while (left.isNotEmpty()) {
            val prev = out.lastOrNull()?.artist?.lowercase()
            val i = left.indexOfFirst { it.artist.lowercase() != prev }.coerceAtLeast(0)
            out += left.removeAt(i)
        }
        return out
    }

    /** Days since the epoch in the local time zone: the mix changes at midnight. */
    fun dayNumber(nowMs: Long): Long = (nowMs + TimeZone.getDefault().getOffset(nowMs)) / DAY_MS
}
