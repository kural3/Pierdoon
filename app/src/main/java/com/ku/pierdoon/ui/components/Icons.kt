package com.ku.pierdoon.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The handful of glyphs the player needs, without pulling in an icon library. */
object PlayerIcons {
    val Play by lazy { icon("Play", "M8,5v14l11,-7z") }
    val Pause by lazy { icon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z") }
    val Next by lazy { icon("Next", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z") }
    val Previous by lazy { icon("Previous", "M6,6h2v12H6zM9.5,12l8.5,6V6z") }
    val Shuffle by lazy {
        icon(
            "Shuffle",
            "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 " +
                "17.96,7.46 20,9.5V4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04 -3.13,-3.13z"
        )
    }
    val Repeat by lazy {
        icon("Repeat", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z")
    }
    val RepeatOne by lazy {
        icon(
            "RepeatOne",
            "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4zM13,15V9h-1l-2,1v1h1.5v4H13z"
        )
    }
    val MusicNote by lazy {
        icon(
            "MusicNote",
            "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"
        )
    }
    val Lyrics by lazy {
        icon("Lyrics", "M14,17H4v2h10v-2zM20,9H4v2h16V9zM4,15h16v-2H4v2zM4,5v2h16V5H4z")
    }
    val Home by lazy { icon("Home", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z") }
    val Album by lazy {
        icon(
            "Album",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,16.5c-2.49,0 " +
                "-4.5,-2.01 -4.5,-4.5S9.51,7.5 12,7.5s4.5,2.01 4.5,4.5 -2.01,4.5 -4.5,4.5zM12,11c-0.55,0 " +
                "-1,0.45 -1,1s0.45,1 1,1 1,-0.45 1,-1 -0.45,-1 -1,-1z"
        )
    }
    val Person by lazy {
        icon(
            "Person",
            "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z"
        )
    }
    val Playlist by lazy {
        icon(
            "Playlist",
            "M15,6H3v2h12V6zM15,10H3v2h12v-2zM3,16h8v-2H3v2zM17,6v8.18c-0.31,-0.11 -0.65,-0.18 -1,-0.18 " +
                "-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3V8h3V6h-5z"
        )
    }
    val PlaylistAdd by lazy {
        icon("PlaylistAdd", "M14,10H2v2h12v-2zM14,6H2v2h12V6zM18,14v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zM2,16h8v-2H2v2z")
    }
    val PlayNext by lazy { icon("PlayNext", "M3,10h11v2H3zM3,6h11v2H3zM3,14h7v2H3zM16,13v8l6,-4z") }
    val Queue by lazy {
        icon("Queue", "M4,10h12v2H4zM4,6h12v2H4zM4,14h8v2H4zM14,14v6l5,-3z")
    }
    val Favorite by lazy {
        icon(
            "Favorite",
            "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 " +
                "14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"
        )
    }
    val FavoriteBorder by lazy {
        icon(
            "FavoriteBorder",
            "M16.5,3c-1.74,0 -3.41,0.81 -4.5,2.09C10.91,3.81 9.24,3 7.5,3 4.42,3 2,5.42 2,8.5c0,3.78 3.4,6.86 " +
                "8.55,11.54L12,21.35l1.45,-1.32C18.6,15.36 22,12.28 22,8.5 22,5.42 19.58,3 16.5,3zM12.1,18.55l-0.1,0.1 " +
                "-0.1,-0.1C7.14,14.24 4,11.39 4,8.5 4,6.5 5.5,5 7.5,5c1.54,0 3.04,0.99 3.57,2.36h1.87C13.46,5.99 " +
                "14.96,5 16.5,5c2,0 3.5,1.5 3.5,3.5 0,2.89 -3.14,5.74 -7.9,10.05z"
        )
    }
    val Add by lazy { icon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }
    val Back by lazy { icon("Back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z") }
    val Delete by lazy {
        icon("Delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    }
    val Edit by lazy {
        icon(
            "Edit",
            "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34" +
                "c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"
        )
    }
    val Remove by lazy { icon("Remove", "M19,13H5v-2h14v2z") }
    val Library by lazy {
        icon(
            "Library",
            "M20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM18,7h-3v5.5" +
                "c0,1.38 -1.12,2.5 -2.5,2.5S10,13.88 10,12.5s1.12,-2.5 2.5,-2.5c0.57,0 1.08,0.19 1.5,0.51V5h4v2zM4,6H2v14" +
                "c0,1.1 0.9,2 2,2h14v-2H4V6z"
        )
    }
    val Settings by lazy {
        icon(
            "Settings",
            "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 " +
                "0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 " +
                "-1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35" +
                "C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 " +
                "2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 " +
                "-0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54" +
                "c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 " +
                "1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94z" +
                "M12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"
        )
    }
    val Check by lazy { icon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z") }
    val ExpandMore by lazy { icon("ExpandMore", "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z") }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
