package com.ku.pierdoon.ui

actual fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000
