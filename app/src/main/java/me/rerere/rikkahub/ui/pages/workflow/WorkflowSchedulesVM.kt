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
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowRunSchedule

data class WorkflowSchedulesUiState(
    val schedules: List<WorkflowRunSchedule> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
)

class WorkflowSchedulesVM(
    private val repository: WorkflowRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowSchedulesUiState())
    val uiState: StateFlow<WorkflowSchedulesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.session.collectLatest { session ->
                if (session != null) {
                    load(isRefresh = false)
                } else {
                    loadJob?.cancel()
                    _uiState.value = WorkflowSchedulesUiState()
                }
            }
        }
    }

    fun refresh() {
        load(isRefresh = true)
    }

    fun setPage(page: Int) {
        val target = page.coerceIn(1, _uiState.value.totalPages.coerceAtLeast(1))
        if (target == _uiState.value.page) return
        _uiState.update { it.copy(page = target) }
        load(isRefresh = false)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun toggle(schedule: WorkflowRunSchedule) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            repository.setRunScheduleEnabled(schedule, !schedule.enabled)
                .onSuccess { updated ->
                    _uiState.update { state ->
                        state.copy(
                            isMutating = false,
                            schedules = state.schedules.map { if (it.id == updated.id) updated else it },
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = err.message ?: "更新重复计划状态失败",
                        )
                    }
                }
        }
    }

    fun delete(schedule: WorkflowRunSchedule, onSuccess: () -> Unit = {}) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            repository.deleteRunSchedule(schedule)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false) }
                    load(isRefresh = true)
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = err.message ?: "删除重复计划失败",
                        )
                    }
                }
        }
    }

    private fun load(isRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val hasData = _uiState.value.schedules.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && !hasData,
                    isRefreshing = isRefresh || hasData,
                    errorMessage = null,
                )
            }
            repository.listRunSchedules(page = _uiState.value.page)
                .onSuccess { pageData ->
                    _uiState.update {
                        it.copy(
                            schedules = pageData.items,
                            page = pageData.page,
                            totalPages = pageData.totalPages,
                            totalItems = pageData.totalItems,
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
                            errorMessage = err.message ?: "加载重复计划失败",
                        )
                    }
                }
        }
    }
}
