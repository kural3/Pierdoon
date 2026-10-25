package com.ku.pierdoon.ui.components

import com.ku.pierdoon.platform.Images
import com.ku.pierdoon.platform.LruCache
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.metadata.MetadataSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Search thumbnails stay around while the user goes through results. */
private val thumbs = LruCache<String, ImageBitmap>(48)

@Composable
fun RemoteImage(url: String?, modifier: Modifier) {
    val bitmap by produceState(url?.let { thumbs.get(it) }, url) {
        if (url == null || value != null) return@produceState
        val bytes = MetadataSearch.download(url) ?: return@produceState
        value = withContext(Dispatchers.Default) {
            Images.decode(bytes)?.let { Images.toBitmap(Images.scaled(it, THUMB_PX)) }
        }?.also { thumbs.put(url, it) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Crossfade(bitmap, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "thumb") { image ->
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        PlayerIcons.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

private const val THUMB_PX = 160
