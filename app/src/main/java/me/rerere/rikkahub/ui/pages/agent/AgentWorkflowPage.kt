package me.rerere.rikkahub.ui.pages.agent

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.data.agent.AgentScheduledTask
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val THINKING_OPTIONS = listOf(
    "" to "自动",
    "minimal" to "最低",
    "low" to "低",
    "medium" to "中",
    "high" to "高",
)

private val INTERVAL_PRESETS = listOf(
    15 to "15 分钟",
    60 to "1 小时",
    360 to "6 小时",
    1440 to "每天",
)

@Composable
fun AgentWorkflowPage() {
    val vm: AgentWorkflowVM = koinViewModel()

    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val savedModels by vm.savedModels.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val isSyncing by vm.isSyncing.collectAsStateWithLifecycle()
    val runningTaskIds by vm.runningTaskIds.collectAsStateWithLifecycle()
    val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.clearStatusMessage()
    }

    var showEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<AgentScheduledTask?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                title = {
                    Column {
                        Text("Agent 工作流", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (session != null) "定时任务 · 已连接云端" else "定时任务 · 未连接云端",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshFromRemote() }, enabled = !isSyncing) {
                        Icon(
                            imageVector = HugeIcons.Refresh03,
                            contentDescription = "刷新",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTask = null
                    showEditor = true
                },
            ) {
                Icon(
                    imageVector = HugeIcons.Add01,
                    contentDescription = "新建定时任务",
                )
            }
        },
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Calendar03,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = "暂无定时任务",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "配置提示词、Gemini 模型与思考程度后，将按设定周期自动触发 GitHub Action 执行任务。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            editingTask = null
                            showEditor = true
                        },
                    ) {
                        Icon(HugeIcons.Add01, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("新建定时任务")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    ScheduledTaskCard(
                        task = task,
                        isRunning = task.id in runningTaskIds,
                        onToggle = { enabled -> vm.toggleTask(task, enabled) },
                        onRunNow = { vm.runTaskNow(task) },
                        onEdit = {
                            editingTask = task
                            showEditor = true
                        },
                        onDelete = { vm.deleteTask(task) },
                    )
                }
            }
        }
    }

    if (showEditor) {
        ScheduledTaskEditorSheet(
            initial = editingTask,
            savedModels = savedModels,
            onSave = { name, prompt, systemPrompt, model, thinkingLevel, intervalMinutes, enabled ->
                vm.saveTask(
                    editingId = editingTask?.id,
                    name = name,
                    prompt = prompt,
                    systemPrompt = systemPrompt,
                    model = model,
                    thinkingLevel = thinkingLevel,
                    intervalMinutes = intervalMinutes,
                    enabled = enabled,
                )
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun ScheduledTaskCard(
    task: AgentScheduledTask,
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${task.model} · ${thinkingLabel(task.thinkingLevel)} · ${intervalLabel(task.intervalMinutes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Switch(
                    checked = task.enabled,
                    onCheckedChange = onToggle,
                )
            }

            Text(
                text = task.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (task.lastRunAt != null || !task.lastStatus.isNullOrBlank()) {
                val timeText = task.lastRunAt?.let(::formatTime) ?: ""
                val statusText = task.lastStatus.orEmpty()
                val errText = task.lastError?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                Text(
                    text = "上次执行：$timeText ($statusText)$errText",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.lastStatus == "failed") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = HugeIcons.Edit03,
                        contentDescription = "编辑",
                    )
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = onRunNow,
                    enabled = !isRunning,
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("触发中…")
                    } else {
                        Text("立即执行")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduledTaskEditorSheet(
    initial: AgentScheduledTask?,
    savedModels: List<String>,
    onSave: (
        name: String,
        prompt: String,
        systemPrompt: String,
        model: String,
        thinkingLevel: String,
        intervalMinutes: Int,
        enabled: Boolean,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var prompt by remember(initial) { mutableStateOf(initial?.prompt ?: "") }
    var systemPrompt by remember(initial) { mutableStateOf(initial?.systemPrompt ?: "") }
    var model by remember(initial) { mutableStateOf(initial?.model ?: savedModels.firstOrNull() ?: "Antigravity") }
    var thinkingLevel by remember(initial) { mutableStateOf(initial?.thinkingLevel ?: "") }
    var intervalText by remember(initial) { mutableStateOf((initial?.intervalMinutes ?: 60).toString()) }
    var enabled by remember(initial) { mutableStateOf(initial?.enabled ?: true) }
    var promptError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (initial == null) "新建定时任务" else "编辑定时任务",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("任务名称") },
                placeholder = { Text("例如：每日仓库巡检") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = {
                    prompt = it
                    promptError = null
                },
                label = { Text("提示词 *") },
                placeholder = { Text("输入要定时发送给模型的指令…") },
                isError = promptError != null,
                supportingText = promptError?.let { msg -> { Text(msg) } },
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
                label = { Text("Gemini / Antigravity 模型名") },
                placeholder = { Text("Antigravity / gemini-2.5-pro / gemini-3-flash-preview") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                savedModels.forEach { preset ->
                    FilterChip(
                        selected = model.equals(preset, ignoreCase = true),
                        onClick = { model = preset },
                        label = { Text(preset, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            Text("思考程度", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                THINKING_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = thinkingLevel == value,
                        onClick = { thinkingLevel = value },
                        label = { Text(label) },
                    )
                }
            }

            Text("执行周期（分钟）", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val currentInt = intervalText.toIntOrNull()
                INTERVAL_PRESETS.forEach { (mins, label) ->
                    FilterChip(
                        selected = currentInt == mins,
                        onClick = { intervalText = mins.toString() },
                        label = { Text(label) },
                    )
                }
            }
            OutlinedTextField(
                value = intervalText,
                onValueChange = { intervalText = it.filter { ch -> ch.isDigit() } },
                label = { Text("自定义间隔（分钟，最小 5）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("启用定时执行", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = {
                        if (prompt.trim().isEmpty()) {
                            promptError = "提示词不能为空"
                            return@Button
                        }
                        val mins = (intervalText.toIntOrNull() ?: 60).coerceIn(5, 43200)
                        onSave(name, prompt, systemPrompt, model, thinkingLevel, mins, enabled)
                    },
                ) {
                    Text("保存")
                }
            }
        }
    }
}

private fun thinkingLabel(level: String): String =
    THINKING_OPTIONS.firstOrNull { it.first == level }?.second ?: level.ifBlank { "自动" }

private fun intervalLabel(minutes: Int): String = when {
    minutes % 1440 == 0 -> "每 ${minutes / 1440} 天"
    minutes % 60 == 0 -> "每 ${minutes / 60} 小时"
    else -> "每 $minutes 分钟"
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
