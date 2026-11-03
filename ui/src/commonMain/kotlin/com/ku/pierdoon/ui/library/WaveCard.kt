package com.ku.pierdoon.ui.library

import androidx.compose.foundation.background
import com.ku.pierdoon.ui.components.hairline
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.cornerDecor
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.radio_my_wave
import com.ku.pierdoon.ui.resources.radio_my_wave_desc
import org.jetbrains.compose.resources.stringResource

/** "Radio" on the home screen of both apps: the radio from what's played most and loved, with new music like it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WaveCard(onStart: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.97f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .scaleBy(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.tertiaryContainer)
            .hairline(RoundedCornerShape(28.dp))
            .cornerDecor(MaterialShapes.SoftBurst.toShape(), colors.onTertiaryContainer.copy(alpha = 0.08f), size = 150.dp)
            .clickable(interactionSource = source, indication = ripple()) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onStart()
            }
            .padding(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(colors.tertiary),
        ) {
            Icon(PlayerIcons.Radio, contentDescription = null, tint = colors.onTertiary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.radio_my_wave),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onTertiaryContainer,
                maxLines = 1,
            )
            Text(
                stringResource(Res.string.radio_my_wave_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onTertiaryContainer.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        ExpressiveIconButton(
            onClick = onStart,
            icon = PlayerIcons.Play,
            contentDescription = stringResource(Res.string.radio_my_wave),
            containerColor = colors.tertiary,
            contentColor = colors.onTertiary,
            iconSize = 28.dp,
            haptic = HapticFeedbackType.Confirm,
            modifier = Modifier.padding(end = 4.dp).size(52.dp),
        )
    }
}
