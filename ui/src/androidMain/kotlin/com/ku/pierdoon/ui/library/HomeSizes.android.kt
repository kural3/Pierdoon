package com.ku.pierdoon.ui.library

import org.jetbrains.compose.resources.StringResource
import com.ku.pierdoon.ui.components.hairline
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.MusicCollections
import com.ku.pierdoon.data.Playlist
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.splitArtists
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.BlurredArtwork
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.cornerDecor
import com.ku.pierdoon.ui.components.livelyCard
import com.ku.pierdoon.ui.components.rememberSpin
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.navigation.Route
import java.util.Calendar

internal actual object HomeSizes {
    actual val heroCover: Dp = 96.dp
    actual val mixCollage: Dp = 88.dp
    actual val mixGap: Dp = 14.dp
    actual val mixPlay: Dp = 52.dp
    actual val largeMixTitle: Boolean = false
}

@Composable
internal actual fun RecentCovers(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val state = rememberCarouselState { songs.size }
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 200.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) { index ->
        val song = songs[index]
        Box(
            Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .clickable {
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
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .graphicsLayer {
                        // Follow the visible (masked) part of the item and fade out as it narrows,
                        // so squeezed items never show clipped, overlapping titles.
                        val info = carouselItemDrawInfo
                        translationX = info.maskRect.left
                        val open = if (info.maxSize > 0f) info.size / info.maxSize else 1f
                        alpha = ((open - 0.7f) / 0.3f).coerceIn(0f, 1f)
                    }
                    .padding(16.dp),
            )
        }
    }
}
