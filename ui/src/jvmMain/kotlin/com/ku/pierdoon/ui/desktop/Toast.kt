package com.ku.pierdoon.ui.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Short notices ("Saved", "Track deleted"), shown as a pill near the bottom of the window. */
object Toast {
    const val LENGTH_SHORT = 2_000L
    const val LENGTH_LONG = 3_500L

    internal data class Message(val text: String, val durationMs: Long, val id: Long = System.nanoTime())

    internal val current = MutableStateFlow<Message?>(null)

    fun show(text: String, durationMs: Long = LENGTH_SHORT) {
        current.value = Message(text, durationMs)
    }
}

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val message by Toast.current.collectAsState()
    var shown by remember { mutableStateOf<Toast.Message?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        shown = m
        visible = true
        delay(m.durationMs)
        visible = false
        if (Toast.current.value == m) Toast.current.value = null
    }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(effects) + slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 },
        exit = fadeOut(effects) + slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
        ) {
            Text(
                shown?.text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}
