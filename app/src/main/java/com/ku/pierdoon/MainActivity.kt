package com.ku.pierdoon

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.UserLibrary
import com.ku.pierdoon.ui.PierdoonApp
import com.ku.pierdoon.ui.components.installFrameRateBooster
import com.ku.pierdoon.ui.theme.PierdoonTheme
import com.ku.pierdoon.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        CoverStore.init(this)
        enableEdgeToEdge()
        setContent {
            val settings by SettingsStore.settings.collectAsStateWithLifecycle()
            val dark = settings.isDarkTheme()
            // Keep status/navigation bar icons readable when the theme is forced.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            PierdoonTheme(settings) {
                PierdoonApp()
            }
        }
        installFrameRateBooster(findViewById(android.R.id.content))
    }

    // Backgrounded apps can be killed at any time: don't leave edits waiting on a timer.
    override fun onStop() {
        super.onStop()
        UserLibrary.get(this).flush()
    }
}
