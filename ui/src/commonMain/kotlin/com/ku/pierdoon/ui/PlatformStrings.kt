package com.ku.pierdoon.ui

import org.jetbrains.compose.resources.StringResource

/**
 * Strings the shared screens show that each app words its own way: same key, a different text
 * in androidMain and jvmMain.
 */
internal expect object PlatformStrings {
    /** Lyrics not downloaded because of the settings: wait for Wi-Fi / turned off. */
    val lyricsWifiOnly: StringResource
}
