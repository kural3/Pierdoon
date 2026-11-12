package com.ku.pierdoon.ui.components

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.ku.pierdoon.ui.theme.LocalUiPrefs

/**
 * A [ModalBottomSheet] over frosted glass: on Android 12+ (with the glass setting on and the
 * system's blur available) the app behind it is blurred and the sheet lets a little of it through.
 */
@Composable
fun GlassSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val glass = LocalUiPrefs.current.glass
    val blur = remember(glass) {
        glass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = if (blur) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.84f) else BottomSheetDefaults.ContainerColor,
        scrimColor = if (blur) BottomSheetDefaults.ScrimColor.copy(alpha = 0.2f) else BottomSheetDefaults.ScrimColor,
    ) {
        if (blur) BlurBehind()
        content()
    }
}

/** Blurs everything behind the dialog window this is composed in. */
@Composable
private fun BlurBehind() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val radius = with(LocalDensity.current) { 36.dp.roundToPx() }
    DisposableEffect(view, radius) {
        val window = generateSequence<Any>(view) { (it as? View)?.parent }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        window?.run {
            addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            attributes = attributes.apply { blurBehindRadius = radius }
        }
        onDispose { }
    }
}
