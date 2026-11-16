package me.rerere.rikkahub.ui.pages.workflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.workflow.DispatchRequestedAction
import me.rerere.rikkahub.data.workflow.DispatchStatus
import me.rerere.rikkahub.data.workflow.RunListFilter
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowRun
import me.rerere.rikkahub.data.workflow.WorkflowSession

data class WorkflowUiState(
    val session: WorkflowSession? = null,
    val baseUrl: String = "",
    val filter: RunListFilter = RunListFilter.ALL,
    val searchInput: String = "",
    val debouncedQuery: String = "",
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val runs: List<WorkflowRun> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoggingIn: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val loginError: String? = null,
    val selectedRun: WorkflowRun? = null,
)

class WorkflowVM(
    private val repository: WorkflowRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        WorkflowUiState(
            session = repository.session.value,
            baseUrl = repository.baseUrl.value,
        )
    )
    val uiState: StateFlow<WorkflowUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            repository.session.collectLatest { session ->
                _uiState.update {
                    it.copy(
                        session = session,
                        baseUrl = repository.baseUrl.value,
                        runs = if (session == null) emptyList() else it.runs,
                        selectedRun = if (session == null) null else it.selectedRun,
                        errorMessage = if (session == null) null else it.errorMessage,
                    )
                }
                if (session != null) {
                    loadRuns(isRefresh = false)
                } else {
                    pollJob?.cancel()
                }
            }
        }
    }

    fun login(identity: String, password: String, baseUrl: String) {
        if (_uiState.value.isLoggingIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginError = null) }
            val result = repository.login(identity, password, baseUrl)
            result.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoggingIn = false,
                        loginError = err.message ?: "连接失败",
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoggingIn = false, loginError = null, page = 1) }
            }
        }
    }

    fun logout() {
        pollJob?.cancel()
        loadJob?.cancel()
        repository.logout()
    }

    fun setFilter(filter: RunListFilter) {
        if (_uiState.value.filter == filter) return
        _uiState.update { it.copy(filter = filter, page = 1) }
        loadRuns(isRefresh = false)
    }

    fun setSearchInput(input: String) {
        _uiState.update { it.copy(searchInput = input) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(280)
            if (_uiState.value.debouncedQuery != input.trim()) {
                _uiState.update { it.copy(debouncedQuery = input.trim(), page = 1) }
                loadRuns(isRefresh = false)
            }
        }
    }

    fun setPage(page: Int) {
        val target = page.coerceIn(1, _uiState.value.totalPages.coerceAtLeast(1))
        if (target == _uiState.value.page) return
        _uiState.update { it.copy(page = target) }
        loadRuns(isRefresh = false)
    }

    fun refresh() {
        loadRuns(isRefresh = true)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun selectRun(run: WorkflowRun?) {
        _uiState.update { it.copy(selectedRun = run) }
    }

    fun createRun(
        title: String,
        planText: String,
        status: DispatchStatus,
        onSuccess: () -> Unit,
    ) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            val result = repository.createRun(title, planText, status)
            result.onSuccess { created ->
                _uiState.update {
                    it.copy(
                        isMutating = false,
                        page = 1,
                        selectedRun = created,
                    )
                }
                loadRuns(isRefresh = true)
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isMutating = false,
                        errorMessage = err.message ?: "创建工作流失败",
                    )
                }
            }
        }
    }

    fun commandRun(run: WorkflowRun, action: DispatchRequestedAction) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            repository.commandRun(run, action)
                .onSuccess { updated ->
                    _uiState.update { state ->
                        state.copy(
                            isMutating = false,
                            runs = state.runs.map { if (it.id == updated.id) updated else it },
                            selectedRun = if (state.selectedRun?.id == updated.id) updated else state.selectedRun,
                        )
                    }
                    schedulePollingIfNeeded()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = err.message ?: "发送控制命令失败",
                        )
                    }
                }
        }
    }

    fun cloneRun(run: WorkflowRun, targetStatus: DispatchStatus) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            repository.cloneRun(run, targetStatus)
                .onSuccess { cloned ->
                    _uiState.update { state ->
                        state.copy(
                            isMutating = false,
                            page = 1,
                            selectedRun = cloned,
                        )
                    }
                    loadRuns(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = err.message ?: "复制工作流失败",
                        )
                    }
                }
        }
    }

    fun deleteRun(run: WorkflowRun) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            repository.deleteRun(run)
                .onSuccess {
                    val state = _uiState.value
                    val nextPage = if (state.page > 1 && state.runs.size == 1) state.page - 1 else state.page
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            page = nextPage,
                            selectedRun = if (it.selectedRun?.id == run.id) null else it.selectedRun,
                        )
                    }
                    loadRuns(isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = err.message ?: "删除 Run 失败",
                        )
                    }
                }
        }
    }

    private fun loadRuns(isRefresh: Boolean) {
        if (_uiState.value.session == null) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val state = _uiState.value
            val hasData = state.runs.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && !hasData,
                    isRefreshing = isRefresh || hasData,
                    errorMessage = null,
                )
            }
            val result = repository.listRuns(
                page = state.page,
                perPage = 30,
                filter = state.filter,
                search = state.debouncedQuery,
            )
            result.onSuccess { pageData ->
                _uiState.update { current ->
                    val refreshedSelected = current.selectedRun?.let { sel ->
                        pageData.items.firstOrNull { it.id == sel.id } ?: sel
                    }
                    current.copy(
                        runs = pageData.items,
                        totalItems = pageData.totalItems,
                        totalPages = pageData.totalPages,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null,
                        selectedRun = refreshedSelected,
                    )
                }
                schedulePollingIfNeeded()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = err.message ?: "加载工作流失败",
                    )
                }
            }
        }
    }

    private fun schedulePollingIfNeeded() {
        pollJob?.cancel()
        val hasActive = _uiState.value.runs.any { it.status.isActive }
        if (!hasActive || _uiState.value.session == null) return
        pollJob = viewModelScope.launch {
            delay(5_000)
            if (_uiState.value.session != null && _uiState.value.runs.any { it.status.isActive }) {
                loadRuns(isRefresh = true)
            }
        }
    }
}
