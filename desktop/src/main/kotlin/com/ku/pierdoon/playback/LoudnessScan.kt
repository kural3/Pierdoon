package com.ku.pierdoon.playback

import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import java.nio.ShortBuffer

/** Decodes a whole file (as stereo, the way it plays) to measure how loud it is. */
object LoudnessScan {
    fun measure(file: File): TrackLoudness? {
        if (!file.isFile) return null
        val g = FFmpegFrameGrabber(file)
        try {
            g.sampleMode = FrameGrabber.SampleMode.SHORT
            g.sampleFormat = avutil.AV_SAMPLE_FMT_S16
            g.audioChannels = 2
            g.start()
            if (g.audioChannels <= 0 || g.sampleRate <= 0) return null
            val meter = LoudnessMeter(g.sampleRate, g.audioChannels)
            var chunk = ShortArray(8192)
            while (true) {
                if (Thread.currentThread().isInterrupted) return null
                val frame = g.grabSamples() ?: break
                val buffer = (frame.samples?.firstOrNull() as? ShortBuffer)?.duplicate() ?: continue
                val n = buffer.remaining()
                if (n > chunk.size) chunk = ShortArray(n)
                buffer.get(chunk, 0, n)
                meter.add(chunk, n)
            }
            return meter.result()
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { g.stop(); g.release() }
        }
    }
}
