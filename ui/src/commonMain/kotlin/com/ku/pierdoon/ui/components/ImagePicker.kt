package com.ku.pierdoon.ui.components

import androidx.compose.runtime.Composable

/** A picture the user picked: a content Uri on the phone, a file on the computer. */
expect class PickedImage

/** Opens the system's picture picker (the phone's gallery, a file dialog); [onPicked] gets the choice. */
@Composable
expect fun rememberImagePicker(onPicked: (PickedImage) -> Unit): () -> Unit
