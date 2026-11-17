package me.rerere.rikkahub.ui.pages.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.pages.chat.ChatList
import me.rerere.rikkahub.ui.pages.chat.ChatSelectionState
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun AgentPage(id: Uuid) {
    val navController = LocalNavController.current
    val vm: AgentVM = koinViewModel(
        key = "agent-$id",
        parameters = { parametersOf(id.toString()) },
    )

    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val isGenerating by vm.isGenerating.collectAsStateWithLifecycle()
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()
    val uiSettings by vm.uiSettings.collectAsStateWithLifecycle()
    val baseSettings = LocalSettings.current
    val composedSettings = remember(uiSettings, baseSettings) {
        uiSettings.toCompositionSettings(baseSettings)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val listState = rememberLazyListState()
    val previewListState = rememberLazyListState()
    val selectionState = remember { ChatSelectionState() }
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    val inputState = remember(id) { ChatInputState() }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<UIMessage?>(null) }
    var editingText by remember { mutableStateOf("") }

    fun navigateToAgentConversation(targetId: Uuid) {
        navController.navigate(Screen.Agent(targetId.toString())) {
            popUpTo(Screen.Home)
        }
    }

    CompositionLocalProvider(LocalSettings provides composedSettings) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AgentDrawerContent(
                    currentConversationId = id,
                    onSelectConversation = { targetId ->
                        if (targetId != id) {
                            navigateToAgentConversation(targetId)
                        }
                    },
                    onNewConversation = {
                        navigateToAgentConversation(Uuid.random())
                    },
                    onBackHome = {
                        navController.popBackStack()
                    },
                    onCloseDrawer = {
                        scope.launch { drawerState.close() }
                    },
                )
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    scope.launch { drawerState.open() }
                                }
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Menu03,
                                    contentDescription = "打开 Agent 侧边栏",
                                )
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = conversation.title.ifBlank { "Antigravity Agent" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "文件转换智能体 · 独立工作区",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { navigateToAgentConversation(Uuid.random()) }
                            ) {
                                Icon(
                                    imageVector = HugeIcons.MessageAdd01,
                                    contentDescription = "新建会话",
                                )
                            }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(
                                        imageVector = HugeIcons.MoreVertical,
                                        contentDescription = "更多选项",
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("重命名会话") },
                                        leadingIcon = { Icon(HugeIcons.PencilEdit01, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showMoreMenu = false
                                            renameText = conversation.title
                                            showRenameDialog = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Agent 界面设置") },
                                        leadingIcon = { Icon(HugeIcons.Settings03, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showMoreMenu = false
                                            navController.navigate(Screen.AgentSetting)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("清空当前消息", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                HugeIcons.Delete01,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            showClearConfirm = true
                                        },
                                    )
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    AgentInput(
                        state = inputState,
                        settings = composedSettings,
                        loading = isGenerating,
                        hazeState = hazeState,
                        onSend = { parts -> vm.sendMessage(parts) },
                        onStop = { vm.stopGeneration() },
                    )
                },
            ) { innerPadding ->
                if (conversation.messageNodes.isEmpty()) {
                    AgentEmptyState(
                        innerPadding = innerPadding,
                        onPromptClick = { prompt ->
                            vm.sendMessage(listOf(UIMessagePart.Text(prompt)))
                        },
                    )
                } else {
                    ChatList(
                        innerPadding = innerPadding,
                        conversation = conversation,
                        state = listState,
                        previewListState = previewListState,
                        selectionState = selectionState,
                        loading = isGenerating,
                        previewMode = previewMode,
                        onPreviewModeChange = { previewMode = it },
                        settings = composedSettings,
                        hazeState = hazeState,
                        onRegenerate = { msg -> vm.regenerate(msg) },
                        onEdit = { msg ->
                            editingMessage = msg
                            editingText = msg.toText()
                        },
                        onDelete = { msg -> vm.deleteMessage(msg) },
                        onUpdateMessage = { node -> vm.updateMessageNode(node) },
                    )
                }
            }
        }
    }

    errorMessage?.let { err ->
        AlertDialog(
            onDismissRequest = { vm.dismissError() },
            title = { Text("Agent 请求提示") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { vm.dismissError() }) {
                    Text("知道了")
                }
            },
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
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
                        vm.renameConversation(renameText)
                        showRenameDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    RikkaConfirmDialog(
        show = showClearConfirm,
        title = "清空消息",
        confirmText = "确定",
        dismissText = "取消",
        onConfirm = {
            vm.clearMessages()
            showClearConfirm = false
        },
        onDismiss = { showClearConfirm = false },
    ) {
        Text("确定要清空当前 Agent 会话中的所有消息吗？")
    }

    editingMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("编辑消息") },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val otherParts = target.parts.filterNot { it is UIMessagePart.Text }
                        val updatedParts = buildList {
                            if (editingText.isNotBlank()) add(UIMessagePart.Text(editingText))
                            addAll(otherParts)
                        }
                        vm.editMessage(target, updatedParts)
                        editingMessage = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentEmptyState(
    innerPadding: PaddingValues,
    onPromptClick: (String) -> Unit,
) {
    val suggestions = remember {
        listOf(
            "将上传的 PDF 转换为结构化 Markdown",
            "提取文档中的表格并整理为 Markdown 表格",
            "校对 OCR 草稿并保留 LaTeX 数学公式",
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Sparkles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Antigravity 文件转换 Agent",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "独立的数据库与界面配置，专为 AI 文件转换与反重力接口打造。上传 PDF、图片或输入指令开始。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(4.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { prompt ->
                    AssistChip(
                        onClick = { onPromptClick(prompt) },
                        shape = RoundedCornerShape(14.dp),
                        label = {
                            Text(
                                text = prompt,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}
