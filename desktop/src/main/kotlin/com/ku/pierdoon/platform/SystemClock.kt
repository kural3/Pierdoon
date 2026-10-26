package com.ku.pierdoon.platform

/** Monotonic clocks under Android's names, so timing code reads the same on both. */
object SystemClock {
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000
    fun elapsedRealtimeNanos(): Long = System.nanoTime()
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000
}
