package me.rerere.rikkahub.ui.pages.agent

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextFieldDefaults
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
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Refresh
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.agent.AgentModelCategory
import me.rerere.rikkahub.data.agent.AgentModelEntity
import me.rerere.rikkahub.data.agent.DEFAULT_GEMINI_GATEWAY_URL
import me.rerere.rikkahub.data.workflow.DEFAULT_POCKETBASE_URL
import me.rerere.rikkahub.ui.components.ai.AgentFilesPicker
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.ChatTopBarCapsule
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
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
    val resolvedChatModel by vm.resolvedChatModel.collectAsStateWithLifecycle()
    val localModels by vm.localModels.collectAsStateWithLifecycle()
    val selectedModelEntity by vm.selectedModelEntity.collectAsStateWithLifecycle()
    val selectedModelCategory by vm.selectedModelCategory.collectAsStateWithLifecycle()
    val selectedAgentModelName by vm.selectedAgentModelName.collectAsStateWithLifecycle()
    val decisionSchemaJson by vm.decisionSchemaJson.collectAsStateWithLifecycle()
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
    var insightsMessageId by remember(id) { mutableStateOf<Uuid?>(null) }
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
                                        selectedModelCategory == AgentModelCategory.CHAT -> "Chat 流式 · $selectedAgentModelName"
                                        selectedModelCategory == AgentModelCategory.DECISION -> "Decision 决策 · $selectedAgentModelName"
                                        session == null -> "Agent Action · $selectedAgentModelName"
                                        else -> "Agent · $selectedAgentModelName"
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
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (selectedModelCategory == AgentModelCategory.DECISION) {
                                DecisionSchemaBar(
                                    schemaJson = decisionSchemaJson,
                                    onUpdateSchema = vm::updateDecisionSchemaJson,
                                )
                            }
                            val inputPlaceholder = when (selectedModelCategory) {
                                AgentModelCategory.CHAT -> "输入对话内容（流式输出）…"
                                AgentModelCategory.DECISION -> "输入决策问题（返回结构化 JSON）…"
                                AgentModelCategory.AGENT -> "输入 Agent 任务指令…"
                            }
                            ChatInput(
                                state = inputState,
                                loading = isGenerating,
                                settings = composedSettings,
                                assistant = agentAssistant,
                                chatModel = selectedModel,
                                hazeState = hazeState,
                                placeholderText = inputPlaceholder,
                                alwaysShowReasoningButton = true,
                                showReasoningButton = selectedModelCategory != AgentModelCategory.AGENT,
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
                        }
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
                                onOpenAgentInsights = { msg -> insightsMessageId = msg.id },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLiveInsights || insightsMessageId != null) {
        val latestAssistant = conversation.currentMessages.lastOrNull {
            it.role == me.rerere.ai.core.MessageRole.ASSISTANT
        }
        val targetMessage = if (showLiveInsights) latestAssistant else conversation.currentMessages
            .firstOrNull { it.id == insightsMessageId }
        AgentInsightsSheet(
            message = targetMessage,
            isRunning = isGenerating && (showLiveInsights || targetMessage?.id == latestAssistant?.id),
            liveElapsedText = activeStatusText,
            onDismiss = {
                showLiveInsights = false
                insightsMessageId = null
            },
        )
    }

    if (showAttachSheet) {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        fun dismissAttachSheet() {
            scope.launch {
                sheetState.hide()
                showAttachSheet = false
            }
        }
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showAttachSheet = false },
        ) {
            AgentFilesPicker(
                onTakePic = {
                    dismissAttachSheet()
                    attachmentPickerActions.onTakePicture()
                },
                onPickImage = {
                    dismissAttachSheet()
                    attachmentPickerActions.onPickImage()
                },
                onSketch = {
                    dismissAttachSheet()
                    attachmentPickerActions.onSketch()
                },
                onPickVideo = {
                    dismissAttachSheet()
                    attachmentPickerActions.onPickVideo()
                },
                onPickAudio = {
                    dismissAttachSheet()
                    attachmentPickerActions.onPickAudio()
                },
                onPickFile = {
                    dismissAttachSheet()
                    attachmentPickerActions.onPickFile()
                },
            )
        }
    }

    if (showModelPickerSheet) {
        AgentModelPickerSheet(
            selectedModelId = selectedModelEntity?.id.orEmpty(),
            models = localModels,
            defaultBaseUrl = uiSettings.gatewayBaseUrl,
            defaultApiKey = uiSettings.gatewayApiKey,
            onSelectModel = { model ->
                vm.selectLocalModel(model)
                showModelPickerSheet = false
            },
            onSaveModel = { id, modelId, displayName, category, baseUrl, apiKey ->
                vm.saveLocalModel(
                    id = id,
                    modelId = modelId,
                    displayName = displayName,
                    category = category,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    selectAfterSave = true,
                )
            },
            onDeleteModel = { id ->
                vm.deleteLocalModel(id)
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
private fun DecisionSchemaBar(
    schemaJson: String,
    onUpdateSchema: (String) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var draftSchema by remember(schemaJson) { mutableStateOf(schemaJson) }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Tag(type = TagType.INFO) {
                Text("Decision JSON")
            }
            Text(
                text = if (schemaJson.isBlank()) "默认结构化决策输出" else "已配置自定义 Schema",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showEditor = true }) {
                Text(if (schemaJson.isBlank()) "设置 Schema" else "编辑")
            }
        }
    }

    if (showEditor) {
        val routePreset = """{"type":"OBJECT","properties":{"route":{"type":"STRING"},"confidence":{"type":"NUMBER"},"reason":{"type":"STRING"}},"required":["route","reason"]}"""
        val scorePreset = """{"type":"OBJECT","properties":{"approved":{"type":"BOOLEAN"},"score":{"type":"INTEGER"},"summary":{"type":"STRING"}},"required":["approved","score"]}"""
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("Decision JSON Schema") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = draftSchema.isBlank(),
                            onClick = { draftSchema = "" },
                            label = { Text("自由 JSON") },
                        )
                        FilterChip(
                            selected = draftSchema == routePreset,
                            onClick = { draftSchema = routePreset },
                            label = { Text("路由分类") },
                        )
                        FilterChip(
                            selected = draftSchema == scorePreset,
                            onClick = { draftSchema = scorePreset },
                            label = { Text("评分审批") },
                        )
                    }
                    OutlinedTextField(
                        value = draftSchema,
                        onValueChange = { draftSchema = it },
                        label = { Text("responseSchema (可选)") },
                        placeholder = { Text("留空则由模型自动决定 JSON 字段") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateSchema(draftSchema.trim())
                        showEditor = false
                    },
                ) {
                    Text("应用")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun AgentModelPickerSheet(
    selectedModelId: String,
    models: List<AgentModelEntity>,
    defaultBaseUrl: String,
    defaultApiKey: String,
    onSelectModel: (AgentModelEntity) -> Unit,
    onSaveModel: (
        id: String?,
        modelId: String,
        displayName: String,
        category: AgentModelCategory,
        baseUrl: String,
        apiKey: String,
    ) -> Unit,
    onDeleteModel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterCategory by rememberSaveable { mutableStateOf<AgentModelCategory?>(null) }
    var editingModel by remember { mutableStateOf<AgentModelEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun dismiss(afterHide: () -> Unit = {}) {
        coroutineScope.launch {
            sheetState.hide()
            afterHide()
            onDismiss()
        }
    }

    val filteredModels = remember(models, searchQuery, filterCategory) {
        val q = searchQuery.trim()
        models.filter { item ->
            val matchCat = filterCategory == null || item.modelCategory == filterCategory
            val matchQuery = q.isEmpty() ||
                item.modelId.contains(q, ignoreCase = true) ||
                item.displayName.contains(q, ignoreCase = true)
            matchCat && matchQuery
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxHeight(0.82f)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索本地 Gemini 模型…") },
                        shape = RoundedCornerShape(50),
                        colors = TextFieldDefaults.colors(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        leadingIcon = { Icon(HugeIcons.Search01, contentDescription = null) },
                        maxLines = 1,
                        singleLine = true,
                    )
                }
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(
                        imageVector = HugeIcons.Add01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("添加")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                FilterChip(
                    selected = filterCategory == null,
                    onClick = { filterCategory = null },
                    label = { Text("全部 (${models.size})") },
                )
                AgentModelCategory.entries.forEach { cat ->
                    val count = models.count { it.modelCategory == cat }
                    FilterChip(
                        selected = filterCategory == cat,
                        onClick = { filterCategory = if (filterCategory == cat) null else cat },
                        label = { Text("${cat.name.lowercase().replaceFirstChar { it.uppercase() }} ($count)") },
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(filteredModels, key = { it.id }) { model ->
                    val isSelected = model.id == selectedModelId
                    val cat = model.modelCategory
                    val hostLabel = remember(model.baseUrl) {
                        runCatching { java.net.URI(model.baseUrl).host }.getOrNull()
                            ?: model.baseUrl.ifBlank { "gemini.any1.tech" }
                    }

                    Card(
                        modifier = Modifier.animateItem(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dismiss { onSelectModel(model) } }
                                .padding(vertical = 12.dp, horizontal = 14.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                AutoAIIcon(
                                    name = model.modelId,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(32.dp),
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = model.displayName.ifBlank { model.modelId },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${model.modelId} · $hostLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    val tagType = when (cat) {
                                        AgentModelCategory.AGENT -> TagType.SUCCESS
                                        AgentModelCategory.CHAT -> TagType.INFO
                                        AgentModelCategory.DECISION -> TagType.WARNING
                                    }
                                    Tag(type = tagType) {
                                        Text(cat.label)
                                    }
                                    Tag(type = TagType.INFO) {
                                        Text("Gemini")
                                    }
                                }
                            }
                            IconButton(onClick = { editingModel = model }) {
                                Icon(
                                    imageVector = HugeIcons.PencilEdit01,
                                    contentDescription = "编辑模型",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            if (!model.isBuiltin) {
                                IconButton(onClick = { onDeleteModel(model.id) }) {
                                    Icon(
                                        imageVector = HugeIcons.Delete01,
                                        contentDescription = "删除模型",
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

    if (showAddDialog || editingModel != null) {
        AgentModelEditDialog(
            initial = editingModel,
            defaultBaseUrl = defaultBaseUrl.ifBlank { DEFAULT_GEMINI_GATEWAY_URL },
            defaultApiKey = defaultApiKey,
            initialModelIdHint = searchQuery.trim(),
            onDismiss = {
                showAddDialog = false
                editingModel = null
            },
            onSave = { id, modelId, displayName, category, baseUrl, apiKey ->
                onSaveModel(id, modelId, displayName, category, baseUrl, apiKey)
                showAddDialog = false
                editingModel = null
            },
        )
    }
}

@Composable
private fun AgentModelEditDialog(
    initial: AgentModelEntity?,
    defaultBaseUrl: String,
    defaultApiKey: String,
    initialModelIdHint: String,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        modelId: String,
        displayName: String,
        category: AgentModelCategory,
        baseUrl: String,
        apiKey: String,
    ) -> Unit,
) {
    var modelId by remember(initial) {
        mutableStateOf(initial?.modelId ?: initialModelIdHint)
    }
    var displayName by remember(initial) {
        mutableStateOf(initial?.displayName ?: "")
    }
    var category by remember(initial) {
        mutableStateOf(
            initial?.modelCategory ?: if (initialModelIdHint.isNotBlank()) {
                AgentModelCategory.inferFromModelId(initialModelIdHint)
            } else {
                AgentModelCategory.CHAT
            }
        )
    }
    var baseUrl by remember(initial) {
        mutableStateOf(initial?.baseUrl?.ifBlank { defaultBaseUrl } ?: defaultBaseUrl)
    }
    var apiKey by remember(initial) {
        mutableStateOf(initial?.apiKey ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加 Gemini 模型" else "编辑 Gemini 模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "当前仅支持 Gemini 协议，模型配置保存在本地数据库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = modelId,
                    onValueChange = {
                        modelId = it
                        if (initial == null && it.isNotBlank()) {
                            category = AgentModelCategory.inferFromModelId(it)
                        }
                    },
                    label = { Text("模型 ID *") },
                    placeholder = { Text("例如 gemini-2.5-flash / gemini-decision") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "模型类别",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AgentModelCategory.entries.forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("请求地址 (默认 gemini.any1.tech)") },
                    placeholder = { Text(DEFAULT_GEMINI_GATEWAY_URL) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("网关密钥（留空则继承全局）") },
                    placeholder = { Text(if (defaultApiKey.isNotBlank()) "已配置全局密钥" else "GATEWAY_AUTH_TOKEN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = modelId.isNotBlank(),
                onClick = {
                    onSave(
                        initial?.id,
                        modelId.trim(),
                        displayName.trim(),
                        category,
                        baseUrl.trim().ifBlank { DEFAULT_GEMINI_GATEWAY_URL },
                        apiKey.trim(),
                    )
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

