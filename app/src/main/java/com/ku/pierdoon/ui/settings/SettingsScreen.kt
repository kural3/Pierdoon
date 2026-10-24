package com.ku.pierdoon.ui.settings

import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.R
import com.ku.pierdoon.data.AppSettings
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.ThemeMode
import com.ku.pierdoon.ui.components.ConnectedChoice
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.library.AppScaffold
import com.ku.pierdoon.ui.library.listBottomPadding

/** Shapes for grouped items: large outer corners, tight inner ones (Android 16 style). */
internal fun groupShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    trackCount: Int,
    lyricsCacheSize: suspend () -> Long,
    onClearLyricsCache: () -> Unit,
    onRescan: () -> Unit,
    bottomInset: Dp,
) {
    val context = LocalContext.current
    var cacheRefresh by remember { mutableIntStateOf(0) }
    val cacheBytes by produceState(0L, cacheRefresh) { value = lyricsCacheSize() }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    AppScaffold(title = stringResource(R.string.nav_settings)) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = listBottomPadding(bottomInset),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "appearance") {
                Group(stringResource(R.string.settings_appearance)) {
                    listOf<@Composable (Shape) -> Unit>(
                        { shape ->
                            ItemContainer(shape) {
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(12.dp))
                                    ConnectedChoice(
                                        options = listOf(
                                            stringResource(R.string.theme_system),
                                            stringResource(R.string.theme_light),
                                            stringResource(R.string.theme_dark),
                                        ),
                                        selected = settings.theme.ordinal,
                                        onSelect = { i -> onUpdate { it.copy(theme = ThemeMode.entries[i]) } },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    )
                                }
                            }
                        },
                        { shape ->
                            // "Wallpaper" only exists on Android 12+, so the options adapt to the device.
                            val sources = buildList {
                                if (dynamicAvailable) add(ColorSource.Wallpaper)
                                add(ColorSource.AlbumArt)
                                add(ColorSource.Custom)
                            }
                            val source = settings.colorSource.takeIf { it in sources } ?: ColorSource.Custom
                            ItemContainer(shape) {
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                    Text(stringResource(R.string.settings_colors), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(
                                            when (source) {
                                                ColorSource.Wallpaper -> R.string.settings_colors_wallpaper_desc
                                                ColorSource.AlbumArt -> R.string.settings_colors_album_desc
                                                ColorSource.Custom -> R.string.settings_colors_custom_desc
                                            }
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    ConnectedChoice(
                                        options = sources.map {
                                            stringResource(
                                                when (it) {
                                                    ColorSource.Wallpaper -> R.string.colors_wallpaper
                                                    ColorSource.AlbumArt -> R.string.colors_album
                                                    ColorSource.Custom -> R.string.colors_custom
                                                }
                                            )
                                        },
                                        selected = sources.indexOf(source),
                                        onSelect = { i -> onUpdate { it.copy(colorSource = sources[i]) } },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    )
                                    if (source != ColorSource.Wallpaper) {
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            stringResource(
                                                if (source == ColorSource.AlbumArt) R.string.settings_colors_fallback
                                                else R.string.settings_colors_pick
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        SwatchRow(
                                            selected = settings.seedColor,
                                            onSelect = { seed -> onUpdate { it.copy(seedColor = seed) } },
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        HuePicker(
                                            seed = settings.seedColor,
                                            onSeed = { seed -> onUpdate { it.copy(seedColor = seed) } },
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    PalettePreview()
                                }
                            }
                        },
                        { shape ->
                            SwitchItem(
                                shape = shape,
                                title = stringResource(R.string.settings_black),
                                subtitle = stringResource(R.string.settings_black_desc),
                                checked = settings.pureBlack,
                                onChange = { v -> onUpdate { it.copy(pureBlack = v) } },
                            )
                        },
                        { shape ->
                            SwitchItem(
                                shape = shape,
                                title = stringResource(R.string.settings_glass),
                                subtitle = stringResource(R.string.settings_glass_desc),
                                checked = settings.glass,
                                onChange = { v -> onUpdate { it.copy(glass = v) } },
                            )
                        },
                        { shape ->
                            SwitchItem(
                                shape = shape,
                                title = stringResource(R.string.settings_floating_nav),
                                subtitle = stringResource(R.string.settings_floating_nav_desc),
                                checked = settings.floatingNav,
                                onChange = { v -> onUpdate { it.copy(floatingNav = v) } },
                            )
                        },
                        { shape ->
                            SwitchItem(
                                shape = shape,
                                title = stringResource(R.string.settings_wavy),
                                subtitle = stringResource(R.string.settings_wavy_desc),
                                checked = settings.wavyProgress,
                                onChange = { v -> onUpdate { it.copy(wavyProgress = v) } },
                            )
                        },
                    )
                }
            }
            item(key = "feedback") {
                Group(stringResource(R.string.settings_feedback)) {
                    listOf<@Composable (Shape) -> Unit>(
                        { shape ->
                            SwitchItem(
                                shape = shape,
                                title = stringResource(R.string.settings_haptics),
                                subtitle = stringResource(R.string.settings_haptics_desc),
                                checked = settings.haptics,
                                onChange = { v -> onUpdate { it.copy(haptics = v) } },
                            )
                        },
                        { shape ->
                            val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
                            SwitchItem(
                                shape = shape,
                                title = stringResource(R.string.settings_smooth),
                                subtitle = stringResource(
                                    if (supported) R.string.settings_smooth_desc else R.string.settings_smooth_unavailable
                                ),
                                checked = settings.smoothAnimations && supported,
                                enabled = supported,
                                onChange = { v -> onUpdate { it.copy(smoothAnimations = v) } },
                            )
                        },
                    )
                }
            }
            item(key = "playback") {
                Group(stringResource(R.string.settings_playback)) {
                    listOf<@Composable (Shape) -> Unit>(
                        { shape ->
                            SwitchItem(
                                shape = shape,
                                title = stringResource(R.string.settings_offload),
                                subtitle = stringResource(R.string.settings_offload_desc),
                                checked = settings.audioOffload,
                                onChange = { v -> onUpdate { it.copy(audioOffload = v) } },
                            )
                        },
                        { shape ->
                            SwitchItem(
                                shape = shape,
                                title = stringResource(R.string.settings_silence),
                                subtitle = stringResource(R.string.settings_silence_desc),
                                checked = settings.skipSilence,
                                onChange = { v -> onUpdate { it.copy(skipSilence = v) } },
                            )
                        },
                    )
                }
            }
            item(key = "lyrics") {
                val cleared = stringResource(R.string.settings_cache_cleared)
                Group(stringResource(R.string.settings_lyrics)) {
                    listOf<@Composable (Shape) -> Unit>(
                        { shape ->
                            SwitchItem(
                                shape = shape,
                                title = stringResource(R.string.settings_wifi_only),
                                subtitle = stringResource(R.string.settings_wifi_only_desc),
                                checked = settings.lyricsWifiOnly,
                                onChange = { v -> onUpdate { it.copy(lyricsWifiOnly = v) } },
                            )
                        },
                        { shape ->
                            ActionItem(
                                shape = shape,
                                title = stringResource(R.string.settings_clear_cache),
                                subtitle = stringResource(
                                    R.string.settings_cache_size,
                                    Formatter.formatShortFileSize(context, cacheBytes),
                                ),
                                onClick = {
                                    onClearLyricsCache()
                                    cacheRefresh++
                                    Toast.makeText(context, cleared, Toast.LENGTH_SHORT).show()
                                },
                            )
                        },
                    )
                }
            }
            item(key = "library") {
                val done = stringResource(R.string.settings_rescan_done)
                Group(stringResource(R.string.settings_library)) {
                    listOf<@Composable (Shape) -> Unit>(
                        { shape ->
                            ActionItem(
                                shape = shape,
                                title = stringResource(R.string.settings_rescan),
                                subtitle = stringResource(R.string.settings_rescan_desc) + " · " +
                                    stringResource(R.string.tracks_count, trackCount),
                                onClick = {
                                    onRescan()
                                    Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                                },
                            )
                        },
                    )
                }
            }
            item(key = "about") {
                Group(stringResource(R.string.settings_about)) {
                    listOf<@Composable (Shape) -> Unit>(
                        { shape ->
                            ItemContainer(shape) {
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.settings_version, version),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.settings_lyrics_source),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun Group(title: String, items: () -> List<@Composable (Shape) -> Unit>) {
    val list = items()
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            list.forEachIndexed { i, item -> item(groupShape(i, list.size)) }
        }
    }
}

@Composable
internal fun ItemContainer(shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) { content() }
}

@Composable
private fun SwitchItem(
    shape: Shape,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val toggle: (Boolean) -> Unit = { v ->
        haptics.performHapticFeedback(if (v) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onChange(v)
    }
    ItemContainer(shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { toggle(!checked) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = toggle,
                enabled = enabled,
                thumbContent = if (checked) {
                    { Icon(PlayerIcons.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ActionItem(shape: Shape, title: String, subtitle: String, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    ItemContainer(shape) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                }
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
