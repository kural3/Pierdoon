package com.ku.pierdoon.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Where covers come from: each app finds and decodes them its own way (MediaStore and the
 * computer on Android, FFmpeg on Windows), the shared screens only place them. Each app's
 * PierdoonTheme provides its own.
 */
interface ArtworkLoader {
    @Composable
    fun Artwork(songId: Long, albumId: Long, modifier: Modifier, large: Boolean, placeholderIconSize: Dp)

    @Composable
    fun FileArtwork(file: File, version: Long, modifier: Modifier, large: Boolean, placeholderIconSize: Dp)
}

val LocalArtworkLoader = staticCompositionLocalOf<ArtworkLoader> { error("No ArtworkLoader: PierdoonTheme provides one") }

/** The track's cover, or its album's. */
@Composable
fun Artwork(
    songId: Long,
    albumId: Long,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    placeholderIconSize: Dp = 24.dp,
) = LocalArtworkLoader.current.Artwork(songId, albumId, modifier, large, placeholderIconSize)

/** A picture file of the app's own (a playlist cover); [version] changes when it does. */
@Composable
fun FileArtwork(
    file: File,
    version: Long,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    placeholderIconSize: Dp = 24.dp,
) = LocalArtworkLoader.current.FileArtwork(file, version, modifier, large, placeholderIconSize)
