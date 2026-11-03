package com.ku.pierdoon.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

/**
 * Cassette mode on the phone: [TapeDeck] around a real ExoPlayer, playing four generated tracks of
 * [TRACK_MS] (side A the first two, side B the other two). The player is silent; the deck's own
 * sounds are heard.
 */
@RunWith(AndroidJUnit4::class)
class TapeDeckDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var deck: TapeDeck
    private val windMs = CASSETTE_WIND_MS

    @Before
    fun setUp() {
        val tracks = (0 until 4).map { tone(it) }
        onMain {
            player = ExoPlayer.Builder(context).build()
            player.volume = SILENT
            deck = TapeDeck(player, scope)
            deck.start()
            deck.tapeMode = true
            player.setMediaItems(tracks.mapIndexed { i, f ->
                MediaItem.Builder()
                    .setMediaId("$i")
                    .setUri(f.toURI().toString())
                    .setMediaMetadata(MediaMetadata.Builder().setDurationMs(TRACK_MS).build())
                    .build()
            })
            player.prepare()
            player.play()
        }
        waitFor("playing") { player.isPlaying }
    }

    @After
    fun tearDown() {
        onMain {
            deck.release()
            player.release()
        }
        scope.cancel()
        TapeMotion.current.value = TapeMotion.Still
    }

    @Test
    fun theCassetteIsTurnedOverAtTheEndOfSideA() {
        onMain { player.seekTo(1, TRACK_MS - 500) }
        waitFor("turning over", 3_000) { TapeMotion.current.value == TapeMotion.Flip }
        assertEquals(1, get { player.currentMediaItemIndex })
        waitFor("on side B", CASSETTE_SOUND_MS + 2_000) { player.currentMediaItemIndex == 2 && player.isPlaying }
        assertEquals(TapeMotion.Still, TapeMotion.current.value)
    }

    @Test
    fun nextSpinsTheTapeAndThenPlaysFromTheTop() {
        onMain { deck.buttons.seekToNext() }
        assertEquals(1, get { player.currentMediaItemIndex })
        assertEquals(TapeMotion.Forward, TapeMotion.current.value)
        // Silent while the tape spins.
        assertEquals(0f, get { player.volume }, 0f)
        waitFor("spun", windMs + 1_000) { TapeMotion.current.value == TapeMotion.Still }
        assertEquals(SILENT, get { player.volume }, 0f)
        // Put back to its start: a moment of buffering, then on.
        waitFor("playing", 1_000) { player.isPlaying }
        val position = get { player.currentPosition }
        assertTrue("position $position", position < 600)
    }

    @Test
    fun nextAcrossTheMiddleTurnsTheCassetteOver() {
        onMain { player.seekTo(1, 0) }
        waitFor("on the last track of side A") { player.currentMediaItemIndex == 1 && player.isPlaying }
        onMain { deck.buttons.seekToNext() }
        assertEquals(TapeMotion.Flip, TapeMotion.current.value)
        // The track stays put while the cassette is turned.
        assertEquals(1, get { player.currentMediaItemIndex })
        waitFor("on side B", CASSETTE_SOUND_MS + 1_500) { player.currentMediaItemIndex == 2 && TapeMotion.current.value == TapeMotion.Still }
        waitFor("playing", 1_000) { player.isPlaying }
        assertEquals(SILENT, get { player.volume }, 0f)
        val position = get { player.currentPosition }
        assertTrue("position $position", position < 600)
    }

    @Test
    fun aPausedTapeSpinsTooAndStaysPaused() {
        onMain {
            player.pause()
            deck.buttons.seekToNext()
        }
        assertEquals(TapeMotion.Forward, TapeMotion.current.value)
        waitFor("spun", windMs + 1_000) { TapeMotion.current.value == TapeMotion.Still }
        assertEquals(1, get { player.currentMediaItemIndex })
        assertTrue("started playing", !get { player.playWhenReady })
        assertEquals(SILENT, get { player.volume }, 0f)
    }

    @Test
    fun pausingMidSpinLeavesTheTrackAtItsStart() {
        onMain { deck.buttons.seekToNext() }
        Thread.sleep(windMs / 2)
        onMain { player.pause() }
        assertEquals(TapeMotion.Still, TapeMotion.current.value)
        assertEquals(SILENT, get { player.volume }, 0f)
        assertEquals(0L, get { player.currentPosition })
        Thread.sleep(windMs)
        // The cancelled spin starts nothing when its time would have been up.
        assertTrue(!get { player.playWhenReady })
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun <T> get(read: () -> T): T {
        var value: T? = null
        onMain { value = read() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun waitFor(what: String, timeoutMs: Long = 5_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!get(check)) {
            assertTrue("never $what", System.currentTimeMillis() < end)
            Thread.sleep(20)
        }
    }

    /** A quiet sine of [TRACK_MS] as a WAV file, a different pitch for each [n]. */
    private fun tone(n: Int): File {
        val file = File(context.cacheDir, "tape-test-$n.wav")
        val rate = 44_100
        val frames = (rate * TRACK_MS / 1000).toInt()
        val data = ByteArray(frames * 4)
        for (i in 0 until frames) {
            val s = (sin(2 * PI * (220 + 110 * n) * i / rate) * 2_000).toInt()
            for (c in 0 until 2) {
                data[i * 4 + c * 2] = s.toByte()
                data[i * 4 + c * 2 + 1] = (s shr 8).toByte()
            }
        }
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            fun int(v: Int) = f.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun short(v: Int) = f.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            f.write("RIFF".toByteArray()); int(36 + data.size); f.write("WAVE".toByteArray())
            f.write("fmt ".toByteArray()); int(16); short(1); short(2); int(rate); int(rate * 4); short(4); short(16)
            f.write("data".toByteArray()); int(data.size); f.write(data)
        }
        return file
    }

    private companion object {
        const val TRACK_MS = 6_000L
        /** Not quite 0, so a volume the deck gave back is told apart from the one it set. */
        const val SILENT = 0.001f
    }
}
