package com.ku.pierdoon.playback

/** Speeds offered, pitch following: slowed below 1, nightcore above. */
object PlaybackSpeed {
    val options = listOf(0.8f, 0.9f, 1f, 1.1f, 1.25f)

    /** The offered speed closest to [value]: what a stored setting comes back as. */
    fun nearest(value: Float): Float = options.minBy { kotlin.math.abs(it - value) }

    /** "0.8×", "1×", "1.25×". */
    fun label(value: Float): String =
        (if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()) + "×"
}
