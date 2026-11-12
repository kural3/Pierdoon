package com.ku.pierdoon

import com.ku.pierdoon.playback.LoudnessScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** The PC's decoding for even volume, on files as they come. */
class LoudnessScanTest {

    @get:Rule val dir = TemporaryFolder()

    /** A 16-bit WAV of a 1 kHz sine at [dbfs] on every channel. */
    private fun wav(name: String, rate: Int, channels: Int, seconds: Int, dbfs: Double): File {
        val frames = rate * seconds
        val data = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        val amplitude = 10.0.pow(dbfs / 20) * 32767
        repeat(frames) { i ->
            val s = (amplitude * sin(2 * PI * 1000 * i / rate)).roundToInt().toShort()
            repeat(channels) { data.putShort(s) }
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(channels.toShort())
            putInt(rate); putInt(rate * channels * 2); putShort((channels * 2).toShort()); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        return File(dir.root, name).apply { writeBytes(header.array() + data.array()) }
    }

    @Test
    fun aStereoReferenceToneReadsMinus23() {
        val l = LoudnessScan.measure(wav("ref.wav", 48000, 2, 10, -23.0))!!
        assertEquals(-23.0, l.lufs.toDouble(), 0.1)
        assertEquals(10.0.pow(-23.0 / 20), l.peak.toDouble(), 0.001)
    }

    @Test
    fun aMonoFileIsMeasuredAsItPlays() {
        // The decoder spreads mono over both speakers 3 dB down each, the same when it plays.
        val l = LoudnessScan.measure(wav("mono.wav", 44100, 1, 10, -23.0))!!
        assertEquals(-26.0, l.lufs.toDouble(), 0.15)
    }

    @Test
    fun aFileThatIsNoAudioIsNull() {
        assertNull(LoudnessScan.measure(File(dir.root, "nothing.mp3").apply { writeText("not audio") }))
        assertNull(LoudnessScan.measure(File(dir.root, "missing.mp3")))
    }
}
