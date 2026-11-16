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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowTurnBackward
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Link01
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.workflow.DEFAULT_START_PAGE_PRESETS
import me.rerere.rikkahub.data.workflow.DispatchExecutionMode
import me.rerere.rikkahub.data.workflow.DispatchStatus
import me.rerere.rikkahub.data.workflow.DslHighlightColors
import me.rerere.rikkahub.data.workflow.DslInsertResult
import me.rerere.rikkahub.data.workflow.DslLoopTemplate
import me.rerere.rikkahub.data.workflow.DslOutlineKind
import me.rerere.rikkahub.data.workflow.DslSyntaxVisualTransformation
import me.rerere.rikkahub.data.workflow.RunEditorTarget
import me.rerere.rikkahub.data.workflow.createStarterPlan
import me.rerere.rikkahub.data.workflow.duplicateCurrentLine
import me.rerere.rikkahub.data.workflow.formatWorkflowDsl
import me.rerere.rikkahub.data.workflow.insertActSnippet
import me.rerere.rikkahub.data.workflow.insertEventSnippet
import me.rerere.rikkahub.data.workflow.insertLinkSnippet
import me.rerere.rikkahub.data.workflow.insertLoopSnippet
import me.rerere.rikkahub.data.workflow.insertMessageBlockSnippet
import me.rerere.rikkahub.data.workflow.insertTaskSnippet
import me.rerere.rikkahub.data.workflow.insertTextAtCursor
import me.rerere.rikkahub.data.workflow.insertVariableDeclSnippet
import me.rerere.rikkahub.data.workflow.insertWaitSnippet
import me.rerere.rikkahub.data.workflow.inspectWorkflowDsl
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.theme.CustomColors

