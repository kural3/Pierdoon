package com.ku.pierdoon.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Text fields as soft filled tiles, no underline: how both apps' forms look. */
val SoftFieldShape = RoundedCornerShape(18.dp)

@Composable
fun softFieldColors(): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHighest,
        disabledContainerColor = colors.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
    )
}
