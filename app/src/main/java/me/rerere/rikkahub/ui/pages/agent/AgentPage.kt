package me.rerere.rikkahub.ui.pages.agent

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Refresh
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.workflow.DEFAULT_POCKETBASE_URL
import me.rerere.rikkahub.ui.components.ai.AgentFilesPicker
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.ChatTopBarCapsule
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.pages.chat.AssistantBackground
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
    val isInitialLoading by vm.isInitialLoading.collectAsStateWithLifecycle()
    val agentAssistant by vm.agentAssistant.collectAsStateWithLifecycle()
    val selectedModel by vm.selectedModel.collectAsStateWithLifecycle()
    val selectedAgentModelName by vm.selectedAgentModelName.collectAsStateWithLifecycle()
    val customGeminiModels by vm.customGeminiModels.collectAsStateWithLifecycle()
    val availableAgentModels by vm.availableAgentModels.collectAsStateWithLifecycle()
    val isGenerating by vm.isGenerating.collectAsStateWithLifecycle()
    val isSyncing by vm.isSyncing.collectAsStateWithLifecycle()
    val activeStatusText by vm.activeStatusText.collectAsStateWithLifecycle()
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val showConnectDialog by vm.showConnectDialog.collectAsStateWithLifecycle()
    val isConnecting by vm.isConnecting.collectAsStateWithLifecycle()
    val connectError by vm.connectError.collectAsStateWithLifecycle()
    val uiSettings by vm.uiSettings.collectAsStateWithLifecycle()
    val initialDraft by vm.initialDraft.collectAsStateWithLifecycle()
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

    LaunchedEffect(id) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(drawerState.currentValue, drawerState.isOpen) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    val listState = rememberLazyListState()
    val previewListState = rememberLazyListState()
    val selectionState = remember { ChatSelectionState() }
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    val inputState = remember(id) { ChatInputState() }
    var draftLoaded by remember(id) { mutableStateOf(false) }

    LaunchedEffect(id, initialDraft) {
        val draft = initialDraft
        if (draft != null && !draftLoaded) {
            if (draft.isNotEmpty() && inputState.textContent.text.isEmpty()) {
                inputState.setMessageText(draft)
            }
            draftLoaded = true
        }
    }

    @OptIn(FlowPreview::class)
    LaunchedEffect(id, draftLoaded) {
        if (!draftLoaded) return@LaunchedEffect
        snapshotFlow { inputState.textContent.text.toString() }
            .drop(1)
            .debounce(250)
            .collectLatest { text ->
                vm.saveDraft(text)
            }
    }

    DisposableEffect(id) {
        onDispose {
            if (draftLoaded) {
                vm.saveDraft(inputState.textContent.text.toString())
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (draftLoaded) {
            vm.saveDraft(inputState.textContent.text.toString())
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (draftLoaded) {
            vm.saveDraft(inputState.textContent.text.toString())
        }
    }

    var listInitialized by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, conversation.messageNodes.size) {
        if (!listInitialized && conversation.messageNodes.isNotEmpty()) {
            listState.requestScrollToItem(conversation.currentMessages.size + 5)
            listInitialized = true
        }
    }

    var showAttachSheet by remember { mutableStateOf(false) }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = composedSettings,
        onAttachmentAdded = { showAttachSheet = false },
    )

    BackHandler(enabled = previewMode && !drawerState.isOpen) {
        previewMode = false
    }

    val listScrolledUnderTopBar by remember(listState, previewListState) {
        derivedStateOf {
            if (previewMode) previewListState.canScrollBackward else listState.canScrollBackward
        }
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showModelPickerSheet by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<UIMessage?>(null) }
    var editingText by remember { mutableStateOf("") }
    var insightsMessage by remember { mutableStateOf<UIMessage?>(null) }
    var showLiveInsights by remember { mutableStateOf(false) }

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
                    currentConversation = conversation,
                    onSelectConversation = { targetId ->
                        if (targetId != id) {
                            navigateToAgentConversation(targetId)
                        }
                    },
                    onNewConversation = {
                        navigateToAgentConversation(Uuid.random())
                    },
                    onCloseDrawer = {
                        scope.launch { drawerState.close() }
                    },
                )
            },
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
            ) {
                AssistantBackground(
                    setting = composedSettings,
                    modifier = Modifier.hazeSource(hazeState),
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                            ),
                            navigationIcon = {
                                ChatTopBarCapsule(
                                    hazeState = hazeState,
                                    displaySetting = composedSettings.displaySetting,
                                    showBackground = listScrolledUnderTopBar,
                                    modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                                ) {
                                    IconButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            scope.launch { drawerState.open() }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = HugeIcons.Menu03,
                                            contentDescription = "打开侧边栏",
                                        )
                                    }
                                }
                            },
                            title = {
                                Column {
                                    Text(
                                        text = conversation.title.ifBlank { "Agent" },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val subtitleText = when {
                                        isGenerating && !activeStatusText.isNullOrBlank() -> "运行中 · $activeStatusText"
                                        !activeStatusText.isNullOrBlank() -> activeStatusText!!
                                        isSyncing -> "正在同步…"
                                        session == null -> "未连接云端 · $selectedAgentModelName"
                                        else -> selectedAgentModelName
                                    }
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            actions = {
                                ChatTopBarCapsule(
                                    hazeState = hazeState,
                                    displaySetting = composedSettings.displaySetting,
                                    showBackground = listScrolledUnderTopBar,
                                    modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                                ) {
                                    IconButton(
                                        onClick = { navigateToAgentConversation(Uuid.random()) }
                                    ) {
                                        Icon(
                                            imageVector = HugeIcons.MessageAdd01,
                                            contentDescription = "新建会话",
                                        )
                                    }
                                    AgentPageMenu(
                                        hasMessages = conversation.messageNodes.isNotEmpty(),
                                        previewMode = previewMode,
                                        hasSession = session != null,
                                        onTogglePreview = { previewMode = !previewMode },
                                        onSyncOrConnect = {
                                            if (session == null) {
                                                vm.openConnectDialog()
                                            } else {
                                                vm.syncFromRemote()
                                            }
                                        },
                                        onRename = {
                                            renameText = conversation.title
                                            showRenameDialog = true
                                        },
                                        onOpenSettings = { navController.navigate(Screen.AgentSetting) },
                                        onClear = { showClearConfirm = true },
                                    )
                                }
                            },
                        )
                    },
                    bottomBar = {
                        ChatInput(
                            state = inputState,
                            loading = isGenerating,
                            settings = composedSettings,
                            assistant = agentAssistant,
                            chatModel = selectedModel,
                            hazeState = hazeState,
                            placeholderText = "输入指令…",
                            alwaysShowReasoningButton = true,
                            showAsrButton = false,
                            overrideModelId = selectedAgentModelName,
                            onModelSelectorClick = { showModelPickerSheet = true },
                            onUpdateChatModel = vm::updateAgentModel,
                            onUpdateAssistant = vm::updateAgentAssistant,
                            onMoreClick = { showAttachSheet = true },
                            onCancelClick = { vm.stopGeneration() },
                            onSendClick = {
                                val parts = inputState.getContents()
                                if (vm.sendMessage(parts)) {
                                    inputState.clearInput()
                                    scope.launch {
                                        delay(100.milliseconds)
                                        listState.requestScrollToItem(conversation.messageNodes.size + 5)
                                    }
                                }
                            },
                            onLongSendClick = {
                                val parts = inputState.getContents()
                                if (vm.sendMessage(parts)) {
                                    inputState.clearInput()
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    val showLoadingPlaceholder =
                        (isInitialLoading || isSyncing) && conversation.messageNodes.isEmpty()

                    AnimatedContent(
                        targetState = showLoadingPlaceholder,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "agent_conversation_loading",
                    ) { loadingConversation ->
                        if (loadingConversation) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
                            }
                        } else {
                            ChatList(
                                innerPadding = innerPadding,
                                conversation = conversation,
                                state = listState,
                                previewListState = previewListState,
                                selectionState = selectionState,
                                loading = isGenerating,
                                processingStatus = activeStatusText,
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
                                onJumpToMessage = { index ->
                                    previewMode = false
                                    scope.launch {
                                        listState.requestScrollToItem(index)
                                    }
                                },
                                onProcessingStatusClick = { showLiveInsights = true },
                                onOpenAgentInsights = { msg -> insightsMessage = msg },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLiveInsights || insightsMessage != null) {
        val targetMessage = insightsMessage ?: conversation.currentMessages.lastOrNull {
            it.role == me.rerere.ai.core.MessageRole.ASSISTANT
        }
        AgentInsightsSheet(
            message = targetMessage,
            isRunning = isGenerating && (insightsMessage == null || insightsMessage?.id == targetMessage?.id),
            liveElapsedText = activeStatusText,
            onDismiss = {
                showLiveInsights = false
                insightsMessage = null
            },
        )
    }

    if (showAttachSheet) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Expanded)
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showAttachSheet = false },
        ) {
            AgentFilesPicker(
                onTakePic = {
                    showAttachSheet = false
                    attachmentPickerActions.onTakePicture()
                },
                onPickImage = {
                    showAttachSheet = false
                    attachmentPickerActions.onPickImage()
                },
                onSketch = {
                    showAttachSheet = false
                    attachmentPickerActions.onSketch()
                },
                onPickVideo = {
                    showAttachSheet = false
                    attachmentPickerActions.onPickVideo()
                },
                onPickAudio = {
                    showAttachSheet = false
                    attachmentPickerActions.onPickAudio()
                },
                onPickFile = {
                    showAttachSheet = false
                    attachmentPickerActions.onPickFile()
                },
            )
        }
    }

    if (showModelPickerSheet) {
        AgentModelPickerSheet(
            selectedModel = selectedAgentModelName,
            availableModels = availableAgentModels,
            customModels = customGeminiModels,
            onSelectModel = { model ->
                vm.selectAgentModel(model)
                showModelPickerSheet = false
            },
            onAddModel = { model ->
                vm.addCustomGeminiModel(model)
                showModelPickerSheet = false
            },
            onRemoveCustomModel = { model ->
                vm.removeCustomGeminiModel(model)
            },
            onDismiss = { showModelPickerSheet = false },
        )
    }

    if (showConnectDialog) {
        AgentConnectDialog(
            isConnecting = isConnecting,
            error = connectError,
            onDismiss = { vm.dismissConnectDialog() },
            onConnect = { identity, password, baseUrl ->
                vm.connectAccount(identity, password, baseUrl)
            },
        )
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

@Composable
private fun AgentConnectDialog(
    isConnecting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConnect: (identity: String, password: String, baseUrl: String) -> Unit,
) {
    var baseUrl by rememberSaveable { mutableStateOf(DEFAULT_POCKETBASE_URL) }
    var identity by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = { Text("连接云端账号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "登录后即可触发云端 Agent 任务并同步记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("PocketBase 地址") },
                    singleLine = true,
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = identity,
                    onValueChange = { identity = it },
                    label = { Text("邮箱") },
                    singleLine = true,
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(identity, password, baseUrl) },
                enabled = !isConnecting && identity.isNotBlank() && password.isNotBlank(),
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("连接并同步")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isConnecting,
            ) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AgentPageMenu(
    hasMessages: Boolean,
    previewMode: Boolean,
    hasSession: Boolean,
    onTogglePreview: () -> Unit,
    onSyncOrConnect: () -> Unit,
    onRename: () -> Unit,
    onOpenSettings: () -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    @Composable
    fun MenuItem(
        text: String,
        icon: ImageVector,
        index: Int,
        count: Int,
        enabled: Boolean = true,
        destructive: Boolean = false,
        onClick: () -> Unit,
    ) {
        DropdownMenuItem(
            text = { Text(text) },
            onClick = {
                expanded = false
                onClick()
            },
            shape = MenuDefaults.itemShape(index = index, count = count).shape,
            leadingIcon = { Icon(icon, contentDescription = null) },
            enabled = enabled,
            colors = if (destructive) {
                val errorColor = MaterialTheme.colorScheme.error
                MenuDefaults.itemColors(textColor = errorColor, leadingIconColor = errorColor)
            } else {
                MenuDefaults.itemColors()
            },
        )
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(HugeIcons.MoreVertical, "更多选项")
        }
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                MenuItem(
                    text = if (previewMode) "返回对话" else "对话概览",
                    icon = HugeIcons.LeftToRightListBullet,
                    index = 0,
                    count = 4,
                    enabled = hasMessages,
                    onClick = onTogglePreview,
                )
                MenuItem(
                    text = if (hasSession) "同步远端记录" else "连接云端账号",
                    icon = HugeIcons.Refresh,
                    index = 1,
                    count = 4,
                    onClick = onSyncOrConnect,
                )
                MenuItem(
                    text = "重命名会话",
                    icon = HugeIcons.PencilEdit01,
                    index = 2,
                    count = 4,
                    onClick = onRename,
                )
                MenuItem(
                    text = "Agent 设置",
                    icon = HugeIcons.Settings03,
                    index = 3,
                    count = 4,
                    onClick = onOpenSettings,
                )
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                MenuItem(
                    text = "清空当前消息",
                    icon = HugeIcons.Delete01,
                    index = 0,
                    count = 1,
                    enabled = hasMessages,
                    destructive = true,
                    onClick = onClear,
                )
            }
        }
    }
}

