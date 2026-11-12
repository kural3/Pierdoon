package com.ku.pierdoon.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.ku.pierdoon.data.Song

/** The home screen's cards: a little smaller on a phone, a little bigger in a window. */
internal expect object HomeSizes {
    /** The cover on the "continue" card (a wide window makes it bigger still). */
    val heroCover: Dp
    val mixCollage: Dp
    val mixGap: Dp
    val mixPlay: Dp
    val largeMixTitle: Boolean
}

/** Big covers of what was played last: a carousel on the phone, a plain row in a window. */
@Composable
internal expect fun RecentCovers(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit)
