package com.ku.pierdoon.ui

import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.lyrics_wifi_only
import com.ku.pierdoon.ui.resources.permission_body
import com.ku.pierdoon.ui.resources.permission_grant
import com.ku.pierdoon.ui.resources.permission_title
import com.ku.pierdoon.ui.resources.cover_gallery
import com.ku.pierdoon.ui.resources.delete_from_device
import com.ku.pierdoon.ui.resources.transfer_send_to_pc
import com.ku.pierdoon.ui.resources.update_confirm
import com.ku.pierdoon.ui.resources.save_in_file_old
import com.ku.pierdoon.ui.resources.save_in_file_desc
import com.ku.pierdoon.ui.resources.search_hint
import com.ku.pierdoon.ui.resources.download_unsupported_os
import com.ku.pierdoon.ui.resources.settings_wifi_only
import com.ku.pierdoon.ui.resources.settings_wifi_only_desc
import com.ku.pierdoon.ui.resources.settings_black_desc
import com.ku.pierdoon.ui.resources.settings_colors_wallpaper_desc
import com.ku.pierdoon.ui.resources.settings_glass_desc
import org.jetbrains.compose.resources.StringResource

internal actual object PlatformStrings {
    actual val lyricsWifiOnly: StringResource = Res.string.lyrics_wifi_only
    actual val permissionTitle: StringResource = Res.string.permission_title
    actual val permissionBody: StringResource = Res.string.permission_body
    actual val permissionGrant: StringResource = Res.string.permission_grant
    actual val updateConfirm: StringResource = Res.string.update_confirm
    actual val deleteFromDevice: StringResource = Res.string.delete_from_device
    actual val sendToComputer: StringResource? = Res.string.transfer_send_to_pc
    actual val fileWritesUnavailable: StringResource? =
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) Res.string.save_in_file_old else null
    actual val coverPick: StringResource = Res.string.cover_gallery
    actual val saveInFileDesc: StringResource = Res.string.save_in_file_desc
    actual val searchHint: StringResource = Res.string.search_hint
    actual val downloadUnsupported: StringResource = Res.string.download_unsupported_os
    actual val lyricsWifiOnlyTitle: StringResource = Res.string.settings_wifi_only
    actual val lyricsWifiOnlyDesc: StringResource = Res.string.settings_wifi_only_desc
    actual val pureBlackDesc: StringResource = Res.string.settings_black_desc
    actual val wallpaperColorsDesc: StringResource = Res.string.settings_colors_wallpaper_desc
    actual val glassDesc: StringResource = Res.string.settings_glass_desc
}
