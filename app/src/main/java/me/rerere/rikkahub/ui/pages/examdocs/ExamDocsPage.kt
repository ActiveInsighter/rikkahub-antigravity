package me.rerere.rikkahub.ui.pages.examdocs

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.data.agent.AgentSettingsStore
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
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.chat.AssistantBackground
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

private val externalLinkSchemes = setOf("https", "http", "mailto", "tel")

private enum class ReaderSheet { Directory, Outline, Settings }

@Composable
fun ExamDocsPage(
    initialUrl: String? = null,
    vm: ExamDocsVM = koinViewModel(parameters = { parametersOf(initialUrl) }),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val agentSettingsStore: AgentSettingsStore = koinInject()
    val agentSettings by agentSettingsStore.settingsFlow.collectAsStateWithLifecycle()
    val toolbarDisplaySetting = remember(agentSettings) { agentSettings.toDisplaySetting() }
    val toaster = LocalToaster.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val canRenderDocument by produceState(false, lifecycle) {
        // Navigation 3 resumes the destination when the entrance transition settles.
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        value = true
    }
    var sheet by rememberSaveable { mutableStateOf<ReaderSheet?>(null) }
    val page = state.currentPage
    val document = state.document
    val listState = key(state.activeSite.baseUrl, page?.url) { rememberLazyListState() }
    val hazeState = rememberHazeState()
    val listScrolledUnderTopBar by remember(listState, document) {
        derivedStateOf { document != null && listState.canScrollBackward }
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
                        ChatTopBarCapsule(
                            hazeState = hazeState,
                            displaySetting = toolbarDisplaySetting,
                            showBackground = listScrolledUnderTopBar,
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                        ) {
                            IconButton(
                                onClick = { sheet = ReaderSheet.Directory },
                                enabled = state.sitesReady,
                            ) {
                                Icon(HugeIcons.Menu03, "文档目录")
                            }
                        }
                    },
                    title = {
                        Column {
                            Text(
                                page?.name ?: state.activeSite.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        ChatTopBarCapsule(
                            hazeState = hazeState,
                            displaySetting = toolbarDisplaySetting,
                            showBackground = listScrolledUnderTopBar,
                            modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                        ) {
                            IconButton(
                                onClick = { sheet = ReaderSheet.Outline },
                                enabled = !document?.headings.isNullOrEmpty(),
                            ) {
                                Icon(HugeIcons.LeftToRightListBullet, "本页大纲")
                            }
                            TopBarMoreMenu(
                                listOf(
                                    TopBarMenuItem("重新加载", HugeIcons.Refresh03, vm::reload, state.sitesReady && !state.isLoadingTree),
                                    TopBarMenuItem(
                                        "复制 Markdown 链接",
                                        HugeIcons.Copy01,
                                        onClick = {
                                            document?.sourceUrl?.let { url ->
                                                scope.launch {
                                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Markdown", url)))
                                                    toaster.show("已复制 Markdown 链接", type = ToastType.Success)
                                                }
                                            }
                                        },
                                        enabled = document != null,
                                    ),
                                    TopBarMenuItem(
                                        "站点设置",
                                        HugeIcons.Settings03,
                                        { sheet = ReaderSheet.Settings },
                                        state.sitesReady,
                                    ),
                                ),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
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
                            onSecondaryAction = { sheet = ReaderSheet.Settings },
                            secondaryLabel = "站点设置",
                        )
                    }
                    state.isLoadingContent || (document != null && !canRenderDocument) ->
                        Box(Modifier.padding(innerPadding)) { ReaderLoading("正在加载文档…") }
                    contentError != null -> Box(Modifier.padding(innerPadding)) {
                        ErrorStateView(
                            title = "文档加载失败",
                            message = contentError,
                            onRetry = vm::reload,
                            onSecondaryAction = { sheet = ReaderSheet.Directory },
                            secondaryLabel = "打开目录",
                        )
                    }
                    document != null && page != null -> ExamDocsArticle(
                        document = document,
                        listState = listState,
                        baseUrl = state.activeSite.baseUrl,
                        pageUrl = page.url,
                        topPadding = innerPadding.calculateTopPadding(),
                        bottomPadding = innerPadding.calculateBottomPadding(),
                        previous = state.prevPage,
                        next = state.nextPage,
                        onPage = vm::openPage,
                        onLink = vm::openLink,
                    )
                    else -> Box(Modifier.padding(innerPadding)) {
                        Button(onClick = { sheet = ReaderSheet.Directory }) { Text("浏览文档目录") }
                    }
                }
            }
        }
    }

    when (sheet) {
        ReaderSheet.Directory -> ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ExamDocsDirectorySheet(
                state = state,
                onSelectSite = vm::switchSite,
                onSelectModule = vm::selectModule,
                onSearchQueryChange = vm::updateSearchQuery,
                onRetry = vm::reload,
                onSelectPage = {
                    sheet = null
                    vm.openPage(it)
                },
            )
        }
        ReaderSheet.Outline -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            ExamDocsOutlineSheet(document?.headings.orEmpty()) { heading ->
                sheet = null
                scope.launch { listState.scrollToItem(heading.blockIndex) }
            }
        }
        ReaderSheet.Settings -> ExamDocsSiteSettings(
            sites = state.sites,
            activeSite = state.activeSite,
            onSelectSite = vm::switchSite,
            onSaveSite = vm::saveSite,
            onRemoveSite = vm::removeSite,
            onRestoreDefaults = vm::restoreDefaultSites,
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

@Composable
private fun ExamDocsArticle(
    document: ExamDocsDocument,
    listState: LazyListState,
    baseUrl: String,
    pageUrl: String,
    topPadding: Dp,
    bottomPadding: Dp,
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

    SelectableContent {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
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

@Composable
private fun ExamDocsOutlineSheet(headings: List<DocHeading>, onSelect: (DocHeading) -> Unit) {
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.6f).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("本页大纲", style = MaterialTheme.typography.titleLarge)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(headings, key = { _, heading -> heading.blockIndex }) { _, heading ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    onClick = { onSelect(heading) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        heading.title,
                        style = if (heading.level <= 2) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = 12.dp + ((heading.level - 1) * 12).dp,
                            end = 12.dp,
                            top = 10.dp,
                            bottom = 10.dp,
                        ),
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
