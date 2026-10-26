package com.ku.pierdoon.ui.components

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.platform.Images
import com.ku.pierdoon.platform.LruCache
import com.ku.pierdoon.platform.MediaProbe
import com.ku.pierdoon.ui.theme.SchemeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkImage
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Tiny album-art loader: an LRU of downsampled bitmaps keyed by album and size bucket,
 * plus a negative cache so albums without art never hit the disk twice.
 *
 * A track with a cover picked in the app ([CoverStore]) is keyed by that cover instead of its
 * album; the cover's version is part of the key, so changing it never shows a stale image.
 *
 * Art comes from the file's own tags first, then from a cover image in its folder
 * (cover.jpg, folder.jpg and the like), as most players do.
 */
object ArtworkCache {
    private const val SMALL_PX = 192
    private const val LARGE_PX = 1024
    private const val BLUR_PX = 36
    private const val CACHE_BYTES = 48 * 1024 * 1024

    // A fixed budget: the bitmaps live outside the Java heap, so its size says nothing about them.
    // 48 MB is a few hundred list thumbnails plus the open player's covers.
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val missing = HashSet<String>()
    private val io = Dispatchers.IO.limitedParallelism(3)

    /** Album id -> a file of that album, so art can be found from the ids alone. */
    private val albumFiles = HashMap<Long, String>()

    /** Called with every library load: which file stands for which album's art. */
    fun registerAlbumFiles(map: Map<Long, String>) = synchronized(albumFiles) {
        albumFiles.clear()
        albumFiles.putAll(map)
    }

    private fun source(songId: Long, albumId: Long): String =
        CoverStore.songVersion(songId)?.let { "c$songId.$it" } ?: "a$albumId"

    private fun key(source: String, large: Boolean) = if (large) "L$source" else "S$source"

    fun peek(songId: Long, albumId: Long, large: Boolean): ImageBitmap? {
        val source = source(songId, albumId)
        return cache.get(key(source, large)) ?: if (large) null else cache.get(key(source, true))
    }

    fun peekBlurred(songId: Long, albumId: Long): ImageBitmap? = cache.get("B" + source(songId, albumId))

    suspend fun load(songId: Long, albumId: Long, large: Boolean): ImageBitmap? {
        val custom = CoverStore.songFile(songId)
        val key = key(source(songId, albumId), large)
        peek(songId, albumId, large)?.let { return it }
        synchronized(missing) { if (key in missing) return null }
        return withContext(io) {
            coroutineContext.ensureActive()
            val px = if (large) LARGE_PX else SMALL_PX
            val image = if (custom != null) decodeFile(custom) else decodeAlbum(albumId)
            val bitmap = image?.let { Images.scaledBitmap(it, px) }
            if (bitmap != null) cache.put(key, bitmap) else synchronized(missing) { missing += key }
            bitmap
        }
    }

    /** A picture file of our own (a playlist cover); [version] busts the cache when it changes. */
    suspend fun loadFile(file: File, version: Long, large: Boolean): ImageBitmap? {
        val key = key("f${file.name}.$version", large)
        cache.get(key)?.let { return it }
        return withContext(io) {
            decodeFile(file)?.let { Images.scaledBitmap(it, if (large) LARGE_PX else SMALL_PX) }
                ?.also { cache.put(key, it) }
        }
    }

    fun peekFile(file: File, version: Long, large: Boolean): ImageBitmap? = cache.get(key("f${file.name}.$version", large))

    /**
     * Drops every cached bitmap: the window went to the tray, nothing is drawn. Anything shown
     * again is decoded anew; the negative cache and the theme seeds stay.
     */
    fun trim() {
        cache.evictAll()
    }

    /** Bumped by [invalidateAlbum]; artwork on screen reads it to reload. */
    private val albumRevisions = mutableStateMapOf<Long, Int>()
    fun albumRevision(albumId: Long): Int = albumRevisions[albumId] ?: 0

    /** Forgets an album's art after its file changed (tags written by the app). */
    fun invalidateAlbum(albumId: Long) {
        albumRevisions[albumId] = albumRevision(albumId) + 1
        val source = "a$albumId"
        cache.snapshot().keys.filter { it.endsWith(source) && it.length == source.length + 1 }.forEach(cache::remove)
        synchronized(missing) { missing.removeAll { it.endsWith(source) && it.length == source.length + 1 } }
        synchronized(seeds) { seeds.remove(source) }
        synchronized(smallImages) { smallImages.remove(source) }
    }

    /** Small Skia copies kept for blurring and colour picking. */
    private val smallImages = HashMap<String, SkImage?>()

    private suspend fun smallImage(songId: Long, albumId: Long): SkImage? {
        val source = source(songId, albumId)
        synchronized(smallImages) { if (source in smallImages) return smallImages[source] }
        val image = withContext(io) {
            val custom = CoverStore.songFile(songId)
            (if (custom != null) decodeFile(custom) else decodeAlbum(albumId))?.use { Images.resized(it, BLUR_PX, BLUR_PX) }
        }
        synchronized(smallImages) { smallImages[source] = image }
        return image
    }

