package me.rerere.rikkahub.ui.pages.workflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.Link01
import me.rerere.hugeicons.stroke.Login03
import me.rerere.hugeicons.stroke.Logout03
import me.rerere.hugeicons.stroke.Pause
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.data.workflow.DEFAULT_POCKETBASE_URL
import me.rerere.rikkahub.data.workflow.DispatchRequestedAction
import me.rerere.rikkahub.data.workflow.DispatchStatus
import me.rerere.rikkahub.data.workflow.LibraryFolderScope
import me.rerere.rikkahub.data.workflow.RunEditorTarget
import me.rerere.rikkahub.data.workflow.RunListFilter
import me.rerere.rikkahub.data.workflow.StatusTone
import me.rerere.rikkahub.data.workflow.WorkflowRun
import me.rerere.rikkahub.data.workflow.WorkflowSession
import me.rerere.rikkahub.data.workflow.createStarterPlan
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.cardGroupItemShape
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class WorkflowNavTab(
    val label: String,
    val title: String,
) {
    RUNS("工作流", "工作流"),
    SCHEDULES("重复", "重复计划"),
    LIBRARY("收藏模板", "收藏与模板"),
    FILES("资料库", "资料库"),
}

@Composable
fun WorkflowPage(
    vm: WorkflowVM = koinViewModel(),
    schedulesVm: WorkflowSchedulesVM = koinViewModel(),
    libraryVm: WorkflowLibraryVM = koinViewModel(),
    filesVm: WorkflowFilesVM = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val schedulesState by schedulesVm.uiState.collectAsStateWithLifecycle()
    val libraryState by libraryVm.uiState.collectAsStateWithLifecycle()
    val filesState by filesVm.uiState.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var activeTab by rememberSaveable { mutableStateOf(WorkflowNavTab.RUNS) }
    var editorTarget by remember { mutableStateOf<RunEditorTarget?>(null) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WorkflowRun?>(null) }
    var showCreateTemplateDialog by remember { mutableStateOf(false) }
    var fileUploadTriggerCount by remember { mutableIntStateOf(0) }

    val activeRefreshing = when (activeTab) {
        WorkflowNavTab.RUNS -> state.isRefreshing
        WorkflowNavTab.SCHEDULES -> schedulesState.isRefreshing
        WorkflowNavTab.LIBRARY -> libraryState.isRefreshing
        WorkflowNavTab.FILES -> filesState.isRefreshing
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(if (state.session == null) "工作流" else activeTab.title)
                },
                subtitle = {
                    Text(
                        text = state.session?.let { "已连接 · ${it.email}" }
                            ?: "编排任务，追踪每一次执行",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    if (state.session != null) {
                        IconButton(
                            onClick = {
                                when (activeTab) {
                                    WorkflowNavTab.RUNS -> vm.refresh()
                                    WorkflowNavTab.SCHEDULES -> schedulesVm.refresh()
                                    WorkflowNavTab.LIBRARY -> libraryVm.refresh()
                                    WorkflowNavTab.FILES -> filesVm.refresh()
                                }
                            },
                            enabled = !activeRefreshing,
                        ) {
                            if (activeRefreshing) {
                                LoadingIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Icon(
                                    imageVector = HugeIcons.Refresh03,
                                    contentDescription = "刷新",
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = { showAccountSheet = true },
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = CustomColors.listItemColors.containerColor,
                            ),
                        ) {
                            Icon(
                                imageVector = HugeIcons.CloudServer,
                                contentDescription = "连接信息",
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        bottomBar = {
            if (state.session != null) {
                NavigationBar {
                    WorkflowNavTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            WorkflowNavTab.RUNS -> HugeIcons.GitFork
                            WorkflowNavTab.SCHEDULES -> HugeIcons.Calendar03
                            WorkflowNavTab.LIBRARY -> HugeIcons.Favourite
                            WorkflowNavTab.FILES -> HugeIcons.Files02
                        }
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Icon(icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (state.session != null) {
                when (activeTab) {
                    WorkflowNavTab.RUNS, WorkflowNavTab.SCHEDULES -> {
                        FloatingActionButton(
                            onClick = { editorTarget = RunEditorTarget.Create },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(HugeIcons.Add01, contentDescription = "新建工作流")
                        }
                    }
                    WorkflowNavTab.LIBRARY -> {
                        if (libraryState.activeScope == LibraryFolderScope.TEMPLATE) {
                            FloatingActionButton(
                                onClick = { showCreateTemplateDialog = true },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Icon(HugeIcons.Add01, contentDescription = "新建模板")
                            }
                        }
                    }
                    WorkflowNavTab.FILES -> {
                        FloatingActionButton(
                            onClick = { fileUploadTriggerCount++ },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(HugeIcons.FileImport, contentDescription = "上传 Markdown")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val session = state.session
        if (session == null) {
            ConnectWorkflowView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                initialBaseUrl = state.baseUrl.ifEmpty { DEFAULT_POCKETBASE_URL },
                isLoading = state.isLoggingIn,
                errorMessage = state.loginError,
                onConnect = { identity, password, url ->
                    vm.login(identity, password, url)
                },
            )
        } else {
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
            val bottomPad = innerPadding.calculateBottomPadding()

            when (activeTab) {
                WorkflowNavTab.RUNS -> WorkflowRunsContent(
                    modifier = contentModifier,
                    bottomPadding = bottomPad,
                    state = state,
                    favoriteRunIds = libraryState.favoriteRunIds,
                    onRefresh = vm::refresh,
                    onFilterChange = vm::setFilter,
                    onSearchChange = vm::setSearchInput,
                    onPageChange = vm::setPage,
                    onSelectRun = vm::selectRun,
                    onEditDraft = { run -> editorTarget = RunEditorTarget.EditDraft(run) },
                    onEditAsNewVersion = { run -> editorTarget = RunEditorTarget.EditedRerun(run) },
                    onCommandRun = vm::commandRun,
                    onCloneRun = vm::cloneRun,
                    onToggleFavorite = libraryVm::toggleFavoriteRun,
                    onSaveAsTemplate = libraryVm::saveRunAsTemplate,
                    onRequestDelete = { deleteTarget = it },
                    onClearError = vm::clearError,
                    onOpenCreate = { editorTarget = RunEditorTarget.Create },
                )

                WorkflowNavTab.SCHEDULES -> WorkflowSchedulesTab(
                    modifier = contentModifier,
                    state = schedulesState,
                    bottomPadding = bottomPad,
                    onRefresh = schedulesVm::refresh,
                    onPageChange = schedulesVm::setPage,
                    onToggle = schedulesVm::toggle,
                    onDelete = schedulesVm::delete,
                    onOpenRun = vm::openRunById,
                    onOpenCreateRun = { editorTarget = RunEditorTarget.Create },
                    onClearError = schedulesVm::clearError,
                )

                WorkflowNavTab.LIBRARY -> WorkflowLibraryTab(
                    modifier = contentModifier,
                    state = libraryState,
                    bottomPadding = bottomPad,
                    onRefresh = libraryVm::refresh,
                    onScopeChange = libraryVm::setScope,
                    onOpenFolder = libraryVm::openFolder,
                    onSearchChange = libraryVm::setSearchQuery,
                    onCreateFolder = libraryVm::createFolder,
                    onRenameFolder = libraryVm::renameFolder,
                    onDeleteFolder = libraryVm::deleteFolder,
                    onOpenRun = vm::openRunById,
                    onMoveFavorite = libraryVm::moveFavorite,
                    onDeleteFavorite = libraryVm::deleteFavorite,
                    onUseTemplate = { tpl ->
                        editorTarget = RunEditorTarget.FromTemplate(
                            title = tpl.title,
                            planText = tpl.planText,
                        )
                    },
                    onCreateTemplate = libraryVm::createTemplate,
                    onUpdateTemplate = libraryVm::updateTemplate,
                    onMoveTemplate = libraryVm::moveTemplate,
                    onDuplicateTemplate = libraryVm::duplicateTemplate,
                    onDeleteTemplate = libraryVm::deleteTemplate,
                    onClearError = libraryVm::clearError,
                    onClearNotice = libraryVm::clearNotice,
                    showCreateTemplateDialog = showCreateTemplateDialog,
                    onDismissCreateTemplateDialog = { showCreateTemplateDialog = false },
                )

                WorkflowNavTab.FILES -> WorkflowFilesTab(
                    modifier = contentModifier,
                    state = filesState,
                    bottomPadding = bottomPad,
                    onRefresh = filesVm::refresh,
                    onOpenFolder = filesVm::openFolder,
                    onSearchChange = filesVm::setSearchQuery,
                    onTypeFilterChange = filesVm::setTypeFilter,
                    onSortChange = filesVm::setSort,
                    onUploadMarkdown = filesVm::uploadMarkdown,
                    onOpenPreview = filesVm::openPreview,
                    onClosePreview = filesVm::closePreview,
                    onRenameFile = filesVm::renameFile,
                    onMoveFile = filesVm::moveFile,
                    onDeleteFile = filesVm::deleteFile,
                    onCreateFolder = filesVm::createFolder,
                    onRenameFolder = filesVm::renameFolder,
                    onDeleteFolder = filesVm::deleteFolder,
                    onUseFileInRun = { file ->
                        val baseTitle = file.fileName.substringBeforeLast('.')
                        val plan = buildString {
                            appendLine("@run=$baseTitle")
                            appendLine("@mode=serial")
                            appendLine()
                            appendLine("@task Task {")
                            appendLine("  @mode=serial")
                            appendLine("  @let source_file=<${file.sourceUrl}>")
                            appendLine()
                            appendLine("  @event Event {")
                            appendLine("    {")
                            appendLine("      请阅读并处理资料：%source_file%")
                            appendLine("    }")
                            appendLine("  }")
                            appendLine("}")
                        }
                        editorTarget = RunEditorTarget.FromTemplate(baseTitle, plan)
                    },
                    onClearError = filesVm::clearError,
                    onClearNotice = filesVm::clearNotice,
                    uploadTriggerCount = fileUploadTriggerCount,
                )
            }
        }
    }

    state.selectedRun?.let { run ->
        WorkflowRunDetailDialog(
            run = run,
            detail = state.detail,
            isMutating = state.isMutating,
            onDismiss = { vm.selectRun(null) },
            onRefresh = vm::refreshSelectedRunDetail,
            onSelectVersion = vm::selectRun,
            onEditDraft = { targetRun ->
                editorTarget = RunEditorTarget.EditDraft(targetRun)
            },
            onEditAsNewVersion = { targetRun ->
                editorTarget = RunEditorTarget.EditedRerun(targetRun)
            },
            onCommand = { action -> vm.commandRun(run, action) },
            onClone = { status -> vm.cloneRun(run, status) },
            onDelete = {
                vm.selectRun(null)
                deleteTarget = run
            },
            onLoadEvents = { taskId -> vm.loadEventsForTask(taskId) },
            onLoadActs = { event -> vm.loadActsForEvent(event) },
            onLoadMessages = { actId, page -> vm.loadMessagesForAct(actId, page) },
            onOpenMessage = vm::openMessageDetail,
            onRetryMessage = vm::retryMessageDetail,
            onCloseMessage = vm::closeMessageDetail,
        )
    }

    editorTarget?.let { target ->
        WorkflowDslEditorDialog(
            target = target,
            isMutating = state.isMutating,
            errorMessage = state.errorMessage,
            onClearError = vm::clearError,
            onDismiss = {
                vm.clearError()
                editorTarget = null
            },
            onSubmit = { title, planText, status, recurrence ->
                val onDone: (WorkflowRun) -> Unit = {
                    editorTarget = null
                    if (recurrence != null) {
                        schedulesVm.refresh()
                    }
                }
                when (target) {
                    RunEditorTarget.Create, is RunEditorTarget.FromTemplate ->
                        vm.createRun(title, planText, status, recurrence, onDone)
                    is RunEditorTarget.EditDraft -> vm.updateDraft(
                        runId = target.run.id,
                        title = title,
                        planText = planText,
                        publish = status == DispatchStatus.QUEUED,
                        recurrence = recurrence,
                        onSuccess = onDone,
                    )
                    is RunEditorTarget.EditedRerun -> vm.createEditedRun(
                        sourceId = target.source.id,
                        title = title,
                        planText = planText,
                        status = status,
                        recurrence = recurrence,
                        onSuccess = onDone,
                    )
                }
            },
        )
    }

    if (showAccountSheet && state.session != null) {
        AccountBottomSheet(
            session = state.session!!,
            onDismiss = { showAccountSheet = false },
            onDisconnect = {
                showAccountSheet = false
                vm.logout()
            },
        )
    }

    deleteTarget?.let { target ->
        RikkaConfirmDialog(
            show = true,
            title = "删除 Run？",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                deleteTarget = null
                vm.deleteRun(target)
            },
            onDismiss = { deleteTarget = null },
        ) {
            Text(
                text = "确定要删除「${target.displayTitle}」(${target.versionLabel}) 吗？此操作无法撤销。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ConnectWorkflowView(
    modifier: Modifier = Modifier,
    initialBaseUrl: String,
    isLoading: Boolean,
    errorMessage: String?,
    onConnect: (identity: String, password: String, baseUrl: String) -> Unit,
) {
    var baseUrl by rememberSaveable { mutableStateOf(initialBaseUrl) }
    var identity by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.Zap,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "连接 AnyWorkflow Remote",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "登录你的 PocketBase 账号后，即可在移动端查看、编辑、控制与追踪云端工作流。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AnimatedVisibility(visible = !errorMessage.isNullOrBlank(), enter = fadeIn(), exit = fadeOut()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = HugeIcons.Alert01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        CardGroup(title = { Text("服务器与凭据") }) {
            formItem(
                leadingContent = { CardGroupIcon(HugeIcons.Link01) },
                headlineContent = { Text("服务器地址") },
                supportingContent = { Text("默认使用 AnyWorkflow 官方实例") },
            ) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text(DEFAULT_POCKETBASE_URL) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
            }
            formItem(
                leadingContent = { CardGroupIcon(HugeIcons.Login03) },
                headlineContent = { Text("账号邮箱") },
            ) {
                OutlinedTextField(
                    value = identity,
                    onValueChange = { identity = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("name@example.com") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )
            }
            formItem(
                headlineContent = { Text("密码") },
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("输入密码") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onConnect(identity, password, baseUrl) }
                    ),
                )
            }
        }

        Button(
            onClick = { onConnect(identity, password, baseUrl) },
            enabled = !isLoading && identity.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = CircleShape,
        ) {
            if (isLoading) {
                LoadingIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("连接中…")
            } else {
                Text("连接工作流服务", style = MaterialTheme.typography.titleSmallEmphasized)
            }
        }
    }
}

@Composable
private fun WorkflowRunsContent(
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp,
    state: WorkflowUiState,
    favoriteRunIds: Set<String>,
    onRefresh: () -> Unit,
    onFilterChange: (RunListFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onSelectRun: (WorkflowRun) -> Unit,
    onEditDraft: (WorkflowRun) -> Unit,
    onEditAsNewVersion: (WorkflowRun) -> Unit,
    onCommandRun: (WorkflowRun, DispatchRequestedAction) -> Unit,
    onCloneRun: (WorkflowRun, DispatchStatus) -> Unit,
    onToggleFavorite: (WorkflowRun) -> Unit,
    onSaveAsTemplate: (WorkflowRun) -> Unit,
    onRequestDelete: (WorkflowRun) -> Unit,
    onClearError: () -> Unit,
    onOpenCreate: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomPadding + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "filter_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RunListFilter.entries.forEach { filter ->
                        val selected = state.filter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { onFilterChange(filter) },
                            label = { Text(filter.label) },
                            shape = CircleShape,
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = HugeIcons.Tick01,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
            }

            item(key = "search_and_status") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.searchInput,
                        onValueChange = onSearchChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        placeholder = { Text("搜索工作流名称或 Run ID") },
                        leadingIcon = {
                            Icon(
                                imageVector = HugeIcons.Search01,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            if (state.searchInput.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(
                                        imageVector = HugeIcons.Cancel01,
                                        contentDescription = "清除搜索",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.debouncedQuery.isNotEmpty()) {
                                "搜索“${state.debouncedQuery}”"
                            } else {
                                "按更新时间排序"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = when {
                                state.isRefreshing -> "更新中…"
                                state.isLoading -> "加载中…"
                                else -> "共 ${state.totalItems} 条"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                item(key = "error_banner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = HugeIcons.Alert01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                onClearError()
                                onRefresh()
                            }) {
                                Text("重试", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            if (state.isLoading && state.runs.isEmpty()) {
                item(key = "loading_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
                    }
                }
            } else if (state.runs.isEmpty()) {
                item(key = "empty_state") {
                    WorkflowEmptyState(
                        filter = state.filter,
                        hasSearch = state.debouncedQuery.isNotEmpty(),
                        onResetFilter = { onFilterChange(RunListFilter.ALL) },
                        onClearSearch = { onSearchChange("") },
                        onCreateNew = onOpenCreate,
                    )
                }
            } else {
                item(key = "runs_group") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.runs.forEachIndexed { index, run ->
                            RunCardItem(
                                run = run,
                                index = index,
                                totalCount = state.runs.size,
                                isMutating = state.isMutating,
                                isFavorited = run.id in favoriteRunIds,
                                onClick = { onSelectRun(run) },
                                onEditDraft = { onEditDraft(run) },
                                onEditAsNewVersion = { onEditAsNewVersion(run) },
                                onCommand = { action -> onCommandRun(run, action) },
                                onClone = { status -> onCloneRun(run, status) },
                                onToggleFavorite = { onToggleFavorite(run) },
                                onSaveAsTemplate = { onSaveAsTemplate(run) },
                                onDelete = { onRequestDelete(run) },
                            )
                        }
                    }
                }

                if (state.totalPages > 1) {
                    item(key = "pager") {
                        WorkflowPager(
                            page = state.page,
                            totalPages = state.totalPages,
                            onPageChange = onPageChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunCardItem(
    run: WorkflowRun,
    index: Int,
    totalCount: Int,
    isMutating: Boolean,
    isFavorited: Boolean,
    onClick: () -> Unit,
    onEditDraft: () -> Unit,
    onEditAsNewVersion: () -> Unit,
    onCommand: (DispatchRequestedAction) -> Unit,
    onClone: (DispatchStatus) -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveAsTemplate: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusMeta = run.statusMeta()
    val toneColors = statusToneColors(statusMeta.tone)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val baseShape = cardGroupItemShape(index, totalCount)
    val animatedCorner by animateDpAsState(
        targetValue = if (isPressed) 20.dp else 4.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val shape = if (isPressed) RoundedCornerShape(animatedCorner) else baseShape

    val actions = remember(run, isMutating, isFavorited) {
        buildList {
            if (run.canEditDraft) {
                add(
                    ItemAction(
                        text = "编辑草稿",
                        icon = HugeIcons.Edit03,
                        enabled = !isMutating,
                        onClick = onEditDraft,
                    )
                )
            }
            if (run.canEditAsNewVersion) {
                add(
                    ItemAction(
                        text = "基于此版本编辑",
                        icon = HugeIcons.Edit03,
                        enabled = !isMutating,
                        onClick = onEditAsNewVersion,
                    )
                )
            }
            if (run.canPause) {
                add(
                    ItemAction(
                        text = "暂停",
                        icon = HugeIcons.Pause,
                        enabled = !isMutating,
                        onClick = { onCommand(DispatchRequestedAction.PAUSE) },
                    )
                )
            }
            if (run.canResume) {
                add(
                    ItemAction(
                        text = "恢复",
                        icon = HugeIcons.Play,
                        enabled = !isMutating,
                        onClick = { onCommand(DispatchRequestedAction.RESUME) },
                    )
                )
            }
            if (run.canCancel) {
                add(
                    ItemAction(
                        text = "取消运行",
                        icon = HugeIcons.StopCircle,
                        enabled = !isMutating,
                        onClick = { onCommand(DispatchRequestedAction.CANCEL) },
                    )
                )
            }
            add(
                ItemAction(
                    text = if (isFavorited) "取消收藏" else "收藏",
                    icon = HugeIcons.Favourite,
                    enabled = !isMutating,
                    onClick = onToggleFavorite,
                )
            )
            if (run.planText.isNotBlank()) {
                add(
                    ItemAction(
                        text = "存为模板",
                        icon = HugeIcons.Book02,
                        enabled = !isMutating,
                        onClick = onSaveAsTemplate,
                    )
                )
                add(
                    ItemAction(
                        text = "复制为草稿",
                        icon = HugeIcons.Copy01,
                        enabled = !isMutating,
                        onClick = { onClone(DispatchStatus.DRAFT) },
                    )
                )
                add(
                    ItemAction(
                        text = "完整重跑",
                        icon = HugeIcons.Refresh03,
                        enabled = !isMutating,
                        onClick = { onClone(DispatchStatus.QUEUED) },
                    )
                )
            }
            if (run.canDelete) {
                add(
                    ItemAction(
                        text = "删除",
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        enabled = !isMutating,
                        onClick = onDelete,
                    )
                )
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        shape = shape,
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (run.status == DispatchStatus.RUNNING) {
                    ContainedLoadingIndicator(modifier = Modifier.size(40.dp))
                } else {
                    RunStatusCircle(status = run.status, toneColors = toneColors)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = run.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusBadgePill(label = statusMeta.label, colors = toneColors)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = HugeIcons.GitFork,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = run.versionLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            text = formatRelativeTime(run.updated),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (run.canEditDraft) {
                        FilledTonalIconButton(
                            onClick = onEditDraft,
                            enabled = !isMutating,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Edit03,
                                contentDescription = "编辑草稿",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (actions.isNotEmpty()) {
                        ItemActionMenu(actions = actions)
                    }
                }
            }

            if (run.status.isActive || run.totalTasks > 0) {
                val progress = if (run.totalTasks > 0) {
                    (run.completedTasks.toFloat() / run.totalTasks.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Row(
                    modifier = Modifier.padding(start = 54.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (run.status.isActive) {
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f),
                            color = toneColors.content,
                            trackColor = toneColors.container,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text(
                        text = "${run.completedTasks} / ${run.totalTasks} 任务",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RunStatusCircle(
    status: DispatchStatus,
    toneColors: ToneColors,
) {
    val icon: ImageVector = when (status) {
        DispatchStatus.DRAFT -> HugeIcons.Edit03
        DispatchStatus.QUEUED -> HugeIcons.Clock01
        DispatchStatus.RUNNING -> HugeIcons.Zap
        DispatchStatus.SUCCEEDED -> HugeIcons.Tick01
        DispatchStatus.FAILED -> HugeIcons.Alert01
        DispatchStatus.CANCELED -> HugeIcons.Cancel01
    }
    val container by animateColorAsState(
        targetValue = toneColors.container,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val content by animateColorAsState(
        targetValue = toneColors.content,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = content,
        )
    }
}

@Composable
internal fun StatusBadgePill(
    label: String,
    colors: ToneColors,
) {
    Surface(
        shape = CircleShape,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun WorkflowEmptyState(
    filter: RunListFilter,
    hasSearch: Boolean,
    onResetFilter: () -> Unit,
    onClearSearch: () -> Unit,
    onCreateNew: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HugeIcons.Zap,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = when {
                    hasSearch -> "没有匹配的工作流"
                    filter == RunListFilter.ALL -> "还没有工作流"
                    else -> "暂无「${filter.label}」状态的工作流"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = when {
                    hasSearch -> "尝试更换关键词或清除搜索条件"
                    filter != RunListFilter.ALL -> "切换筛选条件查看其他状态的 Run"
                    else -> "点击右下角 + 号创建一个新的工作流 Run"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasSearch) {
                    FilledTonalButton(onClick = onClearSearch, shape = CircleShape) {
                        Text("清除搜索")
                    }
                } else if (filter != RunListFilter.ALL) {
                    FilledTonalButton(onClick = onResetFilter, shape = CircleShape) {
                        Text("查看全部")
                    }
                } else {
                    Button(onClick = onCreateNew, shape = CircleShape) {
                        Icon(HugeIcons.Add01, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新建工作流")
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowPager(
    page: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = CircleShape,
        color = CustomColors.listItemColors.containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onPageChange(page - 1) },
                enabled = page > 1,
            ) {
                Icon(HugeIcons.ArrowLeft01, contentDescription = "上一页")
            }
            Text(
                text = "第 $page / $totalPages 页",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = { onPageChange(page + 1) },
                enabled = page < totalPages,
            ) {
                Icon(HugeIcons.ArrowRight01, contentDescription = "下一页")
            }
        }
    }
}

@Composable
private fun AccountBottomSheet(
    session: WorkflowSession,
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardGroup(title = { Text("当前连接") }) {
                item(
                    leadingContent = { CardGroupIcon(HugeIcons.CloudServer) },
                    headlineContent = { Text(session.email) },
                    supportingContent = { Text(session.baseUrl) },
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
                Text("断开连接")
            }
        }
    }
}

internal data class ToneColors(
    val container: Color,
    val content: Color,
)

@Composable
internal fun statusToneColors(tone: StatusTone): ToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        StatusTone.NEUTRAL -> ToneColors(
            container = scheme.surfaceContainerHighest,
            content = scheme.onSurfaceVariant,
        )
        StatusTone.INFO -> ToneColors(
            container = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer,
        )
        StatusTone.SUCCESS -> ToneColors(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
        )
        StatusTone.WARNING -> ToneColors(
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
        )
        StatusTone.DANGER -> ToneColors(
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
        )
    }
}

private fun parseInstantSafe(raw: String): Instant? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val normalized = if (trimmed.contains(' ') && !trimmed.contains('T')) {
        trimmed.replace(' ', 'T')
    } else {
        trimmed
    }
    return runCatching { Instant.parse(normalized) }.getOrNull()
}

internal fun formatRelativeTime(raw: String): String {
    val instant = parseInstantSafe(raw) ?: return raw.ifEmpty { "—" }
    val seconds = Duration.between(instant, Instant.now()).seconds
    return when {
        seconds < 60 -> "刚刚"
        seconds < 3600 -> "${seconds / 60} 分钟前"
        seconds < 86_400 -> "${seconds / 3600} 小时前"
        seconds < 86_400 * 7 -> "${seconds / 86_400} 天前"
        else -> formatDateTime(raw)
    }
}

internal fun formatDateTime(raw: String): String {
    val instant = parseInstantSafe(raw) ?: return raw.ifEmpty { "—" }
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
