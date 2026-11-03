package com.ku.pierdoon.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.stringResource

// The list rows, scaffold and headers both apps share are in ui (SongRows.kt).

/** Forms and settings stop growing here on a wide window; past it the rows only got longer. */
val CONTENT_MAX_WIDTH = 920.dp

/** Cover with the count and Play / Shuffle beside it, for album, artist and playlist screens. */
@Composable
fun CollectionHeader(
    cover: @Composable (Modifier) -> Unit,
    subtitle: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
    ) {
        cover(Modifier.size(184.dp))
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (enabled) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveIconButton(
                        onClick = onPlay,
                        icon = PlayerIcons.Play,
                        contentDescription = stringResource(Res.string.play_all),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        iconSize = 28.dp,
                        haptic = HapticFeedbackType.Confirm,
                        modifier = Modifier.size(width = 112.dp, height = 56.dp),
                    )
                    ExpressiveIconButton(
                        onClick = onShuffle,
                        icon = PlayerIcons.Shuffle,
                        contentDescription = stringResource(Res.string.shuffle_all),
                        iconSize = 24.dp,
                        haptic = HapticFeedbackType.Confirm,
                        modifier = Modifier.size(width = 72.dp, height = 56.dp),
                    )
                }
            }
        }
    }
}
