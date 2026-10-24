package com.ku.pierdoon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.R
import com.ku.pierdoon.ui.components.PlayerIcons

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val entrance = remember { Animatable(0f) }
    val entranceSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { entrance.animateTo(1f, entranceSpec) }
    val cookie = MaterialShapes.Cookie9Sided.toShape()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(168.dp)
                .graphicsLayer {
                    val t = entrance.value
                    scaleX = 0.5f + 0.5f * t
                    scaleY = 0.5f + 0.5f * t
                    rotationZ = -120f * (1f - t)
                    shape = cookie
                    clip = true
                }
                .background(colors.primaryContainer),
        ) {
            Icon(
                imageVector = PlayerIcons.MusicNote,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onGrant()
            },
            modifier = Modifier.height(56.dp),
        ) {
            Text(stringResource(R.string.permission_grant), style = MaterialTheme.typography.titleMedium)
        }
    }
}
