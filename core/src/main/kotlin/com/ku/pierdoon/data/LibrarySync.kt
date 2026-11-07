package com.ku.pierdoon.data

import androidx.compose.runtime.Immutable
import com.ku.pierdoon.data.history.Play
import org.json.JSONArray
import org.json.JSONObject

/** A playlist as it travels between devices or into a backup: its tracks by the sender's ids. */
@Immutable
data class SyncPlaylist(val id: Long, val name: String, val songIds: List<Long>) {
    companion object
}

/**
 * One device's favourites, playlists, smart playlists and plays, with the tags and length of every
 * track they name: song ids differ from device to device (and from one phone to the next), so
 * tracks are recognised by [TrackKey] on the other side.
 */
@Immutable
data class LibrarySnapshot(
    val tracks: Map<Long, TrackKey>,
    val favorites: Set<Long>,
    val playlists: List<SyncPlaylist>,
    val smart: List<SmartPlaylist>,
    val plays: List<Play>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("tracks", JSONArray(tracks.map { (id, k) ->
            JSONArray().put(id).put(k.title).put(k.artist).put(k.durationMs)
        }))
        .put("favorites", JSONArray(favorites.toList()))
        .put("playlists", JSONArray(playlists.map { it.toJson() }))
        .put("smart", JSONArray(smart.map { it.toJson() }))
        .put("plays", JSONArray(plays.map { JSONArray().put(it.songId).put(it.atMs).put(it.listenedMs) }))

    companion object {
        /**
         * The snapshot of a library: [songs] it has now, and what [user] and [plays] say about
         * them. Only the tracks something names are listed, unless [allTracks]: a sync matches
         * every track, since a favourite on one side may be a track nothing names on the other.
         */
        fun of(songs: Collection<Song>, user: UserData, plays: List<Play>, allTracks: Boolean = false): LibrarySnapshot {
            val byId = songs.associateBy { it.id }
            val named = HashSet<Long>()
            named += user.favorites
            user.playlists.forEach { named += it.songIds }
            plays.forEach { named += it.songId }
            return LibrarySnapshot(
                tracks = (if (allTracks) byId.keys else named).mapNotNull { id ->
                    byId[id]?.let { id to TrackKey(it.title, it.artist, it.durationMs) }
                }.toMap(),
                favorites = user.favorites.filterTo(HashSet()) { it in byId },
                playlists = user.playlists.map { p -> SyncPlaylist(p.id, p.name, p.songIds.filter { it in byId }) },
                smart = user.smartPlaylists,
                plays = plays.filter { it.songId in byId },
            )
        }

        fun fromJson(o: JSONObject): LibrarySnapshot {
            val tracks = HashMap<Long, TrackKey>()
            o.optJSONArray("tracks")?.let { a ->
                for (i in 0 until a.length()) {
                    val t = a.getJSONArray(i)
                    tracks[t.getLong(0)] = TrackKey(t.getString(1), t.getString(2), t.getLong(3))
                }
            }
            val plays = o.optJSONArray("plays")?.let { a ->
                List(a.length()) { i -> a.getJSONArray(i).let { Play(it.getLong(0), it.getLong(1), it.getLong(2)) } }
            }.orEmpty()
            return LibrarySnapshot(
                tracks = tracks,
                favorites = o.optJSONArray("favorites")?.longs()?.toSet().orEmpty(),
                playlists = o.optJSONArray("playlists")?.objects()?.map(SyncPlaylist::fromJson).orEmpty(),
                smart = o.optJSONArray("smart")?.objects()?.mapNotNull(SmartPlaylist::fromJson).orEmpty(),
                plays = plays,
            )
        }
    }
}

/** What a sync or a restored backup changes on one side, in that side's song ids. */
@Immutable
data class LibraryChanges(
    val favoritesAdd: Set<Long> = emptySet(),
    val favoritesRemove: Set<Long> = emptySet(),
    val playlistsPut: List<SyncPlaylist> = emptyList(),
    val playlistsDelete: Set<Long> = emptySet(),
    val smartPut: List<SmartPlaylist> = emptyList(),
    val smartDelete: Set<Long> = emptySet(),
    val plays: List<Play> = emptyList(),
) {
    val isEmpty: Boolean
        get() = favoritesAdd.isEmpty() && favoritesRemove.isEmpty() && playlistsPut.isEmpty() &&
            playlistsDelete.isEmpty() && smartPut.isEmpty() && smartDelete.isEmpty() && plays.isEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("favoritesAdd", JSONArray(favoritesAdd.toList()))
        .put("favoritesRemove", JSONArray(favoritesRemove.toList()))
        .put("playlistsPut", JSONArray(playlistsPut.map { it.toJson() }))
        .put("playlistsDelete", JSONArray(playlistsDelete.toList()))
        .put("smartPut", JSONArray(smartPut.map { it.toJson() }))
        .put("smartDelete", JSONArray(smartDelete.toList()))
        .put("plays", JSONArray(plays.map { JSONArray().put(it.songId).put(it.atMs).put(it.listenedMs) }))

    companion object {
        fun fromJson(o: JSONObject) = LibraryChanges(
            favoritesAdd = o.optJSONArray("favoritesAdd")?.longs()?.toSet().orEmpty(),
            favoritesRemove = o.optJSONArray("favoritesRemove")?.longs()?.toSet().orEmpty(),
            playlistsPut = o.optJSONArray("playlistsPut")?.objects()?.map(SyncPlaylist::fromJson).orEmpty(),
            playlistsDelete = o.optJSONArray("playlistsDelete")?.longs()?.toSet().orEmpty(),
            smartPut = o.optJSONArray("smartPut")?.objects()?.mapNotNull(SmartPlaylist::fromJson).orEmpty(),
            smartDelete = o.optJSONArray("smartDelete")?.longs()?.toSet().orEmpty(),
            plays = o.optJSONArray("plays")?.let { a ->
                List(a.length()) { i -> a.getJSONArray(i).let { Play(it.getLong(0), it.getLong(1), it.getLong(2)) } }
            }.orEmpty(),
        )
    }
}

