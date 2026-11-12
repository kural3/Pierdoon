package com.ku.pierdoon.ui.player

import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.splitArtists
import com.ku.pierdoon.ui.library.EntryMenu
import com.ku.pierdoon.ui.library.MenuEntry
import com.ku.pierdoon.ui.components.PlayerIcons

/**
 * A track's artist tag that opens the artist; a tag naming several asks which one first.
 *
 * @param modifier goes around the area that opens it, [padding] inside it.
 * @param pick false where the tap leads to the same place whoever is chosen.
 */
@Composable
fun ArtistLink(
    artist: String,
    style: TextStyle,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(0.dp),
    pick: Boolean = true,
) {
    val artists = remember(artist) { splitArtists(artist) }
    var open by remember(artist) { mutableStateOf(false) }
    Box {
        Text(
            text = artist.ifEmpty { stringResource(Res.string.unknown_artist) },
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
                .clickable(
                    enabled = artist.isNotEmpty(),
                    onClickLabel = stringResource(Res.string.open_artist),
                ) {
                    if (pick && artists.size > 1) open = true else onOpen(artists.firstOrNull() ?: artist)
                }
                .padding(padding),
        )
        EntryMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(artists.map { name -> MenuEntry(name, PlayerIcons.Person) { onOpen(name) } }),
        )
    }
}
