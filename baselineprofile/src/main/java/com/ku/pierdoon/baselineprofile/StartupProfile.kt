package com.ku.pierdoon.baselineprofile

import android.os.Build
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * What a listener does first: open the app onto the home screen, go through the tabs and scroll
 * the library. The code that runs on the way is compiled ahead of time on install, so the first
 * minutes after an update aren't the slow ones.
 *
 * Runs against its own copy of the app (the `.bp` id), never the installed one.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfile {
    @get:Rule
    val rule = BaselineProfileRule()

    private val packageName =
        InstrumentationRegistry.getArguments().getString("targetAppId") ?: "com.ku.pierdoon.bp"

    @Test
    fun startAndBrowse() = rule.collect(packageName, includeInStartupProfile = true) {
        // The music permission, so it opens on the library rather than on the request for it.
        val permission = if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO" else "android.permission.READ_EXTERNAL_STORAGE"
        device.executeShellCommand("pm grant $packageName $permission")
        if (Build.VERSION.SDK_INT >= 33) device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")

        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5_000)
        device.waitForIdle()

        for (tab in TABS) {
            val button = device.wait(Until.findObject(By.text(Pattern.compile(tab))), 3_000) ?: continue
            button.click()
            device.waitForIdle()
            device.findObject(By.scrollable(true))?.let { list ->
                list.setGestureMargin(device.displayWidth / 5)
                list.fling(Direction.DOWN)
                device.waitForIdle()
                list.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }

    private companion object {
        /** The bottom bar's tabs, in English or Russian. */
        val TABS = listOf("Library|Библиотека", "Playlists|Плейлисты", "Search|Поиск", "Home|Главная")
    }
}
