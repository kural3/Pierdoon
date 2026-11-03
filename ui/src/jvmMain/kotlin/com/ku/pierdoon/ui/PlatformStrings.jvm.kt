package com.ku.pierdoon.ui

import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.lyrics_wifi_only
import com.ku.pierdoon.ui.resources.permission_body
import com.ku.pierdoon.ui.resources.permission_grant
import com.ku.pierdoon.ui.resources.permission_title
import com.ku.pierdoon.ui.resources.update_confirm
import org.jetbrains.compose.resources.StringResource

internal actual object PlatformStrings {
    actual val lyricsWifiOnly: StringResource = Res.string.lyrics_wifi_only
    actual val permissionTitle: StringResource = Res.string.permission_title
    actual val permissionBody: StringResource = Res.string.permission_body
    actual val permissionGrant: StringResource = Res.string.permission_grant
    actual val updateConfirm: StringResource = Res.string.update_confirm
}
