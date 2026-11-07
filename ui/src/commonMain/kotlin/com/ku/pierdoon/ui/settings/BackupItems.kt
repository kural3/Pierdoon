package com.ku.pierdoon.ui.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import com.ku.pierdoon.data.Backup
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import java.text.DateFormat
import java.util.Date

/** The Backup page of both apps: save one to a file, or restore from one. */
fun LazyListScope.backupItems(onExport: () -> Unit, onImport: () -> Unit) {
    item(key = "backup") {
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.backup_export),
                        subtitle = stringResource(Res.string.backup_export_desc),
                        onClick = onExport,
                    )
                },
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.backup_import),
                        subtitle = stringResource(Res.string.backup_import_desc),
                        onClick = onImport,
                    )
                },
            )
        }
    }
}

/** "Find duplicates" on the Library page of both apps. */
fun LazyListScope.duplicatesItem(onOpen: () -> Unit) {
    item(key = "duplicates") {
        Group(null) {
            listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    ActionItem(
                        shape = shape,
                        title = stringResource(Res.string.duplicates_find),
                        subtitle = stringResource(Res.string.duplicates_find_desc),
                        onClick = onOpen,
                    )
                },
            )
        }
    }
}

/** Asks before a backup is restored over what's here. */
@Composable
fun RestoreBackupDialog(backup: Backup, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val made = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(backup.createdAtMs))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.backup_restore_title)) },
        text = { Text(stringResource(Res.string.backup_restore_text, made)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.backup_restore)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
