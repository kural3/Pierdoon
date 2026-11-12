package com.ku.pierdoon.ui.library

import androidx.compose.material3.MaterialShapes

/** Flowers a cover turns into: the playing track's in the list, the paused one in the player. */
val CoverFlowers by lazy {
    listOf(
        MaterialShapes.Flower, MaterialShapes.Clover4Leaf, MaterialShapes.Clover8Leaf, MaterialShapes.Sunny,
        MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst, MaterialShapes.Puffy,
    )
}