/**
 * What both sides agreed on at the end of the last sync, in the syncing side's ids: favourites
 * and playlist tracks only among the tracks both had. Telling "removed here" from "never there"
 * needs it: without one, everything is put together and nothing removed.
 */
@Immutable
data class SyncBase(
    val favorites: Set<Long>,
    val playlists: Map<Long, SyncPlaylist>,
    val smart: Map<Long, SmartPlaylist>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("favorites", JSONArray(favorites.toList()))
        .put("playlists", JSONArray(playlists.values.map { it.toJson() }))
        .put("smart", JSONArray(smart.values.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject) = SyncBase(
            favorites = o.optJSONArray("favorites")?.longs()?.toSet().orEmpty(),
            playlists = o.optJSONArray("playlists")?.objects()?.map(SyncPlaylist::fromJson).orEmpty().associateBy { it.id },
            smart = o.optJSONArray("smart")?.objects()?.mapNotNull(SmartPlaylist::fromJson).orEmpty().associateBy { it.id },
        )
    }
}

@Immutable
data class SyncOutcome(val local: LibraryChanges, val remote: LibraryChanges, val base: SyncBase, val matched: Int)

/**
 * Brings two libraries together: a two-way sync between the phone and the computer (the phone
 * does the merging and keeps the [SyncBase]), or a backup restored into a library (no base: only
 * what's missing is added).
 *
 * A change made on one side since the last sync wins over the other side's unchanged state;
 * where both changed the same thing, this side does. Favourites and playlist tracks are only
 * compared among tracks both sides have; the others are left as they are on each side.
 * Plays are put together, each kept once.
 */
object LibrarySync {

