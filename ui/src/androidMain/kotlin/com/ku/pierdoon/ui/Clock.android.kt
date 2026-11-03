package com.ku.pierdoon.ui

import android.os.SystemClock

actual fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

actual fun formatDateTime(millis: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(millis))
