package com.ku.pierdoon.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.ku.pierdoon.ui.settings.SettingsSection

sealed interface Route {
    val key: String

    /** A page that can be a tab; which ones are tabs is each app's own list. */
    sealed interface Tab : Route
    data object Home : Tab { override val key = "home" }
    data object Library : Tab { override val key = "library" }
    /** The phone's playlists tab. */
    data object Playlists : Tab { override val key = "playlists" }
    /** A tab on the computer, a page on the phone. */
    data object Downloads : Tab { override val key = "downloads" }
    /** The computer's page for the phone. */
    data object Phone : Tab { override val key = "phone" }
    data object Settings : Tab { override val key = "settings" }

    data class AlbumDetail(val albumId: Long) : Route { override val key = "album:$albumId" }
    data class ArtistDetail(val name: String) : Route { override val key = "artist:$name" }
    data class PlaylistDetail(val playlistId: Long) : Route { override val key = "playlist:$playlistId" }
    data object Favorites : Route { override val key = "favorites" }
    data object DailyMix : Route { override val key = "dailymix" }
    data object Stats : Route { override val key = "stats" }
    data class YearReview(val year: Int) : Route { override val key = "year:$year" }
    data object Duplicates : Route { override val key = "duplicates" }
    data object Folders : Route { override val key = "folders" }
    data class FolderDetail(val path: String) : Route { override val key = "folder:$path" }
    data class SmartPlaylistDetail(val playlistId: Long) : Route { override val key = "smart:$playlistId" }
    data class EditSong(val songId: Long) : Route { override val key = "edit:$songId" }
    data class EditSongs(val songIds: List<Long>) : Route { override val key = "editmany:" + songIds.joinToString(",") }
    data object Search : Route { override val key = "search" }
    /** The phone's page for the computer. */
    data object Computer : Route { override val key = "computer" }
    data class SettingsPage(val section: SettingsSection) : Route { override val key = "settings:${section.name}" }

    companion object {
        private val allTabs = listOf(Home, Library, Playlists, Downloads, Phone, Settings)

        fun decode(key: String): Route = when {
            key.startsWith("album:") -> AlbumDetail(key.removePrefix("album:").toLong())
            key.startsWith("artist:") -> ArtistDetail(key.removePrefix("artist:"))
            key.startsWith("playlist:") -> PlaylistDetail(key.removePrefix("playlist:").toLong())
            key.startsWith("edit:") -> EditSong(key.removePrefix("edit:").toLong())
            key.startsWith("editmany:") -> EditSongs(key.removePrefix("editmany:").split(",").mapNotNull { it.toLongOrNull() })
            key == Favorites.key -> Favorites
            key == DailyMix.key -> DailyMix
            key == Stats.key -> Stats
            key.startsWith("year:") -> YearReview(key.removePrefix("year:").toInt())
            key.startsWith("smart:") -> SmartPlaylistDetail(key.removePrefix("smart:").toLong())
            key == Duplicates.key -> Duplicates
            key == Folders.key -> Folders
            key.startsWith("folder:") -> FolderDetail(key.removePrefix("folder:"))
            key == Search.key -> Search
            key == Computer.key -> Computer
            key.startsWith("settings:") -> SettingsSection.entries.firstOrNull { it.name == key.removePrefix("settings:") }
                ?.let(::SettingsPage) ?: Settings
            else -> allTabs.firstOrNull { it.key == key } ?: Home
        }
    }
}

/** Minimal back stack: a tab root plus pushed detail screens. Survives rotation and process death. */
@Stable
class Navigator(initial: List<Route>) {
    private val stack = mutableStateListOf<Route>().apply { addAll(initial.ifEmpty { listOf(Route.Home) }) }

    val current: Route get() = stack.last()
    val tab: Route.Tab get() = stack.first() as? Route.Tab ?: Route.Home
    val depth: Int get() = stack.size

    /** Direction of the last change, read by screen transitions. */
    var forward: Boolean = true
        private set

    /** The page a back action would reveal, or null when back should leave the app. */
    val previous: Route?
        get() = when {
            stack.size > 1 -> stack[stack.lastIndex - 1]
            current != Route.Home -> Route.Home
            else -> null
        }

    fun push(route: Route) {
        if (route == current) return
        forward = true
        stack.add(route)
    }

    /** @return false when there is nothing left to pop (let the system handle back). */
    fun pop(): Boolean {
        forward = false
        return when {
            stack.size > 1 -> { stack.removeAt(stack.lastIndex); true }
            current != Route.Home -> { stack[0] = Route.Home; true }
            else -> false
        }
    }

    /** Selecting the active tab again pops back to its root. */
    fun selectTab(tab: Route.Tab) {
        forward = true
        stack.clear()
        stack.add(tab)
    }

    internal fun keys(): List<String> = stack.map { it.key }
}

@Composable
fun rememberNavigator(): Navigator = rememberSaveable(
    saver = listSaver(save = { it.keys() }, restore = { keys -> Navigator(keys.map(Route::decode)) }),
) { Navigator(listOf(Route.Home)) }
