package com.ku.pierdoon.ui.components

import com.ku.pierdoon.data.ALBUM_ART_URI
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.staticCompositionLocalOf
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.ui.theme.SchemeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File

/**
 * Tiny album-art loader: an LRU of downsampled bitmaps keyed by album and size bucket,
 * plus a negative cache so albums without art never hit the disk twice.
 *
 * A track with a cover picked in the app ([CoverStore]) is keyed by that cover instead of its
 * album; the cover's version is part of the key, so changing it never shows a stale image.
 */
object ArtworkCache {
    private const val SMALL_PX = 192
    private const val LARGE_PX = 1024
    private const val BLUR_PX = 36

    private val maxBytes = (Runtime.getRuntime().maxMemory() / 8).toInt()
    private val cache = object : LruCache<String, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val missing = HashSet<String>()
    private val io = Dispatchers.IO.limitedParallelism(3)

    private fun source(songId: Long, albumId: Long): String =
        CoverStore.songVersion(songId)?.let { "c$songId.$it" } ?: "a$albumId"

    private fun key(source: String, large: Boolean) = if (large) "L$source" else "S$source"

    fun peek(songId: Long, albumId: Long, large: Boolean): ImageBitmap? {
        val source = source(songId, albumId)
        return cache.get(key(source, large)) ?: if (large) null else cache.get(key(source, true))
    }

    fun peekBlurred(songId: Long, albumId: Long): ImageBitmap? = cache.get("B" + source(songId, albumId))

    suspend fun load(context: Context, songId: Long, albumId: Long, large: Boolean): ImageBitmap? {
        val custom = CoverStore.songFile(songId)
        val key = key(source(songId, albumId), large)
        peek(songId, albumId, large)?.let { return it }
        synchronized(missing) { if (key in missing) return null }
        return withContext(io) {
            coroutineContext.ensureActive()
            val px = if (large) LARGE_PX else SMALL_PX
            val bitmap = (if (custom != null) decodeFile(custom, px) else decode(context, songId, albumId, px))
                ?.asImageBitmap()
            if (bitmap != null) cache.put(key, bitmap) else synchronized(missing) { missing += key }
            bitmap
        }
    }

    /** A picture file of our own (a playlist cover); [version] busts the cache when it changes. */
    suspend fun loadFile(file: File, version: Long, large: Boolean): ImageBitmap? {
        val key = key("f${file.name}.$version", large)
        cache.get(key)?.let { return it }
        return withContext(io) {
            decodeFile(file, if (large) LARGE_PX else SMALL_PX)?.asImageBitmap()?.also { cache.put(key, it) }
        }
    }

    fun peekFile(file: File, version: Long, large: Boolean): ImageBitmap? = cache.get(key("f${file.name}.$version", large))

    /** Bumped by [invalidateAlbum]; artwork on screen reads it to reload. */
    private val albumRevisions = mutableStateMapOf<Long, Int>()
    fun albumRevision(albumId: Long): Int = albumRevisions[albumId] ?: 0

    /**
     * Forgets an album's art after its file changed (tags written by the app). MediaStore
     * regenerates its thumbnails on rescan; this drops our copies so they're loaded again.
     */
    fun invalidateAlbum(albumId: Long) {
        albumRevisions[albumId] = albumRevision(albumId) + 1
        val source = "a$albumId"
        cache.snapshot().keys.filter { it.endsWith(source) && it.length == source.length + 1 }.forEach(cache::remove)
        synchronized(missing) { missing.removeAll { it.endsWith(source) && it.length == source.length + 1 } }
        synchronized(seeds) { seeds.remove(source) }
    }

    /**
     * A tiny pre-blurred copy for frosted backgrounds. Blurring 36x36 px on the CPU once per
     * album is far cheaper than a full-screen GPU blur on every frame; drawn scaled up with
     * bilinear filtering it looks the same.
     */
    suspend fun loadBlurred(context: Context, songId: Long, albumId: Long): ImageBitmap? {
        val key = "B" + source(songId, albumId)
        cache.get(key)?.let { return it }
        val small = load(context, songId, albumId, large = false) ?: return null
        return withContext(io) {
            val src = small.asAndroidBitmap()
            val scaled = Bitmap.createScaledBitmap(src, BLUR_PX, BLUR_PX, true)
            val pixels = IntArray(BLUR_PX * BLUR_PX)
            scaled.getPixels(pixels, 0, BLUR_PX, 0, 0, BLUR_PX, BLUR_PX)
            repeat(3) { boxBlur(pixels, BLUR_PX, BLUR_PX, radius = 2) }
            if (scaled !== src) scaled.recycle()
            Bitmap.createBitmap(pixels, BLUR_PX, BLUR_PX, Bitmap.Config.ARGB_8888)
                .asImageBitmap()
                .also { cache.put(key, it) }
        }
    }

