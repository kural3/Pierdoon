package com.ku.pierdoon.ui.download

import com.ku.pierdoon.ui.components.livelyCard
import com.ku.pierdoon.ui.components.rememberSpin
import com.ku.pierdoon.ui.components.spunBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.cornerDecor
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.download_empty
import com.ku.pierdoon.ui.resources.download_rights
import org.jetbrains.compose.resources.stringResource

/** What the download page is for, until there's something to show: a card in both apps. */
@Composable
fun EmptyHint() {
    val colors = MaterialTheme.colorScheme
    val spin = rememberSpin()
    Box(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .livelyCard(spin)
                .clip(RoundedCornerShape(32.dp))
                .background(colors.secondaryContainer)
                .cornerDecor(MaterialShapes.Cookie9Sided.toShape(), colors.onSecondaryContainer.copy(alpha = 0.07f), size = 180.dp, spin = spin)
                .padding(20.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp).spunBackground(MaterialShapes.Cookie9Sided.toShape(), colors.secondary, spin),
            ) {
                Icon(PlayerIcons.Download, contentDescription = null, tint = colors.onSecondary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.download_empty),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSecondaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(Res.string.download_rights),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSecondaryContainer.copy(alpha = 0.7f),
            )
            }
        }
    }
}
