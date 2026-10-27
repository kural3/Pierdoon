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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

/**
 * Plays audio files through one output line, FFmpeg decoding whatever the file holds.
 *
 * Everything is resampled to 48 kHz stereo 16-bit, so the line never has to be reopened between
 * tracks: when a track ends, the next one ([setNext]) is decoded straight into the same buffer
 * and plays without a gap.
 *
 * With [crossfadeMs] set and the next track asking for it, the next one is opened that long
 * before the end instead, and the two are mixed: the old one fading out, the new one in. From the
 * first mixed frame on, the new track is the one playing.
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
        data class Next(val file: File?, val token: Long, val crossfade: Boolean) : Command
        data class Seek(val ms: Long) : Command
        data object Play : Command
        data object Pause : Command
        /** [done] completes once the file is closed, so it can be rewritten or moved. */
        class Stop(val done: CompletableFuture<Unit>) : Command
        data class Device(val name: String?) : Command
        data class Speed(val value: Float) : Command
        data object Quit : Command
    }

    /** From [lineFrame] on, [token]'s audio from [mediaMs], played at [speed]. */
    private class Mark(val lineFrame: Long, val token: Long, val mediaMs: Long, val speed: Float)

    private class Upcoming(val file: File, val token: Long, val crossfade: Boolean)

    private val commands = LinkedBlockingQueue<Command>()
    private val meter = LevelMeter(RATE)
    private val marks = ArrayDeque<Mark>()
    private val lock = Any()

    @Volatile var volume: Float = 1f
    @Volatile var skipSilence: Boolean = false
    /** How long the end of a track and the start of the next overlap; 0 for none. */
    @Volatile var crossfadeMs: Long = 0

    /** Set while [Command.Play] is in force; read by the UI thread. */
    @Volatile var isPlaying: Boolean = false
        private set

    // Owned by the audio thread; the volatile ones are also read by position() on the UI thread.
    @Volatile private var line: SourceDataLine? = null
    private var deviceName: String? = null
    private var decoder: FFmpegFrameGrabber? = null
    private var decoderToken = -1L
    private var decoderFile: File? = null
    /** Played this much faster than recorded, pitch and all: the decoder resamples to [RATE] / speed. */
    private var speed = 1f
    private var next: Upcoming? = null
    /** The next track while it fades in over the end of [decoder]'s. */
    private var incoming: FFmpegFrameGrabber? = null
    private var incomingToken = -1L
    /** What [incoming] was opened from, to fall back on if the fade is called off. */
    private var incomingNext: Upcoming? = null
    /** What [incoming] decoded and the mix hasn't used yet. */
    private var incomingRest: ShortBuffer? = null
    private var fadeFrames = 0L
    private var fadeDone = 0L
    @Volatile private var framesWritten = 0L
    /** The rest of a decoded frame whose writing a command interrupted; written before decoding on. */
    private var leftover: ShortBuffer? = null
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
    /** [crossfade]: fade into it over [crossfadeMs] rather than start it right after the end. */
    fun setNext(file: File?, token: Long, crossfade: Boolean = false) = commands.put(Command.Next(file, token, crossfade))
    fun seek(ms: Long) = commands.put(Command.Seek(ms))
    fun play() { isPlaying = true; commands.put(Command.Play) }
    fun pause() { isPlaying = false; commands.put(Command.Pause) }
    /** @return completes once the decoder has let go of the file. */
    fun stop(): CompletableFuture<Unit> {
        isPlaying = false
        return CompletableFuture<Unit>().also { commands.put(Command.Stop(it)) }
    }
    fun setDevice(name: String?) = commands.put(Command.Device(name))
    /** Slower is lower, faster higher, as on a turntable. */
    fun setSpeed(value: Float) = commands.put(Command.Speed(value))
    fun release() = commands.put(Command.Quit)

    /** Token of the track being heard and its position, or null with nothing loaded. */
    fun position(): Pair<Long, Long>? = synchronized(lock) {
        val heard = heardFrame()
        // The newest mark already reached is what's playing now.
        val mark = marks.lastOrNull { it.lineFrame <= heard } ?: marks.firstOrNull() ?: return null
        val elapsed = (heard - mark.lineFrame).coerceAtLeast(0) * 1000 / RATE
        mark.token to mark.mediaMs + (elapsed * mark.speed).toLong()
    }

    /** Loudness and beats of what's coming out of the speakers now, for the visualizer. */
    fun levels(): AudioLevels = if (isPlaying) meter.at(heardFrame()) else AudioLevels.Silent

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
                val rest = leftover
                if (rest != null) {
                    writeLeftover(rest, chunk)
                    continue
                }
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
            is Command.Next -> next = cmd.file?.let { Upcoming(it, cmd.token, cmd.crossfade) }
            is Command.Seek -> {
                // The incoming track is the one playing already: the seek is in it.
                endCrossfade()
                val d = decoder ?: return true
                flushLine()
                runCatching { d.timestamp = cmd.ms.coerceAtLeast(0) * 1000 }
                synchronized(lock) { marks.clear(); marks += Mark(framesWritten, decoderToken, cmd.ms, speed) }
                pendingMark = false
                endedToken = -1L
            }
            // Set again here: a Load queued before them has put isPlaying to its own value since.
            Command.Play -> {
                playing = true
                isPlaying = true
                ensureLine()?.start()
            }
            Command.Pause -> {
                playing = false
                isPlaying = false
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
                endCrossfade()
                // Taken while the old line still says how much of its buffer was heard.
                val heard = position()
                line?.run { stop(); flush(); close() }
                line = null
                // Repositioned below from what was heard: the unwritten rest would come twice.
                leftover = null
                // What was buffered is lost: carry on from where the listener was.
                heard?.let { (token, ms) ->
                    if (token == decoderToken) decoder?.let { d -> runCatching { d.timestamp = ms * 1000 } }
                    synchronized(lock) { marks.clear(); marks += Mark(framesWritten, token, ms, speed) }
                }
                if (wasRunning || playing) ensureLine()?.start()
            }
            is Command.Speed -> if (cmd.value != speed) {
                // The decoder's rate is set when it opens: it opens again where the listener is.
                endCrossfade()
                val heard = position()
                speed = cmd.value
                val file = decoderFile
                if (decoder != null && file != null && heard != null && heard.first == decoderToken) {
                    flushLine()
                    closeDecoder()
                    open(file, heard.first, heard.second)
                }
                // Otherwise the rate goes with the next track opened.
            }
            Command.Quit -> return false
        }
        return true
    }

    /** A started decoder for [file], giving the line's format. */
    private fun grabber(file: File): FFmpegFrameGrabber {
        val g = FFmpegFrameGrabber(file)
        try {
            g.sampleMode = FrameGrabber.SampleMode.SHORT
            g.sampleFormat = avutil.AV_SAMPLE_FMT_S16
            g.sampleRate = (RATE / speed).roundToInt()
            g.audioChannels = CHANNELS
            g.start()
            if (g.audioChannels <= 0) throw IllegalStateException("No audio stream")
            return g
        } catch (e: Exception) {
            runCatching { g.release() }
            throw e
        }
    }

    private fun open(file: File, token: Long, startMs: Long): Boolean {
        return try {
            val g = grabber(file)
            if (startMs > 0) g.timestamp = startMs * 1000
            decoder = g
            decoderToken = token
            decoderFile = file
            synchronized(lock) { marks += Mark(framesWritten, token, startMs.coerceAtLeast(0), speed) }
            pendingMark = false
            silentFrames = 0
            endedToken = -1L
            true
        } catch (e: Exception) {
            post { listener.onError(token, e) }
            false
        }
    }

    /**
     * Opens the next track as [incoming] once [d]'s track is within [crossfadeMs] of its end,
     * which [mediaMs] is the start of the frame just decoded. A track that won't open is reported
     * and skipped: the following one is the player's business then, as after any error.
     */
    private fun startCrossfadeIfDue(d: FFmpegFrameGrabber, mediaMs: Long) {
        val n = next ?: return
        val fadeMs = crossfadeMs
        if (!n.crossfade || fadeMs <= 0) return
        val lengthMs = d.lengthInTime / 1000
        if (lengthMs <= 0) return
        val left = lengthMs - mediaMs
        // Less than that left (a seek to the very end) just runs into the next track.
        if (left > fadeMs || left < MIN_FADE_MS) return
        next = null
        val g = try {
            grabber(n.file)
        } catch (e: Exception) {
            post { listener.onError(n.token, e) }
            return
        }
        incoming = g
        incomingToken = n.token
        incomingNext = n
        incomingRest = null
        fadeFrames = (left * RATE / 1000 / speed).toLong()
        fadeDone = 0
        // From here the new track is what's playing, its position counting from its start.
        synchronized(lock) { marks += Mark(framesWritten, n.token, 0, speed) }
    }

    /**
     * [out] (the old track's samples) mixed with as many of [incoming]'s, along an equal-power
     * curve so the overlap is as loud as either track alone. Runs short of incoming audio only
     * when the next track is shorter than the fade; silence stands in for it then.
     */
    private fun mixIncoming(out: ShortBuffer): ShortBuffer {
        val count = out.remaining()
        val mixed = ShortArray(count)
        val start = out.position()
        var i = 0
        while (i < count) {
            val t = ((fadeDone + i / CHANNELS).toDouble() / fadeFrames).coerceIn(0.0, 1.0)
            val gainOut = cos(t * PI / 2)
            val gainIn = sin(t * PI / 2)
            for (c in 0 until CHANNELS) {
                if (i + c >= count) break
                val a = out.get(start + i + c)
                val b = incomingSample()
                mixed[i + c] = (a * gainOut + b * gainIn).toInt().coerceIn(-32768, 32767).toShort()
            }
            i += CHANNELS
        }
        fadeDone += count / CHANNELS
        return ShortBuffer.wrap(mixed)
    }

    /** The next sample of [incoming], decoding more as needed; 0 past its end. */
    private fun incomingSample(): Int {
        var rest = incomingRest
        while (rest == null || !rest.hasRemaining()) {
            val g = incoming ?: return 0
            val frame = g.grabSamples() ?: run { incomingRest = null; return 0 }
            rest = (frame.samples?.firstOrNull() as? ShortBuffer)?.duplicate()
            incomingRest = rest
        }
        return rest.get().toInt()
    }

    /** The fade is over (or cut short): [incoming] becomes the track being decoded. */
    private fun endCrossfade() {
        val g = incoming ?: return
        decoder?.let { runCatching { it.stop(); it.release() } }
        decoder = g
        decoderToken = incomingToken
        decoderFile = incomingNext?.file
        // Decoded already but not mixed in yet: it goes out first.
        leftover = incomingRest?.takeIf { it.hasRemaining() }
        incoming = null
        incomingRest = null
        incomingNext = null
        pendingMark = false
        silentFrames = 0
        endedToken = -1L
    }

    /** Drops [incoming] unheard-of: the old track is the one playing again, the next one still next. */
    private fun abortCrossfade() {
        val g = incoming ?: return
        runCatching { g.stop(); g.release() }
        if (next == null) next = incomingNext
        incoming = null
        incomingRest = null
        incomingNext = null
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

        // Mid-fade the old track's quiet tail is part of the mix, not something to skip.
        if (skipSilence && incoming == null) {
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
            synchronized(lock) { marks += Mark(framesWritten, decoderToken, mediaMs, speed) }
            pendingMark = false
        }
        // After the mark above: from the fade on, the next track's mark has to be the newest.
        if (incoming == null) startCrossfadeIfDue(d, mediaMs)

        val l = ensureLine() ?: run {
            // The fade starts over once there's an output again.
            abortCrossfade()
            // Decoding on without anywhere to play would race through the queue in silence.
            synchronized(lock) { marks.clear(); marks += Mark(framesWritten, decoderToken, mediaMs, speed) }
            runCatching { d.timestamp = mediaMs * 1000 }
            playing = false
            isPlaying = false
            post { listener.onOutputUnavailable() }
            return frames
        }
        // A view of its own: the grabber's buffer keeps its position for the next grab.
        writeSamples(l, if (incoming != null) mixIncoming(buffer.duplicate()) else buffer.duplicate(), gain, chunk)
        trimMarks()
        announceIfHeard()
        return frames
    }

    /**
     * Writes [samples] from their position on. A command (pause, seek, skip) shouldn't wait for
     * a long frame to finish, so a waiting one stops it; what's left is kept in [leftover] and
     * written next, as dropping it would cut the audio and put the position behind the music.
     */
    private fun writeSamples(l: SourceDataLine, samples: ShortBuffer, gain: Float, chunk: ByteArray) {
        while (samples.hasRemaining()) {
            val n = minOf(samples.remaining(), chunk.size / 2)
            var o = 0
            repeat(n) {
                val raw = samples.get()
                // Before the volume: the picture shouldn't fade with the sound turned down.
                meter.sample(raw)
                val s = (raw * gain).toInt().coerceIn(-32768, 32767)
                chunk[o++] = (s and 0xFF).toByte()
                chunk[o++] = ((s shr 8) and 0xFF).toByte()
            }
            l.write(chunk, 0, o)
            meter.commit(framesWritten)
            framesWritten += n / CHANNELS
            if (commands.isNotEmpty()) break
        }
        leftover = samples.takeIf { it.hasRemaining() }
    }

    private fun writeLeftover(rest: ShortBuffer, chunk: ByteArray) {
        val l = line ?: run { leftover = null; return }
        writeSamples(l, rest, volume.let { it * it * it }, chunk)
        trimMarks()
        announceIfHeard()
    }

    private fun onDecoderEnd() {
        if (incoming != null) {
            // The old track has faded out; the new one goes on by itself.
            endCrossfade()
            return
        }
        val n = next
        val endedOf = decoderToken
        closeDecoder()
        if (n != null) {
            next = null
            // Gapless: the next file's audio goes into the same line right behind this one's.
            open(n.file, n.token, 0L)
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
        leftover = null
        synchronized(lock) { marks.clear() }
        pendingMark = true
        announcedToken = -1L
    }

    private fun closeDecoder() {
        leftover = null
        decoder?.let { runCatching { it.stop(); it.release() } }
        decoder = null
        incoming?.let { runCatching { it.stop(); it.release() } }
        incoming = null
        incomingRest = null
        incomingNext = null
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
        meter.reset()
        synchronized(lock) {
            val m = marks.lastOrNull()
            marks.clear()
            if (m != null) marks += Mark(0, m.token, m.mediaMs, m.speed)
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
        /** A fade shorter than this would sound like a click rather than a crossfade. */
        private const val MIN_FADE_MS = 1000L

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
