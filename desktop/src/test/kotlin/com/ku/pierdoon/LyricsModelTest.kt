package com.ku.pierdoon

import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.lyrics.Lyrics
import com.ku.pierdoon.data.lyrics.LyricsOffsets
import com.ku.pierdoon.data.lyrics.LyricsRepository
import com.ku.pierdoon.playback.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The lyrics side both apps share; no network: tracks have the user's own lyrics or none. */
class LyricsModelTest {

    @get:Rule val dir = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val playing = MutableStateFlow<NowPlaying?>(null)
    private val songs = mapOf(
        1L to Song(1, "Road", "Band", "Album", 10, 200_000),
        2L to Song(2, "Sea", "", "Album", 10, 180_000),
    )
    private val offsets = mutableMapOf<String, Long>()
    private val store = object : LyricsOffsets.Store {
        override fun get(key: String) = offsets[key]
        override fun set(key: String, value: Long?) { if (value == null) offsets.remove(key) else offsets[key] = value }
    }
    private val repository by lazy { LyricsRepository(dir.newFolder("cache"), "test", dir.newFolder("files")) }
    private val model by lazy {
        LyricsModel(
            scope = scope,
            repository = repository,
            offsets = LyricsOffsets(store),
            nowPlaying = playing,
            current = { playing.value to 200_000L },
            song = { songs[it] },
            networkAllowed = { false },
            stopTimeoutMs = 0,
        )
    }

    @After fun stop() = scope.cancel()

    private fun play(id: Long) {
        playing.value = songs.getValue(id).let { NowPlaying(it.id, it.albumId, it.title, it.artist) }
    }

    private fun lyricsBecome(check: (LyricsState) -> Boolean): LyricsState = runBlocking {
        withTimeout(5_000) { model.lyrics.first(check) }
    }

    @Test
    fun nothingIsLookedUpWhileThePanelIsClosed() {
        play(1)
        assertEquals(LyricsState.Idle, lyricsBecome { true })
    }

    @Test
    fun withoutNetworkAndNothingKeptItSaysWifiOnly() {
        play(1)
        model.setVisible(true)
        assertEquals(LyricsState.WifiOnly, lyricsBecome { it == LyricsState.WifiOnly })
    }

    @Test
    fun ownLyricsShowAndGoAway() {
        play(1)
        model.setVisible(true)
        model.saveOwn(1, "first line\nsecond line")
        val ready = lyricsBecome { it is LyricsState.Ready } as LyricsState.Ready
        assertTrue(ready.own)
        assertEquals("first line\nsecond line", runBlocking { model.own(1) })

        model.removeOwn(1)
        lyricsBecome { it == LyricsState.WifiOnly }
        assertEquals(null, runBlocking { model.own(1) })
    }

    @Test
    fun anotherTrackGetsItsOwnLyrics() {
        runBlocking { repository.saveOwn(2, "the sea") }
        play(1)
        model.setVisible(true)
        lyricsBecome { it == LyricsState.WifiOnly }
        play(2)
        val ready = lyricsBecome { it is LyricsState.Ready } as LyricsState.Ready
        assertTrue(ready.lyrics != Lyrics.NotFound)
    }

    @Test
    fun offsetsBelongToTheTrackThatPlays() {
        play(1)
        model.shift(250)
        model.shift(250)
        assertEquals(500L, model.offsetFor(1))
        assertEquals(0L, model.offsetFor(2))
        runBlocking { withTimeout(5_000) { model.offset.first { it == 500L } } }

        play(2)
        runBlocking { withTimeout(5_000) { model.offset.first { it == 0L } } }

        play(1)
        model.resetOffset()
        assertEquals(0L, model.offsetFor(1))
        assertTrue(offsets.isEmpty())
    }

    @Test
    fun aRemovedTrackLosesItsOffset() {
        play(1)
        model.shift(1_000)
        model.dropOffset(1)
        assertEquals(0L, model.offsetFor(1))
    }

    @Test
    fun searchStartsFromTheLibraryTags() {
        assertEquals("", model.searchText())
        play(1)
        assertEquals("Band Road", model.searchText())
        // No artist: just the title, not a stray space.
        play(2)
        assertEquals("Sea", model.searchText())
    }
}
