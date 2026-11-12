package com.ku.pierdoon.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
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

    /** Whether the track has a cover at all, its own or its album's; kept current as covers change. */
    @Composable
    fun hasArt(songId: Long, albumId: Long): Boolean

    /** Full-bleed frosted art of the track, for behind the player and the home screen's card. */
    @Composable
    fun BlurredArtwork(songId: Long, albumId: Long, modifier: Modifier)

    /** A picture from the web (or the paired computer), decoded down to about [maxPx]; null if it can't be had. */
    suspend fun remotePicture(url: String, maxPx: Int): ImageBitmap?
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
) = LocalArtworkLoader.current.Artwork(songId, albumId, Modifier.lively(coverTilt(large)).then(modifier), large, placeholderIconSize)

/** A picture file of the app's own (a playlist cover); [version] changes when it does. */
@Composable
fun FileArtwork(
    file: File,
    version: Long,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    placeholderIconSize: Dp = 24.dp,
) = LocalArtworkLoader.current.FileArtwork(file, version, Modifier.lively(coverTilt(large)).then(modifier), large, placeholderIconSize)

/** Big covers lean a bit further; small ones in lists only a little. */
private fun coverTilt(large: Boolean): Float = if (large) 12f else 8f

/** The track's art, blurred to fill what's behind it. */
@Composable
fun BlurredArtwork(songId: Long, albumId: Long, modifier: Modifier = Modifier) =
    LocalArtworkLoader.current.BlurredArtwork(songId, albumId, modifier)
