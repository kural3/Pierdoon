package com.ku.pierdoon.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.AppFont
import com.ku.pierdoon.data.MAX_CROSSFADE_SECONDS
import com.ku.pierdoon.playback.EQ_BANDS
import com.ku.pierdoon.playback.EQ_MAX_DB
import com.ku.pierdoon.playback.EqPreset
import com.ku.pierdoon.playback.PlaybackSpeed
import com.ku.pierdoon.ui.components.ChoiceChip
import com.ku.pierdoon.ui.components.ConnectedChoice
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.cornerDecor
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.data.AppLanguage
import com.ku.pierdoon.data.ColorSource
import com.ku.pierdoon.data.SharedSettings
import com.ku.pierdoon.ui.resources.language_english
import com.ku.pierdoon.ui.resources.language_russian
import com.ku.pierdoon.ui.resources.language_system
import com.ku.pierdoon.ui.resources.settings_crossfade_summary
import com.ku.pierdoon.ui.resources.settings_equalizer_off
import com.ku.pierdoon.ui.resources.settings_equalizer_on
import com.ku.pierdoon.ui.resources.settings_playback_speed_summary
import com.ku.pierdoon.ui.resources.colors_album
import com.ku.pierdoon.ui.resources.colors_custom
import com.ku.pierdoon.ui.resources.colors_wallpaper
import com.ku.pierdoon.ui.resources.settings_colors_album_desc
import com.ku.pierdoon.ui.resources.settings_colors_custom_desc
import com.ku.pierdoon.ui.resources.settings_colors_fallback
import com.ku.pierdoon.ui.resources.settings_colors_pick
import com.ku.pierdoon.ui.resources.crossfade_off
import com.ku.pierdoon.ui.resources.crossfade_seconds
import com.ku.pierdoon.ui.resources.eq_preset_bass
import com.ku.pierdoon.ui.resources.eq_preset_custom
import com.ku.pierdoon.ui.resources.eq_preset_electronic
import com.ku.pierdoon.ui.resources.eq_preset_flat
import com.ku.pierdoon.ui.resources.eq_preset_rock
import com.ku.pierdoon.ui.resources.eq_preset_vocal
import com.ku.pierdoon.ui.resources.font_custom
import com.ku.pierdoon.ui.resources.font_custom_pick
import com.ku.pierdoon.ui.resources.font_ibm_plex_mono
import com.ku.pierdoon.ui.resources.font_jetbrains_mono
import com.ku.pierdoon.ui.resources.font_system
import com.ku.pierdoon.ui.resources.font_system_mono
import com.ku.pierdoon.ui.resources.settings_crossfade_desc
import com.ku.pierdoon.ui.resources.settings_font
import com.ku.pierdoon.ui.resources.speed
import com.ku.pierdoon.ui.resources.speed_desc
import com.ku.pierdoon.ui.theme.fontFamilyFor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/*
 * The pieces both apps' settings are built from: groups of rounded cards (Android 16 style),
 * switches, actions, drop-down choices, and the items for speed, crossfade, equalizer and font.
 */

/** Shapes for grouped items: large outer corners, tight inner ones. */
fun groupShape(index: Int, count: Int): Shape {
    val outer = 28.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/** A titled run of items; without a title it just leaves a little room above. */
@Composable
fun Group(title: String?, items: () -> List<@Composable (Shape) -> Unit>) {
    val list = items()
    if (list.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp),
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            list.forEachIndexed { i, item -> item(groupShape(i, list.size)) }
        }
    }
}

@Composable
fun ItemContainer(
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
    ) { content() }
}

/** The colour a section's icon sits on: each heading of the list gets its own. */
enum class SectionTone { Primary, Secondary, Tertiary }

/** The section list's headings, in order, each in its own colour. */
fun sectionTone(groupIndex: Int): SectionTone = SectionTone.entries[groupIndex % SectionTone.entries.size]

private val SectionShapes = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Cookie6Sided,
    MaterialShapes.Gem, MaterialShapes.Pentagon, MaterialShapes.Clover8Leaf, MaterialShapes.Cookie7Sided,
    MaterialShapes.SoftBurst, MaterialShapes.Puffy,
)

/** A shape of its own for the section at [index] on the list, so each icon reads apart. */
@Composable
fun sectionIconShape(index: Int): Shape = SectionShapes[index % SectionShapes.size].toShape()

/**
 * The top of the settings list: the app's mark, name and version, and a chip with how updates
 * stand; tapping the chip opens the updates page.
 */
