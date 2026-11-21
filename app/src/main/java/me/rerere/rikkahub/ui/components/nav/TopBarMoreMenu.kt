package me.rerere.rikkahub.ui.components.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.ai.chatInputHaze
import kotlin.math.roundToInt

internal data class TopBarMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

internal class HazeMenuOverlayData(
    val anchorBounds: Rect,
    val groups: List<List<TopBarMenuItem>>,
    val hazeState: HazeState,
    val displaySetting: DisplaySetting,
)

internal class HazeMenuHostState {
    var activeMenu by mutableStateOf<HazeMenuOverlayData?>(null)
}

internal val LocalHazeMenuHost = compositionLocalOf<HazeMenuHostState?> { null }

@Composable
internal fun ProvideHazeMenuHost(content: @Composable () -> Unit) {
    val hostState = remember { HazeMenuHostState() }
    val active = hostState.activeMenu
    var displayed by remember { mutableStateOf<HazeMenuOverlayData?>(null) }
    val visibleState = remember { MutableTransitionState(false) }

    LaunchedEffect(active) {
        if (active != null) {
            displayed = active
            visibleState.targetState = true
        } else {
            visibleState.targetState = false
        }
    }

    LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
        if (visibleState.isIdle && !visibleState.currentState) {
            displayed = null
        }
    }

    val menuData = active ?: displayed

    CompositionLocalProvider(LocalHazeMenuHost provides hostState) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (menuData != null && (visibleState.currentState || visibleState.targetState)) {
                BackHandler { hostState.activeMenu = null }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { hostState.activeMenu = null },
                ) {
                    val density = LocalDensity.current
                    val containerSize = LocalWindowInfo.current.containerSize
                    var menuSize by remember(menuData) { mutableStateOf(IntSize.Zero) }
                    val marginPx = with(density) { 12.dp.roundToPx() }
                    val gapPx = with(density) { 6.dp.roundToPx() }

                    val x = (menuData.anchorBounds.right.roundToInt() - menuSize.width)
                        .coerceIn(marginPx, (containerSize.width - menuSize.width - marginPx).coerceAtLeast(marginPx))
                    val y = (menuData.anchorBounds.bottom.roundToInt() + gapPx)
                        .coerceIn(marginPx, (containerSize.height - menuSize.height - marginPx).coerceAtLeast(marginPx))

                    AnimatedVisibility(
                        visibleState = visibleState,
                        enter = fadeIn() + scaleIn(
                            initialScale = 0.88f,
                            transformOrigin = TransformOrigin(1f, 0f),
                        ),
                        exit = fadeOut() + scaleOut(
                            targetScale = 0.88f,
                            transformOrigin = TransformOrigin(1f, 0f),
                        ),
                        modifier = Modifier
                            .offset { IntOffset(x, y) }
                            .onSizeChanged { menuSize = it },
                    ) {
                        val shape = RoundedCornerShape(20.dp)
                        val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        Surface(
                            modifier = Modifier
                                .widthIn(min = 168.dp, max = 240.dp)
                                .clip(shape)
                                .chatInputHaze(
                                    hazeState = menuData.hazeState,
                                    displaySetting = menuData.displaySetting,
                                    containerColor = containerColor,
                                    shape = shape,
                                ),
                            shape = shape,
                            color = Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp),
                            ) {
                                menuData.groups.forEachIndexed { groupIndex, items ->
                                    if (groupIndex > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                        )
                                    }
                                    items.forEach { item ->
                                        val contentColor = when {
                                            !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            item.destructive -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 6.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable(enabled = item.enabled) {
                                                    hostState.activeMenu = null
                                                    item.onClick()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = null,
                                                tint = contentColor,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = contentColor,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TopBarMoreMenu(
    vararg groups: List<TopBarMenuItem>,
    hazeState: HazeState? = null,
    displaySetting: DisplaySetting? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    val hazeHost = LocalHazeMenuHost.current
    val useHazeOverlay = hazeHost != null && hazeState != null && displaySetting != null && displaySetting.enableBlurEffect

    Box {
        IconButton(
            onClick = {
                val bounds = anchorBounds
                if (useHazeOverlay && bounds != null) {
                    hazeHost.activeMenu = if (hazeHost.activeMenu != null) {
                        null
                    } else {
                        HazeMenuOverlayData(
                            anchorBounds = bounds,
                            groups = groups.toList(),
                            hazeState = hazeState,
                            displaySetting = displaySetting,
                        )
                    }
                } else {
                    expanded = true
                }
            },
            modifier = Modifier.onGloballyPositioned { coords ->
                anchorBounds = coords.boundsInRoot()
            },
        ) {
            Icon(HugeIcons.MoreVertical, stringResource(R.string.more_options))
        }
        if (!useHazeOverlay) {
            DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
                groups.forEachIndexed { groupIndex, items ->
                    if (groupIndex > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = groupIndex, count = groups.size)) {
                        items.forEachIndexed { index, item ->
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                onClick = {
                                    expanded = false
                                    item.onClick()
                                },
                                shape = MenuDefaults.itemShape(index = index, count = items.size).shape,
                                leadingIcon = { Icon(item.icon, contentDescription = null) },
                                enabled = item.enabled,
                                colors = if (item.destructive) {
                                    val color = MaterialTheme.colorScheme.error
                                    MenuDefaults.itemColors(textColor = color, leadingIconColor = color)
                                } else {
                                    MenuDefaults.itemColors()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
