package me.rerere.rikkahub.ui.pages.agent

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.ui.components.ui.Greeting
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.chat.ConversationList
import me.rerere.rikkahub.ui.pages.chat.ConversationSortButton
import me.rerere.rikkahub.ui.pages.chat.DrawerAction
import me.rerere.rikkahub.ui.pages.chat.DrawerEntry
import me.rerere.rikkahub.ui.pages.chat.FolderBar
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun AgentDrawerContent(
    currentConversation: Conversation,
    onSelectConversation: (Uuid) -> Unit,
    onNewConversation: () -> Unit,
    onCloseDrawer: () -> Unit,
) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as ComponentActivity
    val drawerVm: AgentDrawerVM = koinViewModel(viewModelStoreOwner = activity)

    val conversations = drawerVm.conversationsPaging.collectAsLazyPagingItems()
    val allConversations by drawerVm.allConversations.collectAsStateWithLifecycle()
    val folders by drawerVm.folders.collectAsStateWithLifecycle()
    val selectedFolderId by drawerVm.selectedFolderId.collectAsStateWithLifecycle()
    val sortOrder by drawerVm.sortOrder.collectAsStateWithLifecycle()
    val activeConversationIds by drawerVm.activeConversationIds.collectAsStateWithLifecycle()
    val conversationStatuses by drawerVm.conversationStatuses.collectAsStateWithLifecycle()
    val conversationListState = rememberLazyListState()

    var showHistorySearchSheet by remember { mutableStateOf(false) }
    var historySearchQuery by remember { mutableStateOf("") }

    var showMoveToFolderSheet by remember { mutableStateOf(false) }
    var conversationToMoveFolder by remember { mutableStateOf<Conversation?>(null) }
    val folderSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val scrollableHeader = windowHeight < 640.dp

    val drawerHeader: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 用户头像与昵称（与官方 Chat 侧边栏一致）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UIAvatar(
                    name = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                    value = settings.displaySetting.userAvatar,
                    onUpdate = {},
                    modifier = Modifier.size(50.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Greeting(
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // 历史记录入口 + 尾随搜索图标（与官方 Chat 侧边栏一致，不在抽屉内放输入框，避免弹出软键盘）
            DrawerEntry(
                icon = HugeIcons.TransactionHistory,
                text = stringResource(R.string.chat_page_history),
                onClick = {
                    historySearchQuery = ""
                    showHistorySearchSheet = true
                },
                trailingIcon = HugeIcons.Search01,
                trailingDescription = stringResource(R.string.chat_page_search_chats),
                onTrailingClick = {
                    historySearchQuery = ""
                    showHistorySearchSheet = true
                },
            )

            DrawerEntry(
                icon = HugeIcons.Clock01,
                text = "工作流",
                onClick = {
                    onCloseDrawer()
                    navController.navigate(Screen.AgentWorkflow)
                },
            )

            DrawerEntry(
                icon = me.rerere.hugeicons.stroke.Image02,
                text = "资料库",
                onClick = {
                    onCloseDrawer()
                    navController.navigate(Screen.AgentLibrary)
                },
            )

            // 文件夹栏与排序按钮（复用官方组件）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FolderBar(
                    folders = folders,
                    selectedFolderId = selectedFolderId,
                    onSelect = { drawerVm.selectFolder(it) },
                    onCreate = { showCreateFolderDialog = true },
                    onRename = { folderToRename = it },
                    onDelete = { folderToDelete = it },
                    modifier = Modifier.weight(1f),
                )

                ConversationSortButton(
                    sortOrder = sortOrder,
                    onSortOrderChange = { newOrder ->
                        drawerVm.updateSortOrder(newOrder)
                        scope.launch { conversationListState.scrollToItem(0) }
                    },
                )
            }
        }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!scrollableHeader) {
                drawerHeader()
            }

            // 复用官方 ConversationList（含置顶、按时间分组、运行/未读/失败状态显示等）
            ConversationList(
                current = currentConversation,
                conversations = conversations,
                conversationJobs = activeConversationIds,
                listState = conversationListState,
                header = if (scrollableHeader) drawerHeader else null,
                statusMap = conversationStatuses,
                showMoveToAssistant = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onClick = { conv ->
                    onSelectConversation(conv.id)
                    onCloseDrawer()
                },
                onRegenerateTitle = { conv ->
                    renameTarget = conv
                    renameText = conv.title
                },
                onDelete = { conv ->
                    drawerVm.delete(conv.id)
                    if (conv.id == currentConversation.id) {
                        onNewConversation()
                    }
                },
                onPin = { conv ->
                    drawerVm.setPinned(conv.id, !conv.isPinned)
                },
                onMoveToAssistant = { },
                onMoveToFolder = { conv ->
                    conversationToMoveFolder = conv
                    showMoveToFolderSheet = true
                },
            )

            // 底部栏（去除助手相关项；保留用量统计与设置，设置直接进入 AgentSetting）
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                DrawerAction(
                    icon = {
                        Icon(
                            imageVector = HugeIcons.ChartColumn,
                            contentDescription = "用量统计",
                        )
                    },
                    label = {
                        Text("用量统计")
                    },
                    onClick = {
                        onCloseDrawer()
                        navController.navigate(Screen.Stats)
                    },
                )

                Spacer(Modifier.weight(1f))

                DrawerAction(
                    icon = {
                        Icon(
                            imageVector = HugeIcons.Settings03,
                            contentDescription = stringResource(R.string.settings),
                        )
                    },
                    label = {
                        Text(stringResource(R.string.settings))
                    },
                    onClick = {
                        onCloseDrawer()
                        navController.navigate(Screen.AgentSetting)
                    },
                )
            }
        }
    }

    // 移动到文件夹 Bottom Sheet（官方样式）
    if (showMoveToFolderSheet) {
        val doMove: (Uuid?) -> Unit = { folderId ->
            conversationToMoveFolder?.let { conversation ->
                drawerVm.moveConversationToFolder(conversation.id, folderId)
                scope.launch {
                    folderSheetState.hide()
                    showMoveToFolderSheet = false
                    conversationToMoveFolder = null
                }
            }
        }
        ModalBottomSheet(
            onDismissRequest = {
                showMoveToFolderSheet = false
                conversationToMoveFolder = null
            },
            sheetState = folderSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_page_move_to_folder),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Surface(
                    onClick = { doMove(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = if (conversationToMoveFolder?.folderId == null) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(HugeIcons.Folder01, null)
                        Text(
                            text = stringResource(R.string.chat_page_remove_from_folder),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(folders) { folder ->
                        val isCurrent = folder.id == conversationToMoveFolder?.folderId
                        Surface(
                            onClick = { doMove(folder.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            tonalElevation = if (isCurrent) 2.dp else 0.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(HugeIcons.Folder01, null)
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 新建文件夹对话框
    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.chat_page_create_folder)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.chat_page_folder_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.createFolder(name)
                        showCreateFolderDialog = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            },
        )
    }

    // 重命名文件夹对话框
    folderToRename?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text(stringResource(R.string.chat_page_rename_folder)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.renameFolder(folder.id, name)
                        folderToRename = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            },
        )
    }

    // 删除文件夹确认
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.chat_page_delete_folder)) },
            text = { Text(stringResource(R.string.chat_page_delete_folder_confirm, folder.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.deleteFolder(folder.id)
                        folderToDelete = null
                    },
                ) { Text(stringResource(R.string.chat_page_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            },
        )
    }

    // 重命名会话对话框
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
                    },
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            },
        )
    }

    if (showHistorySearchSheet) {
        val searchSheetState = rememberBottomSheetState(initialValue = SheetValue.Expanded)
        val filtered = remember(allConversations, historySearchQuery) {
            val q = historySearchQuery.trim()
            if (q.isEmpty()) allConversations
            else allConversations.filter { it.title.contains(q, ignoreCase = true) }
        }
        ModalBottomSheet(
            onDismissRequest = { showHistorySearchSheet = false },
            sheetState = searchSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_page_history),
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = historySearchQuery,
                    onValueChange = { historySearchQuery = it },
                    placeholder = { Text(stringResource(R.string.chat_page_search_chats)) },
                    leadingIcon = {
                        Icon(HugeIcons.Search01, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = if (historySearchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { historySearchQuery = "" }) {
                                Icon(HugeIcons.Cancel01, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    items(filtered, key = { it.id.toString() }) { conv ->
                        Surface(
                            onClick = {
                                showHistorySearchSheet = false
                                onSelectConversation(conv.id)
                                onCloseDrawer()
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = conv.title.ifBlank { "Agent" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = conv.updateAt.toLocalDateTime(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
