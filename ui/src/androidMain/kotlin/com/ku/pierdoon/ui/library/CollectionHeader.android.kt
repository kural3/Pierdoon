package com.ku.pierdoon.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.ui.resources.Res
import org.jetbrains.compose.resources.stringResource

/** Big cover, title and Play / Shuffle pills for album, artist and playlist screens. */
@Composable
actual fun CollectionHeader(
    cover: @Composable (Modifier) -> Unit,
    subtitle: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    enabled: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        cover(Modifier.size(200.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        if (enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ExpressiveIconButton(
                    onClick = onPlay,
                    icon = PlayerIcons.Play,
                    contentDescription = stringResource(Res.string.play_all),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    iconSize = 28.dp,
                    haptic = HapticFeedbackType.Confirm,
                    modifier = Modifier.weight(1.4f).height(64.dp),
                )
                ExpressiveIconButton(
                    onClick = onShuffle,
                    icon = PlayerIcons.Shuffle,
                    contentDescription = stringResource(Res.string.shuffle_all),
                    iconSize = 26.dp,
                    haptic = HapticFeedbackType.Confirm,
                    modifier = Modifier.weight(1f).height(64.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
