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
import me.rerere.rikkahub.data.workflow.WorkflowEvent
import me.rerere.rikkahub.data.workflow.WorkflowHistoryAct
import me.rerere.rikkahub.data.workflow.WorkflowHistoryMessage
import me.rerere.rikkahub.data.workflow.WorkflowHistoryMessageSummary
import me.rerere.rikkahub.data.workflow.WorkflowHistoryStatus
import me.rerere.rikkahub.data.workflow.WorkflowListPage
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowRun
import me.rerere.rikkahub.data.workflow.WorkflowSession
import me.rerere.rikkahub.data.workflow.WorkflowTask

data class RunDetailUiState(
    val versions: List<WorkflowRun> = emptyList(),
    val tasks: List<WorkflowTask> = emptyList(),
    val isLoadingTasks: Boolean = false,
    val tasksError: String? = null,
    val eventsByTask: Map<String, List<WorkflowEvent>> = emptyMap(),
    val loadingTaskIds: Set<String> = emptySet(),
    val errorByTask: Map<String, String> = emptyMap(),
    val actsByEvent: Map<String, List<WorkflowHistoryAct>> = emptyMap(),
    val loadingEventIds: Set<String> = emptySet(),
    val errorByEvent: Map<String, String> = emptyMap(),
    val messagesByAct: Map<String, WorkflowListPage<WorkflowHistoryMessageSummary>> = emptyMap(),
    val loadingActIds: Set<String> = emptySet(),
    val errorByAct: Map<String, String> = emptyMap(),
    val openedMessageSummary: WorkflowHistoryMessageSummary? = null,
    val openedMessageActTitle: String = "",
    val openedMessageNumber: Int = 1,
    val openedMessageEventActive: Boolean = false,
    val openedMessageDetail: WorkflowHistoryMessage? = null,
    val isLoadingMessageDetail: Boolean = false,
    val messageDetailError: String? = null,
)

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
    val detail: RunDetailUiState = RunDetailUiState(),
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
    private var detailPollJob: Job? = null
    private var messagePollJob: Job? = null

    init {
        viewModelScope.launch {
            repository.session.collectLatest { session ->
                _uiState.update {
                    it.copy(
                        session = session,
                        baseUrl = repository.baseUrl.value,
                        runs = if (session == null) emptyList() else it.runs,
                        selectedRun = if (session == null) null else it.selectedRun,
                        detail = if (session == null) RunDetailUiState() else it.detail,
                        errorMessage = if (session == null) null else it.errorMessage,
                    )
                }
                if (session != null) {
                    loadRuns(isRefresh = false)
                } else {
                    pollJob?.cancel()
                    detailPollJob?.cancel()
                    messagePollJob?.cancel()
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
        detailPollJob?.cancel()
        messagePollJob?.cancel()
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
        detailPollJob?.cancel()
        messagePollJob?.cancel()
        if (run == null) {
            _uiState.update { it.copy(selectedRun = null, detail = RunDetailUiState()) }
            return
        }
        _uiState.update { it.copy(selectedRun = run, detail = RunDetailUiState(isLoadingTasks = true)) }
        loadRunDetailData(run)
    }

    fun refreshSelectedRunDetail() {
        val run = _uiState.value.selectedRun ?: return
        viewModelScope.launch {
            repository.getRun(run.id).onSuccess { freshRun ->
                _uiState.update { state ->
                    state.copy(
                        selectedRun = freshRun,
                        runs = state.runs.map { if (it.id == freshRun.id) freshRun else it },
                    )
                }
                loadTasksForRun(freshRun.id, showLoading = false)
            }
        }
    }

    private fun loadRunDetailData(run: WorkflowRun) {
        viewModelScope.launch {
            repository.listRunVersions(run.familyId).onSuccess { versions ->
                _uiState.update { state ->
                    if (state.selectedRun?.familyId == run.familyId) {
                        state.copy(detail = state.detail.copy(versions = versions))
                    } else {
                        state
                    }
                }
            }
        }
        loadTasksForRun(run.id, showLoading = true)
        scheduleDetailPollingIfNeeded(run)
    }

    private fun loadTasksForRun(runId: String, showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update {
                    it.copy(detail = it.detail.copy(isLoadingTasks = true, tasksError = null))
                }
            }
            repository.listTasksForRun(runId)
                .onSuccess { pageData ->
                    _uiState.update { state ->
                        if (state.selectedRun?.id != runId) return@update state
                        state.copy(
                            detail = state.detail.copy(
                                tasks = pageData.items,
                                isLoadingTasks = false,
                                tasksError = null,
                            )
                        )
                    }
                    // Refresh any already-loaded tasks' events
                    val loadedTaskIds = _uiState.value.detail.eventsByTask.keys.toList()
                    for (task in pageData.items) {
                        if (task.id in loadedTaskIds) {
                            loadEventsForTask(task.id, silent = true)
                        }
                    }
                }
                .onFailure { err ->
                    _uiState.update { state ->
                        if (state.selectedRun?.id != runId) return@update state
                        state.copy(
                            detail = state.detail.copy(
                                isLoadingTasks = false,
                                tasksError = err.message ?: "加载 Task 失败",
                            )
                        )
                    }
                }
        }
    }

    fun loadEventsForTask(taskId: String, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { state ->
                    state.copy(
                        detail = state.detail.copy(
                            loadingTaskIds = state.detail.loadingTaskIds + taskId,
                            errorByTask = state.detail.errorByTask - taskId,
                        )
                    )
                }
            }
            repository.listEventsForTask(taskId)
                .onSuccess { pageData ->
                    _uiState.update { state ->
                        state.copy(
                            detail = state.detail.copy(
                                eventsByTask = state.detail.eventsByTask + (taskId to pageData.items),
                                loadingTaskIds = state.detail.loadingTaskIds - taskId,
                                errorByTask = state.detail.errorByTask - taskId,
                            )
                        )
                    }
                    val loadedEventIds = _uiState.value.detail.actsByEvent.keys
                    for (ev in pageData.items) {
                        if (ev.id in loadedEventIds) {
                            loadActsForEvent(ev, silent = true)
                        }
                    }
                }
                .onFailure { err ->
                    _uiState.update { state ->
                        state.copy(
                            detail = state.detail.copy(
                                loadingTaskIds = state.detail.loadingTaskIds - taskId,
                                errorByTask = state.detail.errorByTask + (taskId to (err.message ?: "加载 Event 失败")),
                            )
                        )
                    }
                }
        }
    }

    fun loadActsForEvent(event: WorkflowEvent, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { state ->
                    state.copy(
                        detail = state.detail.copy(
                            loadingEventIds = state.detail.loadingEventIds + event.id,
                            errorByEvent = state.detail.errorByEvent - event.id,
                        )
                    )
                }
            }
            repository.listHistoryActsForDispatchEvent(event)
                .onSuccess { acts ->
                    _uiState.update { state ->
                        state.copy(
                            detail = state.detail.copy(
                                actsByEvent = state.detail.actsByEvent + (event.id to acts),
                                loadingEventIds = state.detail.loadingEventIds - event.id,
                                errorByEvent = state.detail.errorByEvent - event.id,
                            )
                        )
                    }
                    val loadedActIds = _uiState.value.detail.messagesByAct.keys
                    for (act in acts) {
                        if (act.id in loadedActIds) {
                            val page = _uiState.value.detail.messagesByAct[act.id]?.page ?: 1
                            loadMessagesForAct(act.id, page, silent = true)
                        }
                    }
                }
                .onFailure { err ->
                    _uiState.update { state ->
                        state.copy(
                            detail = state.detail.copy(
                                loadingEventIds = state.detail.loadingEventIds - event.id,
                                errorByEvent = state.detail.errorByEvent + (event.id to (err.message ?: "加载 Act 失败")),
                            )
                        )
                    }
                }
        }
    }

    fun loadMessagesForAct(actId: String, page: Int = 1, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { state ->
                    state.copy(
                        detail = state.detail.copy(
                            loadingActIds = state.detail.loadingActIds + actId,
                            errorByAct = state.detail.errorByAct - actId,
                        )
                    )
                }
            }
            repository.listHistoryMessageSummariesForAct(actId, page)
                .onSuccess { pageData ->
                    _uiState.update { state ->
                        state.copy(
                            detail = state.detail.copy(
                                messagesByAct = state.detail.messagesByAct + (actId to pageData),
                                loadingActIds = state.detail.loadingActIds - actId,
                                errorByAct = state.detail.errorByAct - actId,
                            )
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { state ->
                        state.copy(
                            detail = state.detail.copy(
                                loadingActIds = state.detail.loadingActIds - actId,
                                errorByAct = state.detail.errorByAct + (actId to (err.message ?: "加载消息列表失败")),
                            )
                        )
                    }
                }
        }
    }

    fun openMessageDetail(
        summary: WorkflowHistoryMessageSummary,
        actTitle: String,
        messageNumber: Int,
        eventActive: Boolean,
    ) {
        messagePollJob?.cancel()
        _uiState.update { state ->
            state.copy(
                detail = state.detail.copy(
                    openedMessageSummary = summary,
                    openedMessageActTitle = actTitle,
                    openedMessageNumber = messageNumber,
                    openedMessageEventActive = eventActive,
                    openedMessageDetail = null,
                    isLoadingMessageDetail = true,
                    messageDetailError = null,
                )
            )
        }
        fetchMessageDetail(summary.id, summary.act, allowPoll = true)
    }

    fun retryMessageDetail() {
        val summary = _uiState.value.detail.openedMessageSummary ?: return
        _uiState.update {
            it.copy(detail = it.detail.copy(isLoadingMessageDetail = true, messageDetailError = null))
        }
        fetchMessageDetail(summary.id, summary.act, allowPoll = true)
    }

    fun closeMessageDetail() {
        messagePollJob?.cancel()
        _uiState.update { state ->
            state.copy(
                detail = state.detail.copy(
                    openedMessageSummary = null,
                    openedMessageDetail = null,
                    isLoadingMessageDetail = false,
                    messageDetailError = null,
                )
            )
        }
    }

    private fun fetchMessageDetail(messageId: String, actId: String, allowPoll: Boolean) {
        viewModelScope.launch {
            repository.getHistoryMessage(messageId, actId)
                .onSuccess { msg ->
                    _uiState.update { state ->
                        if (state.detail.openedMessageSummary?.id != messageId) return@update state
                        state.copy(
                            detail = state.detail.copy(
                                openedMessageDetail = msg,
                                isLoadingMessageDetail = false,
                                messageDetailError = null,
                            )
                        )
                    }
                    val unsettled = msg.status == WorkflowHistoryStatus.UNKNOWN && msg.receivedAt.isBlank()
                    if (allowPoll && unsettled && _uiState.value.detail.openedMessageSummary?.id == messageId) {
                        messagePollJob?.cancel()
                        messagePollJob = viewModelScope.launch {
                            delay(3_000)
                            if (_uiState.value.detail.openedMessageSummary?.id == messageId) {
                                fetchMessageDetail(messageId, actId, allowPoll = true)
                            }
                        }
                    }
                }
                .onFailure { err ->
                    _uiState.update { state ->
                        if (state.detail.openedMessageSummary?.id != messageId) return@update state
                        state.copy(
                            detail = state.detail.copy(
                                isLoadingMessageDetail = false,
                                messageDetailError = err.message ?: "加载消息内容失败",
                            )
                        )
                    }
                }
        }
    }

    fun createRun(
        title: String,
        planText: String,
        status: DispatchStatus,
        onSuccess: (WorkflowRun) -> Unit,
    ) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            repository.createRun(title, planText, status)
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            page = 1,
                        )
                    }
                    loadRuns(isRefresh = true)
                    selectRun(created)
                    onSuccess(created)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = err.message ?: "创建工作流失败",
                        )
                    }
                }
        }
    }

    fun updateDraft(
        runId: String,
        title: String,
        planText: String,
        publish: Boolean,
        onSuccess: (WorkflowRun) -> Unit,
    ) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            repository.updateRunDraft(runId, title, planText, publish)
                .onSuccess { updated ->
                    _uiState.update { state ->
                        state.copy(
                            isMutating = false,
                            runs = state.runs.map { if (it.id == updated.id) updated else it },
                        )
                    }
                    loadRuns(isRefresh = true)
                    if (_uiState.value.selectedRun?.id == updated.id || publish) {
                        selectRun(updated)
                    }
                    onSuccess(updated)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = err.message ?: "保存草稿失败",
                        )
                    }
                }
        }
    }

    fun createEditedRun(
        sourceId: String,
        title: String,
        planText: String,
        status: DispatchStatus,
        onSuccess: (WorkflowRun) -> Unit,
    ) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            repository.createEditedRun(sourceId, title, planText, status)
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            page = 1,
                        )
                    }
                    loadRuns(isRefresh = true)
                    selectRun(created)
                    onSuccess(created)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = err.message ?: "创建新版本失败",
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
                    scheduleDetailPollingIfNeeded(updated)
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
                        )
                    }
                    loadRuns(isRefresh = true)
                    selectRun(cloned)
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
                    val clearedSelection = state.selectedRun?.id == run.id
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            page = nextPage,
                            selectedRun = if (clearedSelection) null else it.selectedRun,
                            detail = if (clearedSelection) RunDetailUiState() else it.detail,
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

    private fun scheduleDetailPollingIfNeeded(run: WorkflowRun) {
        detailPollJob?.cancel()
        if (!run.status.isActive || _uiState.value.session == null) return
        detailPollJob = viewModelScope.launch {
            delay(4_000)
            val current = _uiState.value.selectedRun
            if (current != null && current.id == run.id && current.status.isActive && _uiState.value.session != null) {
                refreshSelectedRunDetail()
                scheduleDetailPollingIfNeeded(current)
            }
        }
    }
}
