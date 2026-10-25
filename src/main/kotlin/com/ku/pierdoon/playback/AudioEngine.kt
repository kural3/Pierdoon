package com.ku.pierdoon.playback

import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import java.nio.ShortBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine
import kotlin.math.abs

/**
 * Plays audio files through one output line, FFmpeg decoding whatever the file holds.
 *
 * Everything is resampled to 48 kHz stereo 16-bit, so the line never has to be reopened between
 * tracks: when a track ends, the next one ([setNext]) is decoded straight into the same buffer
 * and plays without a gap.
 *
 * The decoding thread owns FFmpeg and the line; callers only post commands. Where playback is
 * (which track, which millisecond) is derived from marks laid down as audio is written: a mark
 * says "frame N of the line is millisecond M of track T", so the position follows what is
 * actually heard, buffer latency included.
 */
class AudioEngine(private val listener: Listener) {

    interface Listener {
        /** [token]'s audio started coming out of the speakers (also after a gapless switch). */
        fun onTrackStarted(token: Long)

        /** The last track ran out and nothing was set to follow. */
        fun onEnded(token: Long)

        /** [token] couldn't be opened or decoded; playback stopped. */
        fun onError(token: Long, error: Throwable)

        /** No audio output could be opened; playback paused where it was. */
        fun onOutputUnavailable()
    }

    private sealed interface Command {
        data class Load(val file: File, val token: Long, val startMs: Long, val play: Boolean) : Command
        data class Next(val file: File?, val token: Long) : Command
        data class Seek(val ms: Long) : Command
        data object Play : Command
        data object Pause : Command
        /** [done] completes once the file is closed, so it can be rewritten or moved. */
        class Stop(val done: CompletableFuture<Unit>) : Command
        data class Device(val name: String?) : Command
        data object Quit : Command
    }

    private class Mark(val lineFrame: Long, val token: Long, val mediaMs: Long)

    private val commands = LinkedBlockingQueue<Command>()
    private val marks = ArrayDeque<Mark>()
    private val lock = Any()

    @Volatile var volume: Float = 1f
    @Volatile var skipSilence: Boolean = false

    /** Set while [Command.Play] is in force; read by the UI thread. */
    @Volatile var isPlaying: Boolean = false
        private set

    // Owned by the audio thread.
    private var line: SourceDataLine? = null
    private var deviceName: String? = null
    private var decoder: FFmpegFrameGrabber? = null
    private var decoderToken = -1L
    private var next: Pair<File, Long>? = null
    private var framesWritten = 0L
    private var playing = false
    private var pendingMark = true
    private var silentFrames = 0L
    private var announcedToken = -1L
    private var endedToken = -1L

    private val thread = Thread(::loop, "audio").apply {
        isDaemon = true
        priority = Thread.MAX_PRIORITY
        start()
    }

    fun load(file: File, token: Long, startMs: Long, play: Boolean) = commands.put(Command.Load(file, token, startMs, play))
    fun setNext(file: File?, token: Long) = commands.put(Command.Next(file, token))
    fun seek(ms: Long) = commands.put(Command.Seek(ms))
    fun play() { isPlaying = true; commands.put(Command.Play) }
    fun pause() { isPlaying = false; commands.put(Command.Pause) }
    /** @return completes once the decoder has let go of the file. */
    fun stop(): CompletableFuture<Unit> {
        isPlaying = false
        return CompletableFuture<Unit>().also { commands.put(Command.Stop(it)) }
    }
    fun setDevice(name: String?) = commands.put(Command.Device(name))
    fun release() = commands.put(Command.Quit)

    /** Token of the track being heard and its position, or null with nothing loaded. */
    fun position(): Pair<Long, Long>? = synchronized(lock) {
        val heard = heardFrame()
        // The newest mark already reached is what's playing now.
        val mark = marks.lastOrNull { it.lineFrame <= heard } ?: marks.firstOrNull() ?: return null
        val elapsed = ((heard - mark.lineFrame).coerceAtLeast(0) * 1000 / RATE)
        mark.token to mark.mediaMs + elapsed
    }

    /** Frames that have left the line's buffer: what the listener has heard so far. */
    private fun heardFrame(): Long {
        val l = line ?: return framesWritten
        val buffered = (l.bufferSize - l.available()) / FRAME_BYTES
        return (framesWritten - buffered).coerceAtLeast(0)
    }

