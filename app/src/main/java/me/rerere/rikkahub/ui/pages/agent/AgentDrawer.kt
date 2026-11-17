package me.rerere.rikkahub.ui.pages.agent

import androidx.activity.ComponentActivity
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sorting01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun AgentDrawerContent(
    currentConversationId: Uuid,
    onSelectConversation: (Uuid) -> Unit,
    onNewConversation: () -> Unit,
    onBackHome: () -> Unit,
    onCloseDrawer: () -> Unit,
) {
    val navController = LocalNavController.current
    val activity = LocalContext.current as ComponentActivity
    val drawerVm: AgentDrawerVM = koinViewModel(viewModelStoreOwner = activity)

    val conversations by drawerVm.conversations.collectAsStateWithLifecycle()
    val searchQuery by drawerVm.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by drawerVm.sortOrder.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = HugeIcons.Sparkles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Antigravity Agent",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "独立会话 · 独立配置",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                IconButton(
                    onClick = {
                        onNewConversation()
                        onCloseDrawer()
                    }
                ) {
                    Icon(
                        imageVector = HugeIcons.MessageAdd01,
                        contentDescription = "新建 Agent 会话",
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { drawerVm.updateSearchQuery(it) },
                placeholder = { Text("搜索 Agent 会话") },
                leadingIcon = {
                    Icon(
                        imageVector = HugeIcons.Search01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "会话列表 (${conversations.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Sorting01,
                            contentDescription = "排序方式",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("按更新时间") },
                            trailingIcon = {
                                if (sortOrder == ConversationSortOrder.UPDATE_TIME) {
                                    Icon(HugeIcons.Tick01, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            onClick = {
                                drawerVm.updateSortOrder(ConversationSortOrder.UPDATE_TIME)
                                showSortMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("按创建时间") },
                            trailingIcon = {
                                if (sortOrder == ConversationSortOrder.CREATE_TIME) {
                                    Icon(HugeIcons.Tick01, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            onClick = {
                                drawerVm.updateSortOrder(ConversationSortOrder.CREATE_TIME)
                                showSortMenu = false
                            },
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (conversations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "暂无 Agent 会话" else "没有匹配的会话",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(conversations, key = { it.id.toString() }) { item ->
                    val selected = item.id == currentConversationId
                    var menuExpanded by remember { mutableStateOf(false) }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .combinedClickable(
                                    onClick = {
                                        onSelectConversation(item.id)
                                        onCloseDrawer()
                                    },
                                    onLongClick = { menuExpanded = true },
                                ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (item.isPinned) {
                                    Icon(
                                        imageVector = HugeIcons.LookTop,
                                        contentDescription = "已置顶",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title.ifBlank { "未命名任务" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        // Instant 只有 toLocalDateTime/toLocalDate/toLocalTime，没有 toLocalString
                                        text = item.updateAt.toLocalDateTime(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (item.isPinned) "取消置顶" else "置顶") },
                                leadingIcon = { Icon(HugeIcons.LookTop, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    drawerVm.setPinned(item.id, !item.isPinned)
                                    menuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                leadingIcon = { Icon(HugeIcons.PencilEdit01, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    renameTarget = item
                                    renameText = item.title
                                    menuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        HugeIcons.Delete01,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    drawerVm.delete(item.id)
                                    if (selected) {
                                        onNewConversation()
                                    }
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = {
                    onCloseDrawer()
                    onBackHome()
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowLeft01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("首页", maxLines = 1)
            }

            FilledTonalButton(
                onClick = {
                    onCloseDrawer()
                    navController.navigate(Screen.AgentSetting)
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = HugeIcons.Settings03,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("界面设置", maxLines = 1)
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.rename(target.id, renameText)
                        renameTarget = null
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消")
                }
            },
        )
    }
}
