package com.ku.pierdoon.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

sealed interface Route {
    val key: String

    sealed interface Tab : Route
    data object Home : Tab { override val key = "home" }
    data object Library : Tab { override val key = "library" }
    data object Playlists : Tab { override val key = "playlists" }
    data object Settings : Tab { override val key = "settings" }

    data class AlbumDetail(val albumId: Long) : Route { override val key = "album:$albumId" }
    data class ArtistDetail(val name: String) : Route { override val key = "artist:$name" }
    data class PlaylistDetail(val playlistId: Long) : Route { override val key = "playlist:$playlistId" }
    data object Favorites : Route { override val key = "favorites" }

    companion object {
        val tabs = listOf(Home, Library, Playlists, Settings)

        fun decode(key: String): Route = when {
            key.startsWith("album:") -> AlbumDetail(key.removePrefix("album:").toLong())
            key.startsWith("artist:") -> ArtistDetail(key.removePrefix("artist:"))
            key.startsWith("playlist:") -> PlaylistDetail(key.removePrefix("playlist:").toLong())
            key == Favorites.key -> Favorites
            else -> tabs.firstOrNull { it.key == key } ?: Home
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
