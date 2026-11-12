package com.ku.pierdoon.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import com.ku.pierdoon.data.uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.nio.ByteOrder

/**
 * The even-volume setting for the phone: how loud each track is ([loudness], shared by the
 * player service and the screens, which are one process) and whether it's in use.
 */
object EvenVolume {
    @Volatile var enabled: Boolean = false

    @Volatile private var store: Loudness? = null

    fun loudness(context: Context): Loudness = store ?: synchronized(this) {
        store ?: Loudness(
            File(context.applicationContext.cacheDir, "loudness.json"),
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ) { song -> measure(context.applicationContext, song.uri) }.also { store = it }
    }

    /** The factor the track [mediaId] plays at; 1 with the setting off or the track not measured yet. */
    fun gain(mediaId: String?): Float =
        if (!enabled) 1f else store?.gain(mediaId?.toLongOrNull()) ?: 1f

    /** Decodes the whole file to measure it; null if the phone can't. */
    private fun measure(context: Context, uri: Uri): TrackLoudness? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).also { codec = it }
            c.configure(format, null, null, 0)
            c.start()
            val info = MediaCodec.BufferInfo()
            var meter: LoudnessMeter? = null
            var float = false
            var samples = ShortArray(0)
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        val n = extractor.readSampleData(c.getInputBuffer(i)!!, 0)
                        if (n < 0) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, TIMEOUT_US)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = c.outputFormat
                    meter = LoudnessMeter(f.getInteger(MediaFormat.KEY_SAMPLE_RATE), f.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                if (o < 0) continue
                if (info.size > 0) {
                    val m = meter ?: c.outputFormat.let { f ->
                        LoudnessMeter(f.getInteger(MediaFormat.KEY_SAMPLE_RATE), f.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    }.also { meter = it }
                    val buffer = c.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val n = if (float) info.size / 4 else info.size / 2
                    if (samples.size < n) samples = ShortArray(n)
                    if (float) {
                        val fb = buffer.asFloatBuffer()
                        for (k in 0 until n) samples[k] = (fb.get(k).coerceIn(-1f, 1f) * 32767).toInt().toShort()
                    } else {
                        buffer.asShortBuffer().get(samples, 0, n)
                    }
                    m.add(samples, n)
                }
                c.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
            return meter?.result()
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private const val TIMEOUT_US = 10_000L
}

/**
 * A player's volume as the fades set it ([fade]: the crossfade, the sleep timer's, the tape
 * deck's muting), times the playing track's own level for even volume. Whatever changes the
 * volume goes through here, so neither undoes the other.
 */
class PlayerVolume(private val player: ExoPlayer) {
    var fade: Float = 1f
        set(value) {
            field = value
            apply()
        }

    /** Again for the track now playing, or for a level that changed. */
    fun apply() {
        player.volume = fade * EvenVolume.gain(player.currentMediaItem?.mediaId)
    }
}
