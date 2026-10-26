package com.ku.pierdoon.ui.remote

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ku.pierdoon.R
import com.ku.pierdoon.remote.PcTransfers
import com.ku.pierdoon.remote.TransferDirection
import com.ku.pierdoon.remote.TransferMessage

/**
 * What [PcTransfers] needs to say on screen: short messages as it starts or can't, and the
 * question when some tracks seem to be on the other side already. Lives at the app's root, so it
 * works from any screen the copy was started on.
 */
@Composable
fun TransferPrompts() {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        PcTransfers.messages.collect { m ->
            val text = when (m) {
                is TransferMessage.Started -> resources.getQuantityString(
                    if (m.direction == TransferDirection.ToPhone) R.plurals.transfer_started_phone else R.plurals.transfer_started_pc,
                    m.count, m.count,
                )
                TransferMessage.Offline -> resources.getString(R.string.transfer_offline)
                TransferMessage.OutdatedComputer -> resources.getString(R.string.transfer_outdated)
                TransferMessage.Unsupported -> resources.getString(R.string.transfer_unsupported)
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    val checking by PcTransfers.checking.collectAsStateWithLifecycle()
    LaunchedEffect(checking) {
        if (checking) Toast.makeText(context, R.string.transfer_checking, Toast.LENGTH_SHORT).show()
    }

    val pending by PcTransfers.pending.collectAsStateWithLifecycle()
    val p = pending ?: return
    val single = p.items.size == 1
    val allThere = p.duplicates.size == p.items.size
    AlertDialog(
        onDismissRequest = PcTransfers::dismiss,
        title = {
            Text(stringResource(if (p.direction == TransferDirection.ToPhone) R.string.transfer_dup_title_phone else R.string.transfer_dup_title_pc))
        },
        text = {
            Column {
                if (single) {
                    Text(stringResource(R.string.transfer_dup_one))
                } else {
                    Text(stringResource(R.string.transfer_dup_some, p.duplicates.size, p.items.size))
                    Spacer(Modifier.height(8.dp))
                    p.duplicates.take(SHOWN).forEach { item ->
                        Text(
                            listOf(item.artist, item.title).filter { it.isNotBlank() }.joinToString(" – "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (p.duplicates.size > SHOWN) {
                        Text(
                            "…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (allThere) {
                TextButton(onClick = { PcTransfers.confirm(includeDuplicates = true) }) {
                    Text(stringResource(R.string.transfer_dup_anyway))
                }
            } else {
                TextButton(onClick = { PcTransfers.confirm(includeDuplicates = false) }) {
                    Text(stringResource(R.string.transfer_dup_only_new) + " (${p.items.size - p.duplicates.size})")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = PcTransfers::dismiss) { Text(stringResource(R.string.cancel)) }
                if (!allThere) {
                    TextButton(onClick = { PcTransfers.confirm(includeDuplicates = true) }) {
                        Text(stringResource(R.string.transfer_dup_all))
                    }
                }
            }
        },
    )
}

private const val SHOWN = 5
