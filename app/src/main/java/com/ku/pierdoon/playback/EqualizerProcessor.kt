package com.ku.pierdoon.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The equalizer as set in Settings, for the processors inside both players; null while it's off. */
object EqualizerSettings {
    @Volatile var gains: List<Float>? = null
        set(value) {
            field = value
            version++
        }

    /** Bumped on every change, so a processor knows to take it up. Written from the main thread only. */
    @Volatile var version = 0
        private set
}

/**
 * Runs the shared [Equalizer] over ExoPlayer's 16-bit PCM, right before it goes to the speaker.
 * The sink converts other PCM to 16-bit ahead of this, so that's all it meets in practice.
 */
@OptIn(UnstableApi::class)
class EqualizerProcessor : BaseAudioProcessor() {
    private var equalizer: Equalizer? = null
    private var appliedVersion = -1

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioFormat.NOT_SET
        equalizer = Equalizer(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        appliedVersion = -1
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val eq = equalizer ?: return
        val version = EqualizerSettings.version
        if (version != appliedVersion) {
            appliedVersion = version
            eq.setGains(EqualizerSettings.gains)
        }
        val out = replaceOutputBuffer(inputBuffer.remaining())
        if (!eq.beginBlock()) {
            out.put(inputBuffer)
        } else {
            val input = inputBuffer.order(ByteOrder.nativeOrder())
            val channels = inputAudioFormat.channelCount
            var i = 0
            while (input.remaining() >= 2) {
                out.putShort(eq.process(input.getShort().toInt(), i % channels).toShort())
                i++
            }
        }
        out.flip()
    }

    /** A seek or a new track: the filters' memory of the old samples would only ring. */
    override fun onFlush() {
        equalizer?.reset()
    }

    override fun onReset() {
        equalizer = null
    }
}

/**
 * Audio-only renderers (no video, text or metadata pipelines: less memory, and R8 drops the
 * code) whose sound goes through the equalizer.
 */
@OptIn(UnstableApi::class)
fun audioRenderers(context: Context) = RenderersFactory { handler, _, audioListener, _, _ ->
    val sink = DefaultAudioSink.Builder(context)
        .setAudioProcessors(arrayOf(EqualizerProcessor()))
        .build()
    arrayOf(MediaCodecAudioRenderer(context, MediaCodecSelector.DEFAULT, handler, audioListener, sink))
}
