package com.ku.pierdoon.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uTest {
    private fun song(id: Long, title: String, artist: String = "", ms: Long = 200_000, path: String = "") =
        Song(id, title, artist, "", 0, ms, path = path)

    private fun match(text: String, songs: List<Song>, base: String? = null) =
        M3uMatcher(songs, { it.path }, base).match(parseM3u(text))

    @Test
    fun readsNameTagsAndLocations() {
        val file = parseM3u(
            """
            #EXTM3U
            #PLAYLIST:Дорога
            #EXTINF:215,Кино - Группа крови
            D:\Music\a.mp3

            # a comment
            b.flac
            """.trimIndent()
        )
        assertEquals("Дорога", file.name)
        assertEquals(
            listOf(M3uEntry("D:\\Music\\a.mp3", "Группа крови", "Кино", 215_000), M3uEntry("b.flac")),
            file.entries,
        )
    }

    @Test
    fun unknownLengthAndAttributesAreNotALength() {
        assertEquals(0L, parseM3u("#EXTINF:-1,A - B\nx.mp3").entries.single().durationMs)
        assertEquals(12_000L, parseM3u("#EXTINF:12 logo=\"x\",A - B\nx.mp3").entries.single().durationMs)
    }

    @Test
    fun titleWithoutArtistStaysWhole() {
        val entry = parseM3u("#EXTINF:1,Just a title\nx.mp3").entries.single()
        assertEquals("Just a title", entry.title)
        assertEquals("", entry.artist)
    }

    @Test
    fun writesWhatItReads() {
        val song = song(1, "Группа крови", "Кино", 215_000, "/storage/a.mp3")
        val back = parseM3u(writeM3u("Дорога", listOf(song)) { it.path })
        assertEquals("Дорога", back.name)
        assertEquals(listOf(M3uEntry("/storage/a.mp3", "Группа крови", "Кино", 215_000)), back.entries)
    }

    @Test
    fun aLineBreakInATagCannotBecomeATrack() {
        val song = song(1, "Title\n/etc/passwd", path = "a.mp3")
        assertEquals(listOf("a.mp3"), parseM3u(writeM3u("x\ny", listOf(song)) { it.path }).entries.map { it.location })
    }

    @Test
    fun tracksWithNowhereToPointAtAreLeftOut() {
        assertEquals(emptyList<M3uEntry>(), parseM3u(writeM3u("x", listOf(song(1, "a"))) { "" }).entries)
    }

    @Test
    fun decodesUtf8WithBomAndOldWindowsFiles() {
        assertEquals("Кино", decodeM3u(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Кино".toByteArray()))
        assertEquals("Кино", decodeM3u("Кино".toByteArray(charset("windows-1251"))))
    }

    @Test
    fun aPathMatchesWhateverWayItIsWritten() {
        val songs = listOf(song(1, "a", path = "C:\\Music\\A.mp3"))
        for (line in listOf("C:\\Music\\A.mp3", "c:/music/a.MP3", "file:///C:/Music/A.mp3")) {
            assertEquals(line, listOf(1L), match(line, songs).songIds)
        }
    }

    @Test
    fun fileUrlsAreDecodedKeepingPluses() {
        val songs = listOf(song(1, "a", path = "/music/a b+c.mp3"))
        assertEquals(listOf(1L), match("file:///music/a%20b+c.mp3", songs).songIds)
    }

    @Test
    fun relativePathsGoFromThePlaylistsFolder() {
        val songs = listOf(song(1, "a", path = "D:\\M\\x\\a.mp3"), song(2, "a", path = "D:\\M\\y\\a.mp3"))
        assertEquals(listOf(2L), match("y\\a.mp3", songs, base = "D:\\M").songIds)
    }

    @Test
    fun aFileFromAnotherDeviceMatchesByName() {
        val songs = listOf(song(1, "a", path = "D:\\Music\\a.mp3"))
        assertEquals(listOf(1L), match("/storage/emulated/0/Music/a.mp3", songs).songIds)
    }

    @Test
    fun aSameNamedFileWithAnotherTitleIsAnotherSong() {
        val songs = listOf(song(1, "Other", path = "D:\\Music\\01.mp3"))
        assertEquals(M3uMatch(emptyList(), 1), match("#EXTINF:200,Кино - Звезда\n/sd/01.mp3", songs))
    }

    @Test
    fun sameFileNameGoesToTheOneSharingMoreFolders() {
        val songs = listOf(song(1, "a", path = "D:\\M\\x\\a.mp3"), song(2, "b", path = "D:\\M\\y\\a.mp3"))
        assertEquals(listOf(2L), match("/sd/y/a.mp3", songs).songIds)
    }

    @Test
    fun sameFileNameWithNothingToTellThemApartIsNotGuessed() {
        val songs = listOf(song(1, "a", path = "D:\\x\\a.mp3"), song(2, "b", path = "D:\\y\\a.mp3"))
        assertEquals(M3uMatch(emptyList(), 1), match("/sd/z/a.mp3", songs))
    }

    @Test
    fun otherwiseTheTagsDecide() {
        val songs = listOf(song(1, "Группа крови", "Кино", 215_000, "D:\\one.mp3"))
        assertEquals(listOf(1L), match("#EXTINF:216,КИНО - группа крови\n/sd/other.m4a", songs).songIds)
        assertEquals(M3uMatch(emptyList(), 1), match("#EXTINF:100,Кино - Группа крови\n/sd/other.m4a", songs))
    }

    @Test
    fun aFileNamedArtistTitleServesAsTags() {
        val songs = listOf(song(1, "Группа крови", "Кино", path = "D:\\one.mp3"))
        assertEquals(listOf(1L), match("/sd/Кино - Группа крови.mp3", songs).songIds)
    }

    @Test
    fun whatIsNotThereIsCountedAndOrderKept() {
        val songs = listOf(song(1, "a", path = "/m/a.mp3"), song(2, "b", path = "/m/b.mp3"))
        assertEquals(M3uMatch(listOf(2L, 1L), 2), match("/m/b.mp3\nhttp://radio/stream.mp3\n/m/c.mp3\n/m/a.mp3", songs))
    }

    @Test
    fun aUrlIsNeverMatchedByItsFileName() {
        val songs = listOf(song(1, "a", path = "/m/a.mp3"))
        assertEquals(M3uMatch(emptyList(), 1), match("https://example.com/a.mp3", songs))
    }

    @Test
    fun anEmptyFileHasNoName() {
        assertNull(parseM3u("").name)
    }
}
