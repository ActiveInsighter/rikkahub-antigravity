package me.rerere.rikkahub.ui.pages.examdocs

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.data.examdocs.DEFAULT_EXAM_DOCS_BASE_URL
import me.rerere.rikkahub.data.examdocs.ExamDocNode
import me.rerere.rikkahub.data.examdocs.ExamDocsRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDocsPage(
    initialUrl: String? = null,
    vm: ExamDocsVM = koinViewModel(parameters = { parametersOf(initialUrl) }),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var showDirectorySheet by rememberSaveable { mutableStateOf(false) }
    var showOutlineSheet by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }

    val contentScrollState = rememberScrollState()
    LaunchedEffect(state.currentPage?.url) {
        contentScrollState.scrollTo(0)
    }

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            Column {
                TopAppBar(
                    colors = CustomColors.topBarColors,
                    navigationIcon = { BackButton() },
                    title = {
                        Column {
                            Text(
                                text = state.currentPage?.name ?: "Exam Docs",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val subtitle = state.currentBreadcrumb.joinToString(" / ")
                                .ifBlank { state.baseUrl.removePrefix("https://").removePrefix("http://") }
                            Text(
                                text = subtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDirectorySheet = true }) {
                            Icon(
                                imageVector = HugeIcons.Menu03,
                                contentDescription = "目录",
                            )
                        }
                        IconButton(onClick = { vm.refreshCurrentPage() }) {
                            Icon(
                                imageVector = HugeIcons.Refresh03,
                                contentDescription = "刷新",
                            )
                        }
                        IconButton(onClick = { showUrlDialog = true }) {
                            Icon(
                                imageVector = HugeIcons.Settings03,
                                contentDescription = "站点设置",
                            )
                        }
                    },
                )

                // Web 端窄屏风格的顶部导航栏：模块切换 + 面包屑目录触发条 + 大纲按钮
                TopDocsNavBar(
                    modules = state.modules,
                    selectedModuleIndex = state.selectedModuleIndex,
                    currentPage = state.currentPage,
                    breadcrumb = state.currentBreadcrumb,
                    hasHeadings = state.headings.isNotEmpty(),
                    onSelectModule = { index ->
                        vm.selectModule(index)
                        showDirectorySheet = true
                    },
                    onOpenDirectory = { showDirectorySheet = true },
                    onOpenOutline = { showOutlineSheet = true },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LoadingIndicator()
                            Text(
                                text = "正在获取文档目录...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                state.treeError != null && state.modules.isEmpty() -> {
                    ErrorStateView(
                        title = "无法获取网站结构",
                        message = state.treeError ?: "网络请求失败",
                        onRetry = { vm.loadTree(forceRefresh = true) },
                        onEditUrl = { showUrlDialog = true },
                    )
                }

                state.isLoadingContent -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LoadingIndicator()
                            Text(
                                text = "正在加载 Markdown...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                state.contentError != null -> {
                    ErrorStateView(
                        title = "文档加载失败",
                        message = state.contentError ?: "未知错误",
                        onRetry = { vm.refreshCurrentPage() },
                        onEditUrl = { showDirectorySheet = true },
                        secondaryLabel = "打开目录",
                    )
                }

                state.markdownContent != null -> {
                    val mdUrl = remember(state.baseUrl, state.currentPage?.url) {
                        state.currentPage?.let {
                            ExamDocsRepository.buildMarkdownUrl(state.baseUrl, it.url)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(contentScrollState)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // 顶部文档信息条：题数 + .md 链接复制
                        if (state.currentPage != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if ((state.currentPage?.questions ?: 0) > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            text = "${state.currentPage?.questions} 题",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                                if (mdUrl != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = CustomColors.listItemColors.containerColor,
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        ),
                                        onClick = {
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(ClipData.newPlainText("md_url", mdUrl))
                                                )
                                                toaster.show("已复制 .md 链接", type = ToastType.SUCCESS)
                                            }
                                        },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = HugeIcons.Copy01,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = state.currentPage?.url?.plus(".md") ?: "",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 原生 Markdown 渲染（复用 AI 消息渲染栈 + 可扩展自定义组件）
                        ExamDocsMarkdown(
                            content = state.markdownContent ?: "",
                            baseUrl = state.baseUrl,
                            currentPageUrl = state.currentPage?.url,
                            onNavigateDoc = { path -> vm.openByUrl(path) },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // 底部上一节 / 下一节导航卡片
                        if (state.prevPage != null || state.nextPage != null) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                state.prevPage?.let { prev ->
                                    PageNavCard(
                                        label = "上一节",
                                        title = prev.name,
                                        isNext = false,
                                        onClick = { vm.openPage(prev) },
                                        modifier = Modifier.weight(1f),
                                    )
                                } ?: Spacer(Modifier.weight(1f))

                                state.nextPage?.let { next ->
                                    PageNavCard(
                                        label = "下一节",
                                        title = next.name,
                                        isNext = true,
                                        onClick = { vm.openPage(next) },
                                        modifier = Modifier.weight(1f),
                                    )
                                } ?: Spacer(Modifier.weight(1f))
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(onClick = { showDirectorySheet = true }) {
                            Text("浏览文档目录")
                        }
                    }
                }
            }
        }
    }

    if (showDirectorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDirectorySheet = false },
            sheetState = sheetState,
        ) {
            ExamDocsDirectorySheetContent(
                modules = state.modules,
                selectedModuleIndex = state.selectedModuleIndex,
                currentPageUrl = state.currentPage?.url,
                searchQuery = state.searchQuery,
                searchResults = state.searchResults,
                onSelectModule = vm::selectModule,
                onSearchQueryChange = vm::updateSearchQuery,
                onSelectPage = { page ->
                    showDirectorySheet = false
                    vm.openPage(page)
                },
            )
        }
    }

    if (showOutlineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOutlineSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "本页大纲",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.headings) { heading ->
                        val indent = ((heading.level - 1).coerceAtLeast(0) * 14).dp
                        val totalLines = (state.markdownContent?.lines()?.size ?: 1).coerceAtLeast(1)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = {
                                showOutlineSheet = false
                                val fraction = (heading.lineIndex.toFloat() / totalLines.toFloat())
                                    .coerceIn(0f, 1f)
                                val targetPx = (contentScrollState.maxValue * fraction).toInt()
                                scope.launch {
                                    contentScrollState.animateScrollTo(targetPx)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = heading.title,
                                style = if (heading.level <= 2) {
                                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                } else {
                                    MaterialTheme.typography.bodySmall
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(start = 12.dp + indent, end = 12.dp, top = 10.dp, bottom = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        ExamDocsBaseUrlDialog(
            currentUrl = state.baseUrl,
            onDismiss = { showUrlDialog = false },
            onConfirm = { newUrl ->
                showUrlDialog = false
                vm.updateBaseUrl(newUrl)
            },
        )
    }
}

@Composable
private fun TopDocsNavBar(
    modules: List<ExamDocNode.Folder>,
    selectedModuleIndex: Int,
    currentPage: ExamDocNode.Page?,
    breadcrumb: List<String>,
    hasHeadings: Boolean,
    onSelectModule: (Int) -> Unit,
    onOpenDirectory: () -> Unit,
    onOpenOutline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (modules.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(modules) { index, module ->
                    val selected = index == selectedModuleIndex
                    Surface(
                        onClick = { onSelectModule(index) },
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            CustomColors.listItemColors.containerColor
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = module.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                            )
                            if (module.questions > 0) {
                                Text(
                                    text = "${module.questions}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
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

        // 窄屏面包屑目录栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onOpenDirectory,
                shape = RoundedCornerShape(14.dp),
                color = CustomColors.listItemColors.containerColor,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                ),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Book02,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    val pathText = if (breadcrumb.isNotEmpty() && currentPage != null) {
                        (breadcrumb + currentPage.name).joinToString(" › ")
                    } else {
                        currentPage?.name ?: "选择文档目录"
                    }
                    Text(
                        text = pathText,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = HugeIcons.ArrowDown01,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (hasHeadings) {
                Surface(
                    onClick = onOpenOutline,
                    shape = RoundedCornerShape(14.dp),
                    color = CustomColors.listItemColors.containerColor,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    ),
                ) {
                    Text(
                        text = "大纲",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamDocsDirectorySheetContent(
    modules: List<ExamDocNode.Folder>,
    selectedModuleIndex: Int,
    currentPageUrl: String?,
    searchQuery: String,
    searchResults: List<me.rerere.rikkahub.data.examdocs.ExamDocFlatEntry>,
    onSelectModule: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectPage: (ExamDocNode.Page) -> Unit,
) {
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.86f)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "文档目录",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("搜索章节、真题或路径...") },
            leadingIcon = {
                Icon(
                    imageVector = HugeIcons.Search01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = HugeIcons.Cancel01,
                            contentDescription = "清除",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        if (searchQuery.isBlank() && modules.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(modules) { index, module ->
                    val selected = index == selectedModuleIndex
                    Surface(
                        onClick = { onSelectModule(index) },
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ) {
                        Text(
                            text = module.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (searchQuery.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    item {
                        Text(
                            text = "未找到匹配的文档",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(searchResults, key = { it.page.url }) { entry ->
                        val active = entry.page.url == currentPageUrl
                        Surface(
                            onClick = { onSelectPage(entry.page) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = entry.page.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = entry.breadcrumb.joinToString(" › "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            } else {
                val activeModule = modules.getOrNull(selectedModuleIndex)
                if (activeModule != null) {
                    items(activeModule.children) { node ->
                        DirectoryTreeNode(
                            node = node,
                            pathKey = "${activeModule.name}/${node.name}",
                            depth = 0,
                            currentPageUrl = currentPageUrl,
                            expandedFolders = expandedFolders,
                            onSelectPage = onSelectPage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryTreeNode(
    node: ExamDocNode,
    pathKey: String,
    depth: Int,
    currentPageUrl: String?,
    expandedFolders: MutableMap<String, Boolean>,
    onSelectPage: (ExamDocNode.Page) -> Unit,
) {
    val indent = (depth * 14).dp
    when (node) {
        is ExamDocNode.Page -> {
            val active = node.url == currentPageUrl
            Surface(
                onClick = { onSelectPage(node) },
                shape = RoundedCornerShape(12.dp),
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.File02,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (node.questions > 0) {
                        Surface(
                            shape = CircleShape,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        ) {
                            Text(
                                text = "${node.questions}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        is ExamDocNode.Folder -> {
            val containsActive = remember(node, currentPageUrl) {
                folderContainsUrl(node, currentPageUrl)
            }
            val expanded = expandedFolders[pathKey] ?: containsActive
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 90f else 0f,
                label = "folder_chevron",
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = indent)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { expandedFolders[pathKey] = !expanded }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = null,
                        modifier = Modifier
                            .size(15.dp)
                            .rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = HugeIcons.Folder01,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (node.questions > 0) {
                        Text(
                            text = "${node.questions} 题",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        node.children.forEach { child ->
                            DirectoryTreeNode(
                                node = child,
                                pathKey = "$pathKey/${child.name}",
                                depth = depth + 1,
                                currentPageUrl = currentPageUrl,
                                expandedFolders = expandedFolders,
                                onSelectPage = onSelectPage,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun folderContainsUrl(folder: ExamDocNode.Folder, url: String?): Boolean {
    if (url == null) return false
    return folder.children.any { child ->
        when (child) {
            is ExamDocNode.Page -> child.url == url
            is ExamDocNode.Folder -> folderContainsUrl(child, url)
        }
    }
}

@Composable
private fun PageNavCard(
    label: String,
    title: String,
    isNext: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = CustomColors.listItemColors.containerColor,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = if (isNext) Alignment.End else Alignment.Start,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!isNext) {
                    Icon(
                        imageVector = HugeIcons.ArrowLeft01,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isNext) {
                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ErrorStateView(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onEditUrl: () -> Unit,
    secondaryLabel: String = "修改站点地址",
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onEditUrl) {
                    Text(secondaryLabel)
                }
                Button(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
    }
}

@Composable
private fun ExamDocsBaseUrlDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(currentUrl) { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exam Docs 站点地址") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "输入部署的 exam-docs 站点根地址（自动请求 /doc-sources.json 与 .md 文档）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { text = DEFAULT_EXAM_DOCS_BASE_URL },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text("恢复默认地址")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
                Text("保存并加载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
