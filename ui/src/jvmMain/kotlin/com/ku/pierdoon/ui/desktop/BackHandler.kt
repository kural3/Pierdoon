package com.ku.pierdoon.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * "Back" on a PC: Esc, Alt+Left and the mouse's back button. Handlers stack like Android's:
 * the one registered last (the innermost screen or sheet) gets it first.
 */
object BackDispatcher {
    internal class Entry(var enabled: Boolean, var onBack: () -> Unit)

    private val entries = ArrayList<Entry>()

    internal fun add(e: Entry) { entries += e }
    internal fun remove(e: Entry) { entries -= e }

    /** @return whether a handler took it. */
    fun dispatch(): Boolean {
        val e = entries.lastOrNull { it.enabled } ?: return false
        e.onBack()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    val entry = remember { BackDispatcher.Entry(enabled) { current() } }
    SideEffect { entry.enabled = enabled }
    DisposableEffect(entry) {
        BackDispatcher.add(entry)
        onDispose { BackDispatcher.remove(entry) }
    }
}

