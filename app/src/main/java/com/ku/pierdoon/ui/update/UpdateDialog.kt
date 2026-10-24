package com.ku.pierdoon.ui.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ku.pierdoon.R
import com.ku.pierdoon.data.update.AppRelease
import com.ku.pierdoon.data.update.UpdateState
import com.ku.pierdoon.data.update.Updater

/** "Version X is out": release notes, then download progress, then the system takes over. */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onInstall: (AppRelease) -> Unit,
    onDismiss: () -> Unit,
) {
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Installing -> state.release
        is UpdateState.InstallFailed -> state.release
        else -> return
    }
    val busy = state is UpdateState.Downloading || state is UpdateState.Installing
    val notes = remember(release) { plainNotes(release.notes) }
    val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !busy),
        title = { Text(stringResource(R.string.update_available, release.version)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.update_installed_now, Updater.currentVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    )
                }
                AnimatedContent(
                    targetState = state::class,
                    transitionSpec = { fadeIn(fadeInSpec).togetherWith(fadeOut(fadeOutSpec)) },
                    label = "updateStatus",
                ) { kind ->
                    Column(Modifier.fillMaxWidth()) {
                        when (kind) {
                            UpdateState.Downloading::class -> {
                                Spacer(Modifier.height(16.dp))
                                val progress = (state as? UpdateState.Downloading)?.progress
                                if (progress != null) {
                                    LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                } else {
                                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            UpdateState.Installing::class -> Status(stringResource(R.string.update_confirm), error = false)
                            UpdateState.InstallFailed::class -> Status(stringResource(R.string.update_failed), error = true)
                            else -> Unit
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onInstall(release) }, enabled = !busy) {
                Text(
                    stringResource(
                        when (state) {
                            is UpdateState.Downloading -> R.string.update_downloading
                            is UpdateState.InstallFailed -> R.string.retry
                            else -> R.string.update_install
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (state is UpdateState.Downloading) R.string.cancel else R.string.update_later))
            }
        },
    )
}

@Composable
private fun Status(text: String, error: Boolean) {
    Spacer(Modifier.height(16.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

/** Release notes are GitHub markdown; a dialog wants plain lines. */
private fun plainNotes(markdown: String): String = markdown.lines()
    // The "vibe-coded" banner is for the GitHub page, not for every update prompt.
    .filterNot { it.trimStart().startsWith(">") }
    .map { line ->
        line.trim()
            .replace("**", "")
            .replace("`", "")
            .replace(Regex("^#+\\s*"), "")
            .replace(Regex("^[-*]\\s+"), "• ")
    }
    .joinToString("\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
