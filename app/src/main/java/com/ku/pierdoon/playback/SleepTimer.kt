package com.ku.pierdoon.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SleepTimerState {
    /** Pause at [endsAt] ([SystemClock.elapsedRealtime] millis, so a clock change doesn't move it). */
    data class At(val endsAt: Long) : SleepTimerState {
        val remainingMs: Long get() = (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    }

    /** Pause once the current track ends. */
    data object EndOfTrack : SleepTimerState
}

/**
 * Pauses playback after a while. Set from the UI, carried out by [PlaybackService], which owns
 * the player and keeps running with the app closed. Same process, like [com.ku.pierdoon.data.SettingsStore].
 */
object SleepTimer {
    private val _state = MutableStateFlow<SleepTimerState?>(null)
    val state: StateFlow<SleepTimerState?> = _state.asStateFlow()

    fun start(minutes: Int) {
        _state.value = SleepTimerState.At(SystemClock.elapsedRealtime() + minutes * 60_000L)
    }

    fun endOfTrack() {
        _state.value = SleepTimerState.EndOfTrack
    }

    fun cancel() {
        _state.value = null
    }

    /** The service paused: the timer has done its job. */
    internal fun fired() {
        _state.value = null
    }
}
