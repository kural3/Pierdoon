package com.ku.pierdoon.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.ExpressiveToggleButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.add_to_favorites
import com.ku.pierdoon.ui.resources.lyrics
import com.ku.pierdoon.ui.resources.queue
import com.ku.pierdoon.ui.resources.radio_dislike
import com.ku.pierdoon.ui.resources.radio_stop
import com.ku.pierdoon.ui.resources.remove_from_favorites
import com.ku.pierdoon.ui.resources.repeat
import com.ku.pierdoon.ui.resources.shuffle
import org.jetbrains.compose.resources.stringResource

/**
 * The player's four toggles in one capsule of glass: shuffle, lyrics, queue and repeat, or, on
 * the radio, "not this", lyrics, queue and stop. Unchecked buttons sit flat on the capsule; a
 * checked one fills with the primary colour and squares off.
 */
@Composable
fun PlayerToggles(
    shuffle: Boolean,
    repeatOn: Boolean,
    repeatOne: Boolean,
    showLyrics: Boolean,
    showQueue: Boolean,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    radio: PlayerRadio?,
    glass: Color,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
) {
    val flat = Color.Transparent
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(glass)
            .padding(5.dp),
    ) {
        val cell = Modifier.weight(1f).fillMaxHeight()
        // The radio picks its own order and never ends: "not this" and "stop" in place of
        // shuffle and repeat.
        if (radio != null) {
            ExpressiveToggleButton(
                checked = false,
                onClick = radio.onDislike,
                icon = PlayerIcons.ThumbDown,
                contentDescription = stringResource(Res.string.radio_dislike),
                uncheckedColor = flat,
                modifier = cell,
            )
        } else {
            ExpressiveToggleButton(
                checked = shuffle,
                onClick = onShuffle,
                icon = PlayerIcons.Shuffle,
                contentDescription = stringResource(Res.string.shuffle),
                uncheckedColor = flat,
                modifier = cell,
            )
        }
        ExpressiveToggleButton(
            checked = showLyrics,
            onClick = onLyrics,
            icon = PlayerIcons.Lyrics,
            contentDescription = stringResource(Res.string.lyrics),
            uncheckedColor = flat,
            modifier = cell,
        )
        ExpressiveToggleButton(
            checked = showQueue,
            onClick = onQueue,
            icon = PlayerIcons.Queue,
            contentDescription = stringResource(Res.string.queue),
            uncheckedColor = flat,
            modifier = cell,
        )
        if (radio != null) {
            ExpressiveToggleButton(
                checked = false,
                onClick = radio.onStop,
                icon = PlayerIcons.Close,
                contentDescription = stringResource(Res.string.radio_stop),
                uncheckedColor = flat,
                modifier = cell,
            )
        } else {
            ExpressiveToggleButton(
                checked = repeatOn,
                onClick = onRepeat,
                icon = if (repeatOne) PlayerIcons.RepeatOne else PlayerIcons.Repeat,
                contentDescription = stringResource(Res.string.repeat),
                uncheckedColor = flat,
                modifier = cell,
            )
        }
    }
}

/** Heart toggle that "pops" (overshoots and settles) when a track is added to favourites. */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val pop = remember { Animatable(1f) }
    val popSpec = remember { spring<Float>(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(isFavorite) {
        if (first) { first = false; return@LaunchedEffect }
        if (isFavorite) {
            pop.snapTo(1.35f)
            pop.animateTo(1f, popSpec)
        }
    }
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val tint by animateColorAsState(if (isFavorite) colors.onTertiaryContainer else colors.onSurface, effects, label = "heart")
    // Loved: the heart sits on its own tertiary chip rather than the glass.
    val container by animateColorAsState(if (isFavorite) colors.tertiaryContainer else containerColor, effects, label = "heartChip")
    ExpressiveIconButton(
        onClick = onClick,
        icon = if (isFavorite) PlayerIcons.Favorite else PlayerIcons.FavoriteBorder,
        contentDescription = stringResource(if (isFavorite) Res.string.remove_from_favorites else Res.string.add_to_favorites),
        containerColor = container,
        contentColor = tint,
        restCorner = if (isFavorite) 30 else 50,
        haptic = if (isFavorite) HapticFeedbackType.ToggleOff else HapticFeedbackType.Confirm,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
    )
}
