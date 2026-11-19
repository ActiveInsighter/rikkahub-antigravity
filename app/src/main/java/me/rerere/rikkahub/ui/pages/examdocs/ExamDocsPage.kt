package me.rerere.rikkahub.ui.pages.examdocs

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.openUrl
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExamDocsPage(
    vm: ExamDocsVM = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val articleScrollState = rememberScrollState()

    LaunchedEffect(state.selectedPage?.url) {
        articleScrollState.scrollTo(0)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { BackButton() },
                    title = {
                        Column {
                            Text(
                                text = state.selectedPage?.name ?: "Exam Docs",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            state.selectedPage?.let { page ->
                                val subtitle = buildString {
                                    if (page.breadcrumb.isNotEmpty()) {
                                        append(page.breadcrumb.last())
                                    } else {
                                        append("exam.any1.tech")
                                    }
                                    if (page.questions > 0) {
                                        append(" · ${page.questions} 题")
                                    }
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        if (!state.markdownContent.isNullOrBlank()) {
                            IconButton(
                                onClick = {
                                    val md = state.markdownContent ?: return@IconButton
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText("ExamDocMarkdown", md))
                                        )
                                        toaster.show("已复制 Markdown 原文", type = ToastType.SUCCESS)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Copy01,
                                    contentDescription = "复制 Markdown",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                vm.currentWebPageUrl()?.let { context.openUrl(it) }
                                    ?: context.openUrl(state.siteBaseUrl)
                            },
                        ) {
                            Icon(
                                imageVector = HugeIcons.GlobalSearch,
                                contentDescription = "在浏览器打开",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = { vm.refreshCurrentPage() }) {
                            Icon(
                                imageVector = HugeIcons.Refresh03,
                                contentDescription = "刷新",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )

                // 参考 Web 端窄屏顶部导航栏：目录开关 + 当前位置 + 上/下一节
                ExamDocsTopNavBar(
                    state = state,
                    onToggleCatalog = { vm.toggleCatalog() },
                    onPrev = { vm.navigatePrevious() },
                    onNext = { vm.navigateNext() },
                )

                // 顶部展开式目录面板
                AnimatedVisibility(
                    visible = state.isCatalogExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    ExamDocsTopCatalogPanel(
                        state = state,
                        onSearchChange = vm::updateSearchQuery,
                        onToggleFolder = vm::toggleFolder,
                        onSelectPage = { page -> vm.selectPage(page) },
                        onRetryTree = { vm.loadTree(forceRefresh = true) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoadingTree && state.modules.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "正在获取 Exam Docs 目录...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.treeError != null && state.modules.isEmpty() -> {
                    ExamDocsErrorState(
                        message = state.treeError ?: "加载目录失败",
                        onRetry = { vm.loadTree(forceRefresh = true) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.isLoadingPage && state.markdownContent.isNullOrBlank() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "正在加载文档...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.pageError != null && state.markdownContent.isNullOrBlank() -> {
                    ExamDocsErrorState(
                        message = state.pageError ?: "加载文档失败",
                        onRetry = { vm.refreshCurrentPage() },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.markdownContent != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(articleScrollState)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        state.selectedPage?.let { page ->
                            if (page.breadcrumb.isNotEmpty()) {
                                Text(
                                    text = page.breadcrumb.joinToString(" / "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        if (state.pageError != null) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "刷新失败：${state.pageError}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }

                        ExamDocsMarkdownContent(
                            markdown = state.markdownContent ?: "",
                            onClickCitation = { url ->
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    context.openUrl(url)
                                }
                            },
                        )

                        ExamDocsPageFooterNav(
                            previous = state.previousPage,
                            next = state.nextPage,
                            onSelectPage = { vm.selectPage(it) },
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "请从顶部目录选择文档",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = { vm.toggleCatalog(true) }) {
                            Text("打开目录")
                        }
                    }
                }
            }

            if (state.isLoadingPage && state.markdownContent != null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun ExamDocsTopNavBar(
    state: ExamDocsUiState,
    onToggleCatalog: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = onToggleCatalog,
                    shape = MaterialTheme.shapes.medium,
                    color = if (state.isCatalogExpanded) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Book02,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (state.isCatalogExpanded) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        Text(
                            text = state.selectedPage?.name ?: "文档目录",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.flatPages.isNotEmpty() && state.currentPageIndex >= 0) {
                            Text(
                                text = "${state.currentPageIndex + 1}/${state.flatPages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = if (state.isCatalogExpanded) {
                                HugeIcons.ArrowUp01
                            } else {
                                HugeIcons.ArrowDown01
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                IconButton(
                    onClick = onPrev,
                    enabled = state.previousPage != null,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.ArrowLeft01,
                        contentDescription = "上一节",
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = onNext,
                    enabled = state.nextPage != null,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = "下一节",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

private sealed interface CatalogRow {
    data class FolderRow(
        val path: String,
        val node: ExamDocNode,
        val depth: Int,
        val expanded: Boolean,
    ) : CatalogRow

    data class PageRow(
        val page: ExamDocPageSummary,
        val depth: Int,
    ) : CatalogRow
}

@Composable
private fun ExamDocsTopCatalogPanel(
    state: ExamDocsUiState,
    onSearchChange: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectPage: (ExamDocPageSummary) -> Unit,
    onRetryTree: () -> Unit,
) {
    val rows = remember(state.modules, state.expandedFolders, state.searchQuery, state.flatPages) {
        val query = state.searchQuery.trim()
        if (query.isNotEmpty()) {
            state.flatPages
                .filter { page ->
                    page.name.contains(query, ignoreCase = true) ||
                        page.breadcrumb.any { it.contains(query, ignoreCase = true) }
                }
                .map { CatalogRow.PageRow(page = it, depth = 0) }
        } else {
            buildVisibleCatalogRows(
                nodes = state.modules,
                expandedFolders = state.expandedFolders,
            )
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("搜索章节或关键词...") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = HugeIcons.Search01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = HugeIcons.Cancel01,
                                contentDescription = "清除",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            if (state.treeError != null && state.modules.isEmpty()) {
                ExamDocsErrorState(
                    message = state.treeError,
                    onRetry = onRetryTree,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        items = rows,
                        key = { row ->
                            when (row) {
                                is CatalogRow.FolderRow -> "folder:${row.path}"
                                is CatalogRow.PageRow -> "page:${row.page.url}"
                            }
                        },
                    ) { row ->
                        when (row) {
                            is CatalogRow.FolderRow -> {
                                val indent = (row.depth * 14).dp
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable { onToggleFolder(row.path) }
                                        .padding(
                                            start = 10.dp + indent,
                                            end = 10.dp,
                                            top = 9.dp,
                                            bottom = 9.dp,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = if (row.expanded) {
                                            HugeIcons.ArrowDown01
                                        } else {
                                            HugeIcons.ArrowRight01
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Icon(
                                        imageVector = HugeIcons.Folder01,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = row.node.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (row.node.questions > 0) {
                                        Text(
                                            text = "${row.node.questions} 题",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            is CatalogRow.PageRow -> {
                                val isSelected = state.selectedPage?.url == row.page.url
                                val indent = (row.depth * 14).dp
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainer
                                            },
                                        )
                                        .clickable { onSelectPage(row.page) }
                                        .padding(
                                            start = 10.dp + indent,
                                            end = 10.dp,
                                            top = 9.dp,
                                            bottom = 9.dp,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = HugeIcons.File02,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = row.page.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (state.searchQuery.isNotBlank() && row.page.breadcrumb.isNotEmpty()) {
                                            Text(
                                                text = row.page.breadcrumb.joinToString(" / "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    if (row.page.questions > 0) {
                                        Text(
                                            text = "${row.page.questions} 题",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun buildVisibleCatalogRows(
    nodes: List<ExamDocNode>,
    expandedFolders: Set<String>,
    parentPath: String = "",
    breadcrumb: List<String> = emptyList(),
    depth: Int = 0,
): List<CatalogRow> {
    val rows = mutableListOf<CatalogRow>()
    for (node in nodes) {
        if (node.isFolder) {
            val path = if (parentPath.isEmpty()) node.name else "$parentPath/${node.name}"
            val expanded = path in expandedFolders
            rows += CatalogRow.FolderRow(
                path = path,
                node = node,
                depth = depth,
                expanded = expanded,
            )
            if (expanded) {
                rows += buildVisibleCatalogRows(
                    nodes = node.children,
                    expandedFolders = expandedFolders,
                    parentPath = path,
                    breadcrumb = breadcrumb + node.name,
                    depth = depth + 1,
                )
            }
        } else if (node.isPage && !node.url.isNullOrBlank()) {
            rows += CatalogRow.PageRow(
                page = ExamDocPageSummary(
                    name = node.name,
                    url = node.url,
                    questions = node.questions,
                    breadcrumb = breadcrumb,
                ),
                depth = depth,
            )
        }
    }
    return rows
}

@Composable
private fun ExamDocsPageFooterNav(
    previous: ExamDocPageSummary?,
    next: ExamDocPageSummary?,
    onSelectPage: (ExamDocPageSummary) -> Unit,
) {
    if (previous == null && next == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (previous != null) {
            Surface(
                onClick = { onSelectPage(previous) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "上一节",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = previous.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (next != null) {
            Surface(
                onClick = { onSelectPage(next) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "下一节",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = next.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExamDocsErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}