@Composable
private fun AgentModelPickerSheet(
    selectedModel: String,
    availableModels: List<String>,
    customModels: List<String>,
    onSelectModel: (String) -> Unit,
    onAddModel: (String) -> Unit,
    onRemoveCustomModel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Expanded)
    var customInput by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "选择 Agent / Gemini 模型",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "可选择反重力 Agent（支持工具与沙盒）或任意 Gemini 模型名，发送时将连同思考程度一起传给 GitHub Action。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it },
                    label = { Text("手动添加 Gemini 模型名") },
                    placeholder = { Text("例如 gemini-2.5-pro") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = {
                        val trimmed = customInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddModel(trimmed)
                        }
                    },
                    enabled = customInput.isNotBlank(),
                ) {
                    Text("添加")
                }
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(availableModels, key = { it }) { modelName ->
                    val isSelected = modelName.equals(selectedModel, ignoreCase = true)
                    val isCustom = modelName in customModels
                    val isAntigravity = modelName.equals("antigravity", ignoreCase = true) ||
                        modelName.startsWith("antigravity-", ignoreCase = true)
                    Surface(
                        onClick = { onSelectModel(modelName) },
                        shape = MaterialTheme.shapes.large,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = modelName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = when {
                                        isAntigravity -> "Antigravity Interactions API（远程沙盒/工具）"
                                        isCustom -> "手动添加 · Gemini API"
                                        else -> "预设 · Gemini API"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (isSelected) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Text(
                                            text = "当前",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                                if (isCustom) {
                                    IconButton(
                                        onClick = { onRemoveCustomModel(modelName) },
                                    ) {
                                        Icon(
                                            imageVector = HugeIcons.Delete01,
                                            contentDescription = "删除自定义模型",
                                            modifier = Modifier.size(18.dp),
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
