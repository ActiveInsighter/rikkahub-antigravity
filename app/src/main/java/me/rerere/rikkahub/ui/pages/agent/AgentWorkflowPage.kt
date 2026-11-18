package me.rerere.rikkahub.ui.pages.agent

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.data.agent.AgentSchedule
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel

@Composable
fun AgentWorkflowPage() {
    val vm: AgentWorkflowVM = koinViewModel()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val availableModels by vm.availableModels.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()
    val backendNotice by vm.backendNotice.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var editingSchedule by remember { mutableStateOf<AgentSchedule?>(null) }
    var deletingSchedule by remember { mutableStateOf<AgentSchedule?>(null) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            toaster.show(msg, type = ToastType.Info)
            vm.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Agent 工作流") },
                subtitle = { Text("定时执行任务（批量执行敬请期待）") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(
                            imageVector = HugeIcons.Refresh03,
                            contentDescription = "刷新",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingSchedule = AgentSchedule(
                        id = "",
                        title = "",
                        prompt = "",
                        model = availableModels.firstOrNull() ?: "Antigravity",
                        thinkingLevel = "",
                        cron = "0 9 * * *",
                        timezone = "Asia/Shanghai",
                        enabled = true,
                    )
                },
            ) {
                Icon(
                    imageVector = HugeIcons.Add01,
                    contentDescription = "新建定时任务",
                )
            }
        },
        containerColor = CustomColors.topBarColors.containerColor,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!backendNotice.isNullOrBlank()) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Alert01,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = backendNotice!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            leadingIcon = {
                                Icon(
                                    imageVector = HugeIcons.Clock01,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text("定时任务 (${schedules.size})") },
                        )
                        FilterChip(
                            selected = false,
                            enabled = false,
                            onClick = {},
                            label = { Text("批量任务 (敬请期待)") },
                        )
                    }
                }

                if (schedules.isEmpty()) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Clock01,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "暂无定时任务",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "点击右下角 + 创建一个定时任务，设置提示词、Gemini/Antigravity 模型、思考程度与 Cron 计划，自动定时触发 GitHub Action。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(schedules, key = { it.id }) { schedule ->
                        ScheduleTaskCard(
                            schedule = schedule,
                            onToggle = { enabled -> vm.toggleEnabled(schedule, enabled) },
                            onTriggerNow = { vm.triggerNow(schedule) },
                            onEdit = { editingSchedule = schedule },
                            onDelete = { deletingSchedule = schedule },
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(72.dp))
                }
            }
        }
    }

    editingSchedule?.let { draft ->
        ScheduleEditorSheet(
            initial = draft,
            availableModels = availableModels,
            onDismiss = { editingSchedule = null },
            onSave = { updated ->
                vm.saveSchedule(updated)
                editingSchedule = null
            },
        )
    }

    deletingSchedule?.let { target ->
        RikkaConfirmDialog(
            show = true,
            title = "删除定时任务",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                vm.deleteSchedule(target.id)
                deletingSchedule = null
            },
            onDismiss = { deletingSchedule = null },
        ) {
            Text("确定要删除「${target.title.ifBlank { "未命名任务" }}」吗？")
        }
    }
}

@Composable
private fun ScheduleTaskCard(
    schedule: AgentSchedule,
    onToggle: (Boolean) -> Unit,
    onTriggerNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = schedule.title.ifBlank { "未命名定时任务" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Cron: ${schedule.cron} (${schedule.timezone})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = onToggle,
                )
            }

            Text(
                text = schedule.prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = schedule.model,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (schedule.thinkingLevel.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = "思考: ${schedule.thinkingLevel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                if (schedule.lastStatus.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "最近: ${schedule.lastStatus}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onTriggerNow) {
                    Icon(
                        imageVector = HugeIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("立即执行")
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = HugeIcons.Edit03,
                        contentDescription = "编辑",
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleEditorSheet(
    initial: AgentSchedule,
    availableModels: List<String>,
    onDismiss: () -> Unit,
    onSave: (AgentSchedule) -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Expanded)
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var prompt by rememberSaveable(initial.id) { mutableStateOf(initial.prompt) }
    var systemPrompt by rememberSaveable(initial.id) { mutableStateOf(initial.systemPrompt) }
    var model by rememberSaveable(initial.id) { mutableStateOf(initial.model) }
    var thinkingLevel by rememberSaveable(initial.id) { mutableStateOf(initial.thinkingLevel) }
    var cron by rememberSaveable(initial.id) { mutableStateOf(initial.cron) }
    var timezone by rememberSaveable(initial.id) { mutableStateOf(initial.timezone) }

    val cronPresets = listOf(
        "每天 09:00" to "0 9 * * *",
        "每小时" to "0 * * * *",
        "每 6 小时" to "0 */6 * * *",
        "每周一 09:00" to "0 9 * * 1",
    )
    val thinkingOptions = listOf(
        "" to "默认",
        "minimal" to "Minimal",
        "low" to "Low",
        "medium" to "Medium",
        "high" to "High",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (initial.id.isBlank()) "新建定时任务" else "编辑定时任务",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("任务名称") },
                placeholder = { Text("例如：每日新闻摘要") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("提示词 (Prompt)") },
                placeholder = { Text("输入每次定时触发时发送给模型的指令…") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("系统提示词（可选）") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型名（可手动输入或点选下方预设）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                availableModels.forEach { preset ->
                    FilterChip(
                        selected = model.equals(preset, ignoreCase = true),
                        onClick = { model = preset },
                        label = { Text(preset) },
                    )
                }
            }

            Text(
                text = "思考程度",
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                thinkingOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = thinkingLevel == value,
                        onClick = { thinkingLevel = value },
                        label = { Text(label) },
                    )
                }
            }

            Text(
                text = "定时计划 (Cron)",
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                cronPresets.forEach { (label, expr) ->
                    FilterChip(
                        selected = cron == expr,
                        onClick = { cron = expr },
                        label = { Text(label) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = cron,
                    onValueChange = { cron = it },
                    label = { Text("Cron 表达式") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = timezone,
                    onValueChange = { timezone = it },
                    label = { Text("时区") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = {
                        onSave(
                            initial.copy(
                                title = title.trim().ifBlank { "未命名定时任务" },
                                prompt = prompt.trim(),
                                systemPrompt = systemPrompt.trim(),
                                model = model.trim().ifBlank { "Antigravity" },
                                thinkingLevel = thinkingLevel,
                                cron = cron.trim().ifBlank { "0 9 * * *" },
                                timezone = timezone.trim().ifBlank { "Asia/Shanghai" },
                            )
                        )
                    },
                    enabled = prompt.isNotBlank(),
                ) {
                    Text("保存")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
