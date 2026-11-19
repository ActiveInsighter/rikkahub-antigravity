package me.rerere.rikkahub.ui.pages.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.agent.AgentCloudClient
import me.rerere.rikkahub.data.agent.AgentSchedule
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.agent.DEFAULT_AGENT_MODELS
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowSession
import kotlin.coroutines.cancellation.CancellationException

class AgentWorkflowVM(
    private val cloudClient: AgentCloudClient,
    private val settingsStore: AgentSettingsStore,
    private val workflowRepository: WorkflowRepository,
) : ViewModel() {

    val session: StateFlow<WorkflowSession?> = workflowRepository.session

    val availableModels: StateFlow<List<String>> = settingsStore.settingsFlow
        .map { (DEFAULT_AGENT_MODELS + it.customGeminiModels).distinct() }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            (DEFAULT_AGENT_MODELS + settingsStore.current.customGeminiModels).distinct(),
        )

    private val _schedules = MutableStateFlow<List<AgentSchedule>>(emptyList())
    val schedules: StateFlow<List<AgentSchedule>> = _schedules.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _backendNotice = MutableStateFlow<String?>(null)
    val backendNotice: StateFlow<String?> = _backendNotice.asStateFlow()

    init {
        viewModelScope.launch {
            session.collectLatest { current ->
                _schedules.value = emptyList()
                _backendNotice.value = if (current == null) "请先连接 AnyWorkflow 云端账号" else null
                _isLoading.value = false
                if (current != null) refresh()
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun refresh() = update("同步定时任务失败") { current ->
        val remote = cloudClient.listSchedules()
        if (session.value == current) {
            _schedules.value = remote
            _backendNotice.value = null
        }
    }

    fun saveSchedule(schedule: AgentSchedule) = update("保存定时任务失败") { current ->
        val isNew = schedule.id.isBlank()
        val saved = if (isNew) cloudClient.createSchedule(schedule) else cloudClient.updateSchedule(schedule)
        if (session.value == current) {
            val schedules = _schedules.value
            _schedules.value = if (isNew) schedules + saved else schedules.map { if (it.id == saved.id) saved else it }
            _statusMessage.value = if (isNew) "已创建定时任务" else "已更新定时任务"
        }
    }

    fun toggleEnabled(schedule: AgentSchedule, enabled: Boolean) = saveSchedule(schedule.copy(enabled = enabled))

    fun deleteSchedule(id: String) = update("删除定时任务失败") { current ->
        cloudClient.deleteSchedule(id)
        if (session.value == current) {
            _schedules.value = _schedules.value.filterNot { it.id == id }
            _statusMessage.value = "已删除定时任务"
        }
    }

    fun triggerNow(schedule: AgentSchedule) = update("提交定时任务失败") { current ->
        val job = checkNotNull(cloudClient.triggerSchedule(schedule.id)) { "服务端未返回任务 ID" }
        if (session.value == current) {
            _statusMessage.value = "任务已提交，可在 Agent 对话列表查看"
            _schedules.value = _schedules.value.map {
                if (it.id == schedule.id) it.copy(lastJobId = job.jobId, lastStatus = job.status, lastError = "") else it
            }
        }
    }

    private fun update(errorLabel: String, action: suspend (WorkflowSession) -> Unit) {
        val current = session.value
        if (current == null) {
            _statusMessage.value = "请先连接 AnyWorkflow 云端账号"
            return
        }
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                action(current)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (session.value == current) _statusMessage.value = "$errorLabel：${error.message.orEmpty()}"
            } finally {
                if (session.value == current) _isLoading.value = false
            }
        }
    }
}
