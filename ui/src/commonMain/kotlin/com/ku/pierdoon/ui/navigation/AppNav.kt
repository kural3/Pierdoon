package com.ku.pierdoon.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.data.NAV_MAX_WIDTH
import com.ku.pierdoon.data.NAV_MIN_WIDTH
import com.ku.pierdoon.ui.components.Backdrop
import com.ku.pierdoon.ui.components.PlayerIcons
import com.ku.pierdoon.ui.components.animatePressAsState
import com.ku.pierdoon.ui.components.glass
import com.ku.pierdoon.ui.components.hairline
import com.ku.pierdoon.ui.components.horizontalResizePointer
import com.ku.pierdoon.ui.components.scaleBy
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.app_name
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A tab of the app's navigation, as each app names and draws it. */
class NavTab(val route: Route.Tab, val icon: ImageVector, val label: StringResource)

/** Wide enough for the tabs down the side rather than along the bottom: tablets, a phone on its side, the computer. */
fun useNavRail(windowWidth: Dp): Boolean = windowWidth >= RAIL_MIN_WINDOW_WIDTH

private val RAIL_MIN_WINDOW_WIDTH = 600.dp

/** A rail tab at its tallest (icons only), and the logo row above the tabs with its margins. */
private val RAIL_ITEM_HEIGHT = 56.dp
private val RAIL_LOGO_HEIGHT = 72.dp

/**
 * M3 Expressive floating bar along the bottom: a large glass pill. Only the selected tab shows
 * its label, inside an indicator that grows to fit it, so labels always have room.
 */
@Composable
fun FloatingNavBar(nav: Navigator, tabs: List<NavTab>, backdrop: Backdrop, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val pill = RoundedCornerShape(50)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .height(FLOATING_NAV_HEIGHT)
            .border(1.dp, colors.onSurface.copy(alpha = 0.08f), pill)
            .glass(
                backdrop = backdrop,
                shape = pill,
                tint = colors.surfaceContainerHigh.copy(alpha = 0.82f),
                fallback = colors.surfaceContainerHigh,
            )
            .padding(8.dp),
    ) {
        tabs.forEach { tab ->
            val selected = nav.tab == tab.route
            val weight by animateFloatAsState(
                targetValue = if (selected) SELECTED_WEIGHT else 1f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "navWeight",
            )
            FloatingNavItem(
                tab = tab,
                selected = selected,
                onClick = { nav.selectTab(tab.route) },
                // Springs overshoot; weights must stay positive.
                modifier = Modifier.weight(weight.coerceAtLeast(0.5f)),
            )
        }
    }
}

@Composable
private fun FloatingNavItem(tab: NavTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.9f)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        if (selected) colors.secondaryContainer else Color.Transparent, effects, label = "navItemBg"
    )
    val content by animateColorAsState(
        if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, effects, label = "navItemFg"
    )
    val spatialF = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val label = stringResource(tab.label)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .scaleBy(scale)
            .clip(RoundedCornerShape(50))
            .background(container)
            // The press scale and the indicator filling in are the feedback; a ripple would
            // wash the pill white and stretch with it as the tab widens.
            .clickable(interactionSource = source, indication = null, role = Role.Tab) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .semantics { this.selected = selected },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(tab.icon, contentDescription = label, tint = content, modifier = Modifier.size(26.dp))
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(spatialF) + fadeIn(fade),
                exit = shrinkHorizontally(spatialF) + fadeOut(fade),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * The floating bar turned on its side: a column of pills, the selected one filled. Its right
 * edge drags it between a column of icons ([NAV_MIN_WIDTH]) and pills that just fit their labels
 * ([NAV_MAX_WIDTH]); a double click (or tap) on the edge jumps between the two.
 */
