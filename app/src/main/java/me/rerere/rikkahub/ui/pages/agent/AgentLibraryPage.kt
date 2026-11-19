package me.rerere.rikkahub.ui.pages.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.workflow.LibraryImageItem
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgentLibraryPage() {
    val vm: AgentLibraryVM = koinViewModel()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var previewItem by remember { mutableStateOf<LibraryImageItem?>(null) }
    var deletingItem by remember { mutableStateOf<LibraryImageItem?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            toaster.show(msg, type = ToastType.Error)
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("图片资料库") },
                subtitle = {
                    val subtitle = when {
                        uiState.selectedUrls.isNotEmpty() -> "已选择 ${uiState.selectedUrls.size} 张"
                        uiState.isSignedIn -> "共 ${uiState.items.size} 张已上传图片"
                        else -> "未登录工作流账号"
                    }
                    Text(subtitle)
                },
                navigationIcon = { BackButton() },
                actions = {
                    if (uiState.selectedUrls.isNotEmpty()) {
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(
                                imageVector = HugeIcons.Cancel01,
                                contentDescription = "取消选择",
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                vm.reuseImages(uiState.selectedUrls.toList())
                                navController.popBackStack()
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Add01,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("复用 (${uiState.selectedUrls.size})")
                        }
                    } else if (uiState.isSignedIn) {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(
                                imageVector = HugeIcons.Refresh03,
                                contentDescription = "刷新",
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!uiState.isSignedIn) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Image02,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "请先在 Agent 设置中登录工作流账号",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "登录后，拍照、相册选择或绘制的草图会自动上传至 Cloudflare R2，并保存在此处以便随时复用。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { vm.updateSearchQuery(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索文件名") },
                    leadingIcon = {
                        Icon(
                            imageVector = HugeIcons.Search01,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { vm.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = HugeIcons.Cancel01,
                                    contentDescription = "清除搜索",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )

                FilterChip(
                    selected = !uiState.sortNewestFirst,
                    onClick = { vm.toggleSortOrder() },
                    label = { Text(if (uiState.sortNewestFirst) "最新" else "最早") },
                    leadingIcon = {
                        Icon(
                            imageVector = HugeIcons.Clock01,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }

            if (uiState.items.isEmpty() && !uiState.isLoading) {
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
                        Icon(
                            imageVector = HugeIcons.Image02,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (uiState.searchQuery.isBlank()) "暂无已上传图片" else "未找到匹配的图片",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "在对话中添加图片、拍照或绘制草图后，图片会自动上传到这里。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    uiState.groups.forEach { group ->
                        item(
                            key = "header-${group.label}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = group.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "${group.items.size} 张",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        items(
                            items = group.items,
                            key = { it.id },
                        ) { item ->
                            val isSelected = item.sourceUrl in uiState.selectedUrls
                            LibraryImageGridItem(
                                item = item,
                                selected = isSelected,
                                onClick = {
                                    if (uiState.selectedUrls.isNotEmpty()) {
                                        vm.toggleSelection(item.sourceUrl)
                                    } else {
                                        previewItem = item
                                    }
                                },
                                onLongClick = {
                                    vm.toggleSelection(item.sourceUrl)
                                },
                            )
                        }
                    }

                    if (uiState.cursor.isNotBlank()) {
                        item(
                            key = "load-more",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (uiState.isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    TextButton(onClick = { vm.loadMore() }) {
                                        Text("加载更多")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewItem?.let { current ->
        val urls = remember(uiState.items) { uiState.items.map { it.sourceUrl } }
        val initialIndex = urls.indexOf(current.sourceUrl).coerceAtLeast(0)
        ImagePreviewDialog(
            images = if (urls.isNotEmpty()) urls else listOf(current.sourceUrl),
            initialPage = initialIndex,
            actions = { page ->
                val target = uiState.items.getOrNull(page) ?: current
                Button(
                    onClick = {
                        vm.reuseImages(listOf(target.sourceUrl))
                        previewItem = null
                        navController.popBackStack()
                    },
                ) {
                    Icon(
                        imageVector = HugeIcons.Add01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("复用图片")
                }
                IconButton(
                    onClick = {
                        deletingItem = target
                    },
                ) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = "删除图片",
                        tint = Color.White,
                    )
                }
            },
            onDismissRequest = { previewItem = null },
        )
    }

    deletingItem?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("删除图片") },
            text = { Text("确定要从 Cloudflare R2 资料库中永久删除「${item.fileName}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteImage(item)
                        if (previewItem?.id == item.id) {
                            previewItem = null
                        }
                        deletingItem = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryImageGridItem(
    item: LibraryImageItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = if (selected) {
            BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.sourceUrl,
                contentDescription = item.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.Tick01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
