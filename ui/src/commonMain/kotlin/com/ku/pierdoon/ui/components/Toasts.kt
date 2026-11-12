package com.ku.pierdoon.ui.components

import androidx.compose.runtime.Composable

/** Shows a short notice ("Saved"): a system toast on the phone, a pill in the window on the computer. */
@Composable
expect fun rememberToast(): (String) -> Unit