    fun merge(local: LibrarySnapshot, remote: LibrarySnapshot, base: SyncBase?): SyncOutcome {
        val toRemote = matchTracks(local.tracks, remote.tracks)
        val toLocal = toRemote.entries.associate { (l, r) -> r to l }

        // Favourites, among the tracks both have.
        val favAddLocal = HashSet<Long>()
        val favRemoveLocal = HashSet<Long>()
        val favAddRemote = HashSet<Long>()
        val favRemoveRemote = HashSet<Long>()
        val newBaseFavorites = HashSet<Long>()
        for ((l, r) in toRemote) {
            val inLocal = l in local.favorites
            val inRemote = r in remote.favorites
            val inBase = base?.favorites?.contains(l) == true
            val result = when {
                inLocal == inRemote -> inLocal
                inBase == inLocal -> inRemote // changed only on the other side
                else -> inLocal
            }
            if (result != inLocal) (if (result) favAddLocal else favRemoveLocal) += l
            if (result != inRemote) (if (result) favAddRemote else favRemoveRemote) += r
            if (result) newBaseFavorites += l
        }

        // Playlists, by id: a playlist copied over keeps its id on the other side.
        val localById = local.playlists.associateBy { it.id }
        val remoteById = remote.playlists.associateBy { it.id }
        val putLocal = ArrayList<SyncPlaylist>()
        val putRemote = ArrayList<SyncPlaylist>()
        val deleteLocal = HashSet<Long>()
        val deleteRemote = HashSet<Long>()
        val newBasePlaylists = HashMap<Long, SyncPlaylist>()
        val ids = LinkedHashSet<Long>().apply { addAll(localById.keys); addAll(remoteById.keys) }
        for (id in ids) {
            val lp = localById[id]
            val rp = remoteById[id]
            val bp = base?.playlists?.get(id)
            when {
                lp != null && rp != null -> {
                    val name = if (bp != null && lp.name == bp.name) rp.name else lp.name
                    val baseSongs = bp?.songIds?.toHashSet().orEmpty()
                    val remoteSongs = rp.songIds.mapNotNull { toLocal[it] }
                    val remoteSet = remoteSongs.toHashSet()
                    val kept = lp.songIds.filter { s -> s !in toRemote || s in remoteSet || s !in baseSongs }
                    val keptSet = kept.toHashSet()
                    val added = remoteSongs.filter { it !in keptSet && it !in baseSongs }.distinct()
                    val result = kept + added
                    if (result != lp.songIds || name != lp.name) putLocal += SyncPlaylist(id, name, result)
                    // The other side's list: the result in its ids, then its tracks this side lacks.
                    val remoteResult = result.mapNotNull { toRemote[it] } + rp.songIds.filter { it !in toLocal }
                    if (remoteResult != rp.songIds || name != rp.name) putRemote += SyncPlaylist(id, name, remoteResult)
                    newBasePlaylists[id] = SyncPlaylist(id, name, result.filter { it in toRemote })
                }
                lp != null -> if (bp != null) {
                    deleteLocal += id
                } else {
                    putRemote += SyncPlaylist(id, lp.name, lp.songIds.mapNotNull { toRemote[it] })
                    newBasePlaylists[id] = SyncPlaylist(id, lp.name, lp.songIds.filter { it in toRemote })
                }
                rp != null -> if (bp != null) {
                    deleteRemote += id
                } else {
                    val songs = rp.songIds.mapNotNull { toLocal[it] }
                    putLocal += SyncPlaylist(id, rp.name, songs)
                    newBasePlaylists[id] = SyncPlaylist(id, rp.name, songs)
                }
            }
        }

        // Smart playlists: rules only, no tracks to match.
        val localSmart = local.smart.associateBy { it.id }
        val remoteSmart = remote.smart.associateBy { it.id }
        val smartPutLocal = ArrayList<SmartPlaylist>()
        val smartPutRemote = ArrayList<SmartPlaylist>()
        val smartDeleteLocal = HashSet<Long>()
        val smartDeleteRemote = HashSet<Long>()
        val newBaseSmart = HashMap<Long, SmartPlaylist>()
        for (id in LinkedHashSet(localSmart.keys + remoteSmart.keys)) {
            val lp = localSmart[id]
            val rp = remoteSmart[id]
            val bp = base?.smart?.get(id)
            when {
                lp != null && rp != null -> {
                    val result = if (lp == rp || bp != lp) lp else rp
                    if (result != lp) smartPutLocal += result
                    if (result != rp) smartPutRemote += result
                    newBaseSmart[id] = result
                }
                lp != null -> if (bp != null) smartDeleteLocal += id else { smartPutRemote += lp; newBaseSmart[id] = lp }
                rp != null -> if (bp != null) smartDeleteRemote += id else { smartPutLocal += rp; newBaseSmart[id] = rp }
            }
        }

        // Plays: each side gets the other's it doesn't have, in its own ids.
        val localPlays = local.plays.mapTo(HashSet()) { it.songId to it.atMs }
        val remotePlays = remote.plays.mapTo(HashSet()) { it.songId to it.atMs }
        val playsToLocal = remote.plays.mapNotNull { p ->
            toLocal[p.songId]?.let { p.copy(songId = it) }?.takeIf { localPlays.add(it.songId to it.atMs) }
        }
        val playsToRemote = local.plays.mapNotNull { p ->
            toRemote[p.songId]?.let { p.copy(songId = it) }?.takeIf { remotePlays.add(it.songId to it.atMs) }
        }

        return SyncOutcome(
            local = LibraryChanges(favAddLocal, favRemoveLocal, putLocal, deleteLocal, smartPutLocal, smartDeleteLocal, playsToLocal),
            remote = LibraryChanges(favAddRemote, favRemoveRemote, putRemote, deleteRemote, smartPutRemote, smartDeleteRemote, playsToRemote),
            base = SyncBase(newBaseFavorites, newBasePlaylists, newBaseSmart),
            matched = toRemote.size,
        )
    }

    /**
     * Pairs this side's tracks with the other's that look the same ([TrackMatcher]), each of the
     * other's used once. The same id on both sides with the same tags is taken first: a backup
     * restored on the device it came from.
     */
    fun matchTracks(local: Map<Long, TrackKey>, remote: Map<Long, TrackKey>): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        val used = HashSet<Long>()
        for ((id, key) in local) {
            if (remote[id] == key) { out[id] = id; used += id }
        }
        val idsByKey = HashMap<TrackKey, ArrayDeque<Long>>()
        for ((id, key) in remote) if (id !in used) idsByKey.getOrPut(key) { ArrayDeque() } += id
        val matcher = TrackMatcher(idsByKey.keys)
        for ((id, key) in local) {
            if (id in out) continue
            val found = matcher.find(key) ?: continue
            val candidates = idsByKey[found] ?: continue
            val r = candidates.removeFirstOrNull() ?: continue
            out[id] = r
        }
        return out
    }
}

private fun SyncPlaylist.toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("songs", JSONArray(songIds))

private fun SyncPlaylist.Companion.fromJson(o: JSONObject) =
    SyncPlaylist(o.getLong("id"), o.getString("name"), o.optJSONArray("songs")?.longs().orEmpty())

private fun JSONArray.longs(): List<Long> = List(length()) { getLong(it) }
private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
