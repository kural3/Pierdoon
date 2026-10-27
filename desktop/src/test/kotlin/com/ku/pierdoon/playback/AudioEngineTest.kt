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
            override fun onOutputUnavailable() = error(IllegalStateException("No audio output"))
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
    fun speedMovesThePositionFasterOrSlower() {
        assumeTrue(music.isNotEmpty() && AudioSystem.getMixerInfo().isNotEmpty())
        val startedLatch = CountDownLatch(1)
        val e = engine(started = { startedLatch.countDown() })
        e.load(music.first(), token = 1, startMs = 10_000, play = true)
        assertTrue("never started", startedLatch.await(10, TimeUnit.SECONDS))
        // Changed mid-track: it carries on from where it was, at the new rate.
        e.setSpeed(1.25f)
        Thread.sleep(500)
        val fast = e.position()!!.second
        Thread.sleep(2000)
        val fastMoved = e.position()!!.second - fast
        assertTrue("1.25x moved $fastMoved ms in 2 s", fastMoved in 2_250..2_750)
        assertTrue("jumped on the change: $fast", fast in 10_000..11_500)

        e.setSpeed(0.8f)
        Thread.sleep(500)
        val slow = e.position()!!.second
        Thread.sleep(2000)
        val slowMoved = e.position()!!.second - slow
        assertTrue("0.8x moved $slowMoved ms in 2 s", slowMoved in 1_400..1_800)
        e.release()
    }

    @Test
    fun stopLetsGoOfTheFile() {
        assumeTrue(music.isNotEmpty() && AudioSystem.getMixerInfo().isNotEmpty())
        // A copy: Windows refuses to delete a file that is still open, which is what's checked.
        val copy = music.first().copyTo(File.createTempFile("engine", "." + music.first().extension), overwrite = true)
        val startedLatch = CountDownLatch(1)
        val e = engine(started = { startedLatch.countDown() })
        try {
            e.load(copy, token = 1, startMs = 0, play = true)
            assertTrue("never started", startedLatch.await(10, TimeUnit.SECONDS))
            e.stop().get(2, TimeUnit.SECONDS)
            assertTrue("file still held after stop", copy.delete())
        } finally {
            e.release()
            copy.delete()
        }
    }

    @Test
    fun commandsDuringPlaybackLoseNoAudio() {
        assumeTrue(music.isNotEmpty() && AudioSystem.getMixerInfo().isNotEmpty())
        // MP3 frames come out of the resampler longer than one write: those are the ones cut short.
        val track = music.firstOrNull { it.extension.equals("mp3", ignoreCase = true) } ?: music.first()
        val startedLatch = CountDownLatch(1)
        val endedLatch = CountDownLatch(1)
        val e = engine(started = { startedLatch.countDown() }, ended = { endedLatch.countDown() })
        e.load(track, token = 1, startMs = 0, play = true)
        assertTrue("never started", startedLatch.await(10, TimeUnit.SECONDS))
        val duration = com.ku.pierdoon.platform.MediaProbe.tags(track)!!.durationMs
        e.seek(duration - 3_000)
        val start = System.nanoTime()
        // What a queue edit sends: none of it should cut the audio being written short.
        while (endedLatch.count > 0 && System.nanoTime() - start < 10_000_000_000L) {
            e.setNext(null, -1)
            Thread.sleep(5)
        }
        val playedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("the last 3 s played in $playedMs ms", playedMs >= 2_850)
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

    @Test
    fun crossfadeStartsTheNextTrackBeforeTheEnd() {
        assumeTrue(music.size >= 2 && AudioSystem.getMixerInfo().isNotEmpty())
        val second = CountDownLatch(1)
        var ended = false
        val e = engine(started = { if (it == 2L) second.countDown() }, ended = { ended = true })
        e.crossfadeMs = 3_000
        e.load(music[0], token = 1, startMs = 0, play = true)
        e.setNext(music[1], token = 2, crossfade = true)
        Thread.sleep(500)
        val duration = com.ku.pierdoon.platform.MediaProbe.tags(music[0])!!.durationMs
        // Five seconds left: the fade (and the next track) should start after about two.
        e.seek(duration - 5_000)
        val seekedAt = System.nanoTime()
        assertTrue("next never started", second.await(10, TimeUnit.SECONDS))
        val startedAfter = (System.nanoTime() - seekedAt) / 1_000_000
        assertTrue("next started after $startedAfter ms", startedAfter in 1_200..3_500)

        Thread.sleep(1_000)
        val mid = e.position()!!
        assertEquals(2L, mid.first)
        assertTrue("mid-fade position ${mid.second}", mid.second in 500..2_500)

        // Past the end of the old track: the next one goes on alone, in step.
        Thread.sleep(3_000)
        val after = e.position()!!
        assertEquals(2L, after.first)
        assertTrue("after the fade ${after.second}", after.second - mid.second in 2_500..3_600)
        assertTrue("the first track's end was reported", !ended)
        e.release()
    }

    @Test
    fun measuresWhatIsHeard() {
        assumeTrue(music.isNotEmpty() && AudioSystem.getMixerInfo().isNotEmpty())
        val started = CountDownLatch(1)
        val e = engine(started = { started.countDown() })
        e.load(music.first(), token = 1, startMs = 30_000, play = true)
        assertTrue("never started", started.await(10, TimeUnit.SECONDS))
        var loudest = 0f
        var deepest = 0f
        val firstBeats = e.levels().beats
        repeat(120) {
            val l = e.levels()
            loudest = maxOf(loudest, l.energy)
            deepest = maxOf(deepest, l.bass)
            Thread.sleep(50)
        }
        val beats = e.levels().beats - firstBeats
        println("levels: energy up to $loudest, bass up to $deepest, $beats beats in 6 s")
        // Measured before the volume, which is 0 here.
        assertTrue("energy $loudest", loudest > 0.5f)
        assertTrue("bass $deepest", deepest > 0.3f)
        e.pause()
        Thread.sleep(300)
        assertEquals(0f, e.levels().energy)
        e.release()
    }

    @Test
    fun seekDuringCrossfadeMovesTheNextTrack() {
        assumeTrue(music.size >= 2 && AudioSystem.getMixerInfo().isNotEmpty())
        val second = CountDownLatch(1)
        val e = engine(started = { if (it == 2L) second.countDown() })
        e.crossfadeMs = 6_000
        e.load(music[0], token = 1, startMs = 0, play = true)
        e.setNext(music[1], token = 2, crossfade = true)
        Thread.sleep(500)
        val duration = com.ku.pierdoon.platform.MediaProbe.tags(music[0])!!.durationMs
        e.seek(duration - 6_500)
        assertTrue("next never started", second.await(10, TimeUnit.SECONDS))
        Thread.sleep(500)
        e.seek(30_000)
        Thread.sleep(600)
        val p = e.position()!!
        assertEquals(2L, p.first)
        assertTrue("after seek ${p.second}", p.second in 30_000..31_500)
        e.release()
    }
}
