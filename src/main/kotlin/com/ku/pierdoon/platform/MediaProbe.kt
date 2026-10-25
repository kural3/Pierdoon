package com.ku.pierdoon.platform

import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.global.avformat
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacpp.BytePointer
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/** What a file's tags say. Empty strings for what's missing. */
data class ProbedTags(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val durationMs: Long,
)

/**
 * Reads tags, duration and embedded covers. jaudiotagger first (fast, pure Java); FFmpeg for
 * whatever it can't open (Opus, WebM, APE, WavPack and the rest).
 */
object MediaProbe {
    init {
        // jaudiotagger logs every frame it parses at INFO.
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
        avutil.av_log_set_level(avutil.AV_LOG_QUIET)
    }

    private val JAUDIOTAGGER = setOf("mp3", "m4a", "m4b", "mp4", "aac", "flac", "ogg", "wav", "aif", "aiff", "wma", "dsf")

    fun tags(file: File): ProbedTags? {
        if (file.extension.lowercase() in JAUDIOTAGGER) {
            runCatching { jaudiotaggerTags(file) }.getOrNull()?.let { return it }
        }
        return runCatching { ffmpegTags(file) }.getOrNull()
    }

    /** The front cover inside the file, as encoded image bytes; null when there is none. */
    fun embeddedCover(file: File): ByteArray? {
        if (file.extension.lowercase() in JAUDIOTAGGER) {
            runCatching {
                AudioFileIO.read(file).tag?.firstArtwork?.binaryData?.takeIf { it.isNotEmpty() }
            }.getOrNull()?.let { return it }
        }
        return runCatching { ffmpegCover(file) }.getOrNull()
    }

    private fun jaudiotaggerTags(file: File): ProbedTags {
        val audio = AudioFileIO.read(file)
        val tag = audio.tag
        fun field(key: FieldKey) = runCatching { tag?.getFirst(key) }.getOrNull().orEmpty().trim()
        val seconds = audio.audioHeader?.preciseTrackLength ?: 0.0
        return ProbedTags(
            title = field(FieldKey.TITLE),
            artist = field(FieldKey.ARTIST),
            album = field(FieldKey.ALBUM),
            albumArtist = field(FieldKey.ALBUM_ARTIST),
            durationMs = (seconds * 1000).toLong(),
        )
    }

    private inline fun <T> withFormat(file: File, block: (AVFormatContext) -> T): T? {
        val ctx = AVFormatContext(null)
        if (avformat.avformat_open_input(ctx, file.absolutePath, null, null) < 0) return null
        try {
            if (avformat.avformat_find_stream_info(ctx, null as org.bytedeco.ffmpeg.avutil.AVDictionary?) < 0) return null
            return block(ctx)
        } finally {
            avformat.avformat_close_input(ctx)
        }
    }

    private fun ffmpegTags(file: File): ProbedTags? = withFormat(file) { ctx ->
        fun meta(key: String): String {
            // Container tags first; Ogg keeps them on the audio stream instead.
            avutil.av_dict_get(ctx.metadata(), key, null, 0)?.value()?.getString(Charsets.UTF_8)?.let { return it.trim() }
            for (i in 0 until ctx.nb_streams()) {
                val s = ctx.streams(i)
                avutil.av_dict_get(s.metadata(), key, null, 0)?.value()?.getString(Charsets.UTF_8)?.let { return it.trim() }
            }
            return ""
        }
        val hasAudio = (0 until ctx.nb_streams()).any { ctx.streams(it).codecpar().codec_type() == avutil.AVMEDIA_TYPE_AUDIO }
        if (!hasAudio) return@withFormat null
        val duration = ctx.duration().takeIf { it > 0 }?.let { it / 1000 } ?: 0L
        ProbedTags(
            title = meta("title"),
            artist = meta("artist"),
            album = meta("album"),
            albumArtist = meta("album_artist"),
            durationMs = duration,
        )
    }

    private fun ffmpegCover(file: File): ByteArray? = withFormat(file) { ctx ->
        for (i in 0 until ctx.nb_streams()) {
            val s = ctx.streams(i)
            if (s.disposition() and avformat.AV_DISPOSITION_ATTACHED_PIC == 0) continue
            val pkt = s.attached_pic()
            val size = pkt.size()
            if (size <= 0) continue
            val bytes = ByteArray(size)
            BytePointer(pkt.data()).position(0).get(bytes)
            return@withFormat bytes
        }
        null
    }
}
