package com.ku.pierdoon.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.desktop.ContextMenu
import com.ku.pierdoon.ui.desktop.DesktopSheet
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.unknown_artist
import org.jetbrains.compose.resources.stringResource

// On the computer: a small card at the pointer, with compact rows and a hand cursor.

@Composable
actual fun ActionSheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ContextMenu(onDismiss = onDismiss) {
        Column(content = content)
    }
}

@Composable
actual fun ActionSheetTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
actual fun ActionSheetDivider() = HorizontalDivider(Modifier.padding(vertical = 4.dp))

@Composable
actual fun SongSheetHeader(song: Song) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Artwork(song.id, song.albumId, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist.ifEmpty { stringResource(Res.string.unknown_artist) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
actual fun PanelSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DesktopSheet(onDismiss = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
            content = content,
        )
    }
}

@Composable
actual fun ActionItem(icon: ImageVector, label: String, destructive: Boolean, onClick: () -> Unit) {
    val error = MaterialTheme.colorScheme.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (destructive) error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (destructive) error else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
