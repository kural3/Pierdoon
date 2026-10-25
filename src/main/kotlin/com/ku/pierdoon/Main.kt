package com.ku.pierdoon

import com.ku.pierdoon.platform.ErrorLog
import androidx.compose.ui.window.DefaultWindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.ku.pierdoon.data.CoverStore
import com.ku.pierdoon.data.SettingsStore
import com.ku.pierdoon.data.download.Downloader
import com.ku.pierdoon.data.download.findUrl
import com.ku.pierdoon.data.update.Updater
import com.ku.pierdoon.platform.Prefs
import com.ku.pierdoon.platform.WinShell
import com.ku.pierdoon.playback.DiscordPresence
import com.ku.pierdoon.playback.PlaybackController
import com.ku.pierdoon.remote.RemoteServer
import com.ku.pierdoon.resources.Res
import com.ku.pierdoon.resources.app_name
import com.ku.pierdoon.resources.pierdoon
import com.ku.pierdoon.resources.tray_quit
import com.ku.pierdoon.resources.tray_show
import com.ku.pierdoon.ui.PierdoonApp
import com.ku.pierdoon.ui.desktop.BackDispatcher
import com.ku.pierdoon.ui.desktop.Files
import com.ku.pierdoon.ui.navigation.Navigator
import com.ku.pierdoon.ui.navigation.Route
import com.ku.pierdoon.ui.theme.PierdoonTheme
import com.ku.pierdoon.ui.theme.SystemAccent
import com.ku.pierdoon.ui.theme.isDarkTheme
import java.awt.datatransfer.DataFlavor
import java.io.File
import kotlin.system.exitProcess
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
fun main(args: Array<String>) {
    ErrorLog.install()
    // Only one Pier.doon at a time: a second launch hands its arguments over and quits.
    if (!SingleInstance.claim(args)) exitProcess(0)

    SettingsStore.init()
    CoverStore.init()
    Downloader.init()
    PlaybackController.start()
    DiscordPresence.start()
    handleArgs(args)

    val windowPrefs = Prefs("window")
    val vm = MainViewModel()
    Updater.onQuitForUpdate = { quit(vm) }
    RemoteServer.bind(vm)

    application(exitProcessOnExit = false) {
        // A crash inside the UI is written down before Compose shows its error dialog.
        CompositionLocalProvider(
            LocalWindowExceptionHandlerFactory provides WindowExceptionHandlerFactory { window ->
                val shown = DefaultWindowExceptionHandlerFactory.exceptionHandler(window)
                WindowExceptionHandler { e ->
                    ErrorLog.record("UI", e)
                    shown.onException(e)
                }
            },
        ) {
            val settings by SettingsStore.settings.collectAsState()
            var visible by remember { mutableStateOf(true) }
            SingleInstance.onActivate = { visible = true; SingleInstance.bringToFront() }
            val state = rememberWindowState(
                size = DpSize(windowPrefs.getFloat("w", 1280f).dp, windowPrefs.getFloat("h", 820f).dp),
                position = windowPrefs.getFloat("x", Float.NaN).takeUnless { it.isNaN() }
                    ?.let { x -> WindowPosition(x.dp, windowPrefs.getFloat("y", 0f).dp) }
                    ?: WindowPosition.PlatformDefault,
                placement = if (windowPrefs.getBoolean("maximized", false)) WindowPlacement.Maximized else WindowPlacement.Floating,
            )
            val nav = remember { Navigator(listOf(Route.Home)) }
            saveWindowOnQuit = { saveWindow(windowPrefs, state) }
            val icon = painterResource(Res.drawable.pierdoon)

            Tray(
                icon = icon,
                tooltip = stringResource(Res.string.app_name),
                onAction = { visible = true; SingleInstance.bringToFront() },
                menu = {
                    Item(stringResource(Res.string.tray_show), onClick = { visible = true; SingleInstance.bringToFront() })
                    Separator()
                    Item(stringResource(Res.string.tray_quit), onClick = { quit(vm) })
                },
            )

            Window(
                onCloseRequest = {
                    saveWindow(windowPrefs, state)
                    if (settings.closeToTray) visible = false else quit(vm)
                },
                visible = visible,
                state = state,
                title = stringResource(Res.string.app_name),
                icon = icon,
                onPreviewKeyEvent = { e -> e.type == KeyEventType.KeyDown && globalShortcut(e, vm, nav) },
                onKeyEvent = { e -> e.type == KeyEventType.KeyDown && unhandledShortcut(e, vm) },
            ) {
                LaunchedEffect(Unit) {
                    window.minimumSize = java.awt.Dimension(720, 560)
                    Files.owner = window
                    SingleInstance.window = window
                    PlaybackController.attachWindow(window)
                    window.addWindowFocusListener(object : java.awt.event.WindowAdapter() {
                        override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {
                            SystemAccent.refresh()
                            vm.onWindowFocused()
                        }
                    })
                }
                PierdoonTheme(settings) {
                    val dark = settings.isDarkTheme()
                    LaunchedEffect(dark) { DarkTitleBar.apply(window, dark) }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onPointerEvent(PointerEventType.Press) { ev ->
                                if (ev.button == PointerButton.Back) BackDispatcher.dispatch()
                            }
                            .dragAndDropTarget(
                                shouldStartDragAndDrop = { true },
                                target = remember { LinkDropTarget() },
                            ),
                    ) {
                        PierdoonApp(vm, nav)
                    }
                }
            }
        }
    }
}

