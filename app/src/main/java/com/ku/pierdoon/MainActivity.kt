package com.ku.pierdoon

import android.Manifest
import android.view.KeyEvent
import com.ku.pierdoon.remote.PcRemote
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.download.DownloadService
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.data.download.findUrl
import com.ku.pierdoon.data.update.Updater
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
        PcRemote.init(this)
        CoverStore.init(this)
        Updater.init(this)
        Downloader.init(this)
        // Not again after rotation: the link was already taken.
        if (savedInstanceState == null) handleIntent(intent)
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
        askForNotificationsOnFirstDownload()
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /**
     * Android 13+ hides the downloads notification without the permission. It's asked once per
     * launch, when a download starts here (from any screen or a share), not at the first start.
     */
    private fun askForNotificationsOnFirstDownload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (askedForNotifications) return@repeatOnLifecycle
                Downloader.tasks.first { tasks -> tasks.any { it.active } }
                askedForNotifications = true
                val granted = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private var askedForNotifications = false

    /** With the computer in the player, the volume keys set its volume (the phone plays nothing then). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (PcRemote.inPlayer && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            PcRemote.stepVolume(up = keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Share → Pier.doon" downloads the link, here or on the paired computer; the downloads notification opens their page. */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val url = intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::findUrl) ?: return
                // With a computer paired the app first asks which of the two downloads it.
                if (PcRemote.paired.value != null) {
                    PcRemote.shareRequest.value = url
                    return
                }
                Downloader.enqueue(url, SettingsStore.settings.value.downloadFormat)
                Downloader.openRequest.value = true
            }
            DownloadService.ACTION_OPEN_DOWNLOADS -> Downloader.openRequest.value = true
        }
    }

    // Backgrounded apps can be killed at any time: don't leave edits waiting on a timer.
    override fun onStop() {
        super.onStop()
        UserLibrary.get(this).flush()
    }
}
