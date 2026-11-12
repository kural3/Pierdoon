package com.ku.pierdoon.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The settings both apps have, for the shared screens. Each app keeps its own SettingsStore
 * (its own settings on top of these, its own storage) and hands it over here when it opens.
 */
object SharedSettingsStore {
    private var source: StateFlow<SharedSettings> = MutableStateFlow(CommonSettings())
    private var change: (CommonSettings.() -> CommonSettings) -> Unit = {}

    val settings: StateFlow<SharedSettings> get() = source

    fun attach(settings: StateFlow<SharedSettings>, update: (CommonSettings.() -> CommonSettings) -> Unit) {
        source = settings
        change = update
    }

    fun update(change: CommonSettings.() -> CommonSettings) = this.change(change)
}
