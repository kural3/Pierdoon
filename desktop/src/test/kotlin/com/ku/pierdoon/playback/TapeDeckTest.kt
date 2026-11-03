package com.ku.pierdoon.playback

import com.ku.pierdoon.data.Song
import com.ku.pierdoon.platform.MediaProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioSystem
import javax.swing.SwingUtilities

/**
 * Cassette mode on real files, played silently (volume 0) through the real output, like
 * [AudioEngineTest]. Skipped on machines without a sound device or without four tracks in the
 * user's Music folder.
 */
class TapeDeckTest {

    private val music = File(System.getProperty("user.home"), "Music")
        .walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("mp3", "m4a", "flac", "opus", "ogg") }
        .sortedBy { it.name }.take(4).toList()

    private val windMs = cassetteSoundMs(cassetteWindSound())

    private val songs by lazy {
        music.map { f -> Song(Song.idFor(f), f.name, "", "", 0, MediaProbe.tags(f)!!.durationMs, path = f.path) }
    }

    private fun canRun() = music.size == 4 && AudioSystem.getMixerInfo().isNotEmpty()

    private val sideBStart by lazy { tapeSides(songs.map { it.durationMs }, 0).sideBStart }

    /** Two tracks one after the other on the same side, for spinning between them. */
    private val sameSide by lazy { if (sideBStart >= 2) 0 to 1 else 1 to 2 }

    private fun onUi(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    /** A player on the tape of [count] tracks, playing from [start]. */
    private fun newPlayer(count: Int = 4, start: Int = 0, repeat: Int = Player.REPEAT_MODE_OFF): Player {
        lateinit var p: Player
        onUi {
            p = Player()
            p.volume = 0f
            p.tapeMode = true
            p.repeatMode = repeat
            p.setMediaItems(songs.take(count).map { MediaItem(it) }, start, 0)
            p.prepare()
            p.play()
        }
        Thread.sleep(500)
        return p
    }

    private fun movesOnAfter(p: Player, toIndex: Int, start: () -> Unit): Long {
        val moved = CountDownLatch(1)
        onUi {
            p.addListener { player, events ->
                if (Player.Event.Transition in events && player.currentMediaItemIndex == toIndex) moved.countDown()
            }
            start()
        }
        val at = System.nanoTime()
        assertTrue("never moved on", moved.await(15, TimeUnit.SECONDS))
        return (System.nanoTime() - at) / 1_000_000
    }

    private fun <T> read(get: Player.() -> T, p: Player): T {
        var value: T? = null
        onUi { value = p.get() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    @Test
    fun theCassetteIsTurnedOverAtTheEndOfSideA() {
        assumeTrue(canRun())
        val p = newPlayer(count = 2)
        val tookMs = movesOnAfter(p, 1) { p.seekTo(songs[0].durationMs - 1_000) }
        assertTrue("moved on after $tookMs ms", tookMs in CASSETTE_SOUND_MS..CASSETTE_SOUND_MS + 2_500)
        Thread.sleep(800)
        assertTrue("not playing the next track", read({ isPlaying }, p))
        val position = read({ currentPosition }, p)
        assertTrue("position $position", position in 100..1_500)
        onUi { p.release() }
    }

    @Test
    fun theTapeRewindsWhenSideBRunsIntoTheStartOfTheQueue() {
        assumeTrue(canRun())
        val p = newPlayer(count = 2, repeat = Player.REPEAT_MODE_ALL)
        onUi { p.seekTo(1, 0) }
        Thread.sleep(300)
        val rewound = CountDownLatch(1)
        Thread {
            while (TapeMotion.current.value != TapeMotion.Rewind) Thread.sleep(10)
            rewound.countDown()
        }.apply { isDaemon = true }.start()
        val tookMs = movesOnAfter(p, 0) { p.seekTo(songs[1].durationMs - 1_000) }
        assertEquals(0, rewound.count)
        // Side B is the second track alone: the rewind takes as long as a side that long does.
        val rewindMs = cassetteRewindMs(songs[1].durationMs)
        assertTrue("moved on after $tookMs ms, rewind $rewindMs", tookMs in rewindMs..rewindMs + 2_500)
        onUi { p.release() }
    }

    @Test
    fun nextSpinsTheTapeAndPlaysOn() {
        assumeTrue(canRun())
        val (from, to) = sameSide
        val p = newPlayer(start = from)
        onUi { p.seekToNext() }
        // The new track shows at once and stays "playing" while the tape spins to it.
        assertEquals(to, read({ currentMediaItemIndex }, p))
        assertTrue(read({ isPlaying }, p))
        assertEquals(TapeMotion.Forward, TapeMotion.current.value)
        Thread.sleep(windMs / 2)
        val during = read({ currentPosition }, p)
        assertTrue("ran during the spin: $during", during < 100)
        Thread.sleep(windMs / 2 + 1_000)
        val after = read({ currentPosition }, p)
        assertTrue("position $after", after in 500..1_500)
        onUi { p.release() }
    }

    @Test
    fun nextAcrossTheMiddleTurnsTheCassetteOver() {
        assumeTrue(canRun())
        val p = newPlayer(start = sideBStart - 1)
        val tookMs = movesOnAfter(p, sideBStart) { p.seekToNext() }
        assertTrue("moved on after $tookMs ms", tookMs in CASSETTE_SOUND_MS - 100..CASSETTE_SOUND_MS + 2_500)
        Thread.sleep(800)
        assertTrue("not playing", read({ isPlaying }, p))
        val position = read({ currentPosition }, p)
        assertTrue("position $position", position in 100..1_500)
        onUi { p.release() }
    }

    @Test
    fun aPausedTapeSpinsTooAndStaysPaused() {
        assumeTrue(canRun())
        val p = newPlayer(start = sameSide.first)
        onUi {
            p.pause()
            p.seekToNext()
        }
        Thread.sleep(windMs / 2)
        assertEquals(TapeMotion.Forward, TapeMotion.current.value)
        Thread.sleep(windMs / 2 + 800)
        assertTrue("started playing", !read({ isPlaying }, p))
        assertEquals(TapeMotion.Still, TapeMotion.current.value)
        val position = read({ currentPosition }, p)
        assertTrue("position $position", position < 100)
        onUi { p.release() }
    }

    @Test
    fun aPausedCassetteIsTurnedOverAndStaysPaused() {
        assumeTrue(canRun())
        val p = newPlayer(start = sideBStart - 1)
        onUi { p.pause() }
        val tookMs = movesOnAfter(p, sideBStart) { p.seekToNext() }
        assertTrue("moved on after $tookMs ms", tookMs >= CASSETTE_SOUND_MS - 100)
        Thread.sleep(500)
        assertTrue("started playing", !read({ isPlaying }, p))
        onUi { p.release() }
    }

    @Test
    fun playPressedMidSpinStartsOnceTheTapeIsThere() {
        assumeTrue(canRun())
        val p = newPlayer(start = sameSide.first)
        onUi {
            p.pause()
            p.seekToNext()
        }
        Thread.sleep(windMs / 4)
        onUi { p.play() }
        Thread.sleep(windMs / 4)
        val during = read({ currentPosition }, p)
        assertTrue("ran during the spin: $during", during < 100)
        Thread.sleep(windMs / 2 + 1_000)
        val after = read({ currentPosition }, p)
        assertTrue("position $after", after in 500..1_500)
        onUi { p.release() }
    }

    @Test
    fun nextWhileTheCassetteIsTurnedGoesRightThere() {
        assumeTrue(canRun())
        val p = newPlayer(count = 2)
        onUi { p.seekTo(songs[0].durationMs - 300) }
        Thread.sleep(1_000)
        assertEquals(TapeMotion.Flip, TapeMotion.current.value)
        onUi { p.seekToNext() }
        assertEquals(1, read({ currentMediaItemIndex }, p))
        // The cancelled turn mustn't move anything on when its time is up.
        Thread.sleep(CASSETTE_SOUND_MS + 500)
        assertEquals(1, read({ currentMediaItemIndex }, p))
        val position = read({ currentPosition }, p)
        assertTrue("position $position", position >= CASSETTE_SOUND_MS)
        onUi { p.release() }
    }

    @Test
    fun theLastTrackEndsWithoutASound() {
        assumeTrue(canRun())
        val p = newPlayer(count = 2)
        val ended = CountDownLatch(1)
        onUi {
            p.addListener { player, _ -> if (player.playbackState == Player.STATE_ENDED) ended.countDown() }
            p.seekTo(1, songs[1].durationMs - 500)
        }
        val at = System.nanoTime()
        assertTrue("never ended", ended.await(10, TimeUnit.SECONDS))
        val tookMs = (System.nanoTime() - at) / 1_000_000
        assertTrue("ended after $tookMs ms", tookMs < CASSETTE_SOUND_MS)
        onUi { p.release() }
    }
}
