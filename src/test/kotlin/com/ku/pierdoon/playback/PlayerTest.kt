package com.ku.pierdoon.playback

import com.ku.pierdoon.data.Song
import com.ku.pierdoon.platform.MediaProbe
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.swing.SwingUtilities

/**
 * The queue on the real engine, silently (volume 0). Skipped without a sound device or without
 * two tracks in the user's Music folder.
 */
class PlayerTest {

    private val music = File(System.getProperty("user.home"), "Music")
        .walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("mp3", "m4a", "flac", "opus", "ogg") }
        .sortedBy { it.name }.toList()

    private fun <T> ui(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun song(file: File): Song {
        val tags = MediaProbe.tags(file)!!
        return Song(
            id = Song.idFor(file), title = file.nameWithoutExtension, artist = "", album = "", albumId = 0,
            durationMs = tags.durationMs, path = file.absolutePath,
        )
    }

    @Test
    fun followsTheNextTrackWhenTheQueueChangesDuringTheSwitch() {
        assumeTrue(music.size >= 2 && AudioSystem.getMixerInfo().isNotEmpty())
        val first = song(music[0])
        val second = song(music[1])
        val player = ui { Player().apply { volume = 0f } }
        try {
            ui {
                player.setMediaItems(listOf(MediaItem(first), MediaItem(second)), 0, first.durationMs - 1_000)
                player.prepare()
                player.play()
            }
            // Queue edits all through the switch: some land after the engine has opened the second
            // track but before it is heard.
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline && ui { player.currentMediaItemIndex } == 0) {
                ui { player.addMediaItem(MediaItem(first)) }
                Thread.sleep(5)
            }
            assertEquals("still on the first track", 1, ui { player.currentMediaItemIndex })
            assertEquals(second.id, ui { player.currentMediaItem?.song?.id })
        } finally {
            ui { player.release() }
        }
    }
}
