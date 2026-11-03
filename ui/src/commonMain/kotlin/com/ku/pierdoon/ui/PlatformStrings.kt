package com.ku.pierdoon.ui

import org.jetbrains.compose.resources.StringResource

/**
 * Strings the shared screens show that each app words its own way: same key, a different text
 * in androidMain and jvmMain.
 */
internal expect object PlatformStrings {
    /** Lyrics not downloaded because of the settings: wait for Wi-Fi / turned off. */
    val lyricsWifiOnly: StringResource

    /** The first screen: where the music is and how to let the app at it. */
    val permissionTitle: StringResource
    val permissionBody: StringResource
    val permissionGrant: StringResource

    /** An update handed over to the installer: what happens next. */
    val updateConfirm: StringResource
}
