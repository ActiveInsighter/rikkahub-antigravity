package me.rerere.rikkahub.ui.pages.workflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.workflow.DEFAULT_WORKFLOW_START_LINKS
import me.rerere.rikkahub.data.workflow.ModelCatalogSnapshot
import me.rerere.rikkahub.data.workflow.RuntimeDefaultsSnapshot
import me.rerere.rikkahub.data.workflow.SavedAiModel
import me.rerere.rikkahub.data.workflow.StartLink
import me.rerere.rikkahub.data.workflow.UserPreferencesSnapshot
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowSettingsRepository

data class WorkflowSettingsUiState(
    val preferences: UserPreferencesSnapshot = UserPreferencesSnapshot(),
    val catalog: ModelCatalogSnapshot = ModelCatalogSnapshot(),
    val savedModels: List<SavedAiModel> = emptyList(),
    val runtimeDefaults: RuntimeDefaultsSnapshot = RuntimeDefaultsSnapshot(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val isRefreshingCatalog: Boolean = false,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
)

class WorkflowSettingsVM(
    private val workflowRepository: WorkflowRepository,
    private val settingsRepository: WorkflowSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowSettingsUiState())
    val uiState: StateFlow<WorkflowSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            workflowRepository.session.collectLatest { session ->
                if (session != null) {
                    refreshAll()
                } else {
                    _uiState.value = WorkflowSettingsUiState()
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            coroutineScope {
                val prefsDeferred = async { settingsRepository.getUserPreferences() }
                val catalogDeferred = async { settingsRepository.getModelCatalog() }
                val savedDeferred = async { settingsRepository.listSavedModels() }
                val runtimeDeferred = async { settingsRepository.getRuntimeDefaults() }

                val prefsRes = prefsDeferred.await()
                val catalogRes = catalogDeferred.await()
                val savedRes = savedDeferred.await()
                val runtimeRes = runtimeDeferred.await()

                val firstErr = listOf(prefsRes, catalogRes, savedRes, runtimeRes)
                    .firstNotNullOfOrNull { it.exceptionOrNull()?.message }

                _uiState.update { current ->
                    current.copy(
                        preferences = prefsRes.getOrElse { current.preferences },
                        catalog = catalogRes.getOrElse { current.catalog },
                        savedModels = savedRes.getOrElse { current.savedModels },
                        runtimeDefaults = runtimeRes.getOrElse { current.runtimeDefaults },
                        isLoading = false,
                        errorMessage = firstErr,
                    )
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // 快捷链接
    // -----------------------------------------------------------------------

    fun addStartLink(label: String, url: String) {
        val current = _uiState.value.preferences
        val next = current.startLinks + StartLink(
            id = StartLink.createId(),
            label = label.trim(),
            url = url.trim(),
        )
        saveStartLinksList(next, current.recordId, "已添加快捷链接")
    }

    fun updateStartLink(link: StartLink, label: String, url: String) {
        val current = _uiState.value.preferences
        val next = current.startLinks.map {
            if (it.id == link.id) it.copy(label = label.trim(), url = url.trim()) else it
        }
        saveStartLinksList(next, current.recordId, "已更新快捷链接")
    }

    fun deleteStartLink(link: StartLink) {
        val current = _uiState.value.preferences
        val next = current.startLinks.filterNot { it.id == link.id }
        saveStartLinksList(next, current.recordId, "已删除快捷链接")
    }

    fun resetStartLinks() {
        val current = _uiState.value.preferences
        saveStartLinksList(DEFAULT_WORKFLOW_START_LINKS, current.recordId, "已恢复默认快捷链接")
    }

    private fun saveStartLinksList(
        links: List<StartLink>,
        recordId: String?,
        successNotice: String,
    ) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            settingsRepository.saveStartLinks(links, recordId)
                .onSuccess { saved ->
                    _uiState.update {
                        it.copy(
                            preferences = saved,
                            isMutating = false,
                            noticeMessage = successNotice,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = err.message ?: "保存快捷链接失败",
                        )
                    }
                }
        }
    }

    // -----------------------------------------------------------------------
    // 模型库
    // -----------------------------------------------------------------------

    fun addSavedModel(modelId: String, name: String? = null) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            settingsRepository.saveModel(modelId, name)
                .onSuccess { saved ->
                    _uiState.update {
                        it.copy(
                            savedModels = it.savedModels + saved,
                            isMutating = false,
                            noticeMessage = "已保存模型「${saved.name}」",
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = err.message ?: "保存模型失败",
                        )
                    }
                }
        }
    }

    fun removeSavedModel(model: SavedAiModel) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            settingsRepository.deleteSavedModel(model.id)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            savedModels = it.savedModels.filterNot { m -> m.id == model.id },
                            isMutating = false,
                            noticeMessage = "已移除模型「${model.name}」",
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = err.message ?: "移除模型失败",
                        )
                    }
                }
        }
    }

    fun refreshModelCatalog() {
        if (_uiState.value.isRefreshingCatalog) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingCatalog = true, errorMessage = null) }
            settingsRepository.refreshModelCatalog()
                .onSuccess {
                    val catalog = settingsRepository.getModelCatalog().getOrNull()
                    _uiState.update {
                        it.copy(
                            catalog = catalog ?: it.catalog,
                            isRefreshingCatalog = false,
                            noticeMessage = "已刷新模型目录",
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isRefreshingCatalog = false,
                            errorMessage = err.message ?: "刷新模型目录失败",
                        )
                    }
                }
        }
    }

    // -----------------------------------------------------------------------
    // 运行配置
    // -----------------------------------------------------------------------

    fun saveRuntimeDefaults(next: RuntimeDefaultsSnapshot, onSuccess: () -> Unit = {}) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            settingsRepository.saveRuntimeDefaults(next)
                .onSuccess { saved ->
                    _uiState.update {
                        it.copy(
                            runtimeDefaults = saved,
                            isMutating = false,
                            noticeMessage = "已保存运行配置（v${saved.revision}）",
                        )
                    }
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = err.message ?: "保存运行配置失败",
                        )
                    }
                }
        }
    }
}
