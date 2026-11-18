package me.rerere.rikkahub.ui.pages.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.agent.AgentCloudClient
import me.rerere.rikkahub.data.agent.AgentSchedule
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.agent.DEFAULT_AGENT_MODELS
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowSession
import java.util.UUID

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

    private val _schedules = MutableStateFlow(settingsStore.current.localAgentSchedules)
    val schedules: StateFlow<List<AgentSchedule>> = _schedules.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _backendNotice = MutableStateFlow<String?>(null)
    val backendNotice: StateFlow<String?> = _backendNotice.asStateFlow()

    init {
        refresh()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun refresh() {
        if (!cloudClient.hasSession()) {
            _schedules.value = settingsStore.current.localAgentSchedules
            _backendNotice.value = "未连接 AnyWorkflow 云端，当前仅展示本地草稿。"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                cloudClient.listSchedules()
            }.onSuccess { remote ->
                _schedules.value = remote
                _backendNotice.value = null
                settingsStore.update { it.copy(localAgentSchedules = remote) }
            }.onFailure { err ->
                _schedules.value = settingsStore.current.localAgentSchedules
                _backendNotice.value = if (err.message?.contains("404") == true) {
                    "云端定时任务接口尚未迁移（请按后端迁移文档更新 Worker / PocketBase），当前支持本地保存与立即执行。"
                } else {
                    "同步定时任务失败：${err.message ?: "未知错误"}"
                }
            }
            _isLoading.value = false
        }
    }

    fun saveSchedule(schedule: AgentSchedule) {
        val isNew = schedule.id.isBlank()
        val draft = if (isNew) {
            schedule.copy(id = "local_${UUID.randomUUID().toString().take(12)}")
        } else {
            schedule
        }
        viewModelScope.launch {
            _isLoading.value = true
            val saved = if (cloudClient.hasSession()) {
                runCatching {
                    if (isNew || draft.id.startsWith("local_")) {
                        cloudClient.createSchedule(draft)
                    } else {
                        cloudClient.updateSchedule(draft)
                    }
                }.onFailure { err ->
                    _statusMessage.value = "已保存到本地（云端同步待迁移：${err.message ?: "请求失败"}）"
                }.getOrDefault(draft)
            } else {
                draft
            }
            val updatedList = (_schedules.value.filterNot { it.id == draft.id || it.id == saved.id } + saved)
            _schedules.value = updatedList
            settingsStore.update { it.copy(localAgentSchedules = updatedList) }
            if (_statusMessage.value == null) {
                _statusMessage.value = if (isNew) "已创建定时任务" else "已更新定时任务"
            }
            _isLoading.value = false
        }
    }

    fun toggleEnabled(schedule: AgentSchedule, enabled: Boolean) {
        saveSchedule(schedule.copy(enabled = enabled))
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            if (cloudClient.hasSession() && !id.startsWith("local_")) {
                runCatching { cloudClient.deleteSchedule(id) }
            }
            val updatedList = _schedules.value.filterNot { it.id == id }
            _schedules.value = updatedList
            settingsStore.update { it.copy(localAgentSchedules = updatedList) }
            _statusMessage.value = "已删除定时任务"
            _isLoading.value = false
        }
    }

    fun triggerNow(schedule: AgentSchedule) {
        if (!cloudClient.hasSession()) {
            _statusMessage.value = "请先在 Agent 页面连接 AnyWorkflow 云端账号"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val result = runCatching {
                if (!schedule.id.startsWith("local_")) {
                    cloudClient.triggerSchedule(schedule.id)
                } else {
                    null
                } ?: cloudClient.submitMessage(
                    prompt = schedule.prompt,
                    title = schedule.title.ifBlank { "定时任务" },
                    modelOverride = schedule.model,
                    thinkingLevelOverride = schedule.thinkingLevel,
                    systemPromptOverride = schedule.systemPrompt.takeIf { it.isNotBlank() },
                )
            }
            result.onSuccess { job ->
                _statusMessage.value = "已触发 GitHub Action（任务 ID: ${job.jobId}）"
                val updated = schedule.copy(lastJobId = job.jobId, lastStatus = job.status)
                val nextList = _schedules.value.map { if (it.id == schedule.id) updated else it }
                _schedules.value = nextList
                settingsStore.update { it.copy(localAgentSchedules = nextList) }
            }.onFailure { err ->
                _statusMessage.value = "触发失败：${err.message ?: "未知错误"}"
            }
            _isLoading.value = false
        }
    }
}
