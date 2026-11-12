package com.ku.pierdoon.ui.library

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.unknown_artist
import org.jetbrains.compose.resources.stringResource

/** A cover with a title and a line under it, for rows of albums, artists and playlists. */
@Composable
fun CoverCard(
    title: String,
    subtitle: String,
    songId: Long,
    albumId: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
    shape: Shape = RoundedCornerShape(28.dp),
    onLongClick: (() -> Unit)? = null,
    /** Draws the picture instead of the track's art; gets the square, unclipped bounds. */
    art: (@Composable (Modifier) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.94f)
    Column(
        modifier = modifier
            .width(width)
            .scaleBy(scale)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickableCompat(source, onClick, onLongClick, haptics)
            .padding(4.dp),
    ) {
        if (art != null) {
            art(Modifier.fillMaxWidth().aspectRatio(1f))
        } else {
            Artwork(
                songId = songId,
                albumId = albumId,
                placeholderIconSize = 40.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = subtitle.ifEmpty { stringResource(Res.string.unknown_artist) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