    private fun loop() {
        val chunk = ByteArray(CHUNK_FRAMES * FRAME_BYTES)
        while (true) {
            val cmd = if (playing && decoder != null) commands.poll() else commands.poll(250, TimeUnit.MILLISECONDS)
            if (cmd != null) {
                if (!handle(cmd)) break
                continue
            }
            announceIfHeard()
            if (!playing) continue
            val d = decoder ?: continue
            try {
                val written = decodeInto(d, chunk)
                if (written < 0) onDecoderEnd()
            } catch (e: Exception) {
                val token = decoderToken
                closeDecoder()
                post { listener.onError(token, e) }
            }
        }
        closeDecoder()
        line?.run { stop(); close() }
    }

    /** @return false to quit. */
    private fun handle(cmd: Command): Boolean {
        when (cmd) {
            is Command.Load -> {
                flushLine()
                closeDecoder()
                next = null
                if (!open(cmd.file, cmd.token, cmd.startMs)) return true
                playing = cmd.play
                isPlaying = cmd.play
                if (playing) ensureLine()?.start()
            }
            is Command.Next -> next = cmd.file?.let { it to cmd.token }
            is Command.Seek -> {
                val d = decoder ?: return true
                flushLine()
                runCatching { d.timestamp = cmd.ms.coerceAtLeast(0) * 1000 }
                synchronized(lock) { marks.clear(); marks += Mark(framesWritten, decoderToken, cmd.ms) }
                pendingMark = false
                endedToken = -1L
            }
            Command.Play -> {
                playing = true
                ensureLine()?.start()
            }
            Command.Pause -> {
                playing = false
                line?.stop()
            }
            is Command.Stop -> {
                playing = false
                flushLine()
                closeDecoder()
                next = null
                synchronized(lock) { marks.clear() }
                cmd.done.complete(Unit)
            }
            is Command.Device -> if (cmd.name != deviceName) {
                deviceName = cmd.name
                val wasRunning = line?.isRunning == true
                // Taken while the old line still says how much of its buffer was heard.
                val heard = position()
                line?.run { stop(); flush(); close() }
                line = null
                // What was buffered is lost: carry on from where the listener was.
                heard?.let { (token, ms) ->
                    if (token == decoderToken) decoder?.let { d -> runCatching { d.timestamp = ms * 1000 } }
                    synchronized(lock) { marks.clear(); marks += Mark(framesWritten, token, ms) }
                }
                if (wasRunning || playing) ensureLine()?.start()
            }
            Command.Quit -> return false
        }
        return true
    }

    private fun open(file: File, token: Long, startMs: Long): Boolean {
        val g = FFmpegFrameGrabber(file)
        return try {
            g.sampleMode = FrameGrabber.SampleMode.SHORT
            g.sampleFormat = avutil.AV_SAMPLE_FMT_S16
            g.sampleRate = RATE
            g.audioChannels = CHANNELS
            g.start()
            if (g.audioChannels <= 0) throw IllegalStateException("No audio stream")
            if (startMs > 0) g.timestamp = startMs * 1000
            decoder = g
            decoderToken = token
            synchronized(lock) { marks += Mark(framesWritten, token, startMs.coerceAtLeast(0)) }
            pendingMark = false
            silentFrames = 0
            endedToken = -1L
            true
        } catch (e: Exception) {
            runCatching { g.release() }
            post { listener.onError(token, e) }
            false
        }
    }

    /** Decodes one frame of audio and writes it to the line. -1 at the end of the file. */
    private fun decodeInto(d: FFmpegFrameGrabber, chunk: ByteArray): Int {
        val frame = d.grabSamples() ?: return -1
        val buffer = frame.samples?.firstOrNull() as? ShortBuffer ?: return 0
        val mediaMs = frame.timestamp / 1000
        val samples = buffer.remaining()
        if (samples == 0) return 0
        val frames = samples / CHANNELS
        val gain = volume.let { it * it * it }

        if (skipSilence) {
            var peak = 0
            for (i in 0 until samples) peak = maxOf(peak, abs(buffer.get(buffer.position() + i).toInt()))
            if (peak < SILENCE_PEAK) {
                silentFrames += frames
                // A short pause is music; only a long stretch of nothing is cut.
                if (silentFrames > SILENCE_KEEP_FRAMES) {
                    pendingMark = true
                    return frames
                }
            } else {
                silentFrames = 0
            }
        }
        if (pendingMark) {
            synchronized(lock) { marks += Mark(framesWritten, decoderToken, mediaMs) }
            pendingMark = false
        }

        val l = ensureLine() ?: run {
            // Decoding on without anywhere to play would race through the queue in silence.
            synchronized(lock) { marks.clear(); marks += Mark(framesWritten, decoderToken, mediaMs) }
            runCatching { d.timestamp = mediaMs * 1000 }
            playing = false
            isPlaying = false
            post { listener.onOutputUnavailable() }
            return frames
        }
        var offset = 0
        while (offset < samples) {
            val n = minOf(samples - offset, chunk.size / 2)
            var o = 0
            for (i in 0 until n) {
                val s = (buffer.get(buffer.position() + offset + i) * gain).toInt().coerceIn(-32768, 32767)
                chunk[o++] = (s and 0xFF).toByte()
                chunk[o++] = ((s shr 8) and 0xFF).toByte()
            }
            l.write(chunk, 0, o)
            framesWritten += n / CHANNELS
            offset += n
            // A command (pause, seek, skip) shouldn't wait for a long frame to finish.
            if (commands.isNotEmpty()) break
        }
        trimMarks()
        announceIfHeard()
        return frames
    }

