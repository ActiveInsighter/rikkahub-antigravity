package me.rerere.rikkahub.ui.pages.workflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    fun uploadMarkdown(fileName: String, contentBytes: ByteArray) {
        if (_uiState.value.isUploading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, errorMessage = null) }
            libraryRepository.uploadLibraryMarkdown(fileName, contentBytes)
                .onSuccess { uploaded ->
                    val targetFolder = _uiState.value.currentFolderId
                    if (targetFolder.isNotBlank() && uploaded.folder != targetFolder) {
                        libraryRepository.updateLibraryFile(uploaded.id, folderId = targetFolder)
                    }
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            noticeMessage = "已上传「${uploaded.fileName}」",
                        )
                    }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            errorMessage = err.message ?: "上传 Markdown 失败",
                        )
                    }
                }
        }
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
