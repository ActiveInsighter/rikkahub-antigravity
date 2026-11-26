package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Bookshelf01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.FolderOpen
import me.rerere.hugeicons.stroke.Internet
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.rikkahub.data.examdocs.DocsAccountRepository
import me.rerere.rikkahub.data.examdocs.DocsProgressRepository
import me.rerere.rikkahub.data.examdocs.ExamDocNode
import me.rerere.rikkahub.data.examdocs.ExamDocsSite
import org.koin.compose.koinInject

@Composable
internal fun ExamDocsDirectoryDrawer(
    state: ExamDocsUiState,
    onSelectSite: (ExamDocsSite) -> Unit,
    onSelectModule: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onSelectPage: (ExamDocNode.Page) -> Unit,
    onOpenAccount: () -> Unit,
    isDrawerOpen: Boolean = true,
) {
    val currentUrl = state.currentPage?.url
    val error = state.treeError
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isDrawerOpen) {
        if (!isDrawerOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .widthIn(min = 310.dp, max = 368.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = HugeIcons.Bookshelf01,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = colors.primary,
                        )
                    }
                    Text(
                        "文档目录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                DocsSiteSelector(state.sites, state.activeSite, onSelectSite)
            }

            DocsAccountBanner(onClick = onOpenAccount)

            DirectorySearchBar(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChange,
                onClearAndClose = {
                    onSearchQueryChange("")
                    focusManager.clearFocus(force = true)
                    softwareKeyboardController?.hide()
                },
            )

            if (state.isLoadingTree) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                )
            } else if (error != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.errorContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(error, style = MaterialTheme.typography.bodySmall, color = colors.error)
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                }
            } else if (state.searchQuery.isNotBlank()) {
                if (state.searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "未找到匹配的文档",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.searchResults, key = { it.page.url }) { entry ->
                        DirectoryRow(
                            node = entry.page,
                            active = currentUrl == entry.page.url,
                            subtitle = entry.breadcrumb.joinToString(" › "),
                            onClick = {
                                focusManager.clearFocus(force = true)
                                softwareKeyboardController?.hide()
                                onSelectPage(entry.page)
                            },
                        )
                    }
                }
            } else {
                if (state.modules.isNotEmpty()) {
                    ModuleDropdownSelector(
                        modules = state.modules,
                        selectedIndex = state.selectedModuleIndex,
                        onSelectModule = onSelectModule,
                    )
                }
                HorizontalDivider(
                    color = colors.outlineVariant.copy(alpha = 0.35f),
                )
                key(state.activeSite.baseUrl, state.selectedModuleIndex) {
                    DirectoryTree(
                        module = state.modules.getOrNull(state.selectedModuleIndex),
                        currentUrl = currentUrl,
                        onSelectPage = { page ->
                            focusManager.clearFocus(force = true)
                            softwareKeyboardController?.hide()
                            onSelectPage(page)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearAndClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (focused) colors.primary.copy(alpha = 0.7f) else colors.outlineVariant.copy(alpha = 0.45f),
        label = "searchBorder",
    )
    val containerColor by animateColorAsState(
        targetValue = if (focused) colors.surfaceContainerHigh.copy(alpha = 0.75f) else colors.surfaceContainerLow,
        label = "searchContainer",
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (focused) 1.dp else 0.5.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable { focusRequester.requestFocus() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = HugeIcons.Search01,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (focused) colors.primary else colors.onSurfaceVariant,
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索章节或路径",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus(force = true)
                            softwareKeyboardController?.hide()
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            Box(
                modifier = Modifier.size(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (query.isNotEmpty() || focused) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainerHighest.copy(alpha = 0.8f))
                            .clickable(onClick = onClearAndClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = HugeIcons.Cancel01,
                            contentDescription = "清除搜索",
                            modifier = Modifier.size(13.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocsAccountBanner(onClick: () -> Unit) {
    val accounts = koinInject<DocsAccountRepository>()
    val repository = koinInject<DocsProgressRepository>()
    val account by accounts.state.collectAsStateWithLifecycle()
    val syncing by repository.syncing.collectAsStateWithLifecycle()
    val syncError by repository.syncError.collectAsStateWithLifecycle()
    val user = account.user
    val pending by remember(user?.id) {
        user?.let { repository.pending(it.id) } ?: flowOf(0)
    }.collectAsStateWithLifecycle(initialValue = 0)

    val colors = MaterialTheme.colorScheme
    val isError = (user != null && account.needsLogin) || (user != null && syncError?.first == user.id)
    val subtitle = when {
        account.restoring -> "正在恢复登录状态…"
        user != null && account.needsLogin -> "登录已过期，点击重新验证"
        user != null && syncing == user.id -> "正在同步答题记录…"
        user != null && syncError?.first == user.id -> "同步失败，点击查看详情"
        user != null && pending > 0 -> "$pending 条记录待同步"
        user != null -> "云端同步已开启"
        accounts.configured -> "点击登录同步答题进度"
        else -> "本机保存答题记录"
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (user != null) colors.primaryContainer else colors.surfaceContainerHighest,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val initial = user?.email?.firstOrNull()?.uppercaseChar()?.toString()
                if (initial != null) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimaryContainer,
                    )
                } else {
                    Icon(
                        imageVector = HugeIcons.UserCircle,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = user?.email ?: if (account.restoring) "正在读取账号…" else "未登录 · 访客模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isError) colors.error else colors.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = "账号设置",
                modifier = Modifier.size(16.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModuleDropdownSelector(
    modules: List<ExamDocNode.Folder>,
    selectedIndex: Int,
    onSelectModule: (Int) -> Unit,
) {
    val selectedModule = modules.getOrNull(selectedIndex) ?: modules.firstOrNull() ?: return
    val canSwitch = modules.size > 1
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "moduleChevron",
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { if (canSwitch) expanded = true },
            enabled = canSwitch,
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceContainerHigh.copy(alpha = 0.72f),
            border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.Book02,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.primary,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = selectedModule.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selectedModule.questions > 0) {
                        Text(
                            text = "${selectedModule.questions} 道练习题",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (canSwitch) {
                    Surface(
                        shape = CircleShape,
                        color = colors.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "${selectedIndex + 1}/${modules.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Icon(
                        imageVector = HugeIcons.ArrowDown01,
                        contentDescription = "切换模块",
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
        ) {
            modules.forEachIndexed { index, module ->
                val isSelected = index == selectedIndex
                DropdownMenuItem(
                    text = {
                        Text(
                            text = module.name,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) colors.primary else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isSelected) HugeIcons.Tick01 else HugeIcons.Book02,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
                        )
                    },
                    trailingIcon = if (module.questions > 0) {
                        {
                            Text(
                                text = "${module.questions} 题",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelectModule(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun DocsSiteSelector(sites: List<ExamDocsSite>, selected: ExamDocsSite, onSelect: (ExamDocsSite) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(999.dp),
            color = colors.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.45f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    HugeIcons.Internet,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = colors.primary,
                )
                Text(
                    selected.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    HugeIcons.ArrowDown01,
                    contentDescription = "切换站点",
                    modifier = Modifier.size(15.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
        ) {
            for (site in sites) {
                val isSelected = site == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            site.name,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) colors.primary else colors.onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isSelected) HugeIcons.Tick01 else HugeIcons.Internet,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(site)
                    },
                )
            }
        }
    }
}

private data class DirectoryEntry(val node: ExamDocNode, val key: String, val depth: Int, val expanded: Boolean)

@Composable
private fun DirectoryTree(
    module: ExamDocNode.Folder?,
    currentUrl: String?,
    onSelectPage: (ExamDocNode.Page) -> Unit,
    modifier: Modifier,
) {
    val overrides = remember { mutableStateMapOf<String, Boolean>() }
    val nodes = module?.children.orEmpty()
    val activeFolders = remember(nodes, currentUrl) {
        mutableSetOf<String>().also { result ->
            fun visit(children: List<ExamDocNode>, parent: String): Boolean {
                return children.withIndex().any { (index, node) ->
                    when (node) {
                        is ExamDocNode.Page -> node.url == currentUrl
                        is ExamDocNode.Folder -> {
                            val key = "$parent/$index"
                            visit(node.children, key).also { if (it) result.add(key) }
                        }
                    }
                }
            }
            if (currentUrl != null) visit(nodes, "")
        }
    }
    val rows by remember(nodes, activeFolders) {
        derivedStateOf {
            buildList {
                fun flatten(children: List<ExamDocNode>, parent: String, depth: Int) {
                    children.forEachIndexed { index, node ->
                        val key = "$parent/$index"
                        val expanded = node is ExamDocNode.Folder && (overrides[key] ?: (key in activeFolders))
                        add(DirectoryEntry(node, key, depth, expanded))
                        if (node is ExamDocNode.Folder && expanded) flatten(node.children, key, depth + 1)
                    }
                }
                flatten(nodes, "", 0)
            }
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(rows, key = DirectoryEntry::key, contentType = { it.node is ExamDocNode.Folder }) { entry ->
            val node = entry.node
            DirectoryRow(
                node = node,
                active = node is ExamDocNode.Page && node.url == currentUrl,
                expanded = entry.expanded,
                depth = entry.depth,
                modifier = Modifier.animateItem(),
                onClick = {
                    when (node) {
                        is ExamDocNode.Folder -> overrides[entry.key] = !entry.expanded
                        is ExamDocNode.Page -> onSelectPage(node)
                    }
                },
            )
        }
    }
}

@Composable
private fun DirectoryRow(
    node: ExamDocNode,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    expanded: Boolean = false,
    depth: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val folder = node is ExamDocNode.Folder
    val clampedDepth = depth.coerceAtMost(8)
    val guideColor = colors.outlineVariant.copy(alpha = 0.35f)
    val containerColor = when {
        active -> colors.primaryContainer.copy(alpha = 0.52f)
        folder && expanded && depth == 0 -> colors.surfaceContainerLow.copy(alpha = 0.65f)
        else -> Color.Transparent
    }
    val border = when {
        active -> BorderStroke(0.8.dp, colors.primary.copy(alpha = 0.28f))
        else -> null
    }
    val contentColor = if (active) colors.primary else colors.onSurface
    val iconTint = when {
        active -> colors.primary
        folder && expanded -> colors.primary
        folder -> colors.onSurfaceVariant
        else -> colors.onSurfaceVariant.copy(alpha = 0.78f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (clampedDepth > 0) {
                    val stepPx = 14.dp.toPx()
                    val strokePx = 1.dp.toPx()
                    for (level in 0 until clampedDepth) {
                        val x = level * stepPx + 10.dp.toPx()
                        drawLine(
                            color = guideColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = strokePx,
                        )
                    }
                }
            }
            .padding(start = (clampedDepth * 14).dp),
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = containerColor,
            contentColor = contentColor,
            border = border,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (folder) {
                    val rotation = animateFloatAsState(if (expanded) 90f else 0f, label = "directoryChevron")
                    Icon(
                        HugeIcons.ArrowRight01,
                        if (expanded) "收起" else "展开",
                        Modifier.size(14.dp).graphicsLayer { rotationZ = rotation.value },
                        tint = if (expanded) colors.primary else colors.onSurfaceVariant,
                    )
                } else if (subtitle == null && clampedDepth == 0) {
                    Spacer(Modifier.size(14.dp))
                }
                Icon(
                    when {
                        folder && expanded -> HugeIcons.FolderOpen
                        folder -> HugeIcons.Folder01
                        else -> HugeIcons.File02
                    },
                    null,
                    Modifier.size(17.dp),
                    tint = iconTint,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        node.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (folder || active) FontWeight.SemiBold else FontWeight.Normal,
                        color = contentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (node.questions > 0) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (active) {
                            colors.primary.copy(alpha = 0.16f)
                        } else {
                            colors.surfaceContainerHighest.copy(alpha = 0.65f)
                        },
                        border = BorderStroke(
                            0.5.dp,
                            if (active) {
                                colors.primary.copy(alpha = 0.28f)
                            } else {
                                colors.outlineVariant.copy(alpha = 0.35f)
                            },
                        ),
                    ) {
                        Text(
                            "${node.questions} 题",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Medium,
                            color = if (active) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