/** A link (or a .url shortcut) dropped anywhere on the window goes to the downloads. */
@OptIn(ExperimentalComposeUiApi::class)
private class LinkDropTarget : DragAndDropTarget {
    override fun onDrop(event: DragAndDropEvent): Boolean {
        val t = event.awtTransferable
        val text = when {
            t.isDataFlavorSupported(DataFlavor.stringFlavor) -> t.getTransferData(DataFlavor.stringFlavor) as? String
            t.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ->
                (t.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                    ?.filterIsInstance<File>()?.firstOrNull { it.extension.equals("url", true) }
                    ?.readLines()?.firstOrNull { it.startsWith("URL=") }?.removePrefix("URL=")
            else -> null
        }
        val url = text?.let(::findUrl) ?: return false
        Downloader.enqueue(url, SettingsStore.settings.value.downloadFormat)
        Downloader.openRequest.value = true
        return true
    }
}

/** Shortcuts that work everywhere, text fields included. */
private fun globalShortcut(e: androidx.compose.ui.input.key.KeyEvent, vm: MainViewModel, nav: Navigator): Boolean = when {
    e.isCtrlPressed && e.key == Key.F -> { nav.selectTab(Route.Search); true }
    e.isCtrlPressed && e.key == Key.DirectionRight -> { vm.next(); true }
    e.isCtrlPressed && e.key == Key.DirectionLeft -> { vm.previous(); true }
    e.isCtrlPressed && e.key == Key.DirectionUp -> { vm.setVolume(SettingsStore.settings.value.volume + 0.05f); true }
    e.isCtrlPressed && e.key == Key.DirectionDown -> { vm.setVolume(SettingsStore.settings.value.volume - 0.05f); true }
    e.isAltPressed && e.key == Key.DirectionLeft -> BackDispatcher.dispatch()
    else -> false
}

/** Keys nothing focused wanted: Space and the arrows act on the player, Esc goes back. */
private fun unhandledShortcut(e: androidx.compose.ui.input.key.KeyEvent, vm: MainViewModel): Boolean = when (e.key) {
    Key.Spacebar -> { vm.togglePlay(); true }
    Key.Escape -> BackDispatcher.dispatch()
    Key.DirectionRight -> { vm.seekBy(5_000); true }
    Key.DirectionLeft -> { vm.seekBy(-5_000); true }
    else -> false
}

/** Links passed on the command line ("Open with", a shortcut) are downloaded. */
fun handleArgs(args: Array<String>) {
    args.firstNotNullOfOrNull { findUrl(it) }?.let { url ->
        Downloader.enqueue(url, SettingsStore.settings.value.downloadFormat)
        Downloader.openRequest.value = true
    }
}

private fun saveWindow(prefs: Prefs, state: androidx.compose.ui.window.WindowState) {
    prefs.edit {
        putBoolean("maximized", state.placement == WindowPlacement.Maximized)
        if (state.placement == WindowPlacement.Floating) {
            putFloat("w", state.size.width.value)
            putFloat("h", state.size.height.value)
            val p = state.position
            if (p is WindowPosition.Absolute) {
                putFloat("x", p.x.value)
                putFloat("y", p.y.value)
            }
        }
    }
}

/** Set by the window: quitting from the tray or for an update keeps its size and place too. */
private var saveWindowOnQuit: (() -> Unit)? = null

private fun quit(vm: MainViewModel) {
    runCatching { saveWindowOnQuit?.invoke() }
    SettingsStore.flush()
    RemoteServer.stop()
    DiscordPresence.shutdown()
    vm.dispose()
    PlaybackController.shutdown()
    SingleInstance.release()
    exitProcess(0)
}

/** Windows 10/11 draw the title bar dark when asked; it follows the app's theme, not the system's. */
private object DarkTitleBar {
    private interface Dwm : com.sun.jna.Library {
        fun DwmSetWindowAttribute(hwnd: com.sun.jna.platform.win32.WinDef.HWND, attr: Int, value: com.sun.jna.ptr.IntByReference, size: Int): Int
    }

    private val dwm by lazy { runCatching { com.sun.jna.Native.load("dwmapi", Dwm::class.java) }.getOrNull() }

    fun apply(window: java.awt.Window, dark: Boolean) {
        if (!WinShell.isWindows) return
        runCatching {
            val hwnd = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getComponentPointer(window))
            // 20 = DWMWA_USE_IMMERSIVE_DARK_MODE (19 on early Windows 10 builds).
            val v = com.sun.jna.ptr.IntByReference(if (dark) 1 else 0)
            if (dwm?.DwmSetWindowAttribute(hwnd, 20, v, 4) != 0) dwm?.DwmSetWindowAttribute(hwnd, 19, v, 4)
        }
    }
}
