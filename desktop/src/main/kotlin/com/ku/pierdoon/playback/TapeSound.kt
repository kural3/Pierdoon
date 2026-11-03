package com.ku.pierdoon.playback

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** Plays one of the deck's sounds (cassette, rewind, wind, a key) on an output line of its own. */
class TapeSound {
    @Volatile private var running: Thread? = null

    /** Stops whatever is playing and starts over; [onDone] runs on the UI thread unless [cancel]led. */
    fun play(sound: ShortArray, device: String?, volume: Float, onDone: () -> Unit = {}) {
        cancel()
        running = Thread({ run(sound, device, volume, onDone) }, "tape sound").apply {
            isDaemon = true
            start()
        }
    }

    fun cancel() {
        running?.interrupt()
        running = null
    }

    private fun run(sound: ShortArray, device: String?, volume: Float, onDone: () -> Unit) {
        val me = Thread.currentThread()
        // Without an output there's nothing to wait for: the next track reports that as it always does.
        runCatching { write(sound, device, volume, me) }
        javax.swing.SwingUtilities.invokeLater {
            if (running === me) {
                running = null
                onDone()
            }
        }
    }

    private fun write(sound: ShortArray, device: String?, volume: Float, me: Thread) {
        val format = AudioFormat(CASSETTE_SOUND_RATE.toFloat(), 16, 2, true, false)
        val mixer = device?.let { name -> AudioSystem.getMixerInfo().firstOrNull { it.name == name } }
        val line = if (mixer != null) AudioSystem.getSourceDataLine(format, mixer) else AudioSystem.getSourceDataLine(format)
        line.open(format, CHUNK_BYTES * 4)
        try {
            line.start()
            val gain = volume * volume * volume
            val chunk = ByteArray(CHUNK_BYTES)
            var i = 0
            while (i < sound.size && !me.isInterrupted) {
                var o = 0
                while (o < chunk.size && i < sound.size) {
                    val s = (sound[i++] * gain).toInt()
                    chunk[o++] = (s and 0xFF).toByte()
                    chunk[o++] = ((s shr 8) and 0xFF).toByte()
                }
                line.write(chunk, 0, o)
            }
            if (!me.isInterrupted) line.drain()
        } finally {
            line.stop()
            line.close()
        }
    }

    private companion object {
        const val CHUNK_BYTES = 4096
    }
}

/** The deck's sounds, read once; the keys and spins come out a little different every time. */
internal object TapeSounds {
    val flip by lazy { cassetteChangeSound() }
    /** Running the tape back after a side of [sideLengthMs]: longer for a longer side. */
    fun rewind(sideLengthMs: Long) = cassetteRewindSound(cassetteRewindMs(sideLengthMs))
    private val winds by lazy { cassetteWindVariety() }
    private val playKeys by lazy { cassetteKeyVariety(play = true) }
    private val stopKeys by lazy { cassetteKeyVariety(play = false) }

    fun wind() = winds.next()
    fun key(play: Boolean) = (if (play) playKeys else stopKeys).next()
}
