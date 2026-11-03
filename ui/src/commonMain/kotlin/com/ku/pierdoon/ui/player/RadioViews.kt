package com.ku.pierdoon.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.radio_my_wave
import com.ku.pierdoon.ui.resources.radio_new
import com.ku.pierdoon.ui.resources.radio_offline
import com.ku.pierdoon.ui.resources.radio_offline_short
import org.jetbrains.compose.resources.stringResource

/** What a player shows of the radio: what it plays from, whether this track is a new one, and its buttons. */
@Immutable
data class PlayerRadio(
    /** A track's or an artist's name; empty for the radio from the whole library. */
    val seed: String,
    val fresh: Boolean,
    val offline: Boolean,
    val onDislike: () -> Unit,
    val onStop: () -> Unit,
)

/**
 * The player's title while the radio plays, in place of "Now playing": the radio's icon and what
 * it plays from; offline, a crossed-out cloud and a word of it, on the same line.
 */
@Composable
fun RadioTitle(radio: PlayerRadio, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            if (radio.offline) PlayerIcons.CloudOff else PlayerIcons.Radio,
            contentDescription = if (radio.offline) stringResource(Res.string.radio_offline) else null,
            tint = if (radio.offline) colors.onSurfaceVariant else colors.tertiary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            radio.seed.ifEmpty { stringResource(Res.string.radio_my_wave) },
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).widthIn(max = 200.dp),
        )
        if (radio.offline) {
            Text(
                " · " + stringResource(Res.string.radio_offline_short),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Before the title of a radio's track the library hasn't got. */
@Composable
fun NewBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        stringResource(Res.string.radio_new),
        style = MaterialTheme.typography.labelLarge,
        color = colors.onTertiary,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(CircleShape)
            .background(colors.tertiary)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
