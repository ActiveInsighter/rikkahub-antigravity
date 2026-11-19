package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CloudUpload
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.PaintBoard
import me.rerere.hugeicons.stroke.Refresh
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.workflow.WorkflowLibraryRepository
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.ui.sketch.SketchDialog
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.ImageUploadState
import org.koin.compose.koinInject

@Composable
internal fun MediaFileInputRow(
    state: ChatInputState,
) {
    val filesManager: FilesManager = koinInject()
    val libraryRepository: WorkflowLibraryRepository = koinInject()
    val managedFiles by filesManager.observe().collectAsState(initial = emptyList())
    val displayNameByRelativePath = remember(managedFiles) {
        managedFiles.associate { it.relativePath to it.displayName }
    }
    val displayNameByFileName = remember(managedFiles) {
        managedFiles.associate { it.relativePath.substringAfterLast('/') to it.displayName }
    }

    fun removePart(part: UIMessagePart, url: String) {
        state.messageContent = state.messageContent.filterNot { it == part }
        state.setImageUploadState(url, null)
        if (state.shouldDeleteFileOnRemove(part)) {
            filesManager.deleteChatFiles(listOf(url.toUri()))
        }
    }

    // 正在哪张图片上画，非空时显示画板
    val scope = rememberCoroutineScope()
    var drawingOn by remember { mutableStateOf<UIMessagePart.Image?>(null) }
    drawingOn?.let { part ->
        SketchDialog(
            image = part.url.toUri(),
            onDismiss = { drawingOn = null },
            onConfirm = { result ->
                drawingOn = null
                scope.launch {
                    val uri = filesManager.createChatFileBySketch(result) ?: return@launch
                    // 画好的图换掉原来那张，位置不变；等保存的这会儿原图已经被移走的话就不加了
                    if (part !in state.messageContent) {
                        filesManager.deleteChatFiles(listOf(uri))
                        return@launch
                    }
                    state.messageContent = state.messageContent.map {
                        if (it == part) UIMessagePart.Image(uri.toString()) else it
                    }
                    state.setImageUploadState(part.url, null)
                    if (state.shouldDeleteFileOnRemove(part)) {
                        filesManager.deleteChatFiles(listOf(part.url.toUri()))
                    }
                    launchR2ImageUpload(
                        scope = scope,
                        inputState = state,
                        filesManager = filesManager,
                        libraryRepository = libraryRepository,
                        uri = uri,
                    )
                }
            },
        )
    }

    // 点开预览时从哪张图片开始，非空时显示预览；预览里可以切到画板在当前图片上画
    var previewing by remember { mutableStateOf<UIMessagePart.Image?>(null) }
    previewing?.let { start ->
        val images = state.messageContent.filterIsInstance<UIMessagePart.Image>()
        ImagePreviewDialog(
            images = images.map { it.url },
            initialPage = images.indexOf(start).coerceAtLeast(0),
            onDismissRequest = { previewing = null },
            actions = { page ->
                IconButton(
                    onClick = {
                        previewing = null
                        drawingOn = images.getOrNull(page)
                    }
                ) {
                    Icon(
                        imageVector = HugeIcons.PaintBoard,
                        contentDescription = stringResource(R.string.sketch_draw_on_image),
                        tint = Color.White,
                    )
                }
            },
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        state.messageContent.fastForEach { part ->
            when (part) {
                is UIMessagePart.Image -> {
                    val uploadState = state.imageUploadStates[part.url]
                    val baseTitle = attachmentNameFromUrl(
                        url = part.url,
                        fallback = "image",
                        displayNameByRelativePath = displayNameByRelativePath,
                        displayNameByFileName = displayNameByFileName
                    )
                    val chipTitle = when (uploadState) {
                        is ImageUploadState.Uploading -> "上传中 ${(uploadState.progress * 100).toInt().coerceIn(0, 100)}%"
                        is ImageUploadState.Failed -> "上传失败 · 点击重试"
                        null -> baseTitle
                    }
                    AttachmentChip(
                        title = chipTitle,
                        leading = {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = part.url,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    when (uploadState) {
                                        is ImageUploadState.Uploading -> {
                                            val animatedProgress by animateFloatAsState(
                                                targetValue = uploadState.progress.coerceIn(0.05f, 1f),
                                                label = "r2_upload_progress",
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.48f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(
                                                    progress = { animatedProgress },
                                                    modifier = Modifier.size(20.dp),
                                                    color = Color.White,
                                                    trackColor = Color.White.copy(alpha = 0.28f),
                                                    strokeWidth = 2.4.dp,
                                                )
                                            }
                                        }
                                        is ImageUploadState.Failed -> {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = HugeIcons.Refresh,
                                                    contentDescription = "重试上传",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                        null -> Unit
                                    }
                                }
                            }
                        },
                        onRemove = { removePart(part, part.url) },
                        onClick = {
                            if (uploadState is ImageUploadState.Failed) {
                                launchR2ImageUpload(
                                    scope = scope,
                                    inputState = state,
                                    filesManager = filesManager,
                                    libraryRepository = libraryRepository,
                                    uri = part.url.toUri(),
                                )
                            } else {
                                previewing = part
                            }
                        },
                    )
                }

                is UIMessagePart.Video -> {
                    AttachmentChip(
                        title = attachmentNameFromUrl(
                            url = part.url,
                            fallback = "video",
                            displayNameByRelativePath = displayNameByRelativePath,
                            displayNameByFileName = displayNameByFileName
                        ),
                        leading = { AttachmentLeadingIcon(icon = HugeIcons.Video01) },
                        onRemove = { removePart(part, part.url) }
                    )
                }

                is UIMessagePart.Audio -> {
                    AttachmentChip(
                        title = attachmentNameFromUrl(
                            url = part.url,
                            fallback = "audio",
                            displayNameByRelativePath = displayNameByRelativePath,
                            displayNameByFileName = displayNameByFileName
                        ),
                        leading = { AttachmentLeadingIcon(icon = HugeIcons.MusicNote03) },
                        onRemove = { removePart(part, part.url) }
                    )
                }

                is UIMessagePart.Document -> {
                    AttachmentChip(
                        title = attachmentNameFromUrl(
                            url = part.url,
                            fallback = part.fileName,
                            displayNameByRelativePath = displayNameByRelativePath,
                            displayNameByFileName = displayNameByFileName
                        ),
                        leading = { AttachmentLeadingIcon(icon = HugeIcons.Files02) },
                        onRemove = { removePart(part, part.url) }
                    )
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    title: String,
    leading: @Composable () -> Unit,
    onRemove: () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leading()
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(min = 40.dp, max = 180.dp),
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .size(26.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = HugeIcons.Cancel01,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentLeadingIcon(
    icon: ImageVector,
) {
    Surface(
        modifier = Modifier.size(34.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun attachmentNameFromUrl(
    url: String,
    fallback: String,
    displayNameByRelativePath: Map<String, String>,
    displayNameByFileName: Map<String, String>,
): String {
    val parsed = runCatching { url.toUri() }.getOrNull()
    val relativePath = parsed?.path?.substringAfter("/files/", missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
    if (relativePath != null) {
        displayNameByRelativePath[relativePath]?.let { return it }
    }

    val storedFileName = parsed?.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    if (storedFileName != null) {
        displayNameByFileName[storedFileName]?.let { return it }
        return storedFileName
    }

    return fallback
}
