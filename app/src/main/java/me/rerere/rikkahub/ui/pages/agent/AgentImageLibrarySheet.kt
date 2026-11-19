package me.rerere.rikkahub.ui.pages.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Refresh
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.data.workflow.LibraryImage
import me.rerere.rikkahub.data.workflow.WorkflowLibraryRepository
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.compose.koinInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AgentImageLibrarySheet(
    onReuseImage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val libraryRepository: WorkflowLibraryRepository = koinInject()
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Expanded,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    var sortOrder by remember { mutableStateOf("newest") }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }

    var images by remember { mutableStateOf<List<LibraryImage>>(emptyList()) }
    var nextCursor by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var previewUrl by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<LibraryImage?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        delay(250)
        debouncedQuery = searchQuery.trim()
    }

    fun loadInitial() {
        scope.launch {
            isLoading = true
            errorMessage = null
            libraryRepository.listLibraryImages(
                cursor = "",
                query = debouncedQuery,
                limit = 60,
                sort = sortOrder,
            ).onSuccess { page ->
                images = page.items
                nextCursor = page.cursor
            }.onFailure { err ->
                errorMessage = err.message ?: "加载图片资料库失败"
            }
            isLoading = false
        }
    }

    fun loadMore() {
        if (isLoadingMore || nextCursor.isBlank()) return
        scope.launch {
            isLoadingMore = true
            libraryRepository.listLibraryImages(
                cursor = nextCursor,
                query = debouncedQuery,
                limit = 60,
                sort = sortOrder,
            ).onSuccess { page ->
                val existingIds = images.mapTo(HashSet()) { it.id }
                images = images + page.items.filterNot { it.id in existingIds }
                nextCursor = page.cursor
            }.onFailure { err ->
                errorMessage = err.message ?: "加载更多失败"
            }
            isLoadingMore = false
        }
    }

    LaunchedEffect(sortOrder, debouncedQuery) {
        loadInitial()
    }

    val groupedByDate = remember(images) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        images.groupBy { item ->
            runCatching {
                val date = Instant.parse(item.createdAt).atZone(zone).toLocalDate()
                when (date) {
                    today -> "今天"
                    yesterday -> "昨天"
                    else -> date.format(formatter)
                }
            }.getOrDefault("未知时间")
        }
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Image02,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "资料库",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (images.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = images.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                IconButton(onClick = { loadInitial() }) {
                    Icon(
                        imageVector = HugeIcons.Refresh,
                        contentDescription = "刷新",
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("按文件名搜索已上传图片…") },
                leadingIcon = {
                    Icon(
                        imageVector = HugeIcons.Search01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = sortOrder == "newest",
                    onClick = { sortOrder = "newest" },
                    label = { Text("最新上传") },
                )
                FilterChip(
                    selected = sortOrder == "oldest",
                    onClick = { sortOrder = "oldest" },
                    label = { Text("最早上传") },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    errorMessage != null && images.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { loadInitial() }) {
                                Text("重试")
                            }
                        }
                    }

                    images.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = HugeIcons.Image02,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (debouncedQuery.isNotEmpty()) "没有匹配的图片" else "暂无已上传的图片",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "拍照、从相册选择或绘制草图后，图片会自动保存到这里以便随时复用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            groupedByDate.forEach { (dateLabel, dateItems) ->
                                item(key = "header-$dateLabel") {
                                    Text(
                                        text = dateLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                                    )
                                }
                                val rows = dateItems.chunked(3)
                                items(
                                    items = rows,
                                    key = { row -> row.joinToString("-") { it.id } },
                                ) { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        rowItems.forEach { item ->
                                            LibraryImageTile(
                                                image = item,
                                                modifier = Modifier.weight(1f),
                                                onPreview = { previewUrl = item.sourceUrl },
                                                onReuse = { onReuseImage(item.sourceUrl) },
                                                onDelete = { deleteTarget = item },
                                            )
                                        }
                                        repeat(3 - rowItems.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            if (nextCursor.isNotBlank()) {
                                item(key = "load-more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isLoadingMore) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        } else {
                                            TextButton(onClick = { loadMore() }) {
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
        }
    }

    previewUrl?.let { url ->
        ImagePreviewDialog(
            images = listOf(url),
            onDismissRequest = { previewUrl = null },
        )
    }

    deleteTarget?.let { target ->
        RikkaConfirmDialog(
            show = true,
            title = "删除图片",
            confirmText = if (isDeleting) "删除中…" else "删除",
            dismissText = "取消",
            onConfirm = {
                if (!isDeleting) {
                    scope.launch {
                        isDeleting = true
                        libraryRepository.deleteLibraryImage(target.id)
                            .onSuccess {
                                images = images.filterNot { it.id == target.id }
                                deleteTarget = null
                            }
                            .onFailure { err ->
                                errorMessage = err.message ?: "删除失败"
                                deleteTarget = null
                            }
                        isDeleting = false
                    }
                }
            },
            onDismiss = {
                if (!isDeleting) deleteTarget = null
            },
        ) {
            Text("确定要从 R2 资料库永久删除「${target.fileName}」吗？")
        }
    }
}

@Composable
private fun LibraryImageTile(
    image: LibraryImage,
    modifier: Modifier = Modifier,
    onPreview: () -> Unit,
    onReuse: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onPreview),
        ) {
            AsyncImage(
                model = image.sourceUrl,
                contentDescription = image.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Surface(
                onClick = onDelete,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatImageTime(image.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        onClick = onReuse,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Add01,
                                contentDescription = "复用",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = "复用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatImageTime(createdAt: String): String {
    if (createdAt.isBlank()) return ""
    return runCatching {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        Instant.parse(createdAt).atZone(ZoneId.systemDefault()).format(formatter)
    }.getOrDefault("")
}
