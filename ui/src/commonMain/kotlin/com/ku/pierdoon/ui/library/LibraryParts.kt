package com.ku.pierdoon.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.SmartPlaylist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.scaleBy

/** Pill that looks like a search field and opens the search page. */
@Composable
fun SearchCapsule(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.96f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(56.dp)
            .scaleBy(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(source, ripple()) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            }
            .padding(horizontal = 20.dp),
    ) {
        Icon(PlayerIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What the library's playlists section shows and does. */
class LibraryPlaylists(
    val playlists: List<Playlist>,
    val favoritesCount: Int,
    val resolveCover: (Playlist) -> Song?,
    val onCreate: () -> Unit,
    val onLongPress: (Playlist) -> Unit,
    val onImport: () -> Unit,
    val smart: List<SmartPlaylist>,
    val smartCount: (SmartPlaylist) -> Int,
    val onCreateSmart: () -> Unit,
    val onEditSmart: (SmartPlaylist) -> Unit,
)
