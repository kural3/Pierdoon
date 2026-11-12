package com.ku.pierdoon.ui.library

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.ku.pierdoon.data.Song

// Actions on something (a track, a few tracks, a playlist): a sheet from the bottom on the phone,
// a menu at the pointer on the computer. The actions are the same; the frame is each app's own.

/** The sheet or menu itself; [content] is its header and [ActionItem]s. */
@Composable
expect fun ActionSheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit)

/** What the actions are on, as a line at the top. */
@Composable
expect fun ActionSheetTitle(title: String)

/** Between the header and the actions. */
@Composable
expect fun ActionSheetDivider()

/** A track's cover, title and artist, as the header. */
@Composable
expect fun SongSheetHeader(song: Song)

/** One line of an action sheet. */
@Composable
expect fun ActionItem(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit)

/** A titled list of actions. */
@Composable
fun ActionSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ActionSheetFrame(onDismiss) {
        ActionSheetTitle(title)
        ActionSheetDivider()
        content()
    }
}

/** For more than a list of actions (a playlist's cover): a sheet on the phone, a card in the middle of the window on the computer. */
@Composable
expect fun PanelSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit)
