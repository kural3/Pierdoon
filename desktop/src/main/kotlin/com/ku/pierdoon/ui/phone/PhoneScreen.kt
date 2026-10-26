package com.ku.pierdoon.ui.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.AppSettings
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.remote.RemoteServer
import com.ku.pierdoon.resources.*
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.library.AppScaffold
import com.ku.pierdoon.ui.library.CONTENT_MAX_WIDTH
import com.ku.pierdoon.ui.library.listBottomPadding
import com.ku.pierdoon.ui.settings.Group
import com.ku.pierdoon.ui.settings.ItemContainer
import com.ku.pierdoon.ui.settings.SwitchItem
import org.jetbrains.compose.resources.stringResource

/**
 * The Phone tab: lets the Pier.doon Android app drive this player over the local network
 * (see RemoteServer), shows the code to pair with and the phones paired so far.
 */
@Composable
fun PhoneScreen(settings: AppSettings, bottomInset: Dp) {
    val remote by RemoteServer.status.collectAsState()
    AppScaffold(title = stringResource(Res.string.nav_phone)) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = listBottomPadding(bottomInset),
            ),
            modifier = Modifier.fillMaxHeight().widthIn(max = CONTENT_MAX_WIDTH),
        ) {
            item(key = "remote") {
                Group(null) {
                    listOf<@Composable (Shape) -> Unit>(
                        { shape ->
                            SwitchItem(
                                shape = shape,
                                title = stringResource(Res.string.remote_control),
                                subtitle = remote.error?.let { stringResource(Res.string.remote_error, it) }
                                    ?: stringResource(Res.string.remote_control_desc),
                                checked = settings.remoteControl,
                                onChange = { v -> SettingsStore.update { it.copy(remoteControl = v) } },
                            )
                        },
                    )
                }
            }
            if (remote.running) {
                item(key = "pair") {
                    Group(null) {
                        listOf<@Composable (Shape) -> Unit>(
                            { shape ->
                                ValueItem(
                                    shape = shape,
                                    title = stringResource(Res.string.remote_pair_title),
                                    value = remote.pairingCode.chunked(3).joinToString(" "),
                                    subtitle = stringResource(Res.string.phone_hint),
                                    large = true,
                                )
                            },
                            { shape ->
                                ValueItem(
                                    shape = shape,
                                    title = stringResource(Res.string.remote_address),
                                    value = remote.addresses.joinToString("\n") { "$it:${remote.port}" }
                                        .ifEmpty { stringResource(Res.string.remote_address_none) },
                                    subtitle = null,
                                )
                            },
                        )
                    }
                }
            }
            item(key = "devices") {
                Group(stringResource(Res.string.remote_devices)) {
                    if (remote.devices.isEmpty()) {
                        listOf<@Composable (Shape) -> Unit>(
                            { shape ->
                                ItemContainer(shape) {
                                    Text(
                                        stringResource(Res.string.remote_devices_none),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                    )
                                }
                            },
                        )
                    } else {
                        remote.devices.map { device ->
                            { shape: Shape -> DeviceItem(shape, device.name) { RemoteServer.forget(device) } }
                        }
                    }
                }
            }
        }
    }
}

/** A setting that is read, not changed: a code, an address. */
@Composable
private fun ValueItem(shape: Shape, title: String, value: String, subtitle: String?, large: Boolean = false) {
    ItemContainer(shape) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (large) 20.dp else 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(
                    value,
                    style = if (large) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineSmall,
                    color = if (large) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DeviceItem(shape: Shape, name: String, onForget: () -> Unit) {
    ItemContainer(shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(PlayerIcons.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            IconButton(onClick = onForget) {
                Icon(PlayerIcons.Close, contentDescription = stringResource(Res.string.remote_forget))
            }
        }
    }
}
