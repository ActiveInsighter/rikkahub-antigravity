package me.rerere.rikkahub.ui.pages.workflow

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.workflow.LibraryDriveData
import me.rerere.rikkahub.data.workflow.LibraryFile
import me.rerere.rikkahub.data.workflow.LibraryFileFolder
import me.rerere.rikkahub.data.workflow.LibraryFileSort
import me.rerere.rikkahub.data.workflow.LibraryFileTypeFilter
import me.rerere.rikkahub.data.workflow.WorkflowLibraryRepository
import me.rerere.rikkahub.data.workflow.WorkflowRepository

data class WorkflowFilesUiState(
    val drive: LibraryDriveData = LibraryDriveData(),
    val currentFolderId: String = "",
    val searchQuery: String = "",
    val typeFilter: LibraryFileTypeFilter = LibraryFileTypeFilter.ALL,
    val sort: LibraryFileSort = LibraryFileSort.UPDATED_DESC,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val isUploading: Boolean = false,
    val uploadingFileName: String? = null,
    val uploadProgress: Int? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val previewFile: LibraryFile? = null,
    val previewContent: String? = null,
    val isLoadingPreview: Boolean = false,
    val previewError: String? = null,
)

class WorkflowFilesVM(
    private val workflowRepository: WorkflowRepository,
    private val libraryRepository: WorkflowLibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowFilesUiState())
    val uiState: StateFlow<WorkflowFilesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            workflowRepository.session.collectLatest { session ->
                if (session != null) {
                    load(isRefresh = false)
                } else {
                    loadJob?.cancel()
                    _uiState.value = WorkflowFilesUiState()
                }
            }
        }
    }

    fun refresh() {
        load(isRefresh = true)
    }

    fun openFolder(folderId: String) {
        _uiState.update { it.copy(currentFolderId = folderId) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setTypeFilter(filter: LibraryFileTypeFilter) {
        _uiState.update { it.copy(typeFilter = filter) }
    }

    fun setSort(sort: LibraryFileSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    fun uploadFromUri(context: Context, uri: Uri) {
        if (_uiState.value.isUploading) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    uploadingFileName = null,
                    uploadProgress = null,
                    errorMessage = null,
                )
            }

            val meta = withContext(Dispatchers.IO) { resolveUriMeta(appContext, uri) }
            if (meta == null) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadingFileName = null,
                        uploadProgress = null,
                        errorMessage = "无法读取所选文件，请重试",
                    )
                }
                return@launch
            }

            val (displayName, reportedSize, mimeType) = meta
            val isPdf = displayName.endsWith(".pdf", ignoreCase = true) ||
                mimeType.equals("application/pdf", ignoreCase = true)
            val isMarkdown = displayName.endsWith(".md", ignoreCase = true) ||
                displayName.endsWith(".markdown", ignoreCase = true) ||
                mimeType.equals("text/markdown", ignoreCase = true) ||
                mimeType.equals("text/x-markdown", ignoreCase = true)

            if (!isPdf && !isMarkdown) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadingFileName = null,
                        uploadProgress = null,
                        errorMessage = "仅支持上传 .pdf 或 .md 文件（当前：$displayName）",
                    )
                }
                return@launch
            }

            val normalizedName = when {
                isPdf && !displayName.endsWith(".pdf", ignoreCase = true) -> "$displayName.pdf"
                isMarkdown && !displayName.endsWith(".md", ignoreCase = true) && !displayName.endsWith(".markdown", ignoreCase = true) -> "$displayName.md"
                else -> displayName
            }

            _uiState.update {
                it.copy(
                    uploadingFileName = normalizedName,
                    uploadProgress = if (isPdf) 0 else null,
                )
            }

            val result = if (isPdf) {
                val actualSize = if (reportedSize > 0L) {
                    reportedSize
                } else {
                    withContext(Dispatchers.IO) { computeUriSize(appContext, uri) }
                }
                libraryRepository.uploadLibraryPdf(
                    fileName = normalizedName,
                    fileSize = actualSize,
                    openStream = { appContext.contentResolver.openInputStream(uri) },
                    onProgress = { percent ->
                        _uiState.update { state -> state.copy(uploadProgress = percent) }
                    },
                )
            } else {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        val maxRead = 2 * 1024 * 1024 + 1
                        val buf = ByteArray(8192)
                        val out = java.io.ByteArrayOutputStream()
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > maxRead) break
                        }
                        out.toByteArray()
                    }
                }
                if (bytes == null) {
                    Result.failure(IllegalStateException("无法读取 Markdown 文件内容"))
                } else {
                    libraryRepository.uploadLibraryMarkdown(normalizedName, bytes)
                }
            }

            result
                .onSuccess { uploaded ->
                    val targetFolder = _uiState.value.currentFolderId
                    if (targetFolder.isNotBlank() && uploaded.folder != targetFolder) {
                        libraryRepository.updateLibraryFile(uploaded.id, folderId = targetFolder)
                    }
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadingFileName = null,
                            uploadProgress = null,
                            noticeMessage = "已上传「${uploaded.fileName}」",
                        )
                    }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadingFileName = null,
                            uploadProgress = null,
                            errorMessage = err.message ?: "上传文件失败",
                        )
                    }
                }
        }
    }

    private fun resolveUriMeta(context: Context, uri: Uri): Triple<String, Long, String?>? {
        val resolver = context.contentResolver
        var displayName: String? = null
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx)?.trim()?.takeIf { it.isNotEmpty() }
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            size = cursor.getLong(sizeIdx)
                        }
                    }
                }
        }
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/')?.trim()?.takeIf { it.isNotEmpty() }
        val finalName = displayName ?: fallbackName ?: return null
        val mimeType = runCatching { resolver.getType(uri) }.getOrNull()
        return Triple(finalName, size, mimeType)
    }

    private fun computeUriSize(context: Context, uri: Uri): Long {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                fd.length.takeIf { it >= 0L }
            } ?: context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                }
                total
            } ?: -1L
        }.getOrDefault(-1L)
    }

    fun openPreview(file: LibraryFile) {
        _uiState.update {
            it.copy(
                previewFile = file,
                previewContent = null,
                isLoadingPreview = true,
                previewError = null,
            )
        }
        viewModelScope.launch {
            libraryRepository.readLibraryMarkdown(file.id)
                .onSuccess { content ->
                    _uiState.update { state ->
                        if (state.previewFile?.id != file.id) return@update state
                        state.copy(
                            previewContent = content,
                            isLoadingPreview = false,
                            previewError = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { state ->
                        if (state.previewFile?.id != file.id) return@update state
                        state.copy(
                            isLoadingPreview = false,
                            previewError = err.message ?: "读取文件内容失败",
                        )
                    }
                }
        }
    }

    fun closePreview() {
        _uiState.update {
            it.copy(
                previewFile = null,
                previewContent = null,
                isLoadingPreview = false,
                previewError = null,
            )
        }
    }

    fun renameFile(file: LibraryFile, newName: String) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.updateLibraryFile(file.id, fileName = newName)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已重命名文件") }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "重命名文件失败")
                    }
                }
        }
    }

    fun moveFile(file: LibraryFile, targetFolderId: String) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.updateLibraryFile(file.id, folderId = targetFolderId)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已移动文件") }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "移动文件失败")
                    }
                }
        }
    }

    fun deleteFile(file: LibraryFile) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.deleteLibraryFile(file.id)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已删除文件") }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "删除文件失败")
                    }
                }
        }
    }

    fun createFolder(name: String, parentId: String) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.createLibraryFileFolder(name, parentId)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已创建目录「${name.trim()}」") }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "创建目录失败")
                    }
                }
        }
    }

    fun renameFolder(folder: LibraryFileFolder, newName: String) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.updateLibraryFileFolder(folder.id, name = newName)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已重命名目录") }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "重命名目录失败")
                    }
                }
        }
    }

    fun deleteFolder(folder: LibraryFileFolder) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.deleteLibraryFileFolder(folder.id)
                .onSuccess {
                    _uiState.update { state ->
                        val nextFolder = if (state.currentFolderId == folder.id) folder.parent else state.currentFolderId
                        state.copy(
                            isMutating = false,
                            currentFolderId = nextFolder,
                            noticeMessage = "已删除目录",
                        )
                    }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "删除目录失败")
                    }
                }
        }
    }

    private fun load(isRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val hasData = _uiState.value.drive.files.isNotEmpty() || _uiState.value.drive.folders.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && !hasData,
                    isRefreshing = isRefresh || hasData,
                    errorMessage = null,
                )
            }
            libraryRepository.listLibraryDrive()
                .onSuccess { drive ->
                    _uiState.update {
                        it.copy(
                            drive = drive,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = err.message ?: "加载资料库失败",
                        )
                    }
                }
        }
    }
}
