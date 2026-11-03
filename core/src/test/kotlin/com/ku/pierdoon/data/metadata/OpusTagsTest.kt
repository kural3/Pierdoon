package com.ku.pierdoon.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class OpusTagsTest {

    private fun jpeg(width: Int, height: Int): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", it) }
            .toByteArray()

    @Test
    fun jpegSizeComesFromTheFrameHeader() {
        assertEquals(640 to 480, OpusTags.jpegSize(jpeg(640, 480)))
        assertEquals(1 to 1000, OpusTags.jpegSize(jpeg(1, 1000)))
    }

    @Test
    fun anythingElseHasNoSize() {
        assertNull(OpusTags.jpegSize(ByteArray(0)))
        assertNull(OpusTags.jpegSize("not a picture at all".toByteArray()))
        assertNull(OpusTags.jpegSize(jpeg(10, 10).copyOf(30)))
    }
}
