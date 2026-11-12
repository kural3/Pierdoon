package com.ku.pierdoon

import com.ku.pierdoon.data.update.AppRelease
import com.ku.pierdoon.data.update.AppUpdater
import com.ku.pierdoon.data.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The "new version" dialog of both apps: when it comes up by itself, and what its buttons do.
 * Checks quietly on launch when [autoCheck] is on and the last check was a while ago.
 */
class UpdatePrompt(private val scope: CoroutineScope, private val updater: AppUpdater, autoCheck: Boolean) {
    val state: StateFlow<UpdateState> get() = updater.state

    private val _shown = MutableStateFlow(false)
    /** Whether the dialog is up. */
    val shown: StateFlow<Boolean> = _shown.asStateFlow()

    init {
        // A release the user put off with "Later" isn't offered again.
        if (autoCheck && updater.isDue()) {
            scope.launch {
                val s = updater.check()
                if (s is UpdateState.Available && !updater.isSkipped(s.release)) _shown.value = true
            }
        }
    }

    fun check() {
        scope.launch {
            if (updater.check() is UpdateState.Available) _shown.value = true
        }
    }

    fun show() { _shown.value = true }
    fun install(release: AppRelease) = updater.install(release)

    /** "Later" or closing: stops a download, or keeps quiet about this release from now on. */
    fun dismiss() {
        when (val s = updater.state.value) {
            is UpdateState.Downloading -> updater.cancel()
            is UpdateState.Available -> updater.skip(s.release)
            else -> Unit
        }
        _shown.value = false
    }
}
