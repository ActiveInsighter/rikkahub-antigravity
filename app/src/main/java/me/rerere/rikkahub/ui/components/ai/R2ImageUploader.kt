package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    if (!file.exists()) return

    val mimeType = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/png"
    }
    val fileName = filesManager.getFileNameFromUri(uri)
        ?.takeIf { it.isNotBlank() }
        ?: file.name.ifBlank { "image.${file.extension.ifBlank { "png" }}" }

    inputState.setImageUploadState(url, ImageUploadState.Uploading(0f))
    scope.launch {
        val result = libraryRepository.uploadLibraryImage(
            file = file,
            fileName = fileName,
            mimeType = mimeType,
            onProgress = { progress ->
                scope.launch(Dispatchers.Main.immediate) {
                    if (inputState.imageUploadStates[url] is ImageUploadState.Uploading) {
                        inputState.setImageUploadState(url, ImageUploadState.Uploading(progress))
                    }
                }
            },
        )
        withContext(Dispatchers.Main.immediate) {
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