    /**
     * A tiny pre-blurred copy for frosted backgrounds. Blurring 36x36 px on the CPU once per
     * album is far cheaper than a full-window blur on every frame; drawn scaled up with
     * bilinear filtering it looks the same.
     */
    suspend fun loadBlurred(songId: Long, albumId: Long): ImageBitmap? {
        val key = "B" + source(songId, albumId)
        cache.get(key)?.let { return it }
        val small = smallImage(songId, albumId) ?: return null
        return withContext(io) {
            val pixels = Images.pixels(small)
            repeat(3) { boxBlur(pixels, BLUR_PX, BLUR_PX, radius = 2) }
            Images.fromPixels(pixels, BLUR_PX, BLUR_PX).use(Images::toBitmap).also { cache.put(key, it) }
        }
    }

    /** Theme seed from album art, from the 36x36 copy; null without art. Cached per album. */
    suspend fun themeSeed(songId: Long, albumId: Long): Int? {
        val source = source(songId, albumId)
        synchronized(seeds) { if (source in seeds) return seeds[source] }
        val small = smallImage(songId, albumId)
        val seed = small?.let { withContext(io) { SchemeGenerator.seedFromPixels(Images.pixels(it)) } }
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

    private fun decodeFile(file: File): SkImage? = runCatching { Images.decode(file.readBytes()) }.getOrNull()

    /** The cover as stored (custom, embedded or the folder's picture), undecoded. Blocking. */
    fun coverBytes(songId: Long, albumId: Long): ByteArray? = runCatching {
        CoverStore.songFile(songId)?.let { return@runCatching it.readBytes() }
        val path = synchronized(albumFiles) { albumFiles[albumId] } ?: return@runCatching null
        val audio = File(path)
        MediaProbe.embeddedCover(audio) ?: folderCover(audio.parentFile)?.readBytes()
    }.getOrNull()

    private fun decodeAlbum(albumId: Long): SkImage? {
        val path = synchronized(albumFiles) { albumFiles[albumId] } ?: return null
        val audio = File(path)
        MediaProbe.embeddedCover(audio)?.let(Images::decode)?.let { return it }
        return folderCover(audio.parentFile)?.let(::decodeFile)
    }

    private val FOLDER_NAMES = listOf("cover", "folder", "front", "album", "albumart", "albumartsmall")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp")

    /** cover.jpg, folder.png and friends; failing those, the only picture in the folder. */
    private fun folderCover(dir: File?): File? {
        val images = dir?.listFiles { f -> f.isFile && f.extension.lowercase() in IMAGE_EXT }.orEmpty()
        FOLDER_NAMES.forEach { name -> images.firstOrNull { it.nameWithoutExtension.equals(name, ignoreCase = true) }?.let { return it } }
        return images.singleOrNull()
    }
}

@Composable
fun Artwork(
    songId: Long,
    albumId: Long,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    placeholderIconSize: Dp = 24.dp,
) {
    val cover = CoverStore.songVersion(songId)
    val revision = ArtworkCache.albumRevision(albumId)
    val bitmap by produceState(ArtworkCache.peek(songId, albumId, large), songId, albumId, large, cover, revision) {
        value = ArtworkCache.load(songId, albumId, large)
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
                    filterQuality = FilterQuality.Medium,
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

/**
 * Full-bleed frosted album art. Stretching the tiny blurred copy over the whole window and
 * tinting it is costly, and the window repaints whole whenever anything moves, so it is done
 * once per size into a bitmap; every frame after that is a plain copy.
 */
@Composable
fun BlurredArtwork(songId: Long, albumId: Long, modifier: Modifier = Modifier) {
    val cover = CoverStore.songVersion(songId)
    val revision = ArtworkCache.albumRevision(albumId)
    val bitmap by produceState(ArtworkCache.peekBlurred(songId, albumId), songId, albumId, cover, revision) {
        value = ArtworkCache.loadBlurred(songId, albumId)
    }
    Crossfade(
        targetState = bitmap,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "blurredArtwork",
        modifier = modifier,
    ) { image ->
        if (image != null) {
            Spacer(Modifier.fillMaxSize().drawWithCache {
                val baked = bakeFrosted(image, size.width.roundToInt(), size.height.roundToInt())
                onDrawBehind { baked?.let { drawImage(it) } }
            })
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer))
        }
    }
}

/** [image] cropped to fill [width]×[height] and saturated, as a ready bitmap. */
private fun bakeFrosted(image: ImageBitmap, width: Int, height: Int): ImageBitmap? {
    if (width <= 0 || height <= 0) return null
    val scale = max(width / image.width.toFloat(), height / image.height.toFloat())
    val srcSize = IntSize((width / scale).roundToInt().coerceIn(1, image.width), (height / scale).roundToInt().coerceIn(1, image.height))
    val srcOffset = IntOffset((image.width - srcSize.width) / 2, (image.height - srcSize.height) / 2)
    return ImageBitmap(width, height).also {
        Canvas(it).drawImageRect(
            image = image,
            srcOffset = srcOffset,
            srcSize = srcSize,
            dstSize = IntSize(width, height),
            // Smooth scaling costs nothing here: it runs once, not every frame.
            paint = Paint().apply {
                colorFilter = Vibrant
                filterQuality = FilterQuality.High
            },
        )
    }
}
