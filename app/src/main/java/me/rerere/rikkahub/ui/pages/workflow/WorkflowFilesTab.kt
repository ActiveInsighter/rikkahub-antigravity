package me.rerere.rikkahub.ui.pages.workflow

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Link01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.workflow.LibraryFile
import me.rerere.rikkahub.data.workflow.LibraryFileFolder
import me.rerere.rikkahub.data.workflow.LibraryFileSort
import me.rerere.rikkahub.data.workflow.LibraryFileTypeFilter
import me.rerere.rikkahub.ui.components.richtext.MaterialMarkdown
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.RikkaConfirmDialog

@Composable
fun WorkflowFilesTab(
    state: WorkflowFilesUiState,
    bottomPadding: Dp,
    onRefresh: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onTypeFilterChange: (LibraryFileTypeFilter) -> Unit,
    onSortChange: (LibraryFileSort) -> Unit,
    onUploadUri: (Context, Uri) -> Unit,
    onOpenPreview: (LibraryFile) -> Unit,
    onClosePreview: () -> Unit,
    onRenameFile: (LibraryFile, String) -> Unit,
    onMoveFile: (LibraryFile, String) -> Unit,
    onDeleteFile: (LibraryFile) -> Unit,
    onCreateFolder: (name: String, parentId: String) -> Unit,
    onRenameFolder: (LibraryFileFolder, String) -> Unit,
    onDeleteFolder: (LibraryFileFolder) -> Unit,
    onUseFileInRun: (LibraryFile) -> Unit,
    onClearError: () -> Unit,
    onClearNotice: () -> Unit,
    pendingUploadTrigger: Boolean,
    onConsumeUploadTrigger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    var localNotice by remember { mutableStateOf<String?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<LibraryFileFolder?>(null) }
    var deletingFolder by remember { mutableStateOf<LibraryFileFolder?>(null) }
    var pdfDetailFile by remember { mutableStateOf<LibraryFile?>(null) }
    var renamingFile by remember { mutableStateOf<LibraryFile?>(null) }
    var movingFile by remember { mutableStateOf<LibraryFile?>(null) }
    var deletingFile by remember { mutableStateOf<LibraryFile?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onUploadUri(context, uri)
        }
    }

    LaunchedEffect(pendingUploadTrigger) {
        if (pendingUploadTrigger) {
            onConsumeUploadTrigger()
            filePicker.launch(arrayOf("application/pdf", "text/markdown", "text/plain", "*/*"))
        }
    }

    val folderById = remember(state.drive.folders) { state.drive.folders.associateBy { it.id } }
    val currentFolderId = state.currentFolderId.takeIf { it.isEmpty() || it in folderById } ?: ""

    val breadcrumbs = remember(state.drive.folders, currentFolderId) {
        buildList {
            var cursor = currentFolderId
            val seen = mutableSetOf<String>()
            while (cursor.isNotBlank() && seen.add(cursor)) {
                val folder = folderById[cursor] ?: break
                add(0, folder)
                cursor = folder.parent
            }
        }
    }

    val query = state.searchQuery.trim().lowercase()
    val visibleFolders = remember(state.drive.folders, currentFolderId, query) {
        state.drive.folders.filter { folder ->
            if (query.isNotEmpty()) {
                folder.name.lowercase().contains(query)
            } else {
                folder.parent == currentFolderId
            }
        }.sortedBy { it.name.lowercase() }
    }

    val visibleFiles = remember(state.drive.files, currentFolderId, query, state.typeFilter, state.sort) {
        val filtered = state.drive.files.filter { file ->
            val matchesLocation = if (query.isNotEmpty()) {
                file.fileName.lowercase().contains(query)
            } else {
                file.folder == currentFolderId
            }
            val matchesType = when (state.typeFilter) {
                LibraryFileTypeFilter.ALL -> true
                LibraryFileTypeFilter.MARKDOWN -> file.isMarkdown
                LibraryFileTypeFilter.PDF -> file.isPdf
            }
            matchesLocation && matchesType
        }
        when (state.sort) {
            LibraryFileSort.UPDATED_DESC -> filtered.sortedByDescending { it.updatedAt }
            LibraryFileSort.NAME_ASC -> filtered.sortedBy { it.fileName.lowercase() }
            LibraryFileSort.SIZE_DESC -> filtered.sortedByDescending { it.size }
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        if (state.isLoading && state.drive.files.isEmpty() && state.drive.folders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ContainedLoadingIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomPadding + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = { Text("搜索资料库文件或目录") },
                            leadingIcon = {
                                Icon(HugeIcons.Search01, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(HugeIcons.Cancel01, contentDescription = "清除", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = CircleShape,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(
                            onClick = { showCreateFolderDialog = true },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(HugeIcons.Folder01, contentDescription = "新建目录")
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            LibraryFileTypeFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = state.typeFilter == filter,
                                    onClick = { onTypeFilterChange(filter) },
                                    label = { Text(filter.label) },
                                    shape = CircleShape,
                                )
                            }
                        }

                        Box {
                            Surface(
                                onClick = { sortMenuExpanded = true },
                                shape = CircleShape,
                                color = CustomColors.listItemColors.containerColor,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(state.sort.label, style = MaterialTheme.typography.labelMedium)
                                    Icon(HugeIcons.ArrowDown01, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                LibraryFileSort.entries.forEach { sortOption ->
                                    DropdownMenuItem(
                                        text = { Text(sortOption.label) },
                                        trailingIcon = if (state.sort == sortOption) {
                                            { Icon(HugeIcons.Tick01, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        onClick = {
                                            sortMenuExpanded = false
                                            onSortChange(sortOption)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (query.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                onClick = { onOpenFolder("") },
                                shape = CircleShape,
                                color = if (currentFolderId.isEmpty()) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    CustomColors.listItemColors.containerColor
                                },
                            ) {
                                Text(
                                    text = "资料库",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            breadcrumbs.forEachIndexed { idx, folder ->
                                Icon(
                                    imageVector = HugeIcons.ArrowRight01,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val isLast = idx == breadcrumbs.lastIndex
                                Surface(
                                    onClick = { onOpenFolder(folder.id) },
                                    shape = CircleShape,
                                    color = if (isLast) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        CustomColors.listItemColors.containerColor
                                    },
                                ) {
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.isUploading) {
                    item(key = "upload_progress") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
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
                                        text = state.uploadingFileName?.let { "正在上传「$it」…" } ?: "正在准备上传…",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    state.uploadProgress?.let { pct ->
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "$pct%",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                                val pct = state.uploadProgress
                                if (pct != null) {
                                    LinearWavyProgressIndicator(
                                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }

                if (!state.errorMessage.isNullOrBlank()) {
                    item(key = "files_error") {
                        WorkflowErrorBanner(
                            message = state.errorMessage,
                            onDismiss = onClearError,
                        )
                    }
                }
                val notice = localNotice ?: state.noticeMessage
                if (!notice.isNullOrBlank()) {
                    item(key = "files_notice") {
                        WorkflowNoticeBanner(
                            message = notice,
                            onDismiss = {
                                localNotice = null
                                onClearNotice()
                            },
                        )
                    }
                }

                if (visibleFolders.isNotEmpty()) {
                    items(visibleFolders, key = { "file_folder_${it.id}" }) { folder ->
                        FileFolderRowCard(
                            folder = folder,
                            onOpen = { onOpenFolder(folder.id) },
                            onRename = { renamingFolder = folder },
                            onDelete = { deletingFolder = folder },
                        )
                    }
                }

                if (visibleFiles.isEmpty() && visibleFolders.isEmpty()) {
                    item(key = "files_empty") {
                        WorkflowEmptyState(
                            icon = HugeIcons.FileImport,
                            title = if (query.isNotEmpty()) "未找到匹配的文件" else "当前目录暂无文件",
                            subtitle = if (query.isNotEmpty()) {
                                "试试调整搜索关键词或类型筛选。"
                            } else {
                                "点击右下角按钮上传 .md 或 .pdf 文件到云端资料库。"
                            },
                            actionLabel = if (query.isEmpty()) "上传文件" else null,
                            onAction = if (query.isEmpty()) {
                                { filePicker.launch(arrayOf("application/pdf", "text/markdown", "text/plain", "*/*")) }
                            } else null,
                        )
                    }
                } else {
                    items(visibleFiles, key = { "file_${it.id}" }) { file ->
                        LibraryFileExpressiveCard(
                            file = file,
                            onClick = {
                                if (file.isMarkdown) {
                                    onOpenPreview(file)
                                } else {
                                    pdfDetailFile = file
                                }
                            },
                            onCopyLink = {
                                clipboard.setText(AnnotatedString(file.sourceUrl))
                                localNotice = "已复制「${file.fileName}」链接"
                            },
                            onUseInRun = { onUseFileInRun(file) },
                            onRename = { renamingFile = file },
                            onMove = { movingFile = file },
                            onDelete = { deletingFile = file },
                        )
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        WorkflowTextInputDialog(
            title = "新建资料目录",
            label = "目录名称",
            placeholder = "例如：参考资料",
            confirmLabel = "创建",
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                showCreateFolderDialog = false
                onCreateFolder(name, currentFolderId)
            },
        )
    }

    renamingFolder?.let { folder ->
        WorkflowTextInputDialog(
            title = "重命名目录",
            label = "目录名称",
            initialValue = folder.name,
            confirmLabel = "保存",
            onDismiss = { renamingFolder = null },
            onConfirm = { name ->
                renamingFolder = null
                onRenameFolder(folder, name)
            },
        )
    }

    deletingFolder?.let { folder ->
        RikkaConfirmDialog(
            show = true,
            title = "删除目录「${folder.name}」？",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                deletingFolder = null
                onDeleteFolder(folder)
            },
            onDismiss = { deletingFolder = null },
        ) {
            Text("确定要删除此目录吗？")
        }
    }

    renamingFile?.let { file ->
        WorkflowTextInputDialog(
            title = "重命名文件",
            label = "文件名",
            initialValue = file.fileName,
            confirmLabel = "保存",
            onDismiss = { renamingFile = null },
            onConfirm = { name ->
                renamingFile = null
                onRenameFile(file, name)
            },
        )
    }

    movingFile?.let { file ->
        FileFolderPickerSheet(
            folders = state.drive.folders,
            currentFolderId = file.folder,
            onDismiss = { movingFile = null },
            onSelect = { targetFolderId ->
                movingFile = null
                onMoveFile(file, targetFolderId)
            },
        )
    }

    deletingFile?.let { file ->
        RikkaConfirmDialog(
            show = true,
            title = "删除文件「${file.fileName}」？",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                deletingFile = null
                onDeleteFile(file)
            },
            onDismiss = { deletingFile = null },
        ) {
            Text("删除后文件将从资料库移除，无法恢复。")
        }
    }

    state.previewFile?.let { file ->
        MarkdownPreviewDialog(
            file = file,
            content = state.previewContent,
            isLoading = state.isLoadingPreview,
            errorMessage = state.previewError,
            onDismiss = onClosePreview,
            onCopyContent = { text ->
                clipboard.setText(AnnotatedString(text))
                localNotice = "已复制 Markdown 内容"
            },
            onUseInRun = {
                onClosePreview()
                onUseFileInRun(file)
            },
        )
    }

    pdfDetailFile?.let { file ->
        PdfFileDetailSheet(
            file = file,
            onDismiss = { pdfDetailFile = null },
            onOpenInBrowser = {
                pdfDetailFile = null
                runCatching { uriHandler.openUri(file.sourceUrl) }
            },
            onCopyLink = {
                clipboard.setText(AnnotatedString(file.sourceUrl))
                localNotice = "已复制「${file.fileName}」链接"
                pdfDetailFile = null
            },
            onUseInRun = {
                pdfDetailFile = null
                onUseFileInRun(file)
            },
            onRename = {
                pdfDetailFile = null
                renamingFile = file
            },
            onMove = {
                pdfDetailFile = null
                movingFile = file
            },
            onDelete = {
                pdfDetailFile = null
                deletingFile = file
            },
        )
    }
}

@Composable
private fun FileFolderRowCard(
    folder: LibraryFileFolder,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = CustomColors.listItemColors.containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HugeIcons.Folder01,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(HugeIcons.MoreVertical, contentDescription = "目录操作", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(HugeIcons.Edit03, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除目录", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryFileExpressiveCard(
    file: LibraryFile,
    onClick: () -> Unit,
    onCopyLink: () -> Unit,
    onUseInRun: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val iconContainer = if (file.isMarkdown) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val iconTint = if (file.isMarkdown) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = CustomColors.listItemColors.containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(iconContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (file.isMarkdown) HugeIcons.File02 else HugeIcons.Book02,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.titleSmall,
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
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(HugeIcons.MoreVertical, contentDescription = "文件操作", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (file.isMarkdown) {
                        DropdownMenuItem(
                            text = { Text("预览 Markdown") },
                            leadingIcon = { Icon(HugeIcons.File02, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("用此文件新建 Run") },
                        leadingIcon = { Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onUseInRun()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("复制文件链接") },
                        leadingIcon = { Icon(HugeIcons.Link01, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onCopyLink()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(HugeIcons.Edit03, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("移动到目录") },
                        leadingIcon = { Icon(HugeIcons.Folder01, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onMove()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除文件", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileFolderPickerSheet(
    folders: List<LibraryFileFolder>,
    currentFolderId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "移动到资料库目录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                onClick = { onSelect("") },
                shape = RoundedCornerShape(14.dp),
                color = if (currentFolderId.isEmpty()) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    CustomColors.listItemColors.containerColor
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("根目录", style = MaterialTheme.typography.bodyMedium)
                    if (currentFolderId.isEmpty()) {
                        Icon(HugeIcons.Tick01, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                folders.sortedBy { it.name.lowercase() }.forEach { folder ->
                    val selected = folder.id == currentFolderId
                    Surface(
                        onClick = { onSelect(folder.id) },
                        shape = RoundedCornerShape(14.dp),
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(HugeIcons.Folder01, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(folder.name, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (selected) {
                                Icon(HugeIcons.Tick01, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MarkdownPreviewDialog(
    file: LibraryFile,
    content: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onCopyContent: (String) -> Unit,
    onUseInRun: () -> Unit,
) {
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
                            Text(
                                text = file.fileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(HugeIcons.Cancel01, contentDescription = "关闭")
                            }
                        },
                        actions = {
                            if (!content.isNullOrEmpty()) {
                                IconButton(onClick = { onCopyContent(content) }) {
                                    Icon(HugeIcons.Copy01, contentDescription = "复制内容")
                                }
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
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("关闭")
                            }
                            Button(
                                onClick = onUseInRun,
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("用此文件新建 Run")
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                ) {
                    when {
                        isLoading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ContainedLoadingIndicator()
                            }
                        }
                        !errorMessage.isNullOrBlank() -> {
                            WorkflowErrorBanner(
                                message = errorMessage,
                                onDismiss = onDismiss,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        content != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 12.dp),
                            ) {
                                MaterialMarkdown(content = content)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfFileDetailSheet(
    file: LibraryFile,
    onDismiss: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onCopyLink: () -> Unit,
    onUseInRun: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.File02,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatByteSize(file.size)} · 更新于 ${formatWorkflowTime(file.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenInBrowser,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(HugeIcons.Link01, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("浏览器打开")
                }
                Button(
                    onClick = onUseInRun,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建 Run")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onCopyLink,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(HugeIcons.Copy01, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制链接")
                }
                FilledTonalButton(
                    onClick = onMove,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(HugeIcons.Folder01, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("移动")
                }
                FilledTonalButton(
                    onClick = onRename,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(HugeIcons.Edit03, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重命名")
                }
            }

            OutlinedButton(
                onClick = onDelete,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    HugeIcons.Delete01,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("删除文件", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
