package com.ku.pierdoon.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.ku.pierdoon.MainViewModel
import com.ku.pierdoon.data.AppSettings
import com.ku.pierdoon.platform.Prefs
import com.ku.pierdoon.platform.SystemClock
import com.ku.pierdoon.ui.components.Artwork
import com.ku.pierdoon.ui.components.ExpressiveIconButton
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.*
import com.ku.pierdoon.ui.theme.PierdoonTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.window.WindowDraggableArea

/** Whether the small always-on-top player is up instead of the main window. */
object MiniPlayerMode {
    val open = MutableStateFlow(false)
}

/**
 * A small window that stays above the others: the cover, the track, a line of progress and the
 * three transport buttons. Dragged anywhere by its body; the button on the right brings the full
 * window back. Its place on the screen is remembered.
 */
@Composable
fun MiniPlayerWindow(vm: MainViewModel, settings: AppSettings, prefs: Prefs, icon: androidx.compose.ui.graphics.painter.Painter, onBack: () -> Unit) {
    val state = rememberWindowState(
        size = DpSize(400.dp, 104.dp),
        position = prefs.getFloat("miniX", Float.NaN).takeUnless { it.isNaN() }
            ?.let { x -> WindowPosition(x.dp, prefs.getFloat("miniY", 0f).dp) }
            ?: WindowPosition.Aligned(Alignment.BottomEnd),
    )
    fun savePlace() {
        val p = state.position
        if (p is WindowPosition.Absolute) prefs.edit { putFloat("miniX", p.x.value); putFloat("miniY", p.y.value) }
    }
    Window(
        onCloseRequest = { savePlace(); onBack() },
        state = state,
        title = stringResource(Res.string.mini_player),
        icon = icon,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        PierdoonTheme(settings) {
            WindowDraggableArea(Modifier.fillMaxSize().clip(MiniShape)) {
                MiniPlayerContent(vm) { savePlace(); onBack() }
            }
        }
    }
}

private val MiniShape = RoundedCornerShape(28.dp)

/** What the mini window shows; kept apart from the window so the shots tool can draw it too. */
@Composable
fun MiniPlayerContent(vm: MainViewModel, onBack: () -> Unit) {
            val player by vm.player.collectAsState()
            val position by vm.position.collectAsState()
            val isFavorite = vm.userData.collectAsState().value.favorites.contains(player.nowPlaying?.songId)
            // The progress line moves on its own between the player's samples.
            var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            LaunchedEffect(position.playing) {
                while (position.playing) {
                    now = SystemClock.elapsedRealtime()
                    delay(250)
                }
            }
            val colors = MaterialTheme.colorScheme
                Box(Modifier.fillMaxSize().clip(MiniShape).background(colors.surfaceContainer)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 14.dp),
                    ) {
                        val np = player.nowPlaying
                        if (np != null) {
                            Artwork(np.songId, np.albumId, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
                        } else {
                            Box(Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerHighest))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                np?.title ?: stringResource(Res.string.app_name),
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                np?.artist.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                MiniButton(PlayerIcons.Previous, stringResource(Res.string.previous), vm::previous)
                                ExpressiveIconButton(
                                    onClick = vm::togglePlay,
                                    icon = if (player.isPlaying) PlayerIcons.Pause else PlayerIcons.Play,
                                    contentDescription = stringResource(if (player.isPlaying) Res.string.pause else Res.string.play),
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary,
                                    iconSize = 20.dp,
                                    modifier = Modifier.size(width = 52.dp, height = 36.dp),
                                )
                                MiniButton(PlayerIcons.Next, stringResource(Res.string.next), vm::next)
                                if (np != null) {
                                    MiniButton(
                                        if (isFavorite) PlayerIcons.Favorite else PlayerIcons.FavoriteBorder,
                                        stringResource(if (isFavorite) Res.string.remove_from_favorites else Res.string.add_to_favorites),
                                    ) { vm.toggleFavorite(np.songId) }
                                }
                            }
                        }
                        ExpressiveIconButton(
                            onClick = onBack,
                            icon = PlayerIcons.OpenInFull,
                            contentDescription = stringResource(Res.string.mini_player_back),
                            containerColor = Color.Transparent,
                            contentColor = colors.onSurfaceVariant,
                            iconSize = 18.dp,
                            modifier = Modifier.size(36.dp).align(Alignment.Top),
                        )
                    }
                    // Progress along the bottom edge.
                    val duration = player.durationMs
                    val fraction = if (duration > 0) (position.estimate(now).toFloat() / duration).coerceIn(0f, 1f) else 0f
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 6.dp)
                            .fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(colors.onSurface.copy(alpha = 0.12f)),
                    ) {
                        Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(colors.primary))
                    }
                }
}

@Composable
private fun MiniButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    ExpressiveIconButton(
        onClick = onClick,
        icon = icon,
        contentDescription = description,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        iconSize = 20.dp,
        modifier = Modifier.size(36.dp),
    )
}
