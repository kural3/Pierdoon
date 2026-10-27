package com.ku.pierdoon

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.ku.pierdoon.data.AppLanguage
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.platform.InterfaceLanguage
import com.ku.pierdoon.playback.PlaybackController
import com.ku.pierdoon.ui.PierdoonApp
import com.ku.pierdoon.ui.navigation.Navigator
import com.ku.pierdoon.ui.navigation.Route
import com.ku.pierdoon.ui.theme.PierdoonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.system.exitProcess

/**
 * Renders the real app into an off-screen image and saves screenshots, so the UI can be checked
 * without a window or touching the mouse. Run on a copy of the app's data (see `shots` in the
 * build file). Steps, one per line, coordinates in dp:
 *
 *     size 1280 820      window size (first line only)
 *     wait 1500          let it run
 *     tab Settings       select a tab
 *     lang en            interface language: en, ru or system
 *     click 300 400      press and release there
 *     rclick 300 400     right click
 *     move 300 400       hover
 *     scroll 300 400 5   wheel (positive = down)
 *     drag 10 10 200 10  press, move, release
 *     shot home          save home.png
 *     shotnow mid        save mid.png at once, mid-animation
 *     state              print whether it plays and how loud (the Delicious mode's levels)
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val steps = File(args[0]).readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    val out = File(args[1]).apply { mkdirs() }
    var width = 1280
    var height = 820
    steps.firstOrNull()?.split(" ")?.takeIf { it[0] == "size" }?.let { width = it[1].toInt(); height = it[2].toInt() }

    runBlocking {
        withContext(Dispatchers.Main) {
            SettingsStore.init()
            CoverStore.init()
            PlaybackController.start()
            // Silent: a click that starts a track must not play through the speakers.
            SettingsStore.update { it.copy(muted = true) }
            val vm = MainViewModel()
            com.ku.pierdoon.remote.RemoteServer.bind(vm)
            val nav = Navigator(listOf(Route.Home))
            val scene = ImageComposeScene(width, height, Density(1f), coroutineContext = Dispatchers.Main) {
                val settings by SettingsStore.settings.collectAsState()
                // As in Main.kt: the language is applied before the strings are read, and a change reads them anew.
                val language = settings.language
                remember(language) { InterfaceLanguage.apply(language) }
                PierdoonTheme(settings) { key(language) { PierdoonApp(vm, nav) } }
            }
            var time = 0L
            // Wall time of each rendered frame, for the `perf` step.
            val frameMs = mutableListOf<Double>()
            suspend fun run(ms: Long) {
                val end = time + ms * 1_000_000
                while (time < end) {
                    val started = System.nanoTime()
                    scene.render(time)
                    frameMs += (System.nanoTime() - started) / 1e6
                    time += 16_000_000
                    delay(16)
                }
            }
            fun at(x: String, y: String) = Offset(x.toFloat(), y.toFloat())
            val pressed = PointerButtons(isPrimaryPressed = true)

            for (line in steps) {
                val p = line.split(" ")
                when (p[0]) {
                    "size" -> Unit
                    "wait" -> run(p[1].toLong())
                    "tab" -> nav.selectTab(Route.tabs.first { it::class.simpleName == p[1] })
                    "lang" -> SettingsStore.update { it.copy(language = if (p[1] == "system") AppLanguage.System else AppLanguage.fromTag(p[1])) }
                    "move" -> scene.sendPointerEvent(PointerEventType.Move, at(p[1], p[2]))
                    "click" -> {
                        val o = at(p[1], p[2])
                        scene.sendPointerEvent(PointerEventType.Move, o)
                        scene.sendPointerEvent(PointerEventType.Press, o, buttons = pressed, button = PointerButton.Primary)
                        run(60)
                        scene.sendPointerEvent(PointerEventType.Release, o, button = PointerButton.Primary)
                    }
                    "rclick" -> {
                        val o = at(p[1], p[2])
                        val right = PointerButtons(isSecondaryPressed = true)
                        scene.sendPointerEvent(PointerEventType.Move, o)
                        scene.sendPointerEvent(PointerEventType.Press, o, buttons = right, button = PointerButton.Secondary)
                        run(60)
                        scene.sendPointerEvent(PointerEventType.Release, o, button = PointerButton.Secondary)
                    }
                    "scroll" -> scene.sendPointerEvent(
                        PointerEventType.Scroll, at(p[1], p[2]), scrollDelta = Offset(0f, p[3].toFloat()),
                    )
                    "drag" -> {
                        val a = at(p[1], p[2])
                        val b = at(p[3], p[4])
                        scene.sendPointerEvent(PointerEventType.Move, a)
                        scene.sendPointerEvent(PointerEventType.Press, a, buttons = pressed, button = PointerButton.Primary)
                        for (i in 1..12) {
                            scene.sendPointerEvent(PointerEventType.Move, a + (b - a) * (i / 12f), buttons = pressed)
                            run(16)
                        }
                        scene.sendPointerEvent(PointerEventType.Release, b, button = PointerButton.Primary)
                    }
                    "shot", "shotnow" -> {
                        if (p[0] == "shot") run(400)
                        val image = scene.render(time)
                        File(out, "${p[1]}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                        println("shot ${p[1]}")
                    }
                    "state" -> PlaybackController.player.let { pl ->
                        val l = pl.levels()
                        println("state: playing ${pl.isPlaying} at ${pl.currentPosition} ms, energy ${l.energy}, bass ${l.bass}, beats ${l.beats}")
                    }
                    // Frame times since the previous `perf`: average, 95th percentile and worst.
                    "perf" -> {
                        val sorted = frameMs.sorted()
                        if (sorted.isNotEmpty()) {
                            val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
                            println("perf ${p[1]}: ${sorted.size} frames, avg %.1f ms, p95 %.1f ms, max %.1f ms".format(sorted.average(), p95, sorted.last()))
                        }
                        frameMs.clear()
                    }
                    else -> error("unknown step: $line")
                }
            }
            scene.close()
        }
    }
    exitProcess(0)
}
