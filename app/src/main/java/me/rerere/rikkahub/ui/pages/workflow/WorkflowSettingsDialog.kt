package me.rerere.rikkahub.ui.pages.workflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.Link01
import me.rerere.hugeicons.stroke.Logout03
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.SlidersHorizontal
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.workflow.CatalogModelEntry
import me.rerere.rikkahub.data.workflow.RuntimeDefaultsSnapshot
import me.rerere.rikkahub.data.workflow.SavedAiModel
import me.rerere.rikkahub.data.workflow.StartLink
import me.rerere.rikkahub.data.workflow.WorkflowSession
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.theme.CustomColors

private enum class SettingsSubPage {
    NONE,
    START_LINKS,
    MODELS,
    RUNTIME,
}

@Composable
internal fun WorkflowSettingsDialog(
    session: WorkflowSession,
    state: WorkflowSettingsUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onAddStartLink: (label: String, url: String) -> Unit,
    onUpdateStartLink: (StartLink, label: String, url: String) -> Unit,
    onDeleteStartLink: (StartLink) -> Unit,
    onResetStartLinks: () -> Unit,
    onAddSavedModel: (modelId: String, name: String?) -> Unit,
    onRemoveSavedModel: (SavedAiModel) -> Unit,
    onRefreshCatalog: () -> Unit,
    onSaveRuntimeDefaults: (RuntimeDefaultsSnapshot, () -> Unit) -> Unit,
    onClearError: () -> Unit,
    onClearNotice: () -> Unit,
) {
    var activeSubPage by remember { mutableStateOf(SettingsSubPage.NONE) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = CustomColors.topBarColors.containerColor,
        ) {
            Scaffold(
                containerColor = CustomColors.topBarColors.containerColor,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                when (activeSubPage) {
                                    SettingsSubPage.NONE -> "工作流设置"
                                    SettingsSubPage.START_LINKS -> "快捷链接"
                                    SettingsSubPage.MODELS -> "模型库"
                                    SettingsSubPage.RUNTIME -> "运行配置"
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (activeSubPage != SettingsSubPage.NONE) {
                                        activeSubPage = SettingsSubPage.NONE
                                    } else {
                                        onDismiss()
                                    }
                                },
                            ) {
                                Icon(HugeIcons.Cancel01, contentDescription = "关闭")
                            }
                        },
                        actions = {
                            if (activeSubPage == SettingsSubPage.MODELS) {
                                IconButton(
                                    onClick = onRefreshCatalog,
                                    enabled = !state.isRefreshingCatalog,
                                ) {
                                    if (state.isRefreshingCatalog) {
                                        LoadingIndicator(modifier = Modifier.size(18.dp))
                                    } else {
                                        Icon(HugeIcons.Refresh03, contentDescription = "刷新目录")
                                    }
                                }
                            } else {
                                IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                                    if (state.isLoading) {
                                        LoadingIndicator(modifier = Modifier.size(18.dp))
                                    } else {
                                        Icon(HugeIcons.Refresh03, contentDescription = "刷新")
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = CustomColors.topBarColors.containerColor,
                        ),
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    if (!state.errorMessage.isNullOrBlank()) {
                        WorkflowErrorBanner(
                            message = state.errorMessage,
                            onDismiss = onClearError,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    if (!state.noticeMessage.isNullOrBlank()) {
                        WorkflowNoticeBanner(
                            message = state.noticeMessage,
                            onDismiss = onClearNotice,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    when (activeSubPage) {
                        SettingsSubPage.NONE -> SettingsDirectoryContent(
                            session = session,
                            state = state,
                            onOpenStartLinks = { activeSubPage = SettingsSubPage.START_LINKS },
                            onOpenModels = { activeSubPage = SettingsSubPage.MODELS },
                            onOpenRuntime = { activeSubPage = SettingsSubPage.RUNTIME },
                            onDisconnect = onDisconnect,
                        )
                        SettingsSubPage.START_LINKS -> StartLinksSettingsContent(
                            state = state,
                            onAdd = onAddStartLink,
                            onUpdate = onUpdateStartLink,
                            onDelete = onDeleteStartLink,
                            onReset = onResetStartLinks,
                        )
                        SettingsSubPage.MODELS -> ModelsSettingsContent(
                            state = state,
                            onAddSavedModel = onAddSavedModel,
                            onRemoveSavedModel = onRemoveSavedModel,
                        )
                        SettingsSubPage.RUNTIME -> RuntimeSettingsContent(
                            state = state,
                            onSave = { snapshot ->
                                onSaveRuntimeDefaults(snapshot) {
                                    activeSubPage = SettingsSubPage.NONE
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDirectoryContent(
    session: WorkflowSession,
    state: WorkflowSettingsUiState,
    onOpenStartLinks: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenRuntime: () -> Unit,
    onDisconnect: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = {},
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardGroup(title = { Text("当前连接") }) {
                item(
                    leadingContent = { CardGroupIcon(HugeIcons.CloudServer) },
                    headlineContent = { Text(session.email) },
                    supportingContent = { Text(session.baseUrl) },
                )
            }

            CardGroup(title = { Text("云端工作流配置") }) {
                item(
                    onClick = onOpenModels,
                    leadingContent = { CardGroupIcon(HugeIcons.AiBrain01) },
                    headlineContent = { Text("模型库") },
                    supportingContent = {
                        Text("已保存 ${state.savedModels.size} 个常用模型 · 目录 ${state.catalog.models.size} 项")
                    },
                    trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
                item(
                    onClick = onOpenRuntime,
                    leadingContent = { CardGroupIcon(HugeIcons.SlidersHorizontal) },
                    headlineContent = { Text("运行配置") },
                    supportingContent = {
                        Text("超时 ${state.runtimeDefaults.browser.replyTimeoutMinutes} 分钟 · 版本 v${state.runtimeDefaults.revision}")
                    },
                    trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
                item(
                    onClick = onOpenStartLinks,
                    leadingContent = { CardGroupIcon(HugeIcons.Link01) },
                    headlineContent = { Text("快捷链接") },
                    supportingContent = {
                        Text("已配置 ${state.preferences.startLinks.size} 个常用 start_url")
                    },
                    trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }

            FilledTonalButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(HugeIcons.Logout03, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("断开当前账号")
            }
        }
    }
}

@Composable
private fun StartLinksSettingsContent(
    state: WorkflowSettingsUiState,
    onAdd: (String, String) -> Unit,
    onUpdate: (StartLink, String, String) -> Unit,
    onDelete: (StartLink) -> Unit,
    onReset: () -> Unit,
) {
    var editingLink by remember { mutableStateOf<StartLink?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "actions_bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    enabled = !state.isMutating,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(HugeIcons.Add01, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加链接")
                }
                OutlinedButton(
                    onClick = onReset,
                    enabled = !state.isMutating,
                    shape = CircleShape,
                ) {
                    Text("恢复默认")
                }
            }
        }

        items(state.preferences.startLinks, key = { it.id }) { link ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CustomColors.listItemColors.containerColor,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = link.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = link.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { editingLink = link }, enabled = !state.isMutating) {
                        Icon(HugeIcons.Edit03, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDelete(link) }, enabled = !state.isMutating) {
                        Icon(
                            HugeIcons.Delete01,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        StartLinkEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { label, url ->
                showAddDialog = false
                onAdd(label, url)
            },
        )
    }

    editingLink?.let { link ->
        StartLinkEditDialog(
            initial = link,
            onDismiss = { editingLink = null },
            onSave = { label, url ->
                editingLink = null
                onUpdate(link, label, url)
            },
        )
    }
}

@Composable
private fun StartLinkEditDialog(
    initial: StartLink?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var url by remember(initial) { mutableStateOf(initial?.url ?: "https://") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加快捷链接" else "编辑快捷链接") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如：ChatGPT") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("链接") },
                    placeholder = { Text("https://chatgpt.com/") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(label, url) },
                enabled = label.isNotBlank() && url.trim().startsWith("https://"),
                shape = CircleShape,
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

@Composable
private fun ModelsSettingsContent(
    state: WorkflowSettingsUiState,
    onAddSavedModel: (String, String?) -> Unit,
    onRemoveSavedModel: (SavedAiModel) -> Unit,
) {
    var customModelId by remember { mutableStateOf("") }
    var customModelName by remember { mutableStateOf("") }
    var showCatalogSheet by remember { mutableStateOf(false) }

    val savedModelIds = remember(state.savedModels) { state.savedModels.map { it.modelId }.toSet() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "add_model_card") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = CustomColors.listItemColors.containerColor,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("添加常用模型", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = customModelId,
                        onValueChange = { customModelId = it },
                        label = { Text("模型 ID") },
                        placeholder = { Text("例如：gemini-3.5-flash-lite") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = customModelName,
                        onValueChange = { customModelName = it },
                        label = { Text("显示名称（可选）") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WorkflowActionButton(
                            text = "添加",
                            onClick = {
                                onAddSavedModel(customModelId.trim(), customModelName.trim().ifBlank { null })
                                customModelId = ""
                                customModelName = ""
                            },
                            icon = HugeIcons.Add01,
                            tone = WorkflowButtonTone.PRIMARY,
                            enabled = !state.isMutating && customModelId.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        )
                        WorkflowActionButton(
                            text = "从目录选择 (${state.catalog.models.size})",
                            onClick = { showCatalogSheet = true },
                            tone = WorkflowButtonTone.TONAL,
                            enabled = state.catalog.models.isNotEmpty(),
                        )
                    }
                }
            }
        }

        item(key = "saved_header") {
            Text(
                text = "已保存的模型 (${state.savedModels.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (state.isLoading && state.savedModels.isEmpty()) {
            item(key = "models_loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            }
        } else if (state.savedModels.isEmpty()) {
            item(key = "models_empty") {
                WorkflowEmptyState(
                    icon = HugeIcons.AiBrain01,
                    title = "暂无已保存模型",
                    subtitle = "在上方输入模型 ID 或从目录中添加常用模型。",
                )
            }
        } else {
            items(state.savedModels, key = { it.id }) { model ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CustomColors.listItemColors.containerColor,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = model.modelId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onRemoveSavedModel(model) },
                            enabled = !state.isMutating,
                        ) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = "移除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCatalogSheet) {
        CatalogPickerSheet(
            models = state.catalog.models,
            savedModelIds = savedModelIds,
            onDismiss = { showCatalogSheet = false },
            onSelect = { entry ->
                onAddSavedModel(entry.id, entry.name)
            },
        )
    }
}

@Composable
private fun CatalogPickerSheet(
    models: List<CatalogModelEntry>,
    savedModelIds: Set<String>,
    onDismiss: () -> Unit,
    onSelect: (CatalogModelEntry) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("模型目录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyColumn(
                modifier = Modifier.height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(models, key = { it.id }) { entry ->
                    val alreadySaved = entry.id in savedModelIds
                    Surface(
                        onClick = { if (!alreadySaved) onSelect(entry) },
                        shape = RoundedCornerShape(16.dp),
                        color = CustomColors.listItemColors.containerColor,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = listOfNotNull(entry.provider.takeIf { it.isNotBlank() }, entry.id).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (alreadySaved) {
                                Icon(
                                    HugeIcons.Tick01,
                                    contentDescription = "已保存",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Icon(HugeIcons.Add01, contentDescription = "添加", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeSettingsContent(
    state: WorkflowSettingsUiState,
    onSave: (RuntimeDefaultsSnapshot) -> Unit,
) {
    val initial = state.runtimeDefaults
    var timeoutText by remember(initial.revision) { mutableStateOf(initial.browser.replyTimeoutMinutes.toString()) }
    var randomWaitEnabled by remember(initial.revision) { mutableStateOf(initial.browser.randomWaitEnabled) }
    var randomMinText by remember(initial.revision) { mutableStateOf(initial.browser.randomWaitMinSeconds.toString()) }
    var randomMaxText by remember(initial.revision) { mutableStateOf(initial.browser.randomWaitMaxSeconds.toString()) }
    var deferredEnabled by remember(initial.revision) { mutableStateOf(initial.browser.deferredReplyEnabled) }
    var deferredMinutesText by remember(initial.revision) { mutableStateOf(initial.browser.deferredReplyDelayMinutes.toString()) }
    var defaultStartUrl by remember(initial.revision) { mutableStateOf(initial.defaultStartUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CardGroup(title = { Text("浏览器执行超时与等待") }) {
            formItem(
                headlineContent = { Text("回复超时（分钟）") },
                supportingContent = { Text("单次等待 AI 回复的最长时间（1–240）") },
            ) {
                OutlinedTextField(
                    value = timeoutText,
                    onValueChange = { timeoutText = it.filter(Char::isDigit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(
                headlineContent = { Text("任务间随机等待") },
                supportingContent = { Text("在连续对话之间插入随机间隔") },
                trailingContent = {
                    Switch(checked = randomWaitEnabled, onCheckedChange = { randomWaitEnabled = it })
                },
            )
            if (randomWaitEnabled) {
                formItem(headlineContent = { Text("随机等待范围（秒）") }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = randomMinText,
                            onValueChange = { randomMinText = it.filter(Char::isDigit) },
                            label = { Text("最短") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = randomMaxText,
                            onValueChange = { randomMaxText = it.filter(Char::isDigit) },
                            label = { Text("最长") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item(
                headlineContent = { Text("延迟检测回复") },
                supportingContent = { Text("发送消息后先等待一段时间再开始检查结果") },
                trailingContent = {
                    Switch(checked = deferredEnabled, onCheckedChange = { deferredEnabled = it })
                },
            )
            if (deferredEnabled) {
                formItem(headlineContent = { Text("延迟时间（分钟）") }) {
                    OutlinedTextField(
                        value = deferredMinutesText,
                        onValueChange = { deferredMinutesText = it.filter(Char::isDigit) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (initial.schemaVersion >= 3) {
            CardGroup(title = { Text("默认开始页面") }) {
                formItem(
                    headlineContent = { Text("默认 start_url") },
                    supportingContent = { Text("Task 未指定 start_url 时使用") },
                ) {
                    OutlinedTextField(
                        value = defaultStartUrl,
                        onValueChange = { defaultStartUrl = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Button(
            onClick = {
                val next = initial.copy(
                    browser = initial.browser.copy(
                        replyTimeoutMinutes = timeoutText.toIntOrNull() ?: initial.browser.replyTimeoutMinutes,
                        randomWaitEnabled = randomWaitEnabled,
                        randomWaitMinSeconds = randomMinText.toIntOrNull() ?: 0,
                        randomWaitMaxSeconds = randomMaxText.toIntOrNull() ?: 0,
                        deferredReplyEnabled = deferredEnabled,
                        deferredReplyDelayMinutes = deferredMinutesText.toIntOrNull() ?: initial.browser.deferredReplyDelayMinutes,
                    ),
                    defaultStartUrl = defaultStartUrl.trim(),
                )
                onSave(next)
            },
            enabled = !state.isMutating,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            if (state.isMutating) {
                LoadingIndicator(modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text("保存运行配置")
        }
    }
}
