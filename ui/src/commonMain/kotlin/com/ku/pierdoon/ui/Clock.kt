package com.ku.pierdoon.ui

/**
 * The monotonic clock both players stamp positions with ([com.ku.pierdoon.PositionSnapshot]):
 * Android's SystemClock.elapsedRealtime(), and System.nanoTime() on Windows.
 */
expect fun elapsedRealtime(): Long

/** [millis] as a short local date and time, for logs. */
expect fun formatDateTime(millis: Long): String
