package com.ku.pierdoon.ui.library

import com.ku.pierdoon.ui.components.ScrollableRow
import com.ku.pierdoon.ui.components.hairline
import com.ku.pierdoon.ui.library.TWO_PANE_WIDTH
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.splitArtists
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.BlurredArtwork
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.cornerDecor
import com.ku.pierdoon.ui.components.livelyCard
import com.ku.pierdoon.ui.components.rememberSpin
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.navigation.Route
import java.util.Calendar

internal actual object HomeSizes {
    actual val heroCover: Dp = 112.dp
    actual val mixCollage: Dp = 104.dp
    actual val mixGap: Dp = 16.dp
    actual val mixPlay: Dp = 56.dp
    actual val largeMixTitle: Boolean = true
}

/**
 * Big covers of what was played last. A plain row rather than the phone's carousel: the carousel
 * re-lays its items whenever its width changes, so opening the side panel made them jump.
 */
@Composable
internal actual fun RecentCovers(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    ScrollableRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        firstKey = songs.firstOrNull()?.id,
    ) {
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            val source = remember { MutableInteractionSource() }
            val scale = animatePressAsState(source, 0.95f)
            Box(
                Modifier
                    .size(200.dp)
                    .scaleBy(scale)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable(interactionSource = source, indication = ripple()) {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onPlay(songs, index)
                    },
            ) {
                Artwork(song.id, song.albumId, Modifier.fillMaxSize(), placeholderIconSize = 48.dp)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))
                        )
                )
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                )
            }
        }
    }
}
