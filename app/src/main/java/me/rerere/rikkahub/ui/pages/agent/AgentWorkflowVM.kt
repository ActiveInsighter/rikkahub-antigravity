package me.rerere.rikkahub.ui.pages.agent

import android.content.Context
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
import me.rerere.rikkahub.data.agent.AgentScheduledTask
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.agent.AgentWorkflowWorker
import me.rerere.rikkahub.data.agent.DEFAULT_AGENT_MODELS
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowSession
import java.util.UUID

class AgentWorkflowVM(
    private val context: Context,
    private val settingsStore: AgentSettingsStore,
    private val cloudClient: AgentCloudClient,
    private val workflowRepository: WorkflowRepository,
) : ViewModel() {

    val tasks: StateFlow<List<AgentScheduledTask>> = settingsStore.settingsFlow
        .map { it.agentSchedules }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.current.agentSchedules)

    val savedModels: StateFlow<List<String>> = settingsStore.settingsFlow
        .map { it.savedAgentModels.ifEmpty { DEFAULT_AGENT_MODELS } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.current.savedAgentModels.ifEmpty { DEFAULT_AGENT_MODELS })

    val session: StateFlow<WorkflowSession?> = workflowRepository.session

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _runningTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val runningTaskIds: StateFlow<Set<String>> = _runningTaskIds.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshFromRemote()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun refreshFromRemote() {
        if (session.value == null || _isSyncing.value) return
        _isSyncing.value = true
        viewModelScope.launch {
            cloudClient.listRemoteSchedules().onSuccess { remoteList ->
                settingsStore.update { it.copy(agentSchedules = remoteList) }
                AgentWorkflowWorker.sync(context, remoteList)
            }
            _isSyncing.value = false
        }
    }

    fun saveTask(
        editingId: String?,
        name: String,
        prompt: String,
        systemPrompt: String,
        model: String,
        thinkingLevel: String,
        intervalMinutes: Int,
        enabled: Boolean,
    ) {
        val cleanModel = model.trim().removePrefix("models/").ifBlank { "Antigravity" }
        val cleanName = name.trim().ifBlank { prompt.trim().take(24).ifBlank { "定时任务" } }
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return
        val interval = intervalMinutes.coerceIn(5, 43200)
        val now = System.currentTimeMillis()
        val existing = editingId?.let { id -> settingsStore.current.agentSchedules.find { it.id == id } }
        val draft = AgentScheduledTask(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = cleanName,
            prompt = cleanPrompt,
            systemPrompt = systemPrompt.trim(),
            model = cleanModel,
            thinkingLevel = thinkingLevel,
            intervalMinutes = interval,
            enabled = enabled,
            lastRunAt = existing?.lastRunAt,
            nextRunAt = if (enabled) (existing?.nextRunAt ?: (now + interval * 60_000L)) else null,
            lastJobId = existing?.lastJobId,
            lastStatus = existing?.lastStatus,
            lastError = existing?.lastError,
        )

        viewModelScope.launch {
            val finalTask = if (session.value != null) {
                cloudClient.upsertRemoteSchedule(draft).getOrDefault(draft)
            } else {
                draft
            }
            settingsStore.update { current ->
                val list = current.agentSchedules.toMutableList()
                val idx = list.indexOfFirst { it.id == draft.id || it.id == finalTask.id }
                if (idx >= 0) {
                    list[idx] = finalTask
                } else {
                    list.add(0, finalTask)
                }
                val models = current.savedAgentModels.ifEmpty { DEFAULT_AGENT_MODELS }
                val updatedModels = if (models.any { it.equals(cleanModel, ignoreCase = true) }) models else models + cleanModel
                current.copy(agentSchedules = list, savedAgentModels = updatedModels)
            }
            AgentWorkflowWorker.sync(context, settingsStore.current.agentSchedules)
        }
    }

    fun toggleTask(task: AgentScheduledTask, enabled: Boolean) {
        val now = System.currentTimeMillis()
        val updated = task.copy(
            enabled = enabled,
            nextRunAt = if (enabled) (now + task.intervalMinutes.coerceAtLeast(5) * 60_000L) else null,
        )
        viewModelScope.launch {
            val saved = if (session.value != null) {
                cloudClient.upsertRemoteSchedule(updated).getOrDefault(updated)
            } else {
                updated
            }
            settingsStore.update { current ->
                current.copy(
                    agentSchedules = current.agentSchedules.map {
                        if (it.id == task.id) saved else it
                    }
                )
            }
            AgentWorkflowWorker.sync(context, settingsStore.current.agentSchedules)
        }
    }

    fun deleteTask(task: AgentScheduledTask) {
        viewModelScope.launch {
            if (session.value != null) {
                cloudClient.deleteRemoteSchedule(task.id)
            }
            settingsStore.update { current ->
                current.copy(agentSchedules = current.agentSchedules.filterNot { it.id == task.id })
            }
            AgentWorkflowWorker.sync(context, settingsStore.current.agentSchedules)
        }
    }

    fun runTaskNow(task: AgentScheduledTask) {
        if (session.value == null) {
            _statusMessage.value = "请先在 Agent 界面连接 AnyWorkflow 账号"
            return
        }
        if (task.id in _runningTaskIds.value) return
        _runningTaskIds.value += task.id
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val result = if (task.id.matches(Regex("^[a-z0-9]{15}$"))) {
                cloudClient.triggerRemoteSchedule(task.id).recoverCatching {
                    cloudClient.submitMessage(
                        prompt = task.prompt,
                        title = "[定时] ${task.name}",
                        modelOverride = task.model,
                        thinkingLevelOverride = task.thinkingLevel,
                        systemPromptOverride = task.systemPrompt,
                    )
                }
            } else {
                runCatching {
                    cloudClient.submitMessage(
                        prompt = task.prompt,
                        title = "[定时] ${task.name}",
                        modelOverride = task.model,
                        thinkingLevelOverride = task.thinkingLevel,
                        systemPromptOverride = task.systemPrompt,
                    )
                }
            }

            result.onSuccess { submitted ->
                val updated = task.copy(
                    lastRunAt = now,
                    nextRunAt = if (task.enabled) now + task.intervalMinutes.coerceAtLeast(5) * 60_000L else null,
                    lastJobId = submitted.jobId,
                    lastStatus = submitted.status,
                    lastError = null,
                )
                settingsStore.update { current ->
                    current.copy(
                        agentSchedules = current.agentSchedules.map { if (it.id == task.id) updated else it }
                    )
                }
                _statusMessage.value = "已触发 Action（任务 ${submitted.jobId}）"
            }.onFailure { err ->
                val updated = task.copy(
                    lastRunAt = now,
                    lastStatus = "failed",
                    lastError = err.message ?: "触发失败",
                )
                settingsStore.update { current ->
                    current.copy(
                        agentSchedules = current.agentSchedules.map { if (it.id == task.id) updated else it }
                    )
                }
                _statusMessage.value = err.message ?: "触发 Action 失败"
            }
            _runningTaskIds.value -= task.id
        }
    }
}