    private fun onDecoderEnd() {
        val n = next
        val endedOf = decoderToken
        closeDecoder()
        if (n != null) {
            next = null
            // Gapless: the next file's audio goes into the same line right behind this one's.
            open(n.first, n.second, 0L)
            return
        }
        // Let what's buffered play out before reporting the end.
        line?.drain()
        playing = false
        isPlaying = false
        endedToken = endedOf
        post { listener.onEnded(endedOf) }
    }

    /** Reports a track once its first frame is actually heard. */
    private fun announceIfHeard() {
        val token = position()?.first ?: return
        if (token != announcedToken) {
            announcedToken = token
            post { listener.onTrackStarted(token) }
        }
    }

    private fun trimMarks() = synchronized(lock) {
        val heard = heardFrame()
        while (marks.size > 1 && marks[1].lineFrame <= heard) marks.removeFirst()
    }

    private fun flushLine() {
        line?.run {
            // A stopped line swallows writes without playing them, so it's started again. Not by
            // isRunning: a line just started with nothing in it yet reports false.
            stop()
            flush()
            if (playing) start()
        }
        // Nothing buffered any more: what's written is what's heard.
        synchronized(lock) { marks.clear() }
        pendingMark = true
        announcedToken = -1L
    }

    private fun closeDecoder() {
        decoder?.let { runCatching { it.stop(); it.release() } }
        decoder = null
    }

    private fun ensureLine(): SourceDataLine? {
        line?.let { return it }
        val format = AudioFormat(RATE.toFloat(), 16, CHANNELS, true, false)
        val l = runCatching {
            val mixer = deviceName?.let { name -> AudioSystem.getMixerInfo().firstOrNull { it.name == name } }
            (if (mixer != null) AudioSystem.getSourceDataLine(format, mixer) else AudioSystem.getSourceDataLine(format))
                .apply { open(format, BUFFER_FRAMES * FRAME_BYTES) }
        }.getOrNull() ?: return null
        // A fresh line starts counting from zero again.
        framesWritten = 0
        synchronized(lock) {
            val m = marks.lastOrNull()
            marks.clear()
            if (m != null) marks += Mark(0, m.token, m.mediaMs)
        }
        line = l
        return l
    }

    private fun post(block: () -> Unit) = javax.swing.SwingUtilities.invokeLater(block)

    companion object {
        const val RATE = 48_000
        const val CHANNELS = 2
        private const val FRAME_BYTES = 2 * CHANNELS
        /** ~170 ms: short enough that pause and seek feel instant, long enough not to stutter. */
        private const val BUFFER_FRAMES = 8192
        private const val CHUNK_FRAMES = 1024
        private const val SILENCE_PEAK = 40
        private const val SILENCE_KEEP_FRAMES = RATE / 2L

        /** Names of the outputs that can take 48 kHz stereo, for the settings. */
        fun outputDevices(): List<String> {
            val format = AudioFormat(RATE.toFloat(), 16, CHANNELS, true, false)
            val info = javax.sound.sampled.DataLine.Info(SourceDataLine::class.java, format)
            return AudioSystem.getMixerInfo().filter { mi: Mixer.Info ->
                runCatching { AudioSystem.getMixer(mi).isLineSupported(info) }.getOrDefault(false)
            }.map { it.name }.filterNot { it.startsWith("Port ") }
        }
    }
}
