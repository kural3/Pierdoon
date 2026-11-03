package com.ku.pierdoon.ui

/**
 * The monotonic clock both players stamp positions with ([com.ku.pierdoon.PositionSnapshot]):
 * Android's SystemClock.elapsedRealtime(), and System.nanoTime() on Windows.
 */
expect fun elapsedRealtime(): Long
