package com.ku.pierdoon.radio

import com.ku.pierdoon.data.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

class RadioTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun song(id: Long, title: String, artist: String, seconds: Int = 200) =
        Song(id, title, artist, "", 0, seconds * 1000L)

    private fun track(id: String, title: String, artist: String, seconds: Int? = 200) =
        RadioTrack(id.padEnd(11, '_'), title, artist, seconds)

    @Test
    fun aMixReadsAsTracksNamedLikeDownloads() {
        val json = """{"entries":[
            {"id":"dQw4w9WgXcQ","title":"Never Gonna Give You Up","channel":"Rick Astley - Topic","duration":213.0},
            {"id":"short","title":"Not a video"},
            {"id":"abcdefghijk","title":"Artist B - Song B","channel":"Some Uploads"}
        ]}"""
        val tracks = YouTubeMix.parse(json)
        assertEquals(2, tracks.size)
        assertEquals(RadioTrack("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley", 213), tracks[0])
        assertEquals("Artist B", tracks[1].artist)
        assertEquals("Song B", tracks[1].title)
        assertTrue(YouTubeMix.parse("not json").isEmpty())
    }

    @Test
    fun aVideoTitledWithItsProducerIsStillTheLibrarysTrack() {
        // As found on the phone: the file says "Простужен", the video "Простужен (prod. ttenoji)".
        val owned = song(1, "Простужен", "FORTUNA 812, madk1d", 95)
        val planner = RadioPlanner(listOf(owned), emptyList(), 0, null, novelty = 100)
        val added = planner.add(listOf(track("prost", "Простужен (prod. ttenoji)", "FORTUNA 812, madk1d", 95)))
        assertEquals(0, added)
        assertEquals(RadioPlanner.Pick.Known(owned), planner.next())
    }

    @Test
    fun aMixNamingOnlyOneOfTheArtistsIsStillTheLibrarysTrack() {
        val owned = song(1, "свалка", "тёмный принц, ashleyrossmith", 82)
        val planner = RadioPlanner(listOf(owned), emptyList(), 0, null, novelty = 100)
        assertEquals(0, planner.add(listOf(track("svalka", "свалка", "тёмный принц", null))))
        // Another artist's song of the same name is new.
        assertEquals(1, planner.add(listOf(track("other", "свалка", "кто-то другой", 82))))
    }

    @Test
    fun theVideoFoundForALibraryTrackIsNeverQueuedAsNew() {
        val owned = song(1, "Song", "Artist", 200)
        val planner = RadioPlanner(listOf(owned), emptyList(), 0, null, novelty = 100)
        planner.owns("found".padEnd(11, '_'))
        assertEquals(0, planner.add(listOf(track("found", "Totally different title", "Uploader"))))
    }

    @Test
    fun radioIdsAreNegativeAndStable() {
        val id = RadioTrack.idFor("dQw4w9WgXcQ")
        assertTrue(RadioTrack.isRadio(id))
        assertEquals(id, RadioTrack.idFor("dQw4w9WgXcQ"))
        assertFalse(RadioTrack.isRadio(5))
        assertFalse(RadioTrack.isRadio(-1))
    }

    @Test
    fun tracksAlreadyOwnedDislikedOrTooLongNeverComeAsNew() {
        val memory = RadioMemory.get(tmp.root)
        memory.dislike(track("bad", "Bad", "X"))
        val owned = song(1, "Owned Song", "Artist A")
        val planner = RadioPlanner(listOf(owned), emptyList(), 0, memory, novelty = 100)
        val added = planner.add(
            listOf(
                track("own", "Owned song", "artist a", 201),
                track("bad", "Bad", "X"),
                track("long", "One hour mix", "Y", 3600),
                track("short", "Intro", "Y", 20),
                track("good", "Good", "Z"),
                track("good", "Good", "Z"),
            ),
        )
        assertEquals(1, added)
        // The one owned plays as the library's own track, as soon as the library has a turn.
        assertEquals(RadioPlanner.Pick.New(track("good", "Good", "Z")), planner.next())
        planner.novelty = 0
        assertEquals(RadioPlanner.Pick.Known(owned), planner.next())
        assertNull(planner.next())
    }

    @Test
    fun noveltySetsTheShareOfNewTracks() {
        val library = (1L..40L).map { song(it, "Song $it", "Artist") }
        val planner = RadioPlanner(library, emptyList(), 0, null, novelty = 25, random = Random(1))
        planner.add((1..40).map { track("n$it", "New $it", "Other") })
        val picks = List(20) { planner.next() }
        assertEquals(5, picks.count { it is RadioPlanner.Pick.New })
        assertEquals(15, picks.filterIsInstance<RadioPlanner.Pick.Known>().map { it.song.id }.distinct().size)
    }

    @Test
    fun theSeedPlaysFirstAndItsArtistsLead() {
        val seed = song(1, "Seed", "Near")
        val library = listOf(seed, song(2, "Other", "Far"), song(3, "Also", "Near feat. Someone"))
        val planner = RadioPlanner(library, listOf(seed), 1, null, novelty = 0, random = Random(3))
        assertEquals(RadioPlanner.Pick.Known(seed), planner.next())
        assertEquals(RadioPlanner.Pick.Known(library[2]), planner.next())
        assertEquals(RadioPlanner.Pick.Known(library[1]), planner.next())
    }

    @Test
    fun anArtistTurnedDownTwiceIsLeftOut() {
        val memory = RadioMemory.get(tmp.newFolder())
        memory.dislike(track("a1", "One", "Loud Band"))
        assertFalse(memory.isDisliked(track("a3", "Three", "Loud Band")))
        memory.dislike(track("a2", "Two", "loud band"))
        assertTrue(memory.isDisliked(track("a3", "Three", "Loud Band")))
    }

    @Test
    fun dislikesAreKeptBetweenSessions() {
        val dir = tmp.newFolder()
        RadioMemory.get(dir).dislike(42L)
        // A fresh read of the file, not the one already open.
        val again = RadioMemory::class.java.getDeclaredConstructor(java.io.File::class.java)
            .apply { isAccessible = true }.newInstance(java.io.File(dir, "radio.json"))
        assertTrue(again.isDisliked(42L))
        again.clear()
        assertTrue(again.isEmpty())
    }

    private class FakeSource(
        val mixes: Map<String, List<RadioTrack>>,
        val broken: Set<String> = emptySet(),
        var online: Boolean = true,
    ) : RadioSource {
        val discarded = mutableListOf<String>()
        val kept = mutableListOf<String>()
        override suspend fun find(query: String) = if (online) mixes.values.first().first() else null
        override suspend fun mix(videoId: String) = if (online) mixes[videoId] ?: emptyList() else error("offline")
        override suspend fun fetch(track: RadioTrack): Song? {
            if (!online || track.videoId in broken) return null
            return Song(track.songId, track.title, track.artist, "", 0, 1000, path = "/cache/${track.videoId}")
        }
        override fun discard(track: RadioTrack) { discarded += track.videoId }
        override suspend fun keep(track: RadioTrack): Long { kept += track.videoId; return 1000 }
    }

    @Test
    fun aSessionFetchesNewTracksSkipsBrokenOnesAndCleansUp() = runBlocking {
        val a = track("a", "A", "X")
        val b = track("b", "B", "X")
        val c = track("c", "C", "X")
        val source = FakeSource(mapOf(a.videoId to listOf(a, b, c)), broken = setOf(b.videoId))
        val planner = RadioPlanner(emptyList(), emptyList(), 0, null, novelty = 100)
        val session = RadioSession(source, planner, null, "A", mixFrom = a.videoId)
        val first = session.next()!!
        assertEquals(a.songId, first.id)
        assertTrue(session.isFresh(first.id))
        assertEquals(c.songId, session.next()!!.id)
        assertNull(session.next())
        assertEquals(1000L, session.keep(first.id))
        session.stop()
        assertEquals(listOf(a.videoId), source.kept)
        assertEquals(listOf(c.videoId), source.discarded)
    }

    @Test
    fun aBiggerBufferKeepsMoreFiles() = runBlocking {
        val tracks = (1..12).map { track("t$it", "T$it", "X") }
        fun session(buffer: Int): Pair<RadioSession, FakeSource> {
            val source = FakeSource(mapOf(tracks[0].videoId to tracks))
            val planner = RadioPlanner(emptyList(), emptyList(), 0, null, novelty = 100)
            return RadioSession(source, planner, null, "", mixFrom = tracks[0].videoId).also { it.buffer = buffer } to source
        }
        val (one, oneSource) = session(1)
        repeat(10) { one.next() }
        // Ten fetched, four kept: as many as before the setting.
        assertEquals(6, oneSource.discarded.size)
        val (five, fiveSource) = session(5)
        repeat(10) { five.next() }
        // Five ahead must all be there, besides the one playing and those behind it.
        assertEquals(2, fiveSource.discarded.size)
    }

    @Test
    fun offlineItPlaysTheLibrary() = runBlocking {
        val library = (1L..5L).map { song(it, "Song $it", "Artist") }
        val source = FakeSource(mapOf("x".padEnd(11, '_') to emptyList()), online = false)
        val planner = RadioPlanner(library, emptyList(), 0, null, novelty = 100)
        val session = RadioSession(source, planner, null, "")
        val picks = List(5) { session.next() }
        assertTrue(picks.all { it != null && it.id in 1L..5L })
        assertTrue(session.state.value.offline)
    }
}
