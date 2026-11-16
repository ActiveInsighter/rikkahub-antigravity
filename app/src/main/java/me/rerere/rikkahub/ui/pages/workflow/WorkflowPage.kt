package me.rerere.rikkahub.ui.pages.workflow

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.data.workflow.DEFAULT_POCKETBASE_URL
import me.rerere.rikkahub.data.workflow.DispatchStatus
import me.rerere.rikkahub.data.workflow.LibraryFolderScope
import me.rerere.rikkahub.data.workflow.RunEditorTarget
import me.rerere.rikkahub.data.workflow.WorkflowRun
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel

private enum class WorkflowNavTab(
    val label: String,
    val title: String,
) {
    RUNS("工作流", "工作流"),
    SCHEDULES("重复", "重复计划"),
    LIBRARY("收藏", "收藏与模板"),
    FILES("资料库", "资料库"),
    CONVERTER("转换", "文件转换"),
}

@Composable
fun WorkflowPage(
    vm: WorkflowVM = koinViewModel(),
    schedulesVm: WorkflowSchedulesVM = koinViewModel(),
    libraryVm: WorkflowLibraryVM = koinViewModel(),
    filesVm: WorkflowFilesVM = koinViewModel(),
    converterVm: WorkflowConverterVM = koinViewModel(),
    settingsVm: WorkflowSettingsVM = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val schedulesState by schedulesVm.uiState.collectAsStateWithLifecycle()
    val libraryState by libraryVm.uiState.collectAsStateWithLifecycle()
    val filesState by filesVm.uiState.collectAsStateWithLifecycle()
    val converterState by converterVm.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var activeTab by rememberSaveable { mutableStateOf(WorkflowNavTab.RUNS) }
    var editorTarget by remember { mutableStateOf<RunEditorTarget?>(null) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WorkflowRun?>(null) }
    var showCreateTemplateDialog by remember { mutableStateOf(false) }
    var pendingFileUploadTrigger by remember { mutableStateOf(false) }

    val activeRefreshing = when (activeTab) {
        WorkflowNavTab.RUNS -> state.isRefreshing
        WorkflowNavTab.SCHEDULES -> schedulesState.isRefreshing
        WorkflowNavTab.LIBRARY -> libraryState.isRefreshing
        WorkflowNavTab.FILES -> filesState.isRefreshing
        WorkflowNavTab.CONVERTER -> converterState.isRefreshingJobs
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
                                    WorkflowNavTab.CONVERTER -> converterVm.refresh()
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
                            onClick = { showSettingsDialog = true },
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = CustomColors.listItemColors.containerColor,
                            ),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Settings03,
                                contentDescription = "工作流设置",
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
                            WorkflowNavTab.CONVERTER -> HugeIcons.File02
                        }
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Icon(icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            onClick = { pendingFileUploadTrigger = true },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(HugeIcons.FileImport, contentDescription = "上传文件")
                        }
                    }
                    WorkflowNavTab.CONVERTER -> Unit
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
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
            val bottomPad = 0.dp

            when (activeTab) {
                WorkflowNavTab.RUNS -> WorkflowRunsTab(
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
                    onUploadUri = filesVm::uploadFromUri,
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
                    pendingUploadTrigger = pendingFileUploadTrigger,
                    onConsumeUploadTrigger = { pendingFileUploadTrigger = false },
                )

                WorkflowNavTab.CONVERTER -> WorkflowConverterTab(
                    modifier = contentModifier,
                    state = converterState,
                    libraryFiles = filesState.drive.files,
                    bottomPadding = bottomPad,
                    onRefresh = converterVm::refresh,
                    onPageChange = converterVm::setPage,
                    onUploadPdf = converterVm::uploadPdfFromUri,
                    onClearUploadedPdf = converterVm::clearUploadedPdf,
                    onCreateJob = converterVm::createJob,
                    onCancelJob = converterVm::cancelJob,
                    onRerunJob = converterVm::rerunJob,
                    onRequestDownload = converterVm::requestDownloadUrl,
                    onSaveConfig = converterVm::saveConfig,
                    onClearError = converterVm::clearError,
                    onClearNotice = converterVm::clearNotice,
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

    if (showSettingsDialog && state.session != null) {
        WorkflowSettingsDialog(
            session = state.session!!,
            state = settingsState,
            onDismiss = { showSettingsDialog = false },
            onRefresh = settingsVm::refreshAll,
            onDisconnect = {
                showSettingsDialog = false
                vm.logout()
            },
            onAddStartLink = settingsVm::addStartLink,
            onUpdateStartLink = settingsVm::updateStartLink,
            onDeleteStartLink = settingsVm::deleteStartLink,
            onResetStartLinks = settingsVm::resetStartLinks,
            onAddSavedModel = settingsVm::addSavedModel,
            onRemoveSavedModel = settingsVm::removeSavedModel,
            onRefreshCatalog = settingsVm::refreshModelCatalog,
            onSaveRuntimeDefaults = settingsVm::saveRuntimeDefaults,
            onClearError = settingsVm::clearError,
            onClearNotice = settingsVm::clearNotice,
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
