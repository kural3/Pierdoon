package com.ku.pierdoon.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReceiveTrackTest {
    @get:Rule val tmp = TemporaryFolder()

    private val bytes = ByteArray(200_000) { (it % 251).toByte() }

    @Test
    fun savesUnderItsNameAndBesideOneAlreadyThere() {
        val dir = tmp.newFolder()
        val first = receiveTrack(bytes.inputStream(), bytes.size.toLong(), "Artist - Song.mp3", dir)!!
        val second = receiveTrack(bytes.inputStream(), bytes.size.toLong(), "Artist - Song.mp3", dir)!!
        assertEquals("Artist - Song.mp3", first.name)
        assertEquals("Artist - Song (1).mp3", second.name)
        assertArrayEquals(bytes, second.readBytes())
        assertEquals(setOf("Artist - Song.mp3", "Artist - Song (1).mp3"), dir.list()!!.toSet())
    }

    @Test
    fun staysInsideTheFolder() {
        val dir = tmp.newFolder()
        val file = receiveTrack(bytes.inputStream(), bytes.size.toLong(), "..\\..\\evil/../x:y.flac", dir)!!
        assertEquals(dir, file.parentFile)
        assertEquals("x_y.flac", file.name)
    }

    @Test
    fun shortOrLongBodyLeavesNothing() {
        val dir = tmp.newFolder()
        assertNull(receiveTrack(bytes.inputStream(), bytes.size + 1L, "a.mp3", dir))
        assertNull(receiveTrack(bytes.inputStream(), bytes.size - 1L, "a.mp3", dir))
        assertEquals(0, dir.list()!!.size)
    }
}
