package com.ku.pierdoon.data

import com.ku.pierdoon.data.history.Play
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncTest {
    // The same three tracks, under different ids on each side; the phone also has one the PC lacks.
    private val a = TrackKey("Alpha", "Artist", 200_000)
    private val b = TrackKey("Beta", "Artist", 180_000)
    private val c = TrackKey("Gamma", "Other", 240_000)
    private val phoneOnly = TrackKey("Delta", "Solo", 100_000)
    private val phoneTracks = mapOf(1L to a, 2L to b, 3L to c, 4L to phoneOnly)
    private val pcTracks = mapOf(101L to a, 102L to TrackKey("beta", "artist", 181_000), 103L to c)

    private fun phone(fav: Set<Long> = emptySet(), lists: List<SyncPlaylist> = emptyList(), plays: List<Play> = emptyList()) =
        LibrarySnapshot(phoneTracks, fav, lists, emptyList(), plays)

    private fun pc(fav: Set<Long> = emptySet(), lists: List<SyncPlaylist> = emptyList(), plays: List<Play> = emptyList()) =
        LibrarySnapshot(pcTracks, fav, lists, emptyList(), plays)

    @Test
    fun tracksMatchByTagsAcrossIds() {
        assertEquals(mapOf(1L to 101L, 2L to 102L, 3L to 103L), LibrarySync.matchTracks(phoneTracks, pcTracks))
    }

    @Test
    fun firstSyncPutsFavouritesTogether() {
        val out = LibrarySync.merge(phone(fav = setOf(1, 4)), pc(fav = setOf(103)), base = null)
        assertEquals(setOf(3L), out.local.favoritesAdd)
        assertEquals(setOf(101L), out.remote.favoritesAdd)
        assertTrue(out.local.favoritesRemove.isEmpty() && out.remote.favoritesRemove.isEmpty())
        // The track the PC lacks isn't part of what was agreed.
        assertEquals(setOf(1L, 3L), out.base.favorites)
    }

    @Test
    fun removalOnOneSideReachesTheOther() {
        val base = SyncBase(setOf(1L, 3L), emptyMap(), emptyMap())
        // Un-favourited track 1 on the PC since; the phone still has both.
        val out = LibrarySync.merge(phone(fav = setOf(1, 3)), pc(fav = setOf(103)), base)
        assertEquals(setOf(1L), out.local.favoritesRemove)
        assertTrue(out.remote.favoritesAdd.isEmpty())
    }

    @Test
    fun playlistsMergeAddsAndRemovals() {
        val base = SyncBase(emptySet(), mapOf(9L to SyncPlaylist(9, "Mix", listOf(1, 2))), emptyMap())
        // Phone dropped 2 and added its own 4; the PC added 103.
        val out = LibrarySync.merge(
            phone(lists = listOf(SyncPlaylist(9, "Mix", listOf(1, 4)))),
            pc(lists = listOf(SyncPlaylist(9, "Mix", listOf(101, 102, 103)))),
            base,
        )
        assertEquals(listOf(SyncPlaylist(9, "Mix", listOf(1L, 4L, 3L))), out.local.playlistsPut)
        assertEquals(listOf(SyncPlaylist(9, "Mix", listOf(101L, 103L))), out.remote.playlistsPut)
    }

    @Test
    fun newAndDeletedPlaylists() {
        val base = SyncBase(emptySet(), mapOf(7L to SyncPlaylist(7, "Old", listOf(1))), emptyMap())
        val out = LibrarySync.merge(
            phone(lists = listOf(SyncPlaylist(8, "New", listOf(2, 4)))),
            pc(lists = listOf(SyncPlaylist(7, "Old", listOf(101)))),
            base,
        )
        assertEquals(setOf(7L), out.remote.playlistsDelete)
        assertEquals(listOf(SyncPlaylist(8, "New", listOf(102L))), out.remote.playlistsPut)
        assertTrue(out.local.playlistsPut.isEmpty())
    }

    @Test
    fun playsGoBothWaysOnce() {
        val out = LibrarySync.merge(
            phone(plays = listOf(Play(1, 1000, 60_000), Play(4, 2000, 60_000))),
            pc(plays = listOf(Play(101, 1000, 60_000), Play(103, 3000, 50_000))),
            base = null,
        )
        assertEquals(listOf(Play(3, 3000, 50_000)), out.local.plays)
        assertTrue(out.remote.plays.isEmpty())
    }

    @Test
    fun snapshotSurvivesJson() {
        val snap = phone(fav = setOf(1), lists = listOf(SyncPlaylist(5, "P", listOf(2, 3))), plays = listOf(Play(1, 5, 6)))
        assertEquals(snap, LibrarySnapshot.fromJson(JSONObject(snap.toJson().toString())))
        val changes = LibraryChanges(favoritesAdd = setOf(1), playlistsDelete = setOf(2), plays = listOf(Play(3, 4, 5)))
        assertEquals(changes, LibraryChanges.fromJson(JSONObject(changes.toJson().toString())))
    }

    @Test
    fun backupKeepsSettingsButNotAForeignFont() {
        val settings = CommonSettings(theme = ThemeMode.Dark, crossfadeSeconds = 6, font = AppFont.Custom, customFontFile = "x.ttf")
        val backup = Backup(1, "android", "2.7", settings, phone(fav = setOf(1)))
        val here = CommonSettings(font = AppFont.JetBrainsMono)
        val read = Backup.parse(backup.toJson().toString(), here)
        assertNotNull(read)
        read!!
        assertEquals(ThemeMode.Dark, read.settings!!.theme)
        assertEquals(6, read.settings!!.crossfadeSeconds)
        assertEquals(AppFont.JetBrainsMono, read.settings!!.font)
        assertEquals(setOf(1L), read.library.favorites)
    }
}