@Composable
fun SettingsHero(name: String, version: String, status: String, onStatusClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(colors.primaryContainer)
            .cornerDecor(MaterialShapes.Flower.toShape(), colors.onPrimaryContainer.copy(alpha = 0.07f), size = 170.dp)
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(76.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(colors.primary),
        ) {
            Icon(PlayerIcons.MusicNote, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.headlineSmallEmphasized, color = colors.onPrimaryContainer, maxLines = 1)
            Text(version, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer.copy(alpha = 0.75f), maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.onPrimaryContainer.copy(alpha = 0.10f))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onStatusClick()
                    }
                    .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Icon(PlayerIcons.Refresh, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A section on the main settings list: icon, name, a hint of what's inside, and a chevron. */
@Composable
fun SectionItem(
    shape: Shape,
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    tone: SectionTone = SectionTone.Secondary,
    iconShape: Shape = CircleShape,
    /** The section open beside the list, on a wide window. */
    selected: Boolean = false,
    /** Off where the section opens beside the list rather than on a page of its own. */
    chevron: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val (iconContainer, iconContent) = when (tone) {
        SectionTone.Primary -> colors.primaryContainer to colors.onPrimaryContainer
        SectionTone.Secondary -> colors.secondaryContainer to colors.onSecondaryContainer
        SectionTone.Tertiary -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    val haptics = LocalHapticFeedback.current
    ItemContainer(shape, color = if (selected) colors.secondaryContainer else colors.surfaceContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(iconShape)
                    .background(iconContainer),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconContent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (chevron) Icon(PlayerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SwitchItem(
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
fun ActionItem(shape: Shape, title: String, subtitle: String, onClick: () -> Unit) {
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

/** A card holding a choice of a few, side by side, under a title and a line of explanation. */
@Composable
fun ChoiceRowItem(
    shape: Shape,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    description: String? = null,
    title: String? = null,
) {
    ItemContainer(shape) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
            }
            ConnectedChoice(
                options = options,
                selected = selected,
                onSelect = onSelect,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

/**
 * One setting with many possible values: its name and current value on the card, the values in a
 * menu that drops down from it. Each value can be drawn in its own [optionStyle] (a font in its own
 * typeface); [extra] adds actions under the values, given a function that closes the menu.
 */
@Composable
fun <T> DropdownItem(
    shape: Shape,
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    optionStyle: @Composable (T) -> TextStyle = { MaterialTheme.typography.bodyLarge },
    extra: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (open) 180f else 0f)
    ItemContainer(shape) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        open = true
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        label(selected),
                        style = optionStyle(selected).copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Icon(
                    PlayerIcons.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrow),
                )
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.widthIn(min = 240.dp),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(label(option), style = optionStyle(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            open = false
                            onSelect(option)
                        },
                        trailingIcon = if (option == selected) {
                            { Icon(PlayerIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                    )
                }
                if (extra != null) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    extra { open = false }
                }
            }
        }
    }
}

/** The app's font: a menu of them, each name set in its own font, and a way to bring one's own. */
@Composable
fun FontItem(
    shape: Shape,
    font: AppFont,
    customFontPath: String?,
    customFontName: String?,
    onSelect: (AppFont) -> Unit,
    onPickFile: () -> Unit,
) {
    val fonts = AppFont.entries.filter { it != AppFont.Custom || customFontPath != null }
    DropdownItem(
        shape = shape,
        title = stringResource(Res.string.settings_font),
        options = fonts,
        selected = font.takeIf { it in fonts } ?: AppFont.System,
        label = { fontLabel(it, customFontName) },
        onSelect = onSelect,
        optionStyle = { f ->
            val family = fontFamilyFor(f, customFontPath)
            MaterialTheme.typography.bodyLarge.let { if (family != null) it.copy(fontFamily = family) else it }
        },
        extra = { close ->
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.font_custom_pick)) },
                leadingIcon = { Icon(PlayerIcons.Add, contentDescription = null) },
                onClick = {
                    close()
                    onPickFile()
                },
            )
        },
    )
}

@Composable
fun fontLabel(font: AppFont, customName: String?): String = when (font) {
    AppFont.System -> stringResource(Res.string.font_system)
    AppFont.SystemMono -> stringResource(Res.string.font_system_mono)
    AppFont.JetBrainsMono -> stringResource(Res.string.font_jetbrains_mono)
    AppFont.IbmPlexMono -> stringResource(Res.string.font_ibm_plex_mono)
    AppFont.Custom -> customName ?: stringResource(Res.string.font_custom)
}

/**
 * Where the palette comes from, out of [sources] (the wallpaper only where the system has its
 * colours), with the colour picker under it when a colour is used.
 */
@Composable
fun ColorsItem(
    shape: Shape,
    sources: List<ColorSource>,
    source: ColorSource,
    seed: Int,
    onSource: (ColorSource) -> Unit,
    onSeed: (Int) -> Unit,
    /** What the wallpaper option means here: the phone's wallpaper or the Windows accent colour. */
    wallpaperDescription: String,
) {
    val shown = source.takeIf { it in sources } ?: ColorSource.Custom
    ItemContainer(shape) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                when (shown) {
                    ColorSource.Wallpaper -> wallpaperDescription
                    ColorSource.AlbumArt -> stringResource(Res.string.settings_colors_album_desc)
                    ColorSource.Custom -> stringResource(Res.string.settings_colors_custom_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ConnectedChoice(
                options = sources.map {
                    stringResource(
                        when (it) {
                            ColorSource.Wallpaper -> Res.string.colors_wallpaper
                            ColorSource.AlbumArt -> Res.string.colors_album
                            ColorSource.Custom -> Res.string.colors_custom
                        }
                    )
                },
                selected = sources.indexOf(shown),
                onSelect = { i -> onSource(sources[i]) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            if (shown != ColorSource.Wallpaper) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(
                        if (shown == ColorSource.AlbumArt) Res.string.settings_colors_fallback
                        else Res.string.settings_colors_pick
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SwatchRow(selected = seed, onSelect = onSeed)
                Spacer(Modifier.height(12.dp))
                HuePicker(seed = seed, onSeed = onSeed)
            }
            Spacer(Modifier.height(16.dp))
            PalettePreview()
        }
    }
}

/** "Speed 1× · crossfade 5 s", under Playback on the list of sections. */
@Composable
fun playbackSummary(settings: SharedSettings): String = listOfNotNull(
    stringResource(Res.string.settings_playback_speed_summary, PlaybackSpeed.label(settings.playbackSpeed)),
    settings.crossfadeSeconds.takeIf { it > 0 }?.let { stringResource(Res.string.settings_crossfade_summary, crossfadeLabel(it)) },
).joinToString(" · ")

/** "Off" or "On · Rock", under Equalizer on the list of sections. */
@Composable
fun equalizerSummary(settings: SharedSettings): String =
    if (settings.eqEnabled) stringResource(Res.string.settings_equalizer_on, stringResource(settings.eqPreset.label()))
    else stringResource(Res.string.settings_equalizer_off)

/** Each language under its own name, so it can be found from the other one. */
@Composable
fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.System -> Res.string.language_system
        AppLanguage.Russian -> Res.string.language_russian
        AppLanguage.English -> Res.string.language_english
    }
)

/** Slowed, as recorded or nightcore: the speed, pitch going along with it. */
@Composable
fun SpeedItem(shape: Shape, speed: Float, onChange: (Float) -> Unit) {
    ChoiceRowItem(
        shape = shape,
        options = PlaybackSpeed.options.map(PlaybackSpeed::label),
        selected = PlaybackSpeed.options.indexOf(speed),
        onSelect = { i -> onChange(PlaybackSpeed.options[i]) },
        description = stringResource(Res.string.speed_desc),
        title = stringResource(Res.string.speed),
    )
}

/** The crossfade length: off, or 1 to [MAX_CROSSFADE_SECONDS] seconds. */
@Composable
fun CrossfadeItem(shape: Shape, seconds: Int, onChange: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    // Follows the drag; the setting (and the player) only takes the value on release.
    var dragged by remember(seconds) { mutableIntStateOf(seconds) }
    ItemContainer(shape) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.settings_crossfade_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    crossfadeLabel(dragged),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = dragged.toFloat(),
                onValueChange = {
                    val s = it.roundToInt()
                    if (s != dragged) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    dragged = s
                },
                onValueChangeFinished = { onChange(dragged) },
                valueRange = 0f..MAX_CROSSFADE_SECONDS.toFloat(),
                steps = MAX_CROSSFADE_SECONDS - 1,
            )
        }
    }
}

