package com.ku.pierdoon.data

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.history.Play
import org.json.JSONArray
import org.json.JSONObject

/** What a smart playlist's rule looks at. Each takes a text or a number, as [SmartField.numeric] says. */
enum class SmartField(val numeric: Boolean) {
    /** The artist tag contains the text. */
    Artist(false),
    Album(false),
    Title(false),
    /** Added to the library within the last N days. */
    AddedWithinDays(true),
    /** Played at least N times, ever. */
    PlayedAtLeast(true),
    /** Played at most N times, ever (0: never). */
    PlayedAtMost(true),
    /** Not played within the last N days (never played counts too). */
    NotPlayedForDays(true),
    /** Played within the last N days. */
    PlayedWithinDays(true),
    /** Longer than N minutes. */
    LongerThanMinutes(true),
    /** Shorter than N minutes. */
    ShorterThanMinutes(true),
    /** In the favourites; takes no value. */
    Favorite(false),
}

@Immutable
data class SmartRule(val field: SmartField, val text: String = "", val number: Int = 0, val negate: Boolean = false)

enum class SmartSort { Random, Title, Artist, RecentlyAdded, MostPlayed, LeastPlayed, RecentlyPlayed }

/**
 * A playlist that fills itself: the library's tracks that pass its rules, all of them or any,
 * sorted and cut to a length. Kept with the playlists, evaluated every time it's shown.
 */
@Immutable
data class SmartPlaylist(
    val id: Long,
    val name: String,
    val rules: List<SmartRule>,
    val matchAll: Boolean = true,
    val sort: SmartSort = SmartSort.Random,
    /** At most this many tracks; 0 = no limit. */
    val limit: Int = 0,
) {
    /** The tracks of [songs] this playlist holds now. Random order changes once a day, not on every look. */
    fun evaluate(songs: List<Song>, plays: List<Play>, favorites: Set<Long>, nowMs: Long): List<Song> {
        val facts = PlayFacts.of(plays)
        fun passes(song: Song, r: SmartRule): Boolean {
            val count = facts.count[song.id] ?: 0
            val last = facts.last[song.id]
            val result = when (r.field) {
                SmartField.Artist -> song.artist.contains(r.text.trim(), ignoreCase = true)
                SmartField.Album -> song.album.contains(r.text.trim(), ignoreCase = true)
                SmartField.Title -> song.title.contains(r.text.trim(), ignoreCase = true)
                SmartField.AddedWithinDays -> song.dateAdded * 1000 >= nowMs - r.number * DAY_MS
                SmartField.PlayedAtLeast -> count >= r.number
                SmartField.PlayedAtMost -> count <= r.number
                SmartField.NotPlayedForDays -> last == null || last < nowMs - r.number * DAY_MS
                SmartField.PlayedWithinDays -> last != null && last >= nowMs - r.number * DAY_MS
                SmartField.LongerThanMinutes -> song.durationMs > r.number * MINUTE_MS
                SmartField.ShorterThanMinutes -> song.durationMs in 1 until r.number * MINUTE_MS
                SmartField.Favorite -> song.id in favorites
            }
            return result != r.negate
        }
        val usable = rules.filter { !(!it.field.numeric && it.field != SmartField.Favorite && it.text.isBlank()) }
        val picked = songs.filter { s ->
            when {
                usable.isEmpty() -> true
                matchAll -> usable.all { passes(s, it) }
                else -> usable.any { passes(s, it) }
            }
        }
        val sorted = when (sort) {
            SmartSort.Random -> picked.shuffled(java.util.Random(id xor (nowMs / DAY_MS)))
            SmartSort.Title -> picked.sortedBy { it.title.lowercase() }
            SmartSort.Artist -> picked.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() }))
            SmartSort.RecentlyAdded -> picked.sortedByDescending { it.dateAdded }
            SmartSort.MostPlayed -> picked.sortedByDescending { facts.count[it.id] ?: 0 }
            SmartSort.LeastPlayed -> picked.sortedBy { facts.count[it.id] ?: 0 }
            SmartSort.RecentlyPlayed -> picked.sortedByDescending { facts.last[it.id] ?: 0L }
        }
        return if (limit > 0) sorted.take(limit) else sorted
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("all", matchAll)
        .put("sort", sort.name)
        .put("limit", limit)
        .put("rules", JSONArray(rules.map { r ->
            JSONObject().put("field", r.field.name).put("text", r.text).put("number", r.number).put("not", r.negate)
        }))

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val MINUTE_MS = 60_000L

        /** Null when it isn't one; rules of fields this version doesn't know are left out. */
        fun fromJson(o: JSONObject): SmartPlaylist? = runCatching {
            val rules = o.optJSONArray("rules") ?: JSONArray()
            SmartPlaylist(
                id = o.getLong("id"),
                name = o.getString("name"),
                matchAll = o.optBoolean("all", true),
                sort = SmartSort.entries.firstOrNull { it.name == o.optString("sort") } ?: SmartSort.Random,
                limit = o.optInt("limit", 0).coerceAtLeast(0),
                rules = List(rules.length()) { rules.getJSONObject(it) }.mapNotNull { r ->
                    val field = SmartField.entries.firstOrNull { it.name == r.optString("field") } ?: return@mapNotNull null
                    SmartRule(field, r.optString("text"), r.optInt("number"), r.optBoolean("not"))
                },
            )
        }.getOrNull()

        /** Ready-made ones to start from. */
        fun presets(nowMs: Long): List<SmartPlaylist> = listOf(
            SmartPlaylist(nowMs, "", listOf(SmartRule(SmartField.AddedWithinDays, number = 30)), sort = SmartSort.RecentlyAdded),
            SmartPlaylist(nowMs + 1, "", listOf(SmartRule(SmartField.PlayedAtLeast, number = 5)), sort = SmartSort.MostPlayed, limit = 50),
            SmartPlaylist(nowMs + 2, "", listOf(SmartRule(SmartField.PlayedAtMost, number = 0)), limit = 50),
            SmartPlaylist(
                nowMs + 3, "",
                listOf(SmartRule(SmartField.PlayedAtLeast, number = 3), SmartRule(SmartField.NotPlayedForDays, number = 90)),
                sort = SmartSort.Random, limit = 50,
            ),
        )
    }
}

/** How often and how lately each track was played, counted once per evaluation. */
private class PlayFacts(val count: Map<Long, Int>, val last: Map<Long, Long>) {
    companion object {
        private var cachedFor: List<Play>? = null
        private var cached: PlayFacts? = null

        /** The same log is asked about again and again (each smart playlist, each recomposition). */
        @Synchronized
        fun of(plays: List<Play>): PlayFacts {
            if (cachedFor === plays) cached?.let { return it }
            val count = HashMap<Long, Int>()
            val last = HashMap<Long, Long>()
            for (p in plays) {
                count.merge(p.songId, 1, Int::plus)
                last.merge(p.songId, p.atMs, ::maxOf)
            }
            return PlayFacts(count, last).also { cachedFor = plays; cached = it }
        }
    }
}
