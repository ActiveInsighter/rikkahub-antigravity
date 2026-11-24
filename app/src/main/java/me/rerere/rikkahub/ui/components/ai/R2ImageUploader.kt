package me.rerere.rikkahub.ui.components.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.workflow.WorkflowLibraryRepository
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.ImageUploadState
import org.koin.compose.koinInject
import java.io.File

internal fun launchR2ImageUpload(
    scope: CoroutineScope,
    inputState: ChatInputState,
    filesManager: FilesManager,
    libraryRepository: WorkflowLibraryRepository,
    uri: Uri,
) {
    if (!libraryRepository.hasSession()) return
    val url = uri.toString()
    if (!url.startsWith("file://")) return
    val path = uri.path ?: return
    val file = File(path)
    if (!file.isFile) {
        inputState.setImageUploadState(url, ImageUploadState.Failed("找不到本地图片"))
        return
    }
    if (inputState.imageUploadStates[url] is ImageUploadState.Uploading) return

    inputState.setImageUploadState(url, ImageUploadState.Uploading(0f))
    scope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                val mimeType = bounds.outMimeType ?: error("无法读取图片格式")
                val fileName = filesManager.getFileNameFromUri(uri)?.ifBlank { null } ?: file.name
                var reportedPercent = 0
                val onProgress: (Float) -> Unit = { progress ->
                    val percent = (progress * 100).toInt()
                    if (percent > reportedPercent) {
                        reportedPercent = percent
                        scope.launch(Dispatchers.Main.immediate) {
                            if (inputState.imageUploadStates[url] is ImageUploadState.Uploading) {
                                inputState.setImageUploadState(url, ImageUploadState.Uploading(progress))
                            }
                        }
                    }
                }
                if (mimeType in setOf("image/jpeg", "image/webp", "image/gif", "image/png")) {
                    libraryRepository.uploadLibraryImage(file, fileName, mimeType, onProgress).getOrThrow()
                } else {
                    // Android can pick HEIF and other formats outside the R2 image library contract.
                    val converted = File.createTempFile("agent-image-", ".png", file.parentFile)
                    try {
                        var sampleSize = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 4096) sampleSize *= 2
                        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: error("无法解码图片")
                        try {
                            converted.outputStream().use { output ->
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "无法转换图片格式" }
                            }
                        } finally {
                            bitmap.recycle()
                        }
                        val convertedName = "${fileName.substringBeforeLast('.')}.png"
                        libraryRepository.uploadLibraryImage(converted, convertedName, "image/png", onProgress).getOrThrow()
                    } finally {
                        converted.delete()
                    }
                }
            }.onFailure { if (it is CancellationException) throw it }
        }
        withContext(Dispatchers.Main.immediate) {
            if (inputState.imageUploadStates[url] !is ImageUploadState.Uploading) return@withContext
            result.onSuccess { uploaded ->
                inputState.replaceImageUrl(url, uploaded.sourceUrl)
            }.onFailure { err ->
                inputState.setImageUploadState(
                    url,
                    ImageUploadState.Failed(err.message ?: "上传失败"),
                )
            }
        }
    }
}

@Composable
internal fun rememberR2ImageUploader(
    inputState: ChatInputState,
): (List<Uri>) -> Unit {
    val scope = rememberCoroutineScope()
    val filesManager: FilesManager = koinInject()
    val libraryRepository: WorkflowLibraryRepository = koinInject()
    return remember(inputState, scope, filesManager, libraryRepository) {
        { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                inputState.addImages(uris)
                uris.forEach { uri ->
                    launchR2ImageUpload(
                        scope = scope,
                        inputState = inputState,
                        filesManager = filesManager,
                        libraryRepository = libraryRepository,
                        uri = uri,
                    )
                }
            }
        }
    }
}
