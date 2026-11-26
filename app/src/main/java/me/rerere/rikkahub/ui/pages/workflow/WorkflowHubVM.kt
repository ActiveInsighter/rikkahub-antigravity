package me.rerere.rikkahub.ui.pages.workflow

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.agent.AgentRepository
import me.rerere.rikkahub.data.beszel.BeszelClient
import me.rerere.rikkahub.data.beszel.BeszelSettingsStore
import me.rerere.rikkahub.data.beszel.BeszelSystem
import me.rerere.rikkahub.data.pocketbase.PocketBaseSettingsStore
import me.rerere.rikkahub.data.workflow.DispatchStatus
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowRun

enum class HubNoticeSeverity {
    ERROR,
    WARNING,
    INFO,
}

@Immutable
data class WorkflowHubNotice(
    val id: String,
    val title: String,
    val subtitle: String,
    val severity: HubNoticeSeverity,
    val destination: Screen,
)

@Immutable
data class WorkflowHubUiState(
    val isRefreshing: Boolean = false,
    val workflowConnected: Boolean = false,
    val workflowEmail: String? = null,
    val activeRunsCount: Int = 0,
    val failedRunsCount: Int = 0,
    val recentRunsCount: Int = 0,
    val beszelConnected: Boolean = false,
    val beszelSystems: List<BeszelSystem> = emptyList(),
    val pocketBaseConnected: Boolean = false,
    val pocketBaseHost: String? = null,
    val activeAgentCount: Int = 0,
    val unreadAgentCount: Int = 0,
    val notices: List<WorkflowHubNotice> = emptyList(),
)

class WorkflowHubVM(
    private val workflowRepository: WorkflowRepository,
    private val beszelSettingsStore: BeszelSettingsStore,
    private val beszelClient: BeszelClient,
    private val pocketBaseSettingsStore: PocketBaseSettingsStore,
    private val agentRepository: AgentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkflowHubUiState())
    val uiState: StateFlow<WorkflowHubUiState> = _uiState.asStateFlow()

    private var cachedRuns: List<WorkflowRun> = emptyList()
    private var cachedSystems: List<BeszelSystem> = emptyList()

    init {
        viewModelScope.launch {
            workflowRepository.session.collectLatest { session ->
                _uiState.update {
                    it.copy(
                        workflowConnected = session != null,
                        workflowEmail = session?.email,
                    )
                }
                refresh()
            }
        }
        viewModelScope.launch {
            beszelSettingsStore.session.collectLatest { session ->
                _uiState.update { it.copy(beszelConnected = session != null) }
                refresh()
            }
        }
        viewModelScope.launch {
            pocketBaseSettingsStore.activeSession.collectLatest { session ->
                _uiState.update {
                    it.copy(
                        pocketBaseConnected = session != null,
                        pocketBaseHost = session?.baseUrl,
                    )
                }
            }
        }
        viewModelScope.launch {
            agentRepository.observeActiveConversationIds().collectLatest { activeIds ->
                _uiState.update { it.copy(activeAgentCount = activeIds.size) }
                rebuildNotices()
            }
        }
        viewModelScope.launch {
            agentRepository.observeConversationStatusMap().collectLatest { statusMap ->
                val unread = statusMap.values.count { it.hasUnread }
                _uiState.update { it.copy(unreadAgentCount = unread) }
                rebuildNotices()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val runsDeferred = async {
                if (workflowRepository.session.value != null) {
                    workflowRepository.listRuns(page = 1, perPage = 10).getOrNull()?.items.orEmpty()
                } else {
                    emptyList()
                }
            }
            val systemsDeferred = async {
                val beszelSession = beszelSettingsStore.session.value
                if (beszelSession != null) {
                    runCatching { beszelClient.fetchSystems(beszelSession) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
            cachedRuns = runsDeferred.await()
            cachedSystems = systemsDeferred.await()

            val activeRuns = cachedRuns.count { it.status.isActive }
            val failedRuns = cachedRuns.count { it.status == DispatchStatus.FAILED }

            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    activeRunsCount = activeRuns,
                    failedRunsCount = failedRuns,
                    recentRunsCount = cachedRuns.size,
                    beszelSystems = cachedSystems,
                )
            }
            rebuildNotices()
        }
    }

    private fun rebuildNotices() {
        val state = _uiState.value
        val notices = buildList {
            val downSystems = cachedSystems.filter { it.isDown }
            if (downSystems.isNotEmpty()) {
                val names = downSystems.joinToString("、") { it.name }
                val target = if (downSystems.size == 1) {
                    Screen.BeszelDetail(downSystems.first().id)
                } else {
                    Screen.Beszel
                }
                add(
                    WorkflowHubNotice(
                        id = "beszel_down",
                        title = "${downSystems.size} 台服务器离线",
                        subtitle = names,
                        severity = HubNoticeSeverity.ERROR,
                        destination = target,
                    )
                )
            }

            val highLoadSystems = cachedSystems.filter {
                it.isUp && (it.cpuPercent >= 90.0 || it.memoryPercent >= 90.0 || it.diskPercent >= 90.0)
            }
            if (highLoadSystems.isNotEmpty()) {
                val first = highLoadSystems.first()
                add(
                    WorkflowHubNotice(
                        id = "beszel_high_load",
                        title = "服务器资源告警：${first.name}",
                        subtitle = "CPU ${first.cpuPercent.toInt()}% · 内存 ${first.memoryPercent.toInt()}% · 磁盘 ${first.diskPercent.toInt()}%",
                        severity = HubNoticeSeverity.WARNING,
                        destination = Screen.BeszelDetail(first.id),
                    )
                )
            }

            val failedRuns = cachedRuns.filter { it.status == DispatchStatus.FAILED }
            if (failedRuns.isNotEmpty()) {
                add(
                    WorkflowHubNotice(
                        id = "workflow_failed",
                        title = "${failedRuns.size} 个工作流执行失败",
                        subtitle = failedRuns.first().title.ifBlank { "点击查看执行详情" },
                        severity = HubNoticeSeverity.ERROR,
                        destination = Screen.Workflow,
                    )
                )
            }

            val activeRuns = cachedRuns.filter { it.status.isActive }
            if (activeRuns.isNotEmpty()) {
                add(
                    WorkflowHubNotice(
                        id = "workflow_running",
                        title = "${activeRuns.size} 个工作流正在运行",
                        subtitle = activeRuns.first().title.ifBlank { "点击查看实时进度" },
                        severity = HubNoticeSeverity.INFO,
                        destination = Screen.Workflow,
                    )
                )
            }

            if (state.activeAgentCount > 0 || state.unreadAgentCount > 0) {
                val parts = buildList {
                    if (state.activeAgentCount > 0) add("${state.activeAgentCount} 个运行中")
                    if (state.unreadAgentCount > 0) add("${state.unreadAgentCount} 个未读结果")
                }
                add(
                    WorkflowHubNotice(
                        id = "agent_tasks",
                        title = "Agent 后台任务状态",
                        subtitle = parts.joinToString(" · "),
                        severity = HubNoticeSeverity.INFO,
                        destination = Screen.AgentWorkflow,
                    )
                )
            }
        }
        _uiState.update { it.copy(notices = notices) }
    }
}
