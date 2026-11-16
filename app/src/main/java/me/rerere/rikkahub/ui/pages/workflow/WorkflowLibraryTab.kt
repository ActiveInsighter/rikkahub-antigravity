package me.rerere.rikkahub.ui.pages.workflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.workflow.DslHighlightColors
import me.rerere.rikkahub.data.workflow.DslSyntaxVisualTransformation
import me.rerere.rikkahub.data.workflow.LibraryFolder
import me.rerere.rikkahub.data.workflow.LibraryFolderScope
import me.rerere.rikkahub.data.workflow.RunFavorite
import me.rerere.rikkahub.data.workflow.StatusTone
import me.rerere.rikkahub.data.workflow.WorkflowTemplate
import me.rerere.rikkahub.data.workflow.createStarterPlan
import me.rerere.rikkahub.data.workflow.flattenLibraryFolders
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.RikkaConfirmDialog

@Composable
fun WorkflowLibraryTab(
    state: WorkflowLibraryUiState,
    bottomPadding: Dp,
    onRefresh: () -> Unit,
    onScopeChange: (LibraryFolderScope) -> Unit,
    onOpenFolder: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onCreateFolder: (name: String, scope: LibraryFolderScope, parentId: String) -> Unit,
    onRenameFolder: (LibraryFolder, String) -> Unit,
    onDeleteFolder: (LibraryFolder, LibraryFolderScope) -> Unit,
    onOpenRun: (String) -> Unit,
    onMoveFavorite: (RunFavorite, String) -> Unit,
    onDeleteFavorite: (RunFavorite) -> Unit,
    onUseTemplate: (WorkflowTemplate) -> Unit,
    onCreateTemplate: (title: String, description: String, planText: String, folderId: String, tags: List<String>, onSuccess: () -> Unit) -> Unit,
    onUpdateTemplate: (id: String, title: String, description: String, planText: String, folderId: String, tags: List<String>, onSuccess: () -> Unit) -> Unit,
    onMoveTemplate: (WorkflowTemplate, String) -> Unit,
    onDuplicateTemplate: (WorkflowTemplate) -> Unit,
    onDeleteTemplate: (WorkflowTemplate) -> Unit,
    onClearError: () -> Unit,
    onClearNotice: () -> Unit,
    showCreateTemplateDialog: Boolean,
    onDismissCreateTemplateDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var deletingFolder by remember { mutableStateOf<LibraryFolder?>(null) }

    var movingFavorite by remember { mutableStateOf<RunFavorite?>(null) }
    var deletingFavorite by remember { mutableStateOf<RunFavorite?>(null) }

    var editingTemplate by remember { mutableStateOf<WorkflowTemplate?>(null) }
    var movingTemplate by remember { mutableStateOf<WorkflowTemplate?>(null) }
    var deletingTemplate by remember { mutableStateOf<WorkflowTemplate?>(null) }

    val scope = state.activeScope
    val foldersForScope = if (scope == LibraryFolderScope.FAVORITE) {
        state.snapshot.favoriteFolders
    } else {
        state.snapshot.templateFolders
    }
    val folderById = remember(foldersForScope) { foldersForScope.associateBy { it.id } }
    val currentFolderId = state.currentFolderId.takeIf { it.isEmpty() || it in folderById } ?: ""

    val breadcrumbs = remember(foldersForScope, currentFolderId) {
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
    val visibleSubfolders = remember(foldersForScope, currentFolderId, query) {
        foldersForScope.filter { folder ->
            if (query.isNotEmpty()) {
                folder.name.lowercase().contains(query)
            } else {
                folder.parent == currentFolderId
            }
        }.sortedWith(compareBy<LibraryFolder> { it.sortOrder }.thenBy { it.name.lowercase() })
    }

    val visibleFavorites = remember(state.snapshot.favorites, currentFolderId, query) {
        state.snapshot.favorites.filter { fav ->
            if (query.isNotEmpty()) {
                val title = fav.runRecord?.displayTitle.orEmpty().lowercase()
                val note = fav.note.lowercase()
                title.contains(query) || note.contains(query) || fav.run.lowercase().contains(query)
            } else {
                fav.folder == currentFolderId
            }
        }
    }

    val visibleTemplates = remember(state.snapshot.templates, currentFolderId, query) {
        state.snapshot.templates.filter { tpl ->
            if (query.isNotEmpty()) {
                tpl.title.lowercase().contains(query) ||
                    tpl.description.lowercase().contains(query) ||
                    tpl.tags.any { it.lowercase().contains(query) }
            } else {
                tpl.folder == currentFolderId
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        if (state.isLoading && state.snapshot.folders.isEmpty() && state.snapshot.favorites.isEmpty() && state.snapshot.templates.isEmpty()) {
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
                    ConnectedChoiceButtons(
                        options = LibraryFolderScope.entries,
                        selected = scope,
                        onSelect = onScopeChange,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = 44.dp,
                    ) { option, selected ->
                        val count = if (option == LibraryFolderScope.FAVORITE) {
                            state.snapshot.favorites.size
                        } else {
                            state.snapshot.templates.size
                        }
                        ConnectedChoiceLabel(
                            text = "${option.label} ($count)",
                            selected = selected,
                            icon = HugeIcons.Tick01,
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = {
                                Text(if (scope == LibraryFolderScope.FAVORITE) "搜索收藏的 Run 或目录" else "搜索模板名称、描述或标签")
                            },
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
                                    text = "根目录",
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

                if (!state.errorMessage.isNullOrBlank()) {
                    item(key = "library_error") {
                        WorkflowErrorBanner(message = state.errorMessage, onDismiss = onClearError)
                    }
                }
                if (!state.noticeMessage.isNullOrBlank()) {
                    item(key = "library_notice") {
                        WorkflowNoticeBanner(message = state.noticeMessage, onDismiss = onClearNotice)
                    }
                }

                if (visibleSubfolders.isNotEmpty()) {
                    items(visibleSubfolders, key = { "folder_${it.id}" }) { folder ->
                        LibraryFolderRowCard(
                            folder = folder,
                            onOpen = { onOpenFolder(folder.id) },
                            onRename = { renamingFolder = folder },
                            onDelete = { deletingFolder = folder },
                        )
                    }
                }

                if (scope == LibraryFolderScope.FAVORITE) {
                    if (visibleFavorites.isEmpty() && visibleSubfolders.isEmpty()) {
                        item {
                            WorkflowEmptyState(
                                icon = HugeIcons.Favourite,
                                title = if (query.isNotEmpty()) "未找到匹配的收藏" else "暂无收藏",
                                subtitle = if (query.isNotEmpty()) {
                                    "试试更换搜索关键词。"
                                } else {
                                    "在工作流列表或详情页点击「收藏」，即可将常用 Run 归档到这里。"
                                },
                            )
                        }
                    } else {
                        items(visibleFavorites, key = { "fav_${it.id}" }) { fav ->
                            FavoriteExpressiveCard(
                                favorite = fav,
                                onOpen = { onOpenRun(fav.run) },
                                onMove = { movingFavorite = fav },
                                onDelete = { deletingFavorite = fav },
                            )
                        }
                    }
                } else {
                    if (visibleTemplates.isEmpty() && visibleSubfolders.isEmpty()) {
                        item {
                            WorkflowEmptyState(
                                icon = HugeIcons.Book02,
                                title = if (query.isNotEmpty()) "未找到匹配的模板" else "暂无模板",
                                subtitle = if (query.isNotEmpty()) {
                                    "试试更换搜索关键词。"
                                } else {
                                    "点击右下角新建模板，或在 Run 菜单中选择「存为模板」。"
                                },
                            )
                        }
                    } else {
                        items(visibleTemplates, key = { "tpl_${it.id}" }) { tpl ->
                            TemplateExpressiveCard(
                                template = tpl,
                                onUse = { onUseTemplate(tpl) },
                                onEdit = { editingTemplate = tpl },
                                onDuplicate = { onDuplicateTemplate(tpl) },
                                onMove = { movingTemplate = tpl },
                                onDelete = { deletingTemplate = tpl },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        WorkflowTextInputDialog(
            title = "新建${scope.label}目录",
            label = "目录名称",
            placeholder = "例如：日常任务",
            confirmLabel = "创建",
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                showCreateFolderDialog = false
                onCreateFolder(name, scope, currentFolderId)
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
                onDeleteFolder(folder, scope)
            },
            onDismiss = { deletingFolder = null },
        ) {
            Text("目录内的子目录和项目会自动移至上一级，不会丢失。")
        }
    }

    movingFavorite?.let { fav ->
        LibraryFolderPickerSheet(
            title = "移动收藏到目录",
            folders = state.snapshot.favoriteFolders,
            currentFolderId = fav.folder,
            onDismiss = { movingFavorite = null },
            onSelect = { targetFolderId ->
                movingFavorite = null
                onMoveFavorite(fav, targetFolderId)
            },
        )
    }

    deletingFavorite?.let { fav ->
        RikkaConfirmDialog(
            show = true,
            title = "取消收藏？",
            confirmText = "移除",
            dismissText = "取消",
            onConfirm = {
                deletingFavorite = null
                onDeleteFavorite(fav)
            },
            onDismiss = { deletingFavorite = null },
        ) {
            Text("确定要从收藏夹移除「${fav.runRecord?.displayTitle ?: fav.run}」吗？")
        }
    }

    movingTemplate?.let { tpl ->
        LibraryFolderPickerSheet(
            title = "移动模板到目录",
            folders = state.snapshot.templateFolders,
            currentFolderId = tpl.folder,
            onDismiss = { movingTemplate = null },
            onSelect = { targetFolderId ->
                movingTemplate = null
                onMoveTemplate(tpl, targetFolderId)
            },
        )
    }

    deletingTemplate?.let { tpl ->
        RikkaConfirmDialog(
            show = true,
            title = "删除模板「${tpl.title}」？",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                deletingTemplate = null
                onDeleteTemplate(tpl)
            },
            onDismiss = { deletingTemplate = null },
        ) {
            Text("删除后无法恢复，已基于此模板创建的 Run 不受影响。")
        }
    }

    if (showCreateTemplateDialog || editingTemplate != null) {
        TemplateEditorDialog(
            template = editingTemplate,
            defaultFolderId = currentFolderId,
            folders = state.snapshot.templateFolders,
            isMutating = state.isMutating,
            onDismiss = {
                editingTemplate = null
                onDismissCreateTemplateDialog()
            },
            onSave = { title, description, planText, folderId, tags ->
                val target = editingTemplate
                if (target == null) {
                    onCreateTemplate(title, description, planText, folderId, tags) {
                        onDismissCreateTemplateDialog()
                    }
                } else {
                    onUpdateTemplate(target.id, title, description, planText, folderId, tags) {
                        editingTemplate = null
                    }
                }
            },
        )
    }
}

@Composable
private fun LibraryFolderRowCard(
    folder: LibraryFolder,
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
private fun FavoriteExpressiveCard(
    favorite: RunFavorite,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val run = favorite.runRecord
    val meta = run?.statusMeta()
    val toneColors = statusToneColors(meta?.tone ?: StatusTone.NEUTRAL)

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(22.dp),
        color = CustomColors.listItemColors.containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.Favourite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = run?.displayTitle ?: "Run ${favorite.run}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            if (run != null) append("${run.versionLabel} · ")
                            append(formatWorkflowTime(favorite.updated))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (meta != null) {
                    StatusBadgePill(label = meta.label, colors = toneColors)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(HugeIcons.MoreVertical, contentDescription = "收藏操作", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("查看详情") },
                            onClick = {
                                menuExpanded = false
                                onOpen()
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
                            text = { Text("取消收藏", color = MaterialTheme.colorScheme.error) },
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateExpressiveCard(
    template: WorkflowTemplate,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = template.title.ifBlank { "未命名模板" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (template.description.isNotBlank()) {
                        Text(
                            text = template.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(HugeIcons.MoreVertical, contentDescription = "模板操作", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑模板") },
                            leadingIcon = { Icon(HugeIcons.Edit03, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("复制副本") },
                            leadingIcon = { Icon(HugeIcons.Copy01, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
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
                            text = { Text("删除模板", color = MaterialTheme.colorScheme.error) },
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

            if (template.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    template.tags.forEach { tag ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "更新于 ${formatWorkflowTime(template.updated)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = CircleShape,
                    ) {
                        Text("详情")
                    }
                    FilledTonalButton(
                        onClick = onUse,
                        shape = CircleShape,
                    ) {
                        Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("使用模板")
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryFolderPickerSheet(
    title: String,
    folders: List<LibraryFolder>,
    currentFolderId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val flattened = remember(folders) { flattenLibraryFolders(folders) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
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
                flattened.forEach { item ->
                    val selected = item.folder.id == currentFolderId
                    Surface(
                        onClick = { onSelect(item.folder.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            CustomColors.listItemColors.containerColor
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = (14 + item.depth * 16).dp,
                                end = 14.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(HugeIcons.Folder01, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(item.folder.name, style = MaterialTheme.typography.bodyMedium)
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
private fun TemplateEditorDialog(
    template: WorkflowTemplate?,
    defaultFolderId: String,
    folders: List<LibraryFolder>,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, planText: String, folderId: String, tags: List<String>) -> Unit,
) {
    var title by remember(template) { mutableStateOf(template?.title.orEmpty()) }
    var description by remember(template) { mutableStateOf(template?.description.orEmpty()) }
    var tagsText by remember(template) { mutableStateOf(template?.tags?.joinToString(", ").orEmpty()) }
    var folderId by remember(template, defaultFolderId) { mutableStateOf(template?.folder ?: defaultFolderId) }
    var planText by remember(template) { mutableStateOf(template?.planText ?: createStarterPlan()) }
    var showFolderPicker by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme
    val visualTransformation = remember(colorScheme) {
        DslSyntaxVisualTransformation(
            DslHighlightColors(
                directive = colorScheme.primary,
                variable = colorScheme.tertiary,
                tag = colorScheme.secondary,
                brace = colorScheme.primary.copy(alpha = 0.85f),
                fence = colorScheme.onSurfaceVariant,
            )
        )
    }

    val folderName = remember(folders, folderId) {
        folders.firstOrNull { it.id == folderId }?.name ?: "根目录"
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
                        title = { Text(if (template == null) "新建模板" else "编辑模板") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(HugeIcons.Cancel01, contentDescription = "关闭")
                            }
                        },
                        actions = {
                            Button(
                                onClick = {
                                    val parsedTags = tagsText.split(",", "，")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                    onSave(title, description, planText, folderId, parsedTags)
                                },
                                enabled = !isMutating && title.trim().isNotEmpty() && planText.trim().isNotEmpty(),
                                shape = CircleShape,
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text("保存")
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
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("模板名称") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("模板描述（可选）") },
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = tagsText,
                            onValueChange = { tagsText = it },
                            label = { Text("标签（逗号分隔）") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1.4f),
                        )
                        Surface(
                            onClick = { showFolderPicker = true },
                            shape = RoundedCornerShape(14.dp),
                            color = CustomColors.listItemColors.containerColor,
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(HugeIcons.Folder01, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = folderName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Text(
                        text = "工作流 DSL 定义",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        BasicTextField(
                            value = planText,
                            onValueChange = { planText = it },
                            visualTransformation = visualTransformation,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showFolderPicker) {
        LibraryFolderPickerSheet(
            title = "选择模板目录",
            folders = folders,
            currentFolderId = folderId,
            onDismiss = { showFolderPicker = false },
            onSelect = {
                folderId = it
                showFolderPicker = false
            },
        )
    }
}