    /** Theme seed from album art, reusing the 36x36 blurred copy; null without art. Cached per album. */
    suspend fun themeSeed(context: Context, songId: Long, albumId: Long): Int? {
        val source = source(songId, albumId)
        synchronized(seeds) { if (source in seeds) return seeds[source] }
        val blurred = loadBlurred(context, songId, albumId)
        val seed = blurred?.let {
            withContext(io) {
                val bmp = it.asAndroidBitmap()
                val px = IntArray(bmp.width * bmp.height)
                bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                SchemeGenerator.seedFromPixels(px)
            }
        }
        synchronized(seeds) { seeds[source] = seed }
        return seed
    }

    private val seeds = HashMap<String, Int?>()

    /** Separable box blur, in place. Three passes approximate a gaussian. */
    private fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int) {
        val tmp = IntArray(px.size)
        blurPass(px, tmp, w, h, radius, horizontal = true)
        blurPass(tmp, px, w, h, radius, horizontal = false)
    }

    private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val n = 2 * r + 1
        for (line in 0 until lines) {
            for (i in 0 until len) {
                var a = 0
                var red = 0
                var g = 0
                var b = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val c = if (horizontal) src[line * w + j] else src[j * w + line]
                    a += c ushr 24
                    red += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                }
                val idx = if (horizontal) line * w + i else i * w + line
                dst[idx] = ((a / n) shl 24) or ((red / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }

    private fun decodeFile(file: File, px: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun decode(context: Context, songId: Long, albumId: Long, px: Int): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
            return runCatching { resolver.loadThumbnail(songUri, Size(px, px), null) }.getOrNull()
        }
        val artUri = ContentUris.withAppendedId(Song.ALBUM_ART_URI, albumId)
        return runCatching {
            resolver.openFileDescriptor(artUri, "r")?.use { pfd ->
                val fd = pfd.fileDescriptor
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFileDescriptor(fd, null, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
                BitmapFactory.decodeFileDescriptor(
                    fd, null,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                )
            }
        }.getOrNull()
    }
}

/**
 * Where covers come from when the player shows another device (the paired computer): the
 * address of a track's cover by id and pixel size, or null for the phone's own artwork.
 */
val LocalRemoteCovers = staticCompositionLocalOf<((songId: Long, sizePx: Int) -> String?)?> { null }

@Composable
fun Artwork(
    songId: Long,
    albumId: Long,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    placeholderIconSize: Dp = 24.dp,
) {
    LocalRemoteCovers.current?.let { covers ->
        val size = if (large) REMOTE_LARGE_PX else REMOTE_SMALL_PX
        return RemoteImage(covers(songId, size), modifier, maxPx = size)
    }
    val context = LocalContext.current.applicationContext
    val cover = CoverStore.songVersion(songId)
    val revision = ArtworkCache.albumRevision(albumId)
    val bitmap by produceState(ArtworkCache.peek(songId, albumId, large), songId, albumId, large, cover, revision) {
        value = ArtworkCache.load(context, songId, albumId, large)
    }
    ArtworkImage(bitmap, modifier, placeholderIconSize)
}

/** A cover file of the app's own (picked for a playlist, or about to be saved for a track). */
@Composable
fun FileArtwork(
    file: File,
    version: Long,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    placeholderIconSize: Dp = 24.dp,
) {
    val bitmap by produceState(ArtworkCache.peekFile(file, version, large), file, version, large) {
        value = ArtworkCache.loadFile(file, version, large)
    }
    ArtworkImage(bitmap, modifier, placeholderIconSize)
}

@Composable
private fun ArtworkImage(bitmap: ImageBitmap?, modifier: Modifier, placeholderIconSize: Dp) {
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer)) {
        Crossfade(
            targetState = bitmap,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "artwork",
        ) { image ->
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = PlayerIcons.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(placeholderIconSize),
                    )
                }
            }
        }
    }
}

private val Vibrant = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.35f) })

/** Full-bleed frosted album art. A static image: zero per-frame cost once drawn. */
@Composable
fun BlurredArtwork(songId: Long, albumId: Long, modifier: Modifier = Modifier) {
    LocalRemoteCovers.current?.let { covers ->
        // A small copy, blurred by the GPU: the phone has no file to pre-blur.
        return RemoteImage(covers(songId, REMOTE_SMALL_PX), modifier.blur(48.dp), maxPx = REMOTE_SMALL_PX)
    }
    val context = LocalContext.current.applicationContext
    val cover = CoverStore.songVersion(songId)
    val revision = ArtworkCache.albumRevision(albumId)
    val bitmap by produceState(ArtworkCache.peekBlurred(songId, albumId), songId, albumId, cover, revision) {
        value = ArtworkCache.loadBlurred(context, songId, albumId)
    }
    Crossfade(
        targetState = bitmap,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "blurredArtwork",
        modifier = modifier,
    ) { image ->
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                colorFilter = Vibrant,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer))
        }
    }
}

private const val REMOTE_SMALL_PX = 160
private const val REMOTE_LARGE_PX = 720
