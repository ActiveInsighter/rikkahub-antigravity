package me.rerere.rikkahub.ui.pages.workflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Pause
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.workflow.DispatchRequestedAction
import me.rerere.rikkahub.data.workflow.DispatchStatus
import me.rerere.rikkahub.data.workflow.DslHighlightColors
import me.rerere.rikkahub.data.workflow.StatusTone
import me.rerere.rikkahub.data.workflow.WorkflowEvent
import me.rerere.rikkahub.data.workflow.WorkflowHistoryAct
import me.rerere.rikkahub.data.workflow.WorkflowHistoryMessageSummary
import me.rerere.rikkahub.data.workflow.WorkflowRun
import me.rerere.rikkahub.data.workflow.WorkflowTask
import me.rerere.rikkahub.data.workflow.highlightDsl
import me.rerere.rikkahub.ui.components.richtext.MaterialMarkdown
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.theme.CustomColors

private enum class RunDetailTab(val label: String) {
    EXECUTION("执行记录"),
    PLAN("计划与概览"),
}

@Composable
fun WorkflowRunDetailDialog(
    run: WorkflowRun,
    detail: RunDetailUiState,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelectVersion: (WorkflowRun) -> Unit,
    onEditDraft: (WorkflowRun) -> Unit,
    onEditAsNewVersion: (WorkflowRun) -> Unit,
    onCommand: (DispatchRequestedAction) -> Unit,
    onClone: (DispatchStatus) -> Unit,
    onDelete: () -> Unit,
    onLoadEvents: (taskId: String) -> Unit,
    onLoadActs: (event: WorkflowEvent) -> Unit,
    onLoadMessages: (actId: String, page: Int) -> Unit,
    onOpenMessage: (summary: WorkflowHistoryMessageSummary, actTitle: String, messageNumber: Int, eventActive: Boolean) -> Unit,
    onRetryMessage: () -> Unit,
    onCloseMessage: () -> Unit,
) {
    var selectedTab by remember(run.id) {
        mutableStateOf(if (run.status == DispatchStatus.DRAFT) RunDetailTab.PLAN else RunDetailTab.EXECUTION)
    }
    var versionMenuExpanded by remember { mutableStateOf(false) }
    var queueTextPreview by remember { mutableStateOf<Pair<String, String>?>(null) }

    val expandedTasks = remember(run.id) { mutableStateMapOf<String, Boolean>() }
    val expandedEvents = remember(run.id) { mutableStateMapOf<String, Boolean>() }
    val expandedActs = remember(run.id) { mutableStateMapOf<String, Boolean>() }

    val statusMeta = run.statusMeta()
    val toneColors = statusToneColors(statusMeta.tone)
    val clipboard = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
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
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = run.displayTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    StatusBadgePill(label = statusMeta.label, colors = toneColors)
                                    Box {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .clickable(enabled = detail.versions.size > 1) {
                                                    versionMenuExpanded = true
                                                },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            ) {
                                                Text(
                                                    text = run.versionLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                                if (detail.versions.size > 1) {
                                                    Icon(
                                                        HugeIcons.ArrowDown01,
                                                        contentDescription = "切换版本",
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                }
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = versionMenuExpanded,
                                            onDismissRequest = { versionMenuExpanded = false },
                                        ) {
                                            detail.versions.forEach { ver ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text("${ver.versionLabel} · ${ver.statusMeta().label}")
                                                    },
                                                    trailingIcon = {
                                                        if (ver.id == run.id) {
                                                            Icon(HugeIcons.Tick01, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        }
                                                    },
                                                    onClick = {
                                                        versionMenuExpanded = false
                                                        if (ver.id != run.id) onSelectVersion(ver)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(HugeIcons.Cancel01, contentDescription = "关闭")
                            }
                        },
                        actions = {
                            IconButton(onClick = onRefresh) {
                                Icon(HugeIcons.Refresh03, contentDescription = "刷新详情")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = CustomColors.topBarColors.containerColor,
                        ),
                    )
                },
                bottomBar = {
                    RunDetailBottomBar(
                        run = run,
                        isMutating = isMutating,
                        onEditDraft = { onEditDraft(run) },
                        onEditAsNewVersion = { onEditAsNewVersion(run) },
                        onCommand = onCommand,
                        onClone = onClone,
                        onDelete = onDelete,
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    ConnectedChoiceButtons(
                        options = RunDetailTab.entries,
                        selected = selectedTab,
                        onSelect = { selectedTab = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        minHeight = 42.dp,
                    ) { option, selected ->
                        ConnectedChoiceLabel(
                            text = option.label,
                            selected = selected,
                            icon = HugeIcons.Tick01,
                        )
                    }

                    when (selectedTab) {
                        RunDetailTab.EXECUTION -> {
                            ExecutionRecordsTab(
                                run = run,
                                detail = detail,
                                expandedTasks = expandedTasks,
                                expandedEvents = expandedEvents,
                                expandedActs = expandedActs,
                                onToggleTask = { task ->
                                    val next = !(expandedTasks[task.id] ?: false)
                                    expandedTasks[task.id] = next
                                    if (next && detail.eventsByTask[task.id] == null) {
                                        onLoadEvents(task.id)
                                    }
                                },
                                onToggleEvent = { event ->
                                    val next = !(expandedEvents[event.id] ?: false)
                                    expandedEvents[event.id] = next
                                    if (next && detail.actsByEvent[event.id] == null) {
                                        onLoadActs(event)
                                    }
                                },
                                onToggleAct = { act ->
                                    val next = !(expandedActs[act.id] ?: false)
                                    expandedActs[act.id] = next
                                    if (next && detail.messagesByAct[act.id] == null) {
                                        onLoadMessages(act.id, 1)
                                    }
                                },
                                onLoadEvents = onLoadEvents,
                                onLoadActs = onLoadActs,
                                onLoadMessages = onLoadMessages,
                                onOpenMessage = onOpenMessage,
                                onPreviewQueueText = { title, content ->
                                    queueTextPreview = title to content
                                },
                                onEditDraft = { onEditDraft(run) },
                            )
                        }
                        RunDetailTab.PLAN -> {
                            PlanOverviewTab(
                                run = run,
                                onCopyPlan = {
                                    clipboard.setText(AnnotatedString(run.planText))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // 消息内容详情 BottomSheet
    detail.openedMessageSummary?.let { summary ->
        MessageDetailBottomSheet(
            summary = summary,
            actTitle = detail.openedMessageActTitle,
            messageNumber = detail.openedMessageNumber,
            eventActive = detail.openedMessageEventActive,
            detail = detail.openedMessageDetail,
            isLoading = detail.isLoadingMessageDetail,
            error = detail.messageDetailError,
            onRetry = onRetryMessage,
            onDismiss = onCloseMessage,
        )
    }

    // Event 执行内容预览弹窗
    queueTextPreview?.let { (eventTitle, queueText) ->
        ModalBottomSheet(onDismissRequest = { queueTextPreview = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("执行内容", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = eventTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(queueText)) }) {
                        Icon(HugeIcons.Copy01, contentDescription = "复制")
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = queueText,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExecutionRecordsTab(
    run: WorkflowRun,
    detail: RunDetailUiState,
    expandedTasks: Map<String, Boolean>,
    expandedEvents: Map<String, Boolean>,
    expandedActs: Map<String, Boolean>,
    onToggleTask: (WorkflowTask) -> Unit,
    onToggleEvent: (WorkflowEvent) -> Unit,
    onToggleAct: (WorkflowHistoryAct) -> Unit,
    onLoadEvents: (String) -> Unit,
    onLoadActs: (WorkflowEvent) -> Unit,
    onLoadMessages: (String, Int) -> Unit,
    onOpenMessage: (WorkflowHistoryMessageSummary, String, Int, Boolean) -> Unit,
    onPreviewQueueText: (String, String) -> Unit,
    onEditDraft: () -> Unit,
) {
    val statusMeta = run.statusMeta()
    val toneColors = statusToneColors(statusMeta.tone)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 进度与错误摘要
        if (run.status.isActive || run.totalTasks > 0 || run.lastError.isNotBlank()) {
            item(key = "run_progress_summary") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CustomColors.listItemColors.containerColor,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("执行进度", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${run.completedTasks} / ${run.totalTasks} 任务",
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (run.totalTasks > 0) {
                            val progress = (run.completedTasks.toFloat() / run.totalTasks.toFloat()).coerceIn(0f, 1f)
                            LinearWavyProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = toneColors.content,
                                trackColor = toneColors.container,
                            )
                        }
                        if (run.lastError.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        HugeIcons.Alert01,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = run.lastError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (run.status == DispatchStatus.DRAFT) {
            item(key = "draft_notice") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = CustomColors.listItemColors.containerColor,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("草稿尚未运行", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "编辑工作流定义后点击「保存并运行」，即可在此追踪每个 Task、Event 和 AI 对话消息。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onEditDraft, shape = CircleShape) {
                            Icon(HugeIcons.Edit03, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("编辑草稿")
                        }
                    }
                }
            }
        } else if (detail.isLoadingTasks && detail.tasks.isEmpty()) {
            item(key = "tasks_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator(modifier = Modifier.size(44.dp))
                }
            }
        } else if (detail.tasksError != null && detail.tasks.isEmpty()) {
            item(key = "tasks_error") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = detail.tasksError,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        } else if (detail.tasks.isEmpty()) {
            item(key = "tasks_empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CustomColors.listItemColors.containerColor,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (run.status.isActive) "等待云端编排 Task…" else "暂无 Task 执行记录",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        } else {
            itemsIndexed(detail.tasks, key = { _, t -> t.id }) { _, task ->
                val isExpanded = expandedTasks[task.id] ?: (detail.tasks.size == 1)
                if (detail.tasks.size == 1 && expandedTasks[task.id] == null && detail.eventsByTask[task.id] == null && task.id !in detail.loadingTaskIds) {
                    // 单 Task 时自动触发加载
                    androidx.compose.runtime.LaunchedEffect(task.id) {
                        onLoadEvents(task.id)
                    }
                }
                TaskRecordCard(
                    task = task,
                    expanded = isExpanded,
                    events = detail.eventsByTask[task.id],
                    isLoadingEvents = task.id in detail.loadingTaskIds,
                    eventsError = detail.errorByTask[task.id],
                    expandedEvents = expandedEvents,
                    expandedActs = expandedActs,
                    detail = detail,
                    onToggle = { onToggleTask(task) },
                    onToggleEvent = onToggleEvent,
                    onToggleAct = onToggleAct,
                    onRetryEvents = { onLoadEvents(task.id) },
                    onRetryActs = onLoadActs,
                    onLoadMessages = onLoadMessages,
                    onOpenMessage = onOpenMessage,
                    onPreviewQueueText = onPreviewQueueText,
                )
            }
        }
    }
}

@Composable
private fun TaskRecordCard(
    task: WorkflowTask,
    expanded: Boolean,
    events: List<WorkflowEvent>?,
    isLoadingEvents: Boolean,
    eventsError: String?,
    expandedEvents: Map<String, Boolean>,
    expandedActs: Map<String, Boolean>,
    detail: RunDetailUiState,
    onToggle: () -> Unit,
    onToggleEvent: (WorkflowEvent) -> Unit,
    onToggleAct: (WorkflowHistoryAct) -> Unit,
    onRetryEvents: () -> Unit,
    onRetryActs: (WorkflowEvent) -> Unit,
    onLoadMessages: (String, Int) -> Unit,
    onOpenMessage: (WorkflowHistoryMessageSummary, String, Int, Boolean) -> Unit,
    onPreviewQueueText: (String, String) -> Unit,
) {
    val taskStatus = task.statusMeta()
    val toneColors = statusToneColors(taskStatus.tone)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusBadgePill(label = taskStatus.label, colors = toneColors)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = task.executorKind.label,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "${task.completedEvents}/${task.totalEvents} Events",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = task.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (expanded) HugeIcons.ArrowDown01 else HugeIcons.ArrowRight01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    val taskErr = task.compileError.ifBlank { task.lastError }
                    if (taskErr.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = taskErr,
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }

                    when {
                        isLoadingEvents && events == null -> {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LoadingIndicator(modifier = Modifier.size(18.dp))
                                Text("加载 Event 列表…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        eventsError != null && events == null -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = eventsError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = onRetryEvents) { Text("重试") }
                            }
                        }
                        events.isNullOrEmpty() -> {
                            Text(
                                text = "暂无 Event 记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            events.forEach { event ->
                                val eventExpanded = expandedEvents[event.id] ?: false
                                EventRecordCard(
                                    event = event,
                                    expanded = eventExpanded,
                                    acts = detail.actsByEvent[event.id],
                                    isLoadingActs = event.id in detail.loadingEventIds,
                                    actsError = detail.errorByEvent[event.id],
                                    expandedActs = expandedActs,
                                    detail = detail,
                                    onToggle = { onToggleEvent(event) },
                                    onToggleAct = onToggleAct,
                                    onRetryActs = { onRetryActs(event) },
                                    onLoadMessages = onLoadMessages,
                                    onOpenMessage = onOpenMessage,
                                    onPreviewQueueText = {
                                        onPreviewQueueText(event.displayTitle, event.queueTextOverride)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRecordCard(
    event: WorkflowEvent,
    expanded: Boolean,
    acts: List<WorkflowHistoryAct>?,
    isLoadingActs: Boolean,
    actsError: String?,
    expandedActs: Map<String, Boolean>,
    detail: RunDetailUiState,
    onToggle: () -> Unit,
    onToggleAct: (WorkflowHistoryAct) -> Unit,
    onRetryActs: () -> Unit,
    onLoadMessages: (String, Int) -> Unit,
    onOpenMessage: (WorkflowHistoryMessageSummary, String, Int, Boolean) -> Unit,
    onPreviewQueueText: () -> Unit,
) {
    val eventStatus = event.statusMeta()
    val toneColors = statusToneColors(eventStatus.tone)
    val eventActive = event.status.isActive

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(toneColors.content, CircleShape),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = event.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Event ${event.eventIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "· ${eventStatus.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = toneColors.content,
                        )
                        if (event.progressLabel.isNotBlank()) {
                            Text(
                                text = "· ${event.progressLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) HugeIcons.ArrowDown01 else HugeIcons.ArrowRight01,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (event.lastError.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = event.lastError,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }

                    when {
                        isLoadingActs && acts == null -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LoadingIndicator(modifier = Modifier.size(16.dp))
                                Text("加载 Act 记录…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        actsError != null && acts == null -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = actsError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = onRetryActs) { Text("重试") }
                            }
                        }
                        acts.isNullOrEmpty() -> {
                            Text(
                                text = if (eventActive) "正在执行，等待生成对话记录…" else "暂无 Act 对话记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            acts.forEach { act ->
                                val actExpanded = expandedActs[act.id] ?: false
                                ActRecordCard(
                                    act = act,
                                    eventActive = eventActive,
                                    expanded = actExpanded,
                                    messagesPage = detail.messagesByAct[act.id],
                                    isLoadingMessages = act.id in detail.loadingActIds,
                                    messagesError = detail.errorByAct[act.id],
                                    onToggle = { onToggleAct(act) },
                                    onLoadPage = { page -> onLoadMessages(act.id, page) },
                                    onOpenMessage = { summary, number ->
                                        onOpenMessage(summary, act.displayTitle, number, eventActive)
                                    },
                                )
                            }
                        }
                    }

                    if (event.queueTextOverride.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = onPreviewQueueText,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Icon(HugeIcons.File02, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("查看执行内容", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActRecordCard(
    act: WorkflowHistoryAct,
    eventActive: Boolean,
    expanded: Boolean,
    messagesPage: me.rerere.rikkahub.data.workflow.WorkflowListPage<WorkflowHistoryMessageSummary>?,
    isLoadingMessages: Boolean,
    messagesError: String?,
    onToggle: () -> Unit,
    onLoadPage: (Int) -> Unit,
    onOpenMessage: (WorkflowHistoryMessageSummary, Int) -> Unit,
) {
    val actStatus = act.statusMeta(eventActive)
    val toneColors = statusToneColors(actStatus.tone)
    val msgCount = messagesPage?.totalItems ?: act.messageCount

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = act.displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Act ${act.actIndex + 1} · ${actStatus.label} · $msgCount 条消息",
                        style = MaterialTheme.typography.labelSmall,
                        color = toneColors.content,
                    )
                }
                Icon(
                    imageVector = if (expanded) HugeIcons.ArrowDown01 else HugeIcons.ArrowRight01,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when {
                        isLoadingMessages && messagesPage == null -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LoadingIndicator(modifier = Modifier.size(15.dp))
                                Text("加载消息列表…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        messagesError != null && messagesPage == null -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = messagesError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { onLoadPage(1) }) { Text("重试") }
                            }
                        }
                        messagesPage == null || messagesPage.items.isEmpty() -> {
                            Text(
                                text = "暂无消息，同步后将自动显示。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            messagesPage.items.forEachIndexed { idx, summary ->
                                val number = (messagesPage.page - 1) * messagesPage.perPage + idx + 1
                                val msgStatus = summary.statusMeta(eventActive)
                                val msgTone = statusToneColors(msgStatus.tone)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenMessage(summary, number) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(msgTone.container, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = HugeIcons.Message01,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = msgTone.content,
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "消息 $number",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            val ts = summary.receivedAt.ifBlank { summary.sentAt }
                                            Text(
                                                text = buildString {
                                                    append(msgStatus.label)
                                                    if (ts.isNotBlank()) append(" · ${formatDateTime(ts)}")
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(
                                            imageVector = HugeIcons.ArrowRight01,
                                            contentDescription = "查看消息内容",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            if (messagesPage.totalPages > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(
                                        onClick = { onLoadPage(messagesPage.page - 1) },
                                        enabled = messagesPage.page > 1,
                                    ) {
                                        Icon(HugeIcons.ArrowLeft01, contentDescription = "上一页")
                                    }
                                    Text(
                                        text = "${messagesPage.page} / ${messagesPage.totalPages}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    IconButton(
                                        onClick = { onLoadPage(messagesPage.page + 1) },
                                        enabled = messagesPage.page < messagesPage.totalPages,
                                    ) {
                                        Icon(HugeIcons.ArrowRight01, contentDescription = "下一页")
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

@Composable
private fun MessageDetailBottomSheet(
    summary: WorkflowHistoryMessageSummary,
    actTitle: String,
    messageNumber: Int,
    eventActive: Boolean,
    detail: me.rerere.rikkahub.data.workflow.WorkflowHistoryMessage?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val statusMeta = detail?.statusMeta(eventActive) ?: summary.statusMeta(eventActive)
    val toneColors = statusToneColors(statusMeta.tone)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("消息 $messageNumber", style = MaterialTheme.typography.headlineSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusBadgePill(label = statusMeta.label, colors = toneColors)
                        Text(
                            text = actTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (isLoading && detail == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator(modifier = Modifier.size(40.dp))
                }
            } else if (error != null && detail == null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        FilledTonalButton(onClick = onRetry, shape = CircleShape) {
                            Text("重试")
                        }
                    }
                }
            } else if (detail != null) {
                if (detail.sentAt.isNotBlank() || detail.receivedAt.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (detail.sentAt.isNotBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(HugeIcons.Clock01, contentDescription = null, modifier = Modifier.size(13.dp))
                                Text(
                                    text = "发送 ${formatDateTime(detail.sentAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (detail.receivedAt.isNotBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(HugeIcons.Clock01, contentDescription = null, modifier = Modifier.size(13.dp))
                                Text(
                                    text = "回复 ${formatDateTime(detail.receivedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // 用户消息卡片
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "用户消息",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (detail.userMarkdown.isNotBlank()) {
                                IconButton(
                                    onClick = { clipboard.setText(AnnotatedString(detail.userMarkdown)) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(HugeIcons.Copy01, contentDescription = "复制用户消息", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (detail.userMarkdown.isBlank()) {
                            Text(
                                text = "没有记录用户消息",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            MaterialMarkdown(
                                content = detail.userMarkdown,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                // AI 回复卡片
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "AI 回复",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            if (detail.assistantMarkdown.isNotBlank()) {
                                IconButton(
                                    onClick = { clipboard.setText(AnnotatedString(detail.assistantMarkdown)) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(HugeIcons.Copy01, contentDescription = "复制 AI 回复", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (detail.assistantMarkdown.isBlank()) {
                            Text(
                                text = if (eventActive && detail.receivedAt.isBlank()) "等待 AI 回复…" else "没有记录 AI 回复",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            MaterialMarkdown(
                                content = detail.assistantMarkdown,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanOverviewTab(
    run: WorkflowRun,
    onCopyPlan: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val highlightColors = remember(colorScheme) {
        DslHighlightColors(
            directive = colorScheme.primary,
            variable = colorScheme.tertiary,
            tag = colorScheme.secondary,
            brace = colorScheme.primary.copy(alpha = 0.85f),
            fence = colorScheme.onSurfaceVariant,
        )
    }
    val highlightedPlan = remember(run.planText, highlightColors) {
        highlightDsl(run.planText.trim(), highlightColors)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CardGroup(title = { Text("执行概览") }) {
            item(
                headlineContent = { Text("Run ID") },
                trailingContent = {
                    Text(
                        text = run.id,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            item(
                headlineContent = { Text("版本与来源") },
                trailingContent = {
                    Text(
                        text = "${run.versionLabel} · ${run.origin.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            item(
                headlineContent = { Text("执行模式") },
                trailingContent = {
                    Text(
                        text = if (run.executionMode.wireValue == "parallel") {
                            "并行 (${run.maxConcurrency} 并发)"
                        } else {
                            "串行"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            item(
                headlineContent = { Text("更新时间") },
                trailingContent = {
                    Text(
                        text = formatDateTime(run.updated),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            if (run.scheduledAt.isNotBlank()) {
                item(
                    headlineContent = { Text("计划执行时间") },
                    trailingContent = {
                        Text(
                            text = formatDateTime(run.scheduledAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }

        if (run.planText.isNotBlank()) {
            CardGroup(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("工作流定义 (DSL)")
                        TextButton(onClick = onCopyPlan) {
                            Icon(HugeIcons.Copy01, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("复制")
                        }
                    }
                },
            ) {
                formItem {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = highlightedPlan,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunDetailBottomBar(
    run: WorkflowRun,
    isMutating: Boolean,
    onEditDraft: () -> Unit,
    onEditAsNewVersion: () -> Unit,
    onCommand: (DispatchRequestedAction) -> Unit,
    onClone: (DispatchStatus) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (run.canEditDraft) {
                Button(
                    onClick = onEditDraft,
                    enabled = !isMutating,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Icon(HugeIcons.Edit03, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("编辑草稿")
                }
            }

            if (run.status.isActive) {
                if (run.canPause) {
                    FilledTonalButton(
                        onClick = { onCommand(DispatchRequestedAction.PAUSE) },
                        enabled = !isMutating,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                    ) {
                        Icon(HugeIcons.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("暂停")
                    }
                }
                if (run.canResume) {
                    Button(
                        onClick = { onCommand(DispatchRequestedAction.RESUME) },
                        enabled = !isMutating,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                    ) {
                        Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("恢复")
                    }
                }
                if (run.canCancel) {
                    OutlinedButton(
                        onClick = { onCommand(DispatchRequestedAction.CANCEL) },
                        enabled = !isMutating,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                    ) {
                        Icon(HugeIcons.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("取消")
                    }
                }
            }

            if (run.canEditAsNewVersion) {
                FilledTonalButton(
                    onClick = onEditAsNewVersion,
                    enabled = !isMutating,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Icon(HugeIcons.Edit03, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("编辑新版")
                }
                Button(
                    onClick = { onClone(DispatchStatus.QUEUED) },
                    enabled = !isMutating,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Icon(HugeIcons.Refresh03, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重跑")
                }
            }

            if (run.canDelete) {
                IconButton(
                    onClick = onDelete,
                    enabled = !isMutating,
                ) {
                    Icon(
                        HugeIcons.Delete01,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
