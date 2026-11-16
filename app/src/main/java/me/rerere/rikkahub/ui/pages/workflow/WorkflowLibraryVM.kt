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
import me.rerere.rikkahub.data.workflow.LibraryFolder
import me.rerere.rikkahub.data.workflow.LibraryFolderScope
import me.rerere.rikkahub.data.workflow.RunFavorite
import me.rerere.rikkahub.data.workflow.WorkflowLibraryRepository
import me.rerere.rikkahub.data.workflow.WorkflowLibrarySnapshot
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowRun
import me.rerere.rikkahub.data.workflow.WorkflowTemplate

data class WorkflowLibraryUiState(
    val snapshot: WorkflowLibrarySnapshot = WorkflowLibrarySnapshot(),
    val activeScope: LibraryFolderScope = LibraryFolderScope.FAVORITE,
    val currentFavoriteFolderId: String = "",
    val currentTemplateFolderId: String = "",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
) {
    val currentFolderId: String
        get() = if (activeScope == LibraryFolderScope.FAVORITE) {
            currentFavoriteFolderId
        } else {
            currentTemplateFolderId
        }

    val favoriteRunIds: Set<String>
        get() = snapshot.favorites.mapTo(mutableSetOf()) { it.run }
}

class WorkflowLibraryVM(
    private val workflowRepository: WorkflowRepository,
    private val libraryRepository: WorkflowLibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowLibraryUiState())
    val uiState: StateFlow<WorkflowLibraryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            workflowRepository.session.collectLatest { session ->
                if (session != null) {
                    load(isRefresh = false)
                } else {
                    loadJob?.cancel()
                    _uiState.value = WorkflowLibraryUiState()
                }
            }
        }
    }

    fun refresh() {
        load(isRefresh = true)
    }

    fun setScope(scope: LibraryFolderScope) {
        _uiState.update { it.copy(activeScope = scope) }
    }

    fun openFolder(folderId: String) {
        _uiState.update { state ->
            if (state.activeScope == LibraryFolderScope.FAVORITE) {
                state.copy(currentFavoriteFolderId = folderId)
            } else {
                state.copy(currentTemplateFolderId = folderId)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    fun toggleFavoriteRun(run: WorkflowRun) {
        if (_uiState.value.isMutating) return
        val existing = _uiState.value.snapshot.favorites.firstOrNull { it.run == run.id }
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            if (existing != null) {
                libraryRepository.deleteRunFavorite(existing.id)
                    .onSuccess {
                        _uiState.update { it.copy(isMutating = false, noticeMessage = "已取消收藏") }
                        load(isRefresh = true)
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(isMutating = false, errorMessage = err.message ?: "取消收藏失败")
                        }
                    }
            } else {
                libraryRepository.createRunFavorite(run.id)
                    .onSuccess {
                        _uiState.update { it.copy(isMutating = false, noticeMessage = "已添加到收藏") }
                        load(isRefresh = true)
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(isMutating = false, errorMessage = err.message ?: "收藏失败")
                        }
                    }
            }
        }
    }

    fun saveRunAsTemplate(run: WorkflowRun) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.createWorkflowTemplateFromRun(run)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已保存为模板「${it.snapshot.templates.size + 1}」") }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "保存为模板失败")
                    }
                }
        }
    }

    fun moveFavorite(favorite: RunFavorite, targetFolderId: String) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.moveRunFavorite(favorite.id, targetFolderId)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已移动收藏") }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "移动收藏失败")
                    }
                }
        }
    }

    fun deleteFavorite(favorite: RunFavorite) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.deleteRunFavorite(favorite.id)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已移除收藏") }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "移除收藏失败")
                    }
                }
        }
    }

    fun createFolder(name: String, scope: LibraryFolderScope, parentId: String) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.createLibraryFolder(name, scope, parentId)
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

    fun renameFolder(folder: LibraryFolder, newName: String) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.updateLibraryFolder(folder.id, name = newName)
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

    fun deleteFolder(folder: LibraryFolder, scope: LibraryFolderScope) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.deleteLibraryFolder(folder.id, scope)
                .onSuccess {
                    _uiState.update { state ->
                        val nextFav = if (state.currentFavoriteFolderId == folder.id) folder.parent else state.currentFavoriteFolderId
                        val nextTpl = if (state.currentTemplateFolderId == folder.id) folder.parent else state.currentTemplateFolderId
                        state.copy(
                            isMutating = false,
                            currentFavoriteFolderId = nextFav,
                            currentTemplateFolderId = nextTpl,
                            noticeMessage = "已删除目录，内容已移至上级",
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

    fun createTemplate(
        title: String,
        description: String,
        planText: String,
        folderId: String,
        tags: List<String>,
        onSuccess: () -> Unit = {},
    ) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.createWorkflowTemplate(
                title = title,
                description = description,
                planText = planText,
                folderId = folderId,
                tags = tags,
            )
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已创建模板") }
                    load(isRefresh = true)
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "创建模板失败")
                    }
                }
        }
    }

    fun updateTemplate(
        id: String,
        title: String,
        description: String,
        planText: String,
        folderId: String,
        tags: List<String>,
        onSuccess: () -> Unit = {},
    ) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.updateWorkflowTemplate(
                id = id,
                title = title,
                description = description,
                planText = planText,
                folderId = folderId,
                tags = tags,
            )
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已更新模板") }
                    load(isRefresh = true)
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "更新模板失败")
                    }
                }
        }
    }

    fun moveTemplate(template: WorkflowTemplate, targetFolderId: String) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.updateWorkflowTemplate(template.id, folderId = targetFolderId)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已移动模板") }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "移动模板失败")
                    }
                }
        }
    }

    fun duplicateTemplate(template: WorkflowTemplate) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.duplicateWorkflowTemplate(template)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已复制模板副本") }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "复制模板失败")
                    }
                }
        }
    }

    fun deleteTemplate(template: WorkflowTemplate) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            libraryRepository.deleteWorkflowTemplate(template.id)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, noticeMessage = "已删除模板") }
                    load(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutating = false, errorMessage = err.message ?: "删除模板失败")
                    }
                }
        }
    }

    private fun load(isRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val hasData = _uiState.value.snapshot.folders.isNotEmpty() ||
                _uiState.value.snapshot.favorites.isNotEmpty() ||
                _uiState.value.snapshot.templates.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && !hasData,
                    isRefreshing = isRefresh || hasData,
                    errorMessage = null,
                )
            }
            libraryRepository.listLibrary()
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            snapshot = snapshot,
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
                            errorMessage = err.message ?: "加载收藏与模板失败",
                        )
                    }
                }
        }
    }
}