@Composable
fun NavRail(
    nav: Navigator,
    tabs: List<NavTab>,
    width: Float,
    onResize: (Float) -> Unit,
    bottomInset: Dp,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    /** The tab after which the rest sit at the bottom of the rail; null keeps them all at the top. */
    splitAfter: Route.Tab? = null,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val currentWidth by rememberUpdatedState(width)
    val resize by rememberUpdatedState(onResize)
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown by animateDpAsState(
        targetValue = (dragged ?: width).dp,
        animationSpec = if (dragged != null) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "railWidth",
    )
    val compact = shown < NAV_LABEL_WIDTH
    val handle = remember { MutableInteractionSource() }
    val hovered by handle.collectIsHoveredAsState()
    val edge by animateColorAsState(
        if (hovered || dragged != null) colors.outlineVariant else Color.Transparent,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "railEdge",
    )

    val capsule = RoundedCornerShape(36.dp)
    BoxWithConstraints(modifier.fillMaxHeight().width(shown)) {
        // On a short screen (a phone on its side) the tabs come first; the logo shows only with room to spare.
        val chrome = 12.dp + bottomInset + 12.dp + 16.dp
        val logoFits = maxHeight - chrome - (RAIL_ITEM_HEIGHT + 4.dp) * tabs.size >= RAIL_LOGO_HEIGHT
        // A frosted capsule, like the floating bar stood on end.
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = bottomInset + 12.dp)
                .shadow(8.dp, capsule)
                .glass(
                    backdrop = backdrop,
                    shape = capsule,
                    tint = colors.surfaceContainerHigh.copy(alpha = 0.72f),
                    fallback = colors.surfaceContainerHigh,
                    blurRadius = 40.dp,
                )
                .hairline(capsule)
                .padding(8.dp),
        ) {
            if (logoFits) Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = if (compact) 0.dp else 12.dp, top = 8.dp, bottom = 20.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(colors.primaryContainer),
                ) {
                    Icon(PlayerIcons.Logo, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(26.dp))
                }
                if (!compact) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(Res.string.app_name),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            tabs.forEach { tab ->
                RailItem(tab = tab, selected = nav.tab == tab.route, compact = compact) {
                    nav.selectTab(tab.route)
                }
                if (tab.route == splitAfter) Spacer(Modifier.weight(1f))
            }
        }
        // The grab strip along the right edge; a thin line shows while it's hovered or dragged.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(bottom = bottomInset)
                .width(10.dp)
                .hoverable(handle)
                .pointerHoverIcon(horizontalResizePointer)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        resize(if (currentWidth > NAV_MIN_WIDTH) NAV_MIN_WIDTH else NAV_MAX_WIDTH)
                    })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = shown.value },
                        onDragEnd = {
                            val w = dragged ?: return@detectHorizontalDragGestures
                            // A rail too narrow for its labels collapses to the icons.
                            resize(if (w < NAV_LABEL_WIDTH.value) NAV_MIN_WIDTH else w)
                            dragged = null
                        },
                        onDragCancel = { dragged = null },
                    ) { change, dx ->
                        change.consume()
                        dragged = ((dragged ?: shown.value) + dx / density.density).coerceIn(NAV_MIN_WIDTH, NAV_MAX_WIDTH)
                    }
                },
        ) {
            Box(Modifier.width(2.dp).fillMaxHeight(0.3f).clip(CircleShape).background(edge))
        }
    }
}

@Composable
private fun RailItem(tab: NavTab, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val scale = animatePressAsState(source, 0.94f)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(if (selected) colors.secondaryContainer else Color.Transparent, effects, label = "railBg")
    val content by animateColorAsState(
        if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, effects, label = "railFg"
    )
    val label = stringResource(tab.label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Icons only: a round button rather than a squashed pill.
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
        modifier = Modifier
            .then(if (compact) Modifier.size(RAIL_ITEM_HEIGHT) else Modifier.fillMaxWidth().height(52.dp))
            .scaleBy(scale)
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable(interactionSource = source, indication = null, role = Role.Tab) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .semantics { this.selected = selected }
            .padding(horizontal = if (compact) 0.dp else 16.dp),
    ) {
        Icon(tab.icon, contentDescription = if (compact) label else null, tint = content, modifier = Modifier.size(24.dp))
        if (!compact) {
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val FLOATING_NAV_HEIGHT = 72.dp
private const val SELECTED_WEIGHT = 3f

/** Narrower than this the rail hides its labels. */
private val NAV_LABEL_WIDTH = 160.dp