@Composable
fun crossfadeLabel(seconds: Int): String =
    if (seconds == 0) stringResource(Res.string.crossfade_off) else stringResource(Res.string.crossfade_seconds, seconds)

/** Presets as chips over the bands; moving a band turns the preset into "Custom". */
@Composable
fun EqualizerItem(
    shape: Shape,
    enabled: Boolean,
    preset: EqPreset,
    gains: List<Float>,
    onPreset: (EqPreset) -> Unit,
    onBand: (band: Int, db: Float) -> Unit,
) {
    ItemContainer(shape) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EqPreset.entries.forEach { p ->
                    // "Custom" is where hand-moved bands end up, not something to pick.
                    if (p == EqPreset.Custom && preset != EqPreset.Custom) return@forEach
                    ChoiceChip(
                        selected = preset == p,
                        label = stringResource(p.label()),
                        onClick = { onPreset(p) },
                        enabled = enabled,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            EqualizerBands(
                frequencies = EQ_BANDS,
                gains = gains,
                maxDb = EQ_MAX_DB,
                enabled = enabled,
                onChange = onBand,
                // A wide window would spread ten narrow sliders far apart.
                modifier = Modifier.widthIn(max = 480.dp),
            )
        }
    }
}

fun EqPreset.label(): StringResource = when (this) {
    EqPreset.Flat -> Res.string.eq_preset_flat
    EqPreset.Bass -> Res.string.eq_preset_bass
    EqPreset.Vocal -> Res.string.eq_preset_vocal
    EqPreset.Rock -> Res.string.eq_preset_rock
    EqPreset.Electronic -> Res.string.eq_preset_electronic
    EqPreset.Custom -> Res.string.eq_preset_custom
}
