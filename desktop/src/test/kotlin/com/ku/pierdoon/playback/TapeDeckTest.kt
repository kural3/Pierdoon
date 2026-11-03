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
 * Cassette mode on two tracks: one on side A, one on side B. Plays real files silently (volume 0)
 * through the real output, like [AudioEngineTest]. Skipped on machines without a sound device or
 * without music in the user's Music folder.
 */
class TapeDeckTest {

    private val music = File(System.getProperty("user.home"), "Music")
        .walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("mp3", "m4a", "flac", "opus", "ogg") }
        .sortedBy { it.name }.take(2).toList()

    private val windMs = cassetteSoundMs(cassetteWindSound())

    private fun songs() = music.map { f ->
        Song(Song.idFor(f), f.name, "", "", 0, MediaProbe.tags(f)!!.durationMs, path = f.path)
    }

    private fun onUi(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    private fun newPlayer(repeat: Int = Player.REPEAT_MODE_OFF): Pair<Player, List<Song>> {
        val songs = songs()
        lateinit var p: Player
        onUi {
            p = Player()
            p.volume = 0f
            p.tapeMode = true
            p.repeatMode = repeat
            p.setMediaItems(songs.map { MediaItem(it) }, 0, 0)
            p.prepare()
            p.play()
        }
        Thread.sleep(500)
        return p to songs
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

    @Test
    fun theCassetteIsTurnedOverAtTheEndOfSideA() {
        assumeTrue(music.size == 2 && AudioSystem.getMixerInfo().isNotEmpty())
        val (p, songs) = newPlayer()
        val tookMs = movesOnAfter(p, 1) { p.seekTo(songs[0].durationMs - 1_000) }
        assertTrue("moved on after $tookMs ms", tookMs in CASSETTE_SOUND_MS..CASSETTE_SOUND_MS + 2_500)
        Thread.sleep(800)
        var position = 0L
        var playing = false
        onUi {
            position = p.currentPosition
            playing = p.isPlaying
        }
        assertTrue("not playing the next track", playing)
        assertTrue("position $position", position in 100..1_500)
        onUi { p.release() }
    }

    @Test
    fun theTapeRewindsWhenSideBRunsIntoTheStartOfTheQueue() {
        assumeTrue(music.size == 2 && AudioSystem.getMixerInfo().isNotEmpty())
        val (p, songs) = newPlayer(Player.REPEAT_MODE_ALL)
        onUi { p.seekTo(1, 0) }
        Thread.sleep(300)
        val rewound = CountDownLatch(1)
        Thread {
            while (TapeMotion.current.value != TapeMotion.Rewind) Thread.sleep(10)
            rewound.countDown()
        }.apply { isDaemon = true }.start()
        val tookMs = movesOnAfter(p, 0) { p.seekTo(songs[1].durationMs - 1_000) }
        assertEquals(0, rewound.count)
        assertTrue("moved on after $tookMs ms", tookMs in CASSETTE_REWIND_MS..CASSETTE_REWIND_MS + 2_500)
        onUi { p.release() }
    }

    @Test
    fun nextSpinsTheTapeAndPlaysOn() {
        assumeTrue(music.size == 2 && AudioSystem.getMixerInfo().isNotEmpty())
        val (p, _) = newPlayer()
        var index = -1
        var playing = false
        onUi {
            p.seekToNext()
            index = p.currentMediaItemIndex
            playing = p.isPlaying
        }
        // The new track shows at once and stays "playing" while the tape spins to it.
        assertEquals(1, index)
        assertTrue(playing)
        Thread.sleep(windMs / 2)
        var position = -1L
        onUi { position = p.currentPosition }
        assertTrue("ran during the spin: $position", position < 100)
        Thread.sleep(windMs / 2 + 1_000)
        onUi { position = p.currentPosition }
        assertTrue("position $position", position in 500..1_500)
        onUi { p.release() }
    }

    @Test
    fun aPausedTapeSpinsTooAndStaysPaused() {
        assumeTrue(music.size == 2 && AudioSystem.getMixerInfo().isNotEmpty())
        val (p, _) = newPlayer()
        var playing = true
        onUi {
            p.pause()
            p.seekToNext()
        }
        Thread.sleep(windMs / 2)
        var motion = TapeMotion.Still
        onUi { motion = TapeMotion.current.value }
        assertEquals(TapeMotion.Forward, motion)
        Thread.sleep(windMs / 2 + 800)
        var position = -1L
        onUi {
            playing = p.isPlaying
            position = p.currentPosition
            motion = TapeMotion.current.value
        }
        assertTrue("started playing", !playing)
        assertEquals(TapeMotion.Still, motion)
        assertTrue("position $position", position < 100)
        onUi { p.release() }
    }

    @Test
    fun playPressedMidSpinStartsOnceTheTapeIsThere() {
        assumeTrue(music.size == 2 && AudioSystem.getMixerInfo().isNotEmpty())
        val (p, _) = newPlayer()
        onUi {
            p.pause()
            p.seekToNext()
        }
        Thread.sleep(windMs / 4)
        onUi { p.play() }
        Thread.sleep(windMs / 4)
        var position = -1L
        onUi { position = p.currentPosition }
        assertTrue("ran during the spin: $position", position < 100)
        Thread.sleep(windMs / 2 + 1_000)
        onUi { position = p.currentPosition }
        assertTrue("position $position", position in 500..1_500)
        onUi { p.release() }
    }

    @Test
    fun skippingWhileTheCassetteIsTurnedGoesThereAfterASpin() {
        assumeTrue(music.size == 2 && AudioSystem.getMixerInfo().isNotEmpty())
        val (p, songs) = newPlayer()
        onUi { p.seekTo(songs[0].durationMs - 300) }
        Thread.sleep(1_000)
        var index = -1
        onUi {
            p.seekToNext()
            index = p.currentMediaItemIndex
        }
        assertEquals(1, index)
        // The cancelled change mustn't move anything on when its time is up.
        Thread.sleep(CASSETTE_SOUND_MS + 500)
        var position = 0L
        onUi {
            index = p.currentMediaItemIndex
            position = p.currentPosition
        }
        assertEquals(1, index)
        assertTrue("position $position", position >= CASSETTE_SOUND_MS + 500 - windMs - 500)
        onUi { p.release() }
    }

    @Test
    fun theLastTrackEndsWithoutASound() {
        assumeTrue(music.size == 2 && AudioSystem.getMixerInfo().isNotEmpty())
        val (p, songs) = newPlayer()
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
