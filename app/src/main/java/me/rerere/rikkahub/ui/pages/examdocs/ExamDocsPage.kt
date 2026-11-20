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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.examdocs.DocsSite
import me.rerere.rikkahub.data.examdocs.ExamDocFlatEntry
import me.rerere.rikkahub.data.examdocs.ExamDocNode
import me.rerere.rikkahub.data.examdocs.ExamDocsRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.SelectableContent
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
    var showSiteSettingsDialog by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    LaunchedEffect(state.currentPage?.url, state.activeSite.id) {
        listState.scrollToItem(0)
    }

    val currentLinkToCopy = remember(state.loadedSourceUrl, state.baseUrl, state.currentPage?.url, state.activeSite.preferAndroidMd) {
        state.loadedSourceUrl ?: state.currentPage?.let {
            if (state.activeSite.preferAndroidMd) {
                ExamDocsRepository.buildAndroidMarkdownUrl(state.baseUrl, it.url)
            } else {
                ExamDocsRepository.buildMarkdownUrl(state.baseUrl, it.url)
            }
        }
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
                                text = state.currentPage?.name ?: state.activeSite.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val subtitle = state.currentBreadcrumb.joinToString(" / ")
                                .ifBlank { state.activeSite.name }
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
                                imageVector = HugeIcons.Book02,
                                contentDescription = "文档目录",
                            )
                        }
                        IconButton(
                            onClick = { showOutlineSheet = true },
                            enabled = state.headings.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = HugeIcons.LeftToRightListBullet,
                                contentDescription = "本页大纲",
                            )
                        }
                        ExamDocsMoreMenu(
                            canCopyLink = currentLinkToCopy != null,
                            onRefresh = { vm.refreshCurrentPage() },
                            onCopyLink = {
                                val link = currentLinkToCopy ?: return@ExamDocsMoreMenu
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("doc_md_url", link))
                                    )
                                    toaster.show("已复制 Markdown 链接", type = ToastType.Success)
                                }
                            },
                            onOpenSiteSettings = { showSiteSettingsDialog = true },
                        )
                    },
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
                        onEditUrl = { showSiteSettingsDialog = true },
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

                state.renderItems.isNotEmpty() -> {
                    val registry = remember { ExamDocsComponentRegistry() }
                    val handleLinkClick = rememberExamDocsLinkHandler(
                        baseUrl = state.baseUrl,
                        currentPageUrl = state.currentPage?.url,
                        onNavigateDoc = { path -> vm.openByUrl(path) },
                    )
                    val imageResolver: (String) -> String = remember(state.baseUrl, state.currentPage?.url) {
                        { rawUrl -> resolveExamDocsUrl(state.baseUrl, state.currentPage?.url, rawUrl) }
                    }
                    val hasHeaderMeta = state.currentPage != null

                    SelectableContent {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (hasHeaderMeta) {
                                item(key = "doc_meta_header") {
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
                                        if (currentLinkToCopy != null) {
                                            val suffix = if (state.isAndroidFormat) ".android.md" else ".md"
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
                                                            ClipEntry(ClipData.newPlainText("md_url", currentLinkToCopy))
                                                        )
                                                        toaster.show("已复制 $suffix 链接", type = ToastType.Success)
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
                                                        text = (state.currentPage?.url ?: "") + suffix,
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
                            }

                            items(
                                items = state.renderItems,
                                key = { it.id },
                            ) { item ->
                                ExamDocsRenderItemView(
                                    item = item,
                                    onLinkClick = handleLinkClick,
                                    imageUrlResolver = imageResolver,
                                    registry = registry,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            if (state.prevPage != null || state.nextPage != null) {
                                item(key = "doc_footer_nav") {
                                    Column {
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
                                        Spacer(Modifier.height(24.dp))
                                    }
                                }
                            } else {
                                item(key = "doc_footer_spacer") {
                                    Spacer(Modifier.height(24.dp))
                                }
                            }
                        }
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
                sites = state.sites,
                activeSite = state.activeSite,
                modules = state.modules,
                selectedModuleIndex = state.selectedModuleIndex,
                currentPageUrl = state.currentPage?.url,
                searchQuery = state.searchQuery,
                searchResults = state.searchResults,
                isLoadingTree = state.isLoadingTree,
                onSelectSite = vm::selectSite,
                onManageSites = {
                    showDirectorySheet = false
                    showSiteSettingsDialog = true
                },
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
        val headerOffset = if (state.currentPage != null) 1 else 0
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = {
                                showOutlineSheet = false
                                val targetIndex = (heading.renderItemIndex + headerOffset)
                                    .coerceIn(0, (state.renderItems.size + headerOffset - 1).coerceAtLeast(0))
                                scope.launch {
                                    listState.animateScrollToItem(targetIndex)
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

    if (showSiteSettingsDialog) {
        ExamDocsSiteSettingsDialog(
            sites = state.sites,
            activeSiteId = state.activeSite.id,
            onDismiss = { showSiteSettingsDialog = false },
            onSelectSite = { siteId ->
                showSiteSettingsDialog = false
                vm.selectSite(siteId)
            },
            onSaveSite = vm::saveSite,
            onDeleteSite = vm::deleteSite,
            onResetDefaults = vm::resetDefaultSites,
        )
    }
}

/**
 * 复用 ChatPageMenu 的 Material 3 Expressive 菜单样式
 */
@Composable
private fun ExamDocsMoreMenu(
    canCopyLink: Boolean,
    onRefresh: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenSiteSettings: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    @Composable
    fun MenuItem(
        text: String,
        icon: ImageVector,
        index: Int,
        count: Int,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        DropdownMenuItem(
            text = { Text(text) },
            onClick = {
                expanded = false
                onClick()
            },
            shape = MenuDefaults.itemShape(index = index, count = count).shape,
            leadingIcon = { Icon(icon, contentDescription = null) },
            enabled = enabled,
            colors = MenuDefaults.itemColors(),
        )
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(HugeIcons.MoreVertical, stringResource(R.string.more_options))
        }
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                MenuItem(
                    text = "重新加载",
                    icon = HugeIcons.Refresh03,
                    index = 0,
                    count = 2,
                    onClick = onRefresh,
                )
                MenuItem(
                    text = "复制 Markdown 链接",
                    icon = HugeIcons.Copy01,
                    index = 1,
                    count = 2,
                    enabled = canCopyLink,
                    onClick = onCopyLink,
                )
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                MenuItem(
                    text = "站点设置",
                    icon = HugeIcons.Settings03,
                    index = 0,
                    count = 1,
                    onClick = onOpenSiteSettings,
                )
            }
        }
    }
}

@Composable
private fun ExamDocsDirectorySheetContent(
    sites: List<DocsSite>,
    activeSite: DocsSite,
    modules: List<ExamDocNode.Folder>,
    selectedModuleIndex: Int,
    currentPageUrl: String?,
    searchQuery: String,
    searchResults: List<ExamDocFlatEntry>,
    isLoadingTree: Boolean,
    onSelectSite: (String) -> Unit,
    onManageSites: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectModule: (Int) -> Unit,
    onSelectPage: (ExamDocNode.Page) -> Unit,
) {
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }
    var siteMenuExpanded by remember { mutableStateOf(false) }

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

        // 搜索框上方的站点选择下拉菜单
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { siteMenuExpanded = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Book02,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeSite.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = activeSite.baseUrl.removePrefix("https://").removePrefix("http://"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = HugeIcons.ArrowDown01,
                        contentDescription = "切换站点",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DropdownMenuPopup(
                expanded = siteMenuExpanded,
                onDismissRequest = { siteMenuExpanded = false },
            ) {
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                    sites.forEachIndexed { index, site ->
                        val isSelected = site.id == activeSite.id
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = site.name,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    Text(
                                        text = site.baseUrl.removePrefix("https://").removePrefix("http://"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isSelected) HugeIcons.Tick01 else HugeIcons.Book02,
                                    contentDescription = null,
                                )
                            },
                            shape = MenuDefaults.itemShape(index = index, count = sites.size).shape,
                            onClick = {
                                siteMenuExpanded = false
                                onSelectSite(site.id)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                    DropdownMenuItem(
                        text = { Text("管理站点...") },
                        leadingIcon = { Icon(HugeIcons.Settings03, contentDescription = null) },
                        shape = MenuDefaults.itemShape(index = 0, count = 1).shape,
                        onClick = {
                            siteMenuExpanded = false
                            onManageSites()
                        },
                    )
                }
            }
        }

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

        if (isLoadingTree && modules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        } else {
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
                                pathKey = "${activeSite.id}/${activeModule.name}/${node.name}",
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
    secondaryLabel: String = "站点设置",
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
private fun ExamDocsSiteSettingsDialog(
    sites: List<DocsSite>,
    activeSiteId: String,
    onDismiss: () -> Unit,
    onSelectSite: (String) -> Unit,
    onSaveSite: (existingId: String?, name: String, baseUrl: String, preferAndroidMd: Boolean, activate: Boolean) -> Unit,
    onDeleteSite: (String) -> Unit,
    onResetDefaults: () -> Unit,
) {
    var editingSite by remember { mutableStateOf<DocsSite?>(null) }
    var showAddForm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文档站点设置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "支持 Exam Docs、Study Docs 及自定义 Fumadocs 站点（请求 /doc-sources.json 与 .android.md/.md）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(sites, key = { it.id }) { site ->
                        val active = site.id == activeSiteId
                        Surface(
                            onClick = { onSelectSite(site.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                1.dp,
                                if (active) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                },
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = site.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = site.baseUrl.removePrefix("https://").removePrefix("http://"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = { editingSite = site },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = HugeIcons.Edit03,
                                        contentDescription = "编辑",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                if (sites.size > 1) {
                                    IconButton(
                                        onClick = { onDeleteSite(site.id) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = HugeIcons.Delete01,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onResetDefaults,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Text("恢复默认站点")
                    }
                    OutlinedButton(
                        onClick = { showAddForm = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Add01,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("添加站点")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("完成")
            }
        },
    )

    val targetForForm = editingSite
    if (showAddForm || targetForForm != null) {
        SiteEditDialog(
            initial = targetForForm,
            onDismiss = {
                showAddForm = false
                editingSite = null
            },
            onConfirm = { name, url, preferAndroid ->
                onSaveSite(targetForForm?.id, name, url, preferAndroid, true)
                showAddForm = false
                editingSite = null
                onDismiss()
            },
        )
    }
}

@Composable
private fun SiteEditDialog(
    initial: DocsSite?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, preferAndroidMd: Boolean) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember(initial) { mutableStateOf(initial?.baseUrl ?: "") }
    var preferAndroidMd by remember(initial) { mutableStateOf(initial?.preferAndroidMd ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加文档站点" else "编辑文档站点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("站点名称") },
                    placeholder = { Text("例如：Study Docs") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    singleLine = true,
                    label = { Text("站点根地址 (Base URL)") },
                    placeholder = { Text("https://study-docs.any1.tech") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "优先加载 Android.md",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "不可用时自动回退到 .md",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = preferAndroidMd,
                        onCheckedChange = { preferAndroidMd = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, baseUrl, preferAndroidMd) },
                enabled = baseUrl.isNotBlank(),
            ) {
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
