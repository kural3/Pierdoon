package com.ku.pierdoon.ui

actual fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000

actual fun formatDateTime(millis: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(millis))
