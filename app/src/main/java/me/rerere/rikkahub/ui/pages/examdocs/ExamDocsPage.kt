package me.rerere.rikkahub.ui.pages.examdocs

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.examdocs.DocHeading
import me.rerere.rikkahub.data.examdocs.ExamDocNode
import me.rerere.rikkahub.data.examdocs.ExamDocsDocument
import me.rerere.rikkahub.data.examdocs.ExamDocsLink
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.data.examdocs.getExamDocsLink
import me.rerere.rikkahub.data.examdocs.resolveExamDocsUrl
import me.rerere.rikkahub.ui.components.ai.ChatTopBarCapsule
import me.rerere.rikkahub.ui.components.nav.TopBarMenuItem
import me.rerere.rikkahub.ui.components.nav.TopBarMoreMenu
import me.rerere.rikkahub.ui.components.richtext.SelectableContent
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.chat.AssistantBackground
import me.rerere.rikkahub.ui.pages.chat.topFadingEdge
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private val externalLinkSchemes = setOf("https", "http", "mailto", "tel")

@Composable
fun ExamDocsPage(
    initialUrl: String? = null,
    vm: ExamDocsVM = koinViewModel(parameters = { parametersOf(initialUrl) }),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val canRenderDocument by produceState(false, lifecycle) {
        // Navigation 3 resumes the destination when the entrance transition settles.
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        value = true
    }
    var showOutlineSheet by rememberSaveable { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    val page = state.currentPage
    val document = state.document
    val listState = key(state.activeSite.baseUrl, page?.url) { rememberLazyListState() }
    val hazeState = rememberHazeState()
    val listScrolledUnderTopBar by remember(listState, document) {
        derivedStateOf { document != null && listState.canScrollBackward }
    }
    val docsDisplaySetting = remember(state.uiSettings, settings.displaySetting) {
        settings.displaySetting.copy(
            enableBlurEffect = state.uiSettings.enableBlurEffect,
            backgroundEffectType = state.uiSettings.backgroundEffectType,
            fontSizeRatio = state.uiSettings.fontSizeRatio,
        )
    }
    val docsSettings = remember(settings, docsDisplaySetting) {
        settings.copy(displaySetting = docsDisplaySetting)
    }

    LaunchedEffect(canRenderDocument, document, state.pendingAnchor) {
        val anchor = state.pendingAnchor
        if (canRenderDocument && document != null && anchor != null) {
            document.headings.firstOrNull { it.anchor == anchor }?.let {
                listState.scrollToItem(it.blockIndex)
            }
            vm.consumeAnchor(anchor)
        }
    }

    val drawerContent: @Composable () -> Unit = {
        ExamDocsDirectoryDrawer(
            state = state,
            onSelectSite = vm::switchSite,
            onSelectModule = vm::selectModule,
            onSearchQueryChange = vm::updateSearchQuery,
            onRetry = vm::reload,
            onSelectPage = { selectedPage ->
                if (!isBigScreen) {
                    scope.launch { drawerState.close() }
                }
                vm.openPage(selectedPage)
            },
            onOpenAccount = {
                if (!isBigScreen) {
                    scope.launch { drawerState.close() }
                }
                navController.navigate(Screen.ExamDocsAccount)
            },
        )
    }

    val pageContent: @Composable () -> Unit = {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            AssistantBackground(setting = settings, modifier = Modifier.hazeSource(hazeState))
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        navigationIcon = {
                            if (!isBigScreen) {
                                ChatTopBarCapsule(
                                    hazeState = hazeState,
                                    displaySetting = docsDisplaySetting,
                                    showBackground = listScrolledUnderTopBar,
                                    modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                                ) {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        enabled = state.sitesReady,
                                    ) {
                                        Icon(HugeIcons.LeftToRightListBullet, "文档目录")
                                    }
                                }
                            }
                        },
                        title = {
                            Surface(
                                onClick = {
                                    if (!isBigScreen && state.sitesReady) {
                                        scope.launch { drawerState.open() }
                                    }
                                },
                                color = Color.Transparent,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                    Text(
                                        page?.name ?: state.activeSite.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (page != null) {
                                        val subtitle = remember(state.activeSite.name, state.currentBreadcrumb) {
                                            (listOf(state.activeSite.name) + state.currentBreadcrumb).joinToString(" / ")
                                        }
                                        Text(
                                            subtitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            ChatTopBarCapsule(
                                hazeState = hazeState,
                                displaySetting = docsDisplaySetting,
                                showBackground = listScrolledUnderTopBar,
                                modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                            ) {
                                IconButton(
                                    onClick = { showOutlineSheet = true },
                                    enabled = !document?.headings.isNullOrEmpty(),
                                ) {
                                    Icon(HugeIcons.Book02, "本页大纲")
                                }
                                TopBarMoreMenu(
                                    listOf(
                                        TopBarMenuItem(
                                            "重新加载",
                                            HugeIcons.Refresh03,
                                            vm::reload,
                                            state.sitesReady && !state.isLoadingTree,
                                        ),
                                        TopBarMenuItem(
                                            "复制链接",
                                            HugeIcons.Copy01,
                                            onClick = {
                                                document?.sourceUrl?.let { url ->
                                                    scope.launch {
                                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Link", url)))
                                                        toaster.show("已复制链接", type = ToastType.Success)
                                                    }
                                                }
                                            },
                                            enabled = document != null,
                                        ),
                                        TopBarMenuItem(
                                            "答题统计",
                                            HugeIcons.ChartColumn,
                                            { navController.navigate(Screen.ExamDocsStats) },
                                            state.sitesReady,
                                        ),
                                    ),
                                    listOf(
                                        TopBarMenuItem(
                                            "设置",
                                            HugeIcons.Settings03,
                                            { navController.navigate(Screen.ExamDocsSettings) },
                                            state.sitesReady,
                                        ),
                                    ),
                                    hazeState = hazeState,
                                    displaySetting = docsDisplaySetting,
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val treeError = state.treeError
                    val contentError = state.contentError
                    when {
                        state.isLoadingTree && (page == null || document == null) ->
                            Box(Modifier.padding(innerPadding)) { ReaderLoading("正在获取文档目录…") }
                        treeError != null -> Box(Modifier.padding(innerPadding)) {
                            ErrorStateView(
                                title = "无法获取文档目录",
                                message = treeError,
                                onRetry = vm::reload,
                                onSecondaryAction = { navController.navigate(Screen.ExamDocsSettings) },
                                secondaryLabel = "设置",
                            )
                        }
                        state.isLoadingContent || (document != null && !canRenderDocument) ->
                            Box(Modifier.padding(innerPadding)) { ReaderLoading("正在加载文档…") }
                        contentError != null -> Box(Modifier.padding(innerPadding)) {
                            ErrorStateView(
                                title = "文档加载失败",
                                message = contentError,
                                onRetry = vm::reload,
                                onSecondaryAction = { scope.launch { drawerState.open() } },
                                secondaryLabel = "打开目录",
                            )
                        }
                        document != null && page != null -> CompositionLocalProvider(LocalSettings provides docsSettings) {
                            ExamDocsProgressProvider(state.activeSite, page.url, document) {
                                Box(Modifier.fillMaxSize()) {
                                    ExamDocsArticle(
                                        document = document,
                                        listState = listState,
                                        baseUrl = state.activeSite.baseUrl,
                                        pageUrl = page.url,
                                        hazeState = hazeState,
                                        topPadding = innerPadding.calculateTopPadding(),
                                        bottomPadding = innerPadding.calculateBottomPadding(),
                                        fontSizeRatio = state.uiSettings.fontSizeRatio,
                                        previous = state.prevPage,
                                        next = state.nextPage,
                                        onPage = vm::openPage,
                                        onLink = vm::openLink,
                                    )
                                    ExamDocsQuestionDock(
                                        document = document,
                                        listState = listState,
                                        hazeState = hazeState,
                                        displaySetting = docsDisplaySetting,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(
                                                end = 16.dp,
                                                bottom = innerPadding.calculateBottomPadding() + 16.dp,
                                            ),
                                    )
                                }
                            }
                        }
                        else -> Box(Modifier.padding(innerPadding)) {
                            Button(onClick = { scope.launch { drawerState.open() } }) { Text("浏览文档目录") }
                        }
                    }
                }
            }
        }
    }

    if (isBigScreen) {
        PermanentNavigationDrawer(
            drawerContent = drawerContent,
            content = pageContent,
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = drawerContent,
            content = pageContent,
        )
    }

    if (showOutlineSheet) {
        ModalBottomSheet(onDismissRequest = { showOutlineSheet = false }) {
            val activeHeadingIndex = remember(document?.headings, listState.firstVisibleItemIndex) {
                val headings = document?.headings.orEmpty()
                headings.indexOfLast { it.blockIndex <= listState.firstVisibleItemIndex }.coerceAtLeast(0)
            }
            ExamDocsOutlineSheet(
                headings = document?.headings.orEmpty(),
                activeIndex = activeHeadingIndex,
            ) { heading ->
                showOutlineSheet = false
                scope.launch { listState.scrollToItem(heading.blockIndex) }
            }
        }
    }
}

@Composable
private fun ExamDocsArticle(
    document: ExamDocsDocument,
    listState: LazyListState,
    baseUrl: String,
    pageUrl: String,
    hazeState: HazeState,
    topPadding: Dp,
    bottomPadding: Dp,
    fontSizeRatio: Float,
    previous: ExamDocNode.Page?,
    next: ExamDocNode.Page?,
    onPage: (ExamDocNode.Page) -> Unit,
    onLink: (ExamDocsLink) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val imageResolver: (String) -> String = remember(baseUrl, pageUrl) {
        { resolveExamDocsUrl(baseUrl, pageUrl, it) }
    }
    val handleLink: (String) -> Unit = remember(baseUrl, pageUrl, onLink, uriHandler) {
        { raw ->
            val link = getExamDocsLink(baseUrl, pageUrl, raw)
            if (link != null) {
                onLink(link)
            } else {
                val target = resolveExamDocsUrl(baseUrl, pageUrl, raw)
                if (target.substringBefore(':').lowercase() in externalLinkSchemes) {
                    runCatching { uriHandler.openUri(target) }
                }
            }
        }
    }
    val registry = remember(handleLink) { ExamDocsComponentRegistry(onLinkClick = handleLink) }
    val renderMarkdown = rememberExamDocsRenderer(document, listState, handleLink, imageResolver)
    val baseStyle = LocalTextStyle.current
    val scaledStyle = remember(baseStyle, fontSizeRatio) {
        baseStyle.copy(
            fontSize = baseStyle.fontSize * fontSizeRatio,
            lineHeight = baseStyle.lineHeight * fontSizeRatio,
        )
    }

    CompositionLocalProvider(LocalTextStyle provides scaledStyle) {
        SelectableContent {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .topFadingEdge(topPadding + 24.dp)
                    .hazeSource(hazeState),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topPadding + 14.dp,
                    bottom = bottomPadding + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(document.blocks, contentType = { block ->
                    when (block) {
                        is ExamDocsSegment.Markdown -> block.document.blocks.firstOrNull()?.javaClass
                        is ExamDocsSegment.CustomBlock -> block.type
                    }
                }) { block ->
                    ExamDocsMarkdownBlock(block, registry, renderMarkdown)
                }
                if (previous != null || next != null) {
                    item(key = "page-navigation") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (previous != null) {
                                PageNavCard("上一节", previous.name, false, { onPage(previous) }, Modifier.weight(1f))
                            } else Spacer(Modifier.weight(1f))
                            if (next != null) {
                                PageNavCard("下一节", next.name, true, { onPage(next) }, Modifier.weight(1f))
                            } else Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamDocsOutlineSheet(
    headings: List<DocHeading>,
    activeIndex: Int,
    onSelect: (DocHeading) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val minLevel = remember(headings) { headings.minOfOrNull { it.level } ?: 1 }
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.62f).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "本页大纲",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                shape = CircleShape,
                color = colors.surfaceContainerHigh,
            ) {
                Text(
                    "${headings.size} 节",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(headings, key = { _, heading -> heading.blockIndex }) { index, heading ->
                val isActive = index == activeIndex
                val indent = ((heading.level - minLevel).coerceAtLeast(0).coerceAtMost(4) * 14).dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) colors.primary.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { onSelect(heading) }
                        .padding(
                            start = 10.dp + indent,
                            end = 12.dp,
                            top = 10.dp,
                            bottom = 10.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(if (heading.level <= minLevel) 16.dp else 12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) {
                                    colors.primary
                                } else {
                                    colors.outlineVariant.copy(alpha = 0.55f)
                                },
                            ),
                    )
                    Text(
                        text = heading.title,
                        style = if (heading.level <= minLevel) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        fontWeight = when {
                            isActive -> FontWeight.SemiBold
                            heading.level <= minLevel -> FontWeight.Medium
                            else -> FontWeight.Normal
                        },
                        color = when {
                            isActive -> colors.primary
                            heading.level <= minLevel -> colors.onSurface
                            else -> colors.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderLoading(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator()
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onSecondaryAction: () -> Unit,
    secondaryLabel: String,
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
                OutlinedButton(onClick = onSecondaryAction) {
                    Text(secondaryLabel)
                }
                Button(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
    }
}
