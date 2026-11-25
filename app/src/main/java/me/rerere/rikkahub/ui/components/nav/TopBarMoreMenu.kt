package me.rerere.rikkahub.ui.components.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.ai.chatInputHaze

internal data class TopBarMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

@Composable
internal fun Modifier.topBarMenuHaze(
    hazeState: HazeState?,
    displaySetting: DisplaySetting?,
    containerColor: Color,
    shape: RoundedCornerShape,
): Modifier {
    val shadowModifier = Modifier.shadow(
        elevation = 8.dp,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.24f),
        spotColor = Color.Black.copy(alpha = 0.24f),
    )
    if (hazeState == null || displaySetting == null || !displaySetting.enableBlurEffect) {
        return this.then(shadowModifier)
    }
    return this
        .then(shadowModifier)
        .clip(shape)
        .chatInputHaze(
            hazeState = hazeState,
            displaySetting = displaySetting,
            containerColor = containerColor,
            shape = shape,
        )
}

@Composable
internal fun topBarMenuGroupContainerColor(
    displaySetting: DisplaySetting?,
    baseContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
): Color {
    if (displaySetting == null) return MenuDefaults.groupStandardContainerColor
    if (!displaySetting.enableBlurEffect) return baseContainerColor
    val scrimAlpha = when (displaySetting.backgroundEffectType) {
        BackgroundEffectType.GLASS -> 0.68f
        BackgroundEffectType.BLUR -> 0.74f
    }
    return baseContainerColor.copy(alpha = scrimAlpha)
}

@Composable
internal fun topBarMenuGroupBorder(displaySetting: DisplaySetting?): BorderStroke? =
    if (displaySetting != null) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        null
    }

internal fun MenuGroupShapes.toRoundedCornerShape(): RoundedCornerShape =
    (shape as? RoundedCornerShape) ?: RoundedCornerShape(16.dp)

@Composable
internal fun TopBarMoreMenu(
    vararg groups: List<TopBarMenuItem>,
    hazeState: HazeState? = null,
    displaySetting: DisplaySetting? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val baseContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val styled = hazeState != null && displaySetting != null
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(HugeIcons.MoreVertical, stringResource(R.string.more_options))
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEachIndexed { groupIndex, items ->
                if (groupIndex > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                val groupShapes = MenuDefaults.groupShape(index = groupIndex, count = groups.size)
                val groupContent: @Composable () -> Unit = {
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
                if (styled) {
                    val menuShape = groupShapes.toRoundedCornerShape()
                    DropdownMenuGroup(
                        shapes = groupShapes,
                        modifier = Modifier.topBarMenuHaze(
                            hazeState = hazeState,
                            displaySetting = displaySetting,
                            containerColor = baseContainerColor,
                            shape = menuShape,
                        ),
                        containerColor = topBarMenuGroupContainerColor(displaySetting, baseContainerColor),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = topBarMenuGroupBorder(displaySetting),
                    ) {
                        groupContent()
                    }
                } else {
                    DropdownMenuGroup(shapes = groupShapes) {
                        groupContent()
                    }
                }
            }
        }
    }
}
