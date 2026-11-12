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

    /** Deleting tracks for good: from the phone, or from the computer (to the Recycle Bin). */
    val deleteFromDevice: StringResource

    /** "Send to computer" on the phone's tracks; null on the computer itself. */
    val sendToComputer: StringResource?

    /** Where a picture is picked from: the phone's gallery, a file on the computer. */
    val coverPick: StringResource

    /** Why tags can't be written into files on this system at all; null where they can. */
    val fileWritesUnavailable: StringResource?

    /** Writing tags into the file: on Android, with a word about the permission it asks. */
    val saveInFileDesc: StringResource

    /** How to use an online match: tap on the phone, click on the computer. */
    val searchHint: StringResource

    /** Why the downloader can't run here (Android before 10). */
    val downloadUnsupported: StringResource

    /** The lyrics switch: Wi-Fi only on the phone, no downloads on the computer. */
    val lyricsWifiOnlyTitle: StringResource

    /** What that switch does on each. */
    val lyricsWifiOnlyDesc: StringResource

    /** What true black does: saves power on OLED phones; just deeper on a monitor. */
    val pureBlackDesc: StringResource

    /** Where wallpaper colours come from: the phone's wallpaper, Windows' accent colour. */
    val wallpaperColorsDesc: StringResource

    /** What the glass blurs on each. */
    val glassDesc: StringResource
}
