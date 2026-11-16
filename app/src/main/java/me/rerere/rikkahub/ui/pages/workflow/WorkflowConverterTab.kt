package me.rerere.rikkahub.ui.pages.workflow

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CloudUpload
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Link01
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.SlidersHorizontal
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.workflow.DEFAULT_PDF_SVG_PROMPT
import me.rerere.rikkahub.data.workflow.DEFAULT_PDF_SYSTEM_PROMPT
import me.rerere.rikkahub.data.workflow.FileConversionConfig
import me.rerere.rikkahub.data.workflow.LibraryFile
import me.rerere.rikkahub.data.workflow.PdfConversionOptions
import me.rerere.rikkahub.data.workflow.PdfConversionProvider
import me.rerere.rikkahub.data.workflow.PdfJob
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.theme.CustomColors

private enum class PdfSourceMode(val label: String) {
    UPLOAD("上传 PDF"),
    LIBRARY("资料库"),
    LINK("PDF 链接"),
}

@Composable
internal fun WorkflowConverterTab(
    state: WorkflowConverterUiState,
    libraryFiles: List<LibraryFile>,
    bottomPadding: Dp,
    onRefresh: () -> Unit,
    onPageChange: (Int) -> Unit,
    onUploadPdf: (Context, Uri) -> Unit,
    onClearUploadedPdf: () -> Unit,
    onCreateJob: (
        title: String,
        sourceUrl: String,
        outputName: String,
        prompt: String,
        startPage: Int?,
        endPage: Int?,
        adjacentPages: Boolean,
        onSuccess: () -> Unit,
    ) -> Unit,
    onCancelJob: (PdfJob) -> Unit,
    onRerunJob: (PdfJob) -> Unit,
    onRequestDownload: (PdfJob, (String) -> Unit) -> Unit,
    onSaveConfig: (PdfConversionOptions, String, () -> Unit) -> Unit,
    onClearError: () -> Unit,
    onClearNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var sourceMode by remember { mutableStateOf(PdfSourceMode.UPLOAD) }
    var selectedLibraryFile by remember { mutableStateOf<LibraryFile?>(null) }
    var sourceUrlInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var outputNameInput by remember { mutableStateOf("") }
    var startPageInput by remember { mutableStateOf("") }
    var endPageInput by remember { mutableStateOf("") }
    var adjacentPages by remember { mutableStateOf(false) }
    var taskPromptInput by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    var showConfigSheet by remember { mutableStateOf(false) }
    var showLibraryPicker by remember { mutableStateOf(false) }
    var detailJob by remember { mutableStateOf<PdfJob?>(null) }

    LaunchedEffect(state.preselectedLibraryFile) {
        val pre = state.preselectedLibraryFile
        if (pre != null) {
            sourceMode = PdfSourceMode.LIBRARY
            selectedLibraryFile = pre
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onUploadPdf(context, uri)
        }
    }

    val pdfLibraryItems = remember(libraryFiles) { libraryFiles.filter { it.isPdf } }

    val activeSourceFile = when (sourceMode) {
        PdfSourceMode.UPLOAD -> state.uploadedLibraryFile
        PdfSourceMode.LIBRARY -> selectedLibraryFile
        PdfSourceMode.LINK -> null
    }
    val suggestedTitle = when (sourceMode) {
        PdfSourceMode.UPLOAD -> state.uploadedLibraryFile?.fileName?.substringBeforeLast('.').orEmpty()
        PdfSourceMode.LIBRARY -> selectedLibraryFile?.fileName?.substringBeforeLast('.').orEmpty()
        PdfSourceMode.LINK -> sourceUrlInput.trim()
            .substringAfterLast('/')
            .substringBefore('?')
            .substringBeforeLast('.')
            .take(80)
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshingJobs,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomPadding + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val combinedError = localError ?: state.errorMessage ?: state.configError
            if (!combinedError.isNullOrBlank()) {
                item(key = "converter_error") {
                    WorkflowErrorBanner(
                        message = combinedError,
                        onDismiss = {
                            localError = null
                            onClearError()
                        },
                    )
                }
            }
            if (!state.noticeMessage.isNullOrBlank()) {
                item(key = "converter_notice") {
                    WorkflowNoticeBanner(
                        message = state.noticeMessage,
                        onDismiss = onClearNotice,
                    )
                }
            }

            item(key = "config_summary") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = CustomColors.listItemColors.containerColor,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "当前转换配置",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = when {
                                    state.isLoadingConfig -> "正在加载转换配置…"
                                    state.config != null ->
                                        "v${state.config.revision} · ${state.config.options.model} · ${state.config.options.dpi} DPI"
                                    else -> "转换配置未加载"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        FilledTonalButton(
                            onClick = { showConfigSheet = true },
                            enabled = state.config != null,
                            shape = CircleShape,
                        ) {
                            Icon(HugeIcons.SlidersHorizontal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("参数")
                        }
                    }
                }
            }

            item(key = "composer_card") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = CustomColors.listItemColors.containerColor,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "新建 PDF → Markdown 转换",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )

                        ConnectedChoiceButtons(
                            options = PdfSourceMode.entries,
                            selected = sourceMode,
                            onSelect = { mode ->
                                localError = null
                                sourceMode = mode
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = 42.dp,
                        ) { option, selected ->
                            ConnectedChoiceLabel(
                                text = option.label,
                                selected = selected,
                                icon = HugeIcons.Tick01,
                            )
                        }

                        when (sourceMode) {
                            PdfSourceMode.UPLOAD -> {
                                if (state.isUploadingPdf) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    text = state.uploadingFileName?.let { "正在上传「$it」…" } ?: "准备上传…",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                state.uploadProgress?.let { pct ->
                                                    Text("$pct%", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                            val pct = state.uploadProgress ?: 0
                                            LinearWavyProgressIndicator(
                                                progress = { (pct / 100f).coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                } else if (activeSourceFile != null) {
                                    SelectedSourceBadge(
                                        file = activeSourceFile,
                                        onClear = onClearUploadedPdf,
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                                        shape = CircleShape,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(HugeIcons.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("选择本地 PDF 文件（最大 2 GiB）")
                                    }
                                }
                            }

                            PdfSourceMode.LIBRARY -> {
                                if (activeSourceFile != null) {
                                    SelectedSourceBadge(
                                        file = activeSourceFile,
                                        onClear = { selectedLibraryFile = null },
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showLibraryPicker = true },
                                    shape = CircleShape,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(HugeIcons.Folder01, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (activeSourceFile == null) "从资料库选择 PDF" else "更换资料库 PDF")
                                }
                            }

                            PdfSourceMode.LINK -> {
                                OutlinedTextField(
                                    value = sourceUrlInput,
                                    onValueChange = { sourceUrlInput = it },
                                    label = { Text("PDF 下载链接") },
                                    placeholder = { Text("https://example.com/document.pdf") },
                                    leadingIcon = {
                                        Icon(HugeIcons.Link01, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            label = { Text("任务名称") },
                            placeholder = {
                                Text(suggestedTitle.ifBlank { "留空则自动使用文件名" })
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showAdvanced = !showAdvanced }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "高级选项（页码范围、相邻页、附加提示词）",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                imageVector = if (showAdvanced) HugeIcons.ArrowDown01 else HugeIcons.ArrowRight01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        AnimatedVisibility(visible = showAdvanced) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = outputNameInput,
                                    onValueChange = { outputNameInput = it },
                                    label = { Text("产物文件名（可选）") },
                                    placeholder = { Text("不含 .zip / .md 后缀") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    OutlinedTextField(
                                        value = startPageInput,
                                        onValueChange = { startPageInput = it.filter(Char::isDigit) },
                                        label = { Text("起始页") },
                                        placeholder = { Text("1") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = endPageInput,
                                        onValueChange = { endPageInput = it.filter(Char::isDigit) },
                                        label = { Text("结束页") },
                                        placeholder = { Text("末页") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("附带相邻页边缘截图", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "跨页断句或跨页公式时有助于上下文衔接",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = adjacentPages,
                                        onCheckedChange = { adjacentPages = it },
                                    )
                                }
                                OutlinedTextField(
                                    value = taskPromptInput,
                                    onValueChange = { taskPromptInput = it },
                                    label = { Text("本次附加提示词（可选）") },
                                    placeholder = { Text("例如：保留英文专业术语，跳过参考文献") },
                                    minLines = 2,
                                    maxLines = 4,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val resolvedUrl = when (sourceMode) {
                                    PdfSourceMode.UPLOAD -> state.uploadedLibraryFile?.sourceUrl.orEmpty()
                                    PdfSourceMode.LIBRARY -> selectedLibraryFile?.sourceUrl.orEmpty()
                                    PdfSourceMode.LINK -> sourceUrlInput.trim()
                                }
                                if (resolvedUrl.isEmpty()) {
                                    localError = when (sourceMode) {
                                        PdfSourceMode.UPLOAD -> "请先上传 PDF 文件"
                                        PdfSourceMode.LIBRARY -> "请先从资料库选择 PDF"
                                        PdfSourceMode.LINK -> "请输入 PDF 链接"
                                    }
                                    return@Button
                                }
                                val finalTitle = titleInput.trim().ifEmpty { suggestedTitle }
                                if (finalTitle.isEmpty()) {
                                    localError = "请填写任务名称"
                                    return@Button
                                }
                                val sp = startPageInput.toIntOrNull()
                                val ep = endPageInput.toIntOrNull()
                                if (sp != null && ep != null && ep < sp) {
                                    localError = "结束页不能小于起始页"
                                    return@Button
                                }
                                localError = null
                                onCreateJob(
                                    finalTitle,
                                    resolvedUrl,
                                    outputNameInput.trim(),
                                    taskPromptInput.trim(),
                                    sp,
                                    ep,
                                    adjacentPages,
                                ) {
                                    titleInput = ""
                                    outputNameInput = ""
                                    startPageInput = ""
                                    endPageInput = ""
                                    adjacentPages = false
                                    taskPromptInput = ""
                                    sourceUrlInput = ""
                                    selectedLibraryFile = null
                                }
                            },
                            enabled = !state.isCreatingJob && !state.isUploadingPdf && state.config != null,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            if (state.isCreatingJob) {
                                LoadingIndicator(modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("提交中…")
                            } else {
                                Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("开始转换")
                            }
                        }
                    }
                }
            }

            item(key = "jobs_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "转换记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "共 ${state.totalItems} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.isLoadingJobs && state.jobs.isEmpty()) {
                item(key = "jobs_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
            } else if (state.jobs.isEmpty()) {
                item(key = "jobs_empty") {
                    WorkflowEmptyState(
                        icon = HugeIcons.File02,
                        title = "还没有转换记录",
                        subtitle = "上传 PDF、从资料库选择或粘贴链接即可开始转换。",
                    )
                }
            } else {
                items(state.jobs, key = { "pdf_job_${it.id}" }) { job ->
                    PdfJobExpressiveCard(
                        job = job,
                        busy = state.isMutatingJob,
                        onOpen = { detailJob = job },
                        onCancel = { onCancelJob(job) },
                        onRerun = { onRerunJob(job) },
                        onDownload = {
                            onRequestDownload(job) { url ->
                                runCatching { uriHandler.openUri(url) }
                            }
                        },
                    )
                }

                if (state.totalPages > 1) {
                    item(key = "jobs_pager") {
                        WorkflowPagerBar(
                            page = state.page,
                            totalPages = state.totalPages,
                            totalItems = state.totalItems,
                            enabled = !state.isRefreshingJobs,
                            onPageChange = onPageChange,
                        )
                    }
                }
            }
        }
    }

    if (showLibraryPicker) {
        LibraryPdfPickerSheet(
            files = pdfLibraryItems,
            selectedId = selectedLibraryFile?.id,
            onDismiss = { showLibraryPicker = false },
            onSelect = { file ->
                selectedLibraryFile = file
                showLibraryPicker = false
            },
        )
    }

    if (showConfigSheet && state.config != null) {
        ConversionConfigDialog(
            config = state.config,
            isSaving = state.isSavingConfig,
            errorMessage = state.configError,
            onDismiss = { showConfigSheet = false },
            onSave = { options, prompt ->
                onSaveConfig(options, prompt) { showConfigSheet = false }
            },
        )
    }

    detailJob?.let { job ->
        val latest = state.jobs.firstOrNull { it.id == job.id } ?: job
        ConversionJobDetailSheet(
            job = latest,
            busy = state.isMutatingJob,
            onDismiss = { detailJob = null },
            onOpenActions = { url -> runCatching { uriHandler.openUri(url) } },
            onCancel = { onCancelJob(latest) },
            onRerun = { onRerunJob(latest) },
            onDownload = {
                onRequestDownload(latest) { url ->
                    runCatching { uriHandler.openUri(url) }
                }
            },
        )
    }
}

@Composable
private fun SelectedSourceBadge(
    file: LibraryFile,
    onClear: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = HugeIcons.File02,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatByteSize(file.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(HugeIcons.Cancel01, contentDescription = "清除", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfJobExpressiveCard(
    job: PdfJob,
    busy: Boolean,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onRerun: () -> Unit,
    onDownload: () -> Unit,
) {
    val toneColors = statusToneColors(job.status.tone)
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(22.dp),
        color = CustomColors.listItemColors.containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = job.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = formatRelativeTime(job.created),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (job.pageRangeText.isNotEmpty()) {
                            Text(
                                text = "· ${job.pageRangeText}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (job.modelSummary.isNotBlank()) {
                            Text(
                                text = "· ${job.modelSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                StatusBadgePill(label = job.status.label, colors = toneColors)
            }

            if (job.error.isNotBlank()) {
                Text(
                    text = job.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (job.status.isActive) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !busy && !job.cancelRequested,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(HugeIcons.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (job.cancelRequested) "取消中…" else "取消")
                    }
                }
                if (job.status.isTerminal) {
                    FilledTonalButton(
                        onClick = onRerun,
                        enabled = !busy,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(HugeIcons.Refresh03, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重跑")
                    }
                }
                if (job.file.isNotBlank()) {
                    Button(
                        onClick = onDownload,
                        enabled = !busy,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(HugeIcons.Download01, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (job.fileSize > 0L) "下载 (${formatByteSize(job.fileSize)})" else "下载")
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryPdfPickerSheet(
    files: List<LibraryFile>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (LibraryFile) -> Unit,
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
            Text(
                text = "选择资料库中的 PDF",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (files.isEmpty()) {
                WorkflowEmptyState(
                    icon = HugeIcons.File02,
                    title = "资料库中暂无 PDF",
                    subtitle = "请先在「资料库」或上方「上传 PDF」中上传文件。",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(files, key = { it.id }) { file ->
                        val selected = file.id == selectedId
                        Surface(
                            onClick = { onSelect(file) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                CustomColors.listItemColors.containerColor
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(HugeIcons.File02, contentDescription = null, modifier = Modifier.size(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${formatByteSize(file.size)} · ${formatWorkflowTime(file.updatedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (selected) {
                                    Icon(HugeIcons.Tick01, contentDescription = null, modifier = Modifier.size(18.dp))
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
private fun ConversionJobDetailSheet(
    job: PdfJob,
    busy: Boolean,
    onDismiss: () -> Unit,
    onOpenActions: (String) -> Unit,
    onCancel: () -> Unit,
    onRerun: () -> Unit,
    onDownload: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = job.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusBadgePill(
                    label = job.status.label,
                    colors = statusToneColors(job.status.tone),
                )
            }

            if (job.error.isNotBlank()) {
                WorkflowErrorBanner(message = job.error, onDismiss = {})
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CustomColors.listItemColors.containerColor,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DetailLine("配置版本", "v${job.configRevision}")
                    if (job.modelSummary.isNotBlank()) DetailLine("主模型", job.modelSummary)
                    job.dpiSummary?.let { DetailLine("渲染 DPI", "$it") }
                    if (job.pageRangeText.isNotEmpty()) DetailLine("页码范围", job.pageRangeText)
                    DetailLine("附带相邻页", if (job.adjacentPages) "是" else "否")
                    DetailLine("创建时间", formatWorkflowTime(job.created))
                    if (job.startedAt.isNotBlank()) DetailLine("开始时间", formatWorkflowTime(job.startedAt))
                    if (job.finishedAt.isNotBlank()) DetailLine("结束时间", formatWorkflowTime(job.finishedAt))
                }
            }

            job.githubActionsUrl?.let { url ->
                OutlinedButton(
                    onClick = { onOpenActions(url) },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(HugeIcons.Link01, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("查看 GitHub Actions 运行日志")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (job.status.isActive) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !busy && !job.cancelRequested,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (job.cancelRequested) "取消中…" else "取消任务")
                    }
                }
                if (job.status.isTerminal) {
                    FilledTonalButton(
                        onClick = onRerun,
                        enabled = !busy,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("重跑任务")
                    }
                }
                if (job.file.isNotBlank()) {
                    Button(
                        onClick = onDownload,
                        enabled = !busy,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("下载产物")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConversionConfigDialog(
    config: FileConversionConfig,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (PdfConversionOptions, String) -> Unit,
) {
    var provider by remember(config.id, config.revision) { mutableStateOf(config.options.provider) }
    var model by remember(config.id, config.revision) { mutableStateOf(config.options.model) }
    var thinkingLevel by remember(config.id, config.revision) { mutableStateOf(config.options.thinkingLevel) }
    var dpiText by remember(config.id, config.revision) { mutableStateOf(config.options.dpi.toString()) }
    var imagesPerReqText by remember(config.id, config.revision) { mutableStateOf(config.options.imagesPerRequest.toString()) }
    var concurrencyText by remember(config.id, config.revision) { mutableStateOf(config.options.concurrency.toString()) }
    var verificationText by remember(config.id, config.revision) { mutableStateOf(config.options.verificationPasses.toString()) }
    var svgEnabled by remember(config.id, config.revision) { mutableStateOf(config.options.svgEnabled) }
    var svgModel by remember(config.id, config.revision) { mutableStateOf(config.options.svgModel) }
    var systemPrompt by remember(config.id, config.revision) { mutableStateOf(config.systemPrompt) }

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
                        title = { Text("转换参数配置（v${config.revision}）") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(HugeIcons.Cancel01, contentDescription = "关闭")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    val defaults = PdfConversionOptions()
                                    provider = defaults.provider
                                    model = defaults.model
                                    thinkingLevel = defaults.thinkingLevel
                                    dpiText = defaults.dpi.toString()
                                    imagesPerReqText = defaults.imagesPerRequest.toString()
                                    concurrencyText = defaults.concurrency.toString()
                                    verificationText = defaults.verificationPasses.toString()
                                    svgEnabled = defaults.svgEnabled
                                    svgModel = defaults.svgModel
                                    systemPrompt = DEFAULT_PDF_SYSTEM_PROMPT
                                },
                            ) {
                                Text("恢复默认")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = CustomColors.topBarColors.containerColor,
                        ),
                    )
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("取消")
                            }
                            Button(
                                onClick = {
                                    val nextOptions = config.options.copy(
                                        provider = provider,
                                        model = model.trim(),
                                        thinkingLevel = thinkingLevel,
                                        dpi = dpiText.toIntOrNull() ?: config.options.dpi,
                                        imagesPerRequest = imagesPerReqText.toIntOrNull() ?: config.options.imagesPerRequest,
                                        concurrency = concurrencyText.toIntOrNull() ?: config.options.concurrency,
                                        verificationPasses = verificationText.toIntOrNull() ?: config.options.verificationPasses,
                                        svgEnabled = svgEnabled,
                                        svgModel = svgModel.trim(),
                                        svgPrompt = config.options.svgPrompt.ifBlank { DEFAULT_PDF_SVG_PROMPT },
                                    )
                                    onSave(nextOptions, systemPrompt)
                                },
                                enabled = !isSaving && model.isNotBlank(),
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (isSaving) {
                                    LoadingIndicator(modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("保存配置")
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!errorMessage.isNullOrBlank()) {
                        WorkflowErrorBanner(message = errorMessage, onDismiss = {})
                    }

                    Text("模型与推理", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    ConnectedChoiceButtons(
                        options = PdfConversionProvider.entries,
                        selected = provider,
                        onSelect = { provider = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) { option, selected ->
                        ConnectedChoiceLabel(text = option.label, selected = selected, icon = HugeIcons.Tick01)
                    }

                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("主模型 ID") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("high" to "高思考", "medium" to "中思考", "low" to "低思考", "minimal" to "最低").forEach { (key, label) ->
                            FilterChip(
                                selected = thinkingLevel == key,
                                onClick = { thinkingLevel = key },
                                label = { Text(label) },
                                shape = CircleShape,
                            )
                        }
                    }

                    HorizontalDivider()
                    Text("渲染与并发", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = dpiText,
                            onValueChange = { dpiText = it.filter(Char::isDigit) },
                            label = { Text("DPI (72-300)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = imagesPerReqText,
                            onValueChange = { imagesPerReqText = it.filter(Char::isDigit) },
                            label = { Text("每批页数 (1-20)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = concurrencyText,
                            onValueChange = { concurrencyText = it.filter(Char::isDigit) },
                            label = { Text("并发数 (1-100)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = verificationText,
                            onValueChange = { verificationText = it.filter(Char::isDigit) },
                            label = { Text("审校次数 (0-3)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("图片矢量 SVG 重绘", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "将页面中的几何图形/流程图重绘为矢量 SVG",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = svgEnabled, onCheckedChange = { svgEnabled = it })
                    }

                    if (svgEnabled) {
                        OutlinedTextField(
                            value = svgModel,
                            onValueChange = { svgModel = it },
                            label = { Text("SVG 重绘模型") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    HorizontalDivider()
                    Text("系统提示词", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        minLines = 6,
                        maxLines = 12,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
