package com.ku.pierdoon.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioSystem

/**
 * Plays real files silently (volume 0) through the real output. Skipped on machines without
 * a sound device or without music in the user's Music folder.
 */
class AudioEngineTest {

    private val music = File(System.getProperty("user.home"), "Music")
        .walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("mp3", "m4a", "flac", "opus", "ogg") }
        .sortedBy { it.name }.toList()

    private fun engine(started: (Long) -> Unit = {}, ended: (Long) -> Unit = {}, error: (Throwable) -> Unit = {}) =
        AudioEngine(object : AudioEngine.Listener {
            override fun onTrackStarted(token: Long) = started(token)
            override fun onEnded(token: Long) = ended(token)
            override fun onError(token: Long, error: Throwable) = error(error)
        }).apply { volume = 0f }

    @Test
    fun playsSeeksAndPauses() {
        assumeTrue(music.isNotEmpty() && AudioSystem.getMixerInfo().isNotEmpty())
        val startedLatch = CountDownLatch(1)
        var failure: Throwable? = null
        val e = engine(started = { startedLatch.countDown() }, error = { failure = it })
        e.load(music.first(), token = 1, startMs = 0, play = true)
        assertTrue("never started: $failure", startedLatch.await(10, TimeUnit.SECONDS))
        Thread.sleep(1500)
        val p1 = assertNotNull(e.position()).let { e.position()!! }
        assertEquals(1L, p1.first)
        assertTrue("position ${p1.second}", p1.second in 900..3000)

        e.seek(20_000)
        Thread.sleep(600)
        val p2 = e.position()!!
        assertTrue("after seek ${p2.second}", p2.second in 20_000..21_500)

        e.pause()
        Thread.sleep(300)
        val paused = e.position()!!.second
        Thread.sleep(700)
        assertTrue("moved while paused", e.position()!!.second - paused < 100)
        e.release()
    }

    @Test
    fun switchesToNextTrackWithoutGap() {
        assumeTrue(music.size >= 2 && AudioSystem.getMixerInfo().isNotEmpty())
        val started = mutableListOf<Long>()
        val second = CountDownLatch(1)
        val e = engine(started = { synchronized(started) { started += it }; if (it == 2L) second.countDown() })
        e.load(music[0], token = 1, startMs = 0, play = true)
        e.setNext(music[1], token = 2)
        // Jump to the last two seconds so the switch comes quickly.
        Thread.sleep(500)
        val duration = com.ku.pierdoon.platform.MediaProbe.tags(music[0])!!.durationMs
        e.seek(duration - 2_000)
        assertTrue("next never started", second.await(10, TimeUnit.SECONDS))
        Thread.sleep(500)
        val p = e.position()!!
        assertEquals(2L, p.first)
        assertTrue("next position ${p.second}", p.second in 0..2000)
        e.release()
    }
}