@Composable
fun WorkflowDslEditorDialog(
    target: RunEditorTarget,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (title: String, planText: String, targetStatus: DispatchStatus) -> Unit,
) {
    val initialTitle = remember(target) {
        when (target) {
            RunEditorTarget.Create -> ""
            is RunEditorTarget.EditDraft -> target.run.title
            is RunEditorTarget.EditedRerun -> target.source.title
        }
    }
    val initialPlan = remember(target) {
        when (target) {
            RunEditorTarget.Create -> createStarterPlan()
            is RunEditorTarget.EditDraft -> target.run.planText.ifBlank { createStarterPlan(target.run.title) }
            is RunEditorTarget.EditedRerun -> target.source.planText.ifBlank { createStarterPlan(target.source.title) }
        }
    }

    var title by remember(target) { mutableStateOf(initialTitle) }
    var editorValue by remember(target) { mutableStateOf(TextFieldValue(initialPlan)) }
    val undoStack = remember(target) { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember(target) { mutableStateListOf<TextFieldValue>() }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    var showVariableSheet by remember { mutableStateOf(false) }
    var showOutlineSheet by remember { mutableStateOf(false) }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }
    var showHelpSheet by remember { mutableStateOf(false) }

    val inspection = remember(editorValue.text) { inspectWorkflowDsl(editorValue.text) }
    val currentMode = remember(editorValue.text) { parseEditorMode(editorValue.text) }

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
    val visualTransformation = remember(highlightColors) {
        DslSyntaxVisualTransformation(highlightColors)
    }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(2500)
            feedbackMessage = null
        }
    }

    fun applyEdit(next: TextFieldValue, recordHistory: Boolean = true) {
        if (recordHistory && next.text != editorValue.text) {
            undoStack.add(editorValue)
            if (undoStack.size > 60) undoStack.removeAt(0)
            redoStack.clear()
        }
        editorValue = next
    }

    fun applyInsert(result: DslInsertResult) {
        when (result) {
            is DslInsertResult.Success -> applyEdit(result.value)
            is DslInsertResult.Failure -> feedbackMessage = result.message
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(editorValue)
        editorValue = undoStack.removeAt(undoStack.lastIndex)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(editorValue)
        editorValue = redoStack.removeAt(redoStack.lastIndex)
    }

    fun syncRunDirective(nextTitle: String) {
        val currentText = editorValue.text
        val regex = Regex("""^@run\s*=.*$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val updated = if (regex.containsMatchIn(currentText)) {
            currentText.replaceFirst(regex, "@run=${nextTitle.trim()}")
        } else {
            "@run=${nextTitle.trim()}\n$currentText"
        }
        applyEdit(editorValue.copy(text = updated))
    }

    fun syncModeDirective(mode: DispatchExecutionMode) {
        val currentText = editorValue.text
        val regex = Regex("""^@mode\s*=.*$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val updated = if (regex.containsMatchIn(currentText)) {
            currentText.replaceFirst(regex, "@mode=${mode.wireValue}")
        } else {
            "@mode=${mode.wireValue}\n$currentText"
        }
        applyEdit(editorValue.copy(text = updated))
    }

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
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                containerColor = CustomColors.topBarColors.containerColor,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = when (target) {
                                        RunEditorTarget.Create -> "新建工作流"
                                        is RunEditorTarget.EditDraft -> "编辑草稿"
                                        is RunEditorTarget.EditedRerun -> "基于 ${target.source.versionLabel} 编辑"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(HugeIcons.Cancel01, contentDescription = "关闭")
                            }
                        },
                        actions = {
                            // 诊断状态胶囊
                            Surface(
                                onClick = { showDiagnosticsSheet = true },
                                shape = CircleShape,
                                color = if (inspection.hasErrors) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                contentColor = if (inspection.hasErrors) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = if (inspection.hasErrors) HugeIcons.Alert01 else HugeIcons.Tick01,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = if (inspection.hasErrors) "${inspection.errorCount} 问题" else "语法正常",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            IconButton(onClick = { showHelpSheet = true }) {
                                Icon(HugeIcons.Book02, contentDescription = "DSL 语法帮助")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = CustomColors.topBarColors.containerColor,
                        ),
                    )
                },
                bottomBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 2.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onSubmit(title, editorValue.text, DispatchStatus.DRAFT) },
                                enabled = !isMutating && editorValue.text.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                            ) {
                                Text(if (target is RunEditorTarget.EditDraft) "保存草稿" else "存为草稿")
                            }
                            Button(
                                onClick = {
                                    if (inspection.hasErrors) {
                                        showDiagnosticsSheet = true
                                    } else {
                                        onSubmit(title, editorValue.text, DispatchStatus.QUEUED)
                                    }
                                },
                                enabled = !isMutating && editorValue.text.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                            ) {
                                Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (target is RunEditorTarget.EditDraft) "保存并运行" else "立即运行")
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    // 标题与执行模式栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { next ->
                                title = next
                                syncRunDirective(next)
                            },
                            modifier = Modifier.weight(1.4f),
                            placeholder = { Text("工作流名称") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                        )
                        ConnectedChoiceButtons(
                            options = DispatchExecutionMode.entries,
                            selected = currentMode,
                            onSelect = { syncModeDirective(it) },
                            modifier = Modifier.weight(1f),
                            minHeight = 48.dp,
                        ) { option, selected ->
                            ConnectedChoiceLabel(
                                text = option.label,
                                selected = selected,
                                icon = HugeIcons.Tick01,
                            )
                        }
                    }

                    // DSL 工具栏（双行可横滑）
                    DslToolbar(
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        taskCount = inspection.taskCount,
                        eventCount = inspection.eventCount,
                        onInsertStartPage = { preset -> applyInsert(insertLinkSnippet(editorValue, preset)) },
                        onInsertTask = { applyInsert(insertTaskSnippet(editorValue)) },
                        onInsertEvent = { applyInsert(insertEventSnippet(editorValue)) },
                        onInsertAct = { applyInsert(insertActSnippet(editorValue)) },
                        onInsertLoop = { tpl -> applyInsert(insertLoopSnippet(editorValue, tpl)) },
                        onInsertVar = { applyInsert(insertVariableDeclSnippet(editorValue)) },
                        onOpenVarRef = { showVariableSheet = true },
                        onInsertMessage = { applyInsert(insertMessageBlockSnippet(editorValue)) },
                        onInsertWait = { applyInsert(insertWaitSnippet(editorValue)) },
                        onInsertLink = { applyInsert(insertLinkSnippet(editorValue, "<>")) },
                        onUndo = ::undo,
                        onRedo = ::redo,
                        onFormat = {
                            val formatted = formatWorkflowDsl(editorValue.text)
                            if (formatted != editorValue.text) {
                                applyEdit(TextFieldValue(formatted, TextRange(formatted.length)))
                                feedbackMessage = "已格式化缩进"
                            } else {
                                feedbackMessage = "缩进已是最新"
                            }
                        },
                        onDuplicate = { applyInsert(duplicateCurrentLine(editorValue)) },
                        onOpenOutline = { showOutlineSheet = true },
                    )

                    AnimatedVisibility(visible = feedbackMessage != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = feedbackMessage.orEmpty(),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }

                    // 语法高亮代码编辑区
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        tonalElevation = 1.dp,
                    ) {
                        val scrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(14.dp),
                        ) {
                            BasicTextField(
                                value = editorValue,
                                onValueChange = { next ->
                                    applyEdit(next, recordHistory = true)
                                },
                                modifier = Modifier.fillMaxSize(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                visualTransformation = visualTransformation,
                                decorationBox = { innerTextField ->
                                    if (editorValue.text.isEmpty()) {
                                        Text(
                                            text = "在这里编写 AnyWorkflow DSL…",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    innerTextField()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showVariableSheet) {
        ModalBottomSheet(onDismissRequest = { showVariableSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("插入变量引用", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "选择当前计划中已声明的变量，将在光标处插入 %变量名%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CardGroup {
                    inspection.variables.forEach { varName ->
                        item(
                            onClick = {
                                showVariableSheet = false
                                val ref = "%$varName%"
                                applyInsert(insertTextAtCursor(editorValue, ref, ref.length))
                            },
                            headlineContent = {
                                Text(
                                    text = "%$varName%",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            supportingContent = {
                                Text(if (varName == "index") "内置循环序号（从 1 开始）" else "自定义变量 / 循环变量")
                            },
                        )
                    }
                }
            }
        }
    }

    if (showOutlineSheet) {
        ModalBottomSheet(onDismissRequest = { showOutlineSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("结构大纲", style = MaterialTheme.typography.titleLarge)
                if (inspection.outline.isEmpty()) {
                    Text(
                        text = "尚未检测到 @task 或 @event 块",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    CardGroup {
                        inspection.outline.forEach { item ->
                            item(
                                onClick = {
                                    showOutlineSheet = false
                                    val targetOffset = item.offset.coerceIn(0, editorValue.text.length)
                                    editorValue = editorValue.copy(selection = TextRange(targetOffset))
                                },
                                headlineContent = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (item.kind == DslOutlineKind.TASK) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            },
                                        ) {
                                            Text(
                                                text = item.kind.label,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                },
                                trailingContent = {
                                    Text(
                                        text = "L${item.line}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDiagnosticsSheet) {
        ModalBottomSheet(onDismissRequest = { showDiagnosticsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("语法检查", style = MaterialTheme.typography.titleLarge)
                if (!inspection.hasErrors) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(HugeIcons.Tick01, contentDescription = null)
                            Text("语法结构完整，共 ${inspection.taskCount} 个 Task、${inspection.eventCount} 个 Event。")
                        }
                    }
                } else {
                    CardGroup {
                        inspection.diagnostics.forEach { diag ->
                            item(
                                headlineContent = { Text(diag.message) },
                                supportingContent = { Text("第 ${diag.line} 行") },
                                leadingContent = {
                                    Icon(
                                        HugeIcons.Alert01,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showHelpSheet) {
        ModalBottomSheet(onDismissRequest = { showHelpSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AnyWorkflow DSL 速查", style = MaterialTheme.typography.titleLarge)
                CardGroup {
                    item(
                        headlineContent = { Text("@run=名称 / @mode=serial|parallel") },
                        supportingContent = { Text("定义工作流标题与执行模式（并行可加 @maxConcurrency=4）") },
                    )
                    item(
                        headlineContent = { Text("@task 标题 { ... }") },
                        supportingContent = { Text("一个独立任务单元，内部包含 @let 变量与 @event") },
                    )
                    item(
                        headlineContent = { Text("@event 标题 { ... }") },
                        supportingContent = { Text("打开起始页 <https://...> 并按顺序发送 { 消息块 }") },
                    )
                    item(
                        headlineContent = { Text("@let name=值  与  %name%") },
                        supportingContent = { Text("声明变量并在任意位置通过 %name% 引用") },
                    )
                    item(
                        headlineContent = { Text("@for i in range(1, 3) { ... }") },
                        supportingContent = { Text("支持数字区间、列表 [a, b] 及元组 [(1, 甲)] 循环，内置 %index%") },
                    )
                    item(
                        headlineContent = { Text("<sleep(秒数)>") },
                        supportingContent = { Text("发送下一条消息前等待指定秒数") },
                    )
                }
            }
        }
    }
}

@Composable
private fun DslToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    taskCount: Int,
    eventCount: Int,
    onInsertStartPage: (String) -> Unit,
    onInsertTask: () -> Unit,
    onInsertEvent: () -> Unit,
    onInsertAct: () -> Unit,
    onInsertLoop: (DslLoopTemplate) -> Unit,
    onInsertVar: () -> Unit,
    onOpenVarRef: () -> Unit,
    onInsertMessage: () -> Unit,
    onInsertWait: () -> Unit,
    onInsertLink: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormat: () -> Unit,
    onDuplicate: () -> Unit,
    onOpenOutline: () -> Unit,
) {
    var startPageMenuOpen by remember { mutableStateOf(false) }
    var loopMenuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            // 第一行：语法块快捷插入
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    ToolbarChip(label = "起始页 ▾", icon = HugeIcons.Link01, onClick = { startPageMenuOpen = true })
                    DropdownMenu(
                        expanded = startPageMenuOpen,
                        onDismissRequest = { startPageMenuOpen = false },
                    ) {
                        DEFAULT_START_PAGE_PRESETS.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = {
                                    startPageMenuOpen = false
                                    onInsertStartPage(preset.url)
                                },
                            )
                        }
                    }
                }
                ToolbarChip(label = "Task", icon = HugeIcons.Add01, onClick = onInsertTask)
                ToolbarChip(label = "Event", icon = HugeIcons.Add01, onClick = onInsertEvent)
                ToolbarChip(label = "Act", icon = HugeIcons.Add01, onClick = onInsertAct)
                Box {
                    ToolbarChip(label = "循环 ▾", icon = HugeIcons.Refresh03, onClick = { loopMenuOpen = true })
                    DropdownMenu(
                        expanded = loopMenuOpen,
                        onDismissRequest = { loopMenuOpen = false },
                    ) {
                        DslLoopTemplate.entries.forEach { tpl ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(tpl.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            tpl.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    loopMenuOpen = false
                                    onInsertLoop(tpl)
                                },
                            )
                        }
                    }
                }
                ToolbarChip(label = "变量", icon = HugeIcons.Add01, onClick = onInsertVar)
                ToolbarChip(label = "%引用%", icon = HugeIcons.Code, onClick = onOpenVarRef)
                ToolbarChip(label = "{ 消息 }", icon = null, onClick = onInsertMessage)
                ToolbarChip(label = "<sleep>", icon = null, onClick = onInsertWait)
                ToolbarChip(label = "<链接>", icon = null, onClick = onInsertLink)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 第二行：编辑与结构工具
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarChip(label = "撤销", icon = HugeIcons.ArrowTurnBackward, enabled = canUndo, onClick = onUndo)
                ToolbarChip(label = "重做", icon = null, enabled = canRedo, onClick = onRedo)
                ToolbarChip(label = "格式化", icon = HugeIcons.Code, onClick = onFormat)
                ToolbarChip(label = "复制行", icon = HugeIcons.Copy01, onClick = onDuplicate)
                ToolbarChip(label = "大纲 ($taskCount/$eventCount)", icon = HugeIcons.CheckList, onClick = onOpenOutline)
            }
        }
    }
}

@Composable
private fun ToolbarChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

private fun parseEditorMode(planText: String): DispatchExecutionMode {
    val regex = Regex("""^\s*@mode\s*=\s*(serial|parallel)\s*$""", RegexOption.IGNORE_CASE)
    for (line in planText.lineSequence()) {
        regex.matchEntire(line)?.let { m ->
            return DispatchExecutionMode.fromWire(m.groupValues[1].lowercase())
        }
    }
    return DispatchExecutionMode.SERIAL
}
