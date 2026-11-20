package me.rerere.rikkahub.ui.components.nav

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.rikkahub.R
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
internal fun TopBarMoreMenu(
    vararg groups: List<TopBarMenuItem>,
    hazeState: HazeState? = null,
    displaySetting: DisplaySetting? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val useHaze = hazeState != null && displaySetting?.enableBlurEffect == true
    val fallbackContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(HugeIcons.MoreVertical, stringResource(R.string.more_options))
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEachIndexed { groupIndex, items ->
                if (groupIndex > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                val groupShapes = MenuDefaults.groupShape(index = groupIndex, count = groups.size)
                val cornerShape = (groupShapes.shape as? RoundedCornerShape) ?: RoundedCornerShape(16.dp)
                DropdownMenuGroup(
                    shapes = groupShapes,
                    containerColor = if (useHaze) Color.Transparent else MenuDefaults.groupStandardContainerColor,
                    modifier = if (useHaze) {
                        Modifier
                            .clip(groupShapes.shape)
                            .chatInputHaze(
                                hazeState = hazeState,
                                displaySetting = displaySetting,
                                containerColor = fallbackContainerColor,
                                shape = cornerShape,
                            )
                    } else {
                        Modifier
                    },
                ) {
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
