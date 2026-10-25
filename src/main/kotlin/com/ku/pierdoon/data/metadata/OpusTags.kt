package com.ku.pierdoon.data.metadata

import java.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tags for Ogg Opus files, which jaudiotagger can't write.
 *
 * An Opus stream is OpusHead, then OpusTags (Vorbis-style comments), then audio. Only the
 * OpusTags packet is rebuilt; audio pages are copied byte for byte, renumbered and re-checksummed.
 * Comments other than title, artist, album and the picture are kept.
 */
internal object OpusTags {

    fun write(file: File, title: String, artist: String, album: String, cover: ByteArray?, removeCover: Boolean) {
        val pages = readPages(file)
        if (pages.isEmpty() || !pages[0].body.startsWith(HEAD)) throw IOException("Not an Opus file")

        // Pages 1..n carry the OpusTags packet; it ends on the first page with a lacing value < 255.
        val tagsEnd = (1 until pages.size).firstOrNull { i -> pages[i].lacing.last() < 255 }
            ?: throw IOException("No OpusTags")
        val oldTags = pages.subList(1, tagsEnd + 1).fold(ByteArrayOutputStream()) { out, p -> out.apply { write(p.body) } }
            .toByteArray()
        if (!oldTags.startsWith(TAGS)) throw IOException("No OpusTags")
        val (vendor, comments) = parseComments(oldTags)

        val replaced = setOf("TITLE", "ARTIST", "ALBUM")
        val keepPicture = cover == null && !removeCover
        val kept = comments.filter { c ->
            val key = c.substringBefore('=').uppercase()
            key !in replaced && (keepPicture || key != PICTURE)
        }
        val fresh = buildList {
            if (title.isNotBlank()) add("TITLE=$title")
            if (artist.isNotBlank()) add("ARTIST=$artist")
            if (album.isNotBlank()) add("ALBUM=$album")
            addAll(kept)
            cover?.let { add("$PICTURE=" + Base64.getEncoder().encodeToString(pictureBlock(it))) }
        }
        val serial = pages[0].serial
        val newTagPages = paginate(buildComments(vendor, fresh), serial)

        val temp = File(file.parentFile, file.name + ".tags")
        temp.outputStream().buffered().use { out ->
            var seq = 0
            fun emit(page: Page) = out.write(page.copy(sequence = seq++).encode())
            emit(pages[0])
            newTagPages.forEach(::emit)
            pages.subList(tagsEnd + 1, pages.size).forEach(::emit)
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private class Page(
        val headerType: Int,
        val granule: Long,
        val serial: Int,
        val sequence: Int,
        val lacing: IntArray,
        val body: ByteArray,
    ) {
        fun copy(sequence: Int) = Page(headerType, granule, serial, sequence, lacing, body)

        fun encode(): ByteArray {
            val header = ByteBuffer.allocate(27 + lacing.size).order(ByteOrder.LITTLE_ENDIAN)
            header.put(CAPTURE).put(0).put(headerType.toByte()).putLong(granule).putInt(serial).putInt(sequence)
                .putInt(0).put(lacing.size.toByte())
            lacing.forEach { header.put(it.toByte()) }
            val page = header.array() + body
            val crc = oggCrc(page)
            ByteBuffer.wrap(page, 22, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc)
            return page
        }
    }

    private fun readPages(file: File): List<Page> {
        val pages = ArrayList<Page>()
        DataInputStream(file.inputStream().buffered()).use { input ->
            val header = ByteArray(27)
            while (true) {
                val n = input.read(header)
                if (n <= 0) break
                if (n < 27) input.readFully(header, n, 27 - n)
                if (!header.copyOfRange(0, 4).contentEquals(CAPTURE)) throw IOException("Broken Ogg page")
                val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val lacing = IntArray(header[26].toInt() and 0xFF) { input.readUnsignedByte() }
                val body = ByteArray(lacing.sum()).also { input.readFully(it) }
                pages += Page(header[5].toInt() and 0xFF, b.getLong(6), b.getInt(14), b.getInt(18), lacing, body)
            }
        }
        return pages
    }

    /** One packet over as many pages as it needs (255 segments of up to 255 bytes each). */
    private fun paginate(packet: ByteArray, serial: Int): List<Page> {
        // Lacing: 255s, then the remainder (a 0 if the size divides evenly, so the packet visibly ends).
        val lacing = IntArray(packet.size / 255) { 255 } + (packet.size % 255)
        val pages = ArrayList<Page>()
        var segment = 0
        var offset = 0
        while (segment < lacing.size) {
            val chunk = lacing.copyOfRange(segment, minOf(segment + 255, lacing.size))
            val size = chunk.sum()
            val last = segment + chunk.size == lacing.size
            pages += Page(
                headerType = if (segment == 0) 0 else 1,
                // No packet ends on a page that's all continuation: granule -1, as the spec asks.
                granule = if (last) 0 else -1,
                serial = serial,
                sequence = 0,
                lacing = chunk,
                body = packet.copyOfRange(offset, offset + size),
            )
            segment += chunk.size
            offset += size
        }
        return pages
    }

    private fun parseComments(packet: ByteArray): Pair<String, List<String>> {
        val b = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        b.position(TAGS.size)
        fun string(): String {
            val len = b.int
            val bytes = ByteArray(len).also { b.get(it) }
            return String(bytes, Charsets.UTF_8)
        }
        val vendor = string()
        val count = b.int
        return vendor to List(count) { string() }
    }

    private fun buildComments(vendor: String, comments: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        fun int(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun string(s: String) = s.toByteArray(Charsets.UTF_8).let { int(it.size); out.write(it) }
        out.write(TAGS)
        string(vendor)
        int(comments.size)
        comments.forEach(::string)
        return out.toByteArray()
    }

    /** FLAC's picture block, which Opus and Vorbis players read from METADATA_BLOCK_PICTURE. */
    private fun pictureBlock(jpeg: ByteArray): ByteArray {
        // Only the size is needed; the pixels go right away.
        val size = com.ku.pierdoon.platform.Images.decode(jpeg)?.use { it.width to it.height }
        val mime = "image/jpeg".toByteArray()
        return ByteBuffer.allocate(32 + mime.size + jpeg.size)
            .putInt(3) // front cover
            .putInt(mime.size).put(mime)
            .putInt(0) // no description
            .putInt(size?.first ?: 0)
            .putInt(size?.second ?: 0)
            .putInt(24)
            .putInt(0)
            .putInt(jpeg.size).put(jpeg)
            .array()
    }

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

    private val CAPTURE = "OggS".toByteArray()
    private val HEAD = "OpusHead".toByteArray()
    private val TAGS = "OpusTags".toByteArray()
    private const val PICTURE = "METADATA_BLOCK_PICTURE"

    private val CRC_TABLE = IntArray(256) { i ->
        var r = i shl 24
        repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
        r
    }

    /** Ogg's CRC-32: polynomial 0x04C11DB7, not reflected, starting from 0, over the page with its CRC zeroed. */
    private fun oggCrc(page: ByteArray): Int {
        var crc = 0
        for (byte in page) crc = (crc shl 8) xor CRC_TABLE[((crc ushr 24) xor (byte.toInt() and 0xFF)) and 0xFF]
        return crc
    }
}
