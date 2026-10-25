package com.ku.pierdoon.playback

import com.ku.pierdoon.platform.WinShell
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import javax.swing.SwingUtilities

/**
 * The keyboard's media keys (play/pause, next, previous, stop), wherever focus is.
 *
 * Registered as system-wide hot keys first. If another player already holds them, a low-level
 * keyboard hook listens instead; it only watches and passes every key on, so the other player
 * keeps working too.
 */
object MediaKeys {
    private const val VK_MEDIA_NEXT_TRACK = 0xB0
    private const val VK_MEDIA_PREV_TRACK = 0xB1
    private const val VK_MEDIA_STOP = 0xB2
    private const val VK_MEDIA_PLAY_PAUSE = 0xB3
    private val KEYS = intArrayOf(VK_MEDIA_PLAY_PAUSE, VK_MEDIA_NEXT_TRACK, VK_MEDIA_PREV_TRACK, VK_MEDIA_STOP)

    private var thread: Thread? = null
    @Volatile private var threadId = 0

    fun install(onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onStop: () -> Unit) {
        if (!WinShell.isWindows || thread != null) return
        fun dispatch(vk: Int) = SwingUtilities.invokeLater {
            when (vk) {
                VK_MEDIA_PLAY_PAUSE -> onPlayPause()
                VK_MEDIA_NEXT_TRACK -> onNext()
                VK_MEDIA_PREV_TRACK -> onPrevious()
                VK_MEDIA_STOP -> onStop()
            }
        }
        thread = Thread({
            runCatching { messageLoop(::dispatch) }
        }, "media-keys").apply {
            isDaemon = true
            start()
        }
    }

    fun uninstall() {
        val id = threadId
        if (id != 0) User32.INSTANCE.PostThreadMessage(id, WinUser.WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
        thread = null
    }

    private fun messageLoop(dispatch: (Int) -> Unit) {
        val user32 = User32.INSTANCE
        threadId = Kernel32.INSTANCE.GetCurrentThreadId()
        // Hot key ids are the key codes themselves.
        val registered = KEYS.filter { vk -> user32.RegisterHotKey(null, vk, MOD_NOREPEAT, vk) }
        var hook: WinUser.HHOOK? = null
        if (registered.size < KEYS.size) {
            val proc = WinUser.LowLevelKeyboardProc { nCode, wParam, info ->
                if (nCode >= 0 && wParam.toInt() == WinUser.WM_KEYDOWN && info.vkCode in KEYS && info.vkCode !in registered) {
                    dispatch(info.vkCode)
                }
                user32.CallNextHookEx(hook, nCode, wParam, WinDef.LPARAM(com.sun.jna.Pointer.nativeValue(info.pointer)))
            }
            keepAlive = proc
            hook = user32.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, proc, Kernel32.INSTANCE.GetModuleHandle(null), 0)
        }
        val msg = WinUser.MSG()
        while (user32.GetMessage(msg, null, 0, 0) > 0) {
            if (msg.message == WinUser.WM_HOTKEY) dispatch(msg.wParam.toInt())
        }
        registered.forEach { user32.UnregisterHotKey(null, it) }
        hook?.let(user32::UnhookWindowsHookEx)
    }

    /** The hook callback must stay reachable, or the GC frees it while Windows still calls it. */
    @Suppress("unused")
    private var keepAlive: Any? = null

    private const val MOD_NOREPEAT = 0x4000
}
