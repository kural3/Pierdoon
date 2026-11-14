package com.ku.pierdoon.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Material's slider for a plain value the caller keeps. Material 3 on Android replaced the
 * value-taking Slider with one driven by a SliderState; Compose Multiplatform for the PC doesn't
 * have that one yet. Each side uses what it has.
 */
@Composable
expect fun ValueSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
)
