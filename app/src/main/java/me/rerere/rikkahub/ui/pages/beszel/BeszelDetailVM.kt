package me.rerere.rikkahub.ui.pages.beszel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.beszel.BeszelChartRange
import me.rerere.rikkahub.data.beszel.BeszelClient
import me.rerere.rikkahub.data.beszel.BeszelContainer
import me.rerere.rikkahub.data.beszel.BeszelSettingsStore
import me.rerere.rikkahub.data.beszel.BeszelSmartDevice
import me.rerere.rikkahub.data.beszel.BeszelStatsPoint
import me.rerere.rikkahub.data.beszel.BeszelSystem
import me.rerere.rikkahub.data.beszel.BeszelSystemDetails
import me.rerere.rikkahub.data.beszel.BeszelSystemdService

data class BeszelInspectorSheetState(
    val title: String,
    val subtitle: String,
    val isLoading: Boolean = true,
    val content: String = "",
    val error: String? = null,
)

data class BeszelDetailUiState(
    val systemId: String,
    val system: BeszelSystem? = null,
    val details: BeszelSystemDetails? = null,
    val range: BeszelChartRange = BeszelChartRange.HOUR,
    val stats: List<BeszelStatsPoint> = emptyList(),
    val livePoint: BeszelStatsPoint? = null,
    val containers: List<BeszelContainer> = emptyList(),
    val services: List<BeszelSystemdService> = emptyList(),
    val smartDevices: List<BeszelSmartDevice> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isRefreshingSmart: Boolean = false,
    val error: String? = null,
    val containerSearch: String = "",
    val inspectorSheet: BeszelInspectorSheetState? = null,
) {
    val latestPoint: BeszelStatsPoint? get() = livePoint ?: stats.lastOrNull()
}

class BeszelDetailVM(
    private val systemId: String,
    private val client: BeszelClient,
    private val settingsStore: BeszelSettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BeszelDetailUiState(systemId = systemId))
    val uiState: StateFlow<BeszelDetailUiState> = _uiState.asStateFlow()

    private var liveJob: Job? = null

    init {
        loadAll(initial = true)
    }

    fun selectRange(range: BeszelChartRange) {
        if (_uiState.value.range == range) return
        _uiState.update { it.copy(range = range) }
        if (range == BeszelChartRange.LIVE) {
            loadHistoryForRange(range)
            startLiveStream()
        } else {
            stopLiveStream()
            loadHistoryForRange(range)
        }
    }

    fun refresh() {
        loadAll(initial = false)
    }

    fun setContainerSearch(query: String) {
        _uiState.update { it.copy(containerSearch = query) }
    }

    fun openContainerLogs(container: BeszelContainer) {
        val session = settingsStore.session.value ?: return
        _uiState.update {
            it.copy(
                inspectorSheet = BeszelInspectorSheetState(
                    title = "${container.name} · 日志",
                    subtitle = container.image,
                    isLoading = true,
                ),
            )
        }
        viewModelScope.launch {
            runCatching {
                client.fetchContainerLogs(session, systemId, container.id)
            }.onSuccess { logs ->
                _uiState.update { state ->
                    state.copy(
                        inspectorSheet = state.inspectorSheet?.copy(
                            isLoading = false,
                            content = logs.ifBlank { "暂无日志输出" },
                        ),
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        inspectorSheet = state.inspectorSheet?.copy(
                            isLoading = false,
                            error = err.message ?: "获取日志失败",
                        ),
                    )
                }
            }
        }
    }

    fun openContainerInfo(container: BeszelContainer) {
        val session = settingsStore.session.value ?: return
        _uiState.update {
            it.copy(
                inspectorSheet = BeszelInspectorSheetState(
                    title = "${container.name} · 详情",
                    subtitle = container.image,
                    isLoading = true,
                ),
            )
        }
        viewModelScope.launch {
            runCatching {
                client.fetchContainerInfo(session, systemId, container.id)
            }.onSuccess { info ->
                _uiState.update { state ->
                    state.copy(
                        inspectorSheet = state.inspectorSheet?.copy(
                            isLoading = false,
                            content = info.ifBlank { "暂无详细信息" },
                        ),
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        inspectorSheet = state.inspectorSheet?.copy(
                            isLoading = false,
                            error = err.message ?: "获取容器详情失败",
                        ),
                    )
                }
            }
        }
    }

    fun openServiceLogs(service: BeszelSystemdService) {
        val session = settingsStore.session.value ?: return
        _uiState.update {
            it.copy(
                inspectorSheet = BeszelInspectorSheetState(
                    title = "${service.name} · 日志",
                    subtitle = "${service.stateName} (${service.subName})",
                    isLoading = true,
                ),
            )
        }
        viewModelScope.launch {
            runCatching {
                client.fetchServiceLogs(session, systemId, service.name)
            }.onSuccess { logs ->
                _uiState.update { state ->
                    state.copy(
                        inspectorSheet = state.inspectorSheet?.copy(
                            isLoading = false,
                            content = logs.ifBlank { "暂无日志输出" },
                        ),
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        inspectorSheet = state.inspectorSheet?.copy(
                            isLoading = false,
                            error = err.message ?: "获取服务日志失败",
                        ),
                    )
                }
            }
        }
    }

    fun closeInspectorSheet() {
        _uiState.update { it.copy(inspectorSheet = null) }
    }

    fun refreshSmartDevices() {
        val session = settingsStore.session.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingSmart = true) }
            runCatching {
                client.refreshSmart(session, systemId)
                client.fetchSmartDevices(session, systemId)
            }.onSuccess { devices ->
                _uiState.update {
                    it.copy(smartDevices = devices, isRefreshingSmart = false)
                }
            }.onFailure {
                _uiState.update { it.copy(isRefreshingSmart = false) }
            }
        }
    }

    private fun loadAll(initial: Boolean) {
        val session = settingsStore.session.value ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = initial,
                    isRefreshing = !initial,
                    error = null,
                )
            }
            runCatching {
                coroutineScope {
                    val systemsDeferred = async { client.fetchSystems(session) }
                    val statsDeferred = async { client.fetchStats(session, systemId, _uiState.value.range) }
                    val containersDeferred = async { runCatching { client.fetchContainers(session, systemId) }.getOrDefault(emptyList()) }
                    val servicesDeferred = async { runCatching { client.fetchServices(session, systemId) }.getOrDefault(emptyList()) }
                    val smartDeferred = async { runCatching { client.fetchSmartDevices(session, systemId) }.getOrDefault(emptyList()) }

                    val system = systemsDeferred.await().firstOrNull { it.id == systemId }
                    val details = system?.let { client.fetchSystemDetails(session, it) }
                    val stats = statsDeferred.await()
                    val containers = containersDeferred.await()
                    val services = servicesDeferred.await()
                    val smart = smartDeferred.await()

                    DetailLoadResult(system, details, stats, containers, services, smart)
                }
            }.onSuccess { res ->
                _uiState.update {
                    it.copy(
                        system = res.system ?: it.system,
                        details = res.details ?: it.details,
                        stats = res.stats,
                        containers = res.containers,
                        services = res.services,
                        smartDevices = res.smart,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }
                if (_uiState.value.range == BeszelChartRange.LIVE && liveJob == null) {
                    startLiveStream()
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = err.message ?: "加载服务器详情失败",
                    )
                }
            }
        }
    }

    private fun loadHistoryForRange(range: BeszelChartRange) {
        val session = settingsStore.session.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            runCatching {
                client.fetchStats(session, systemId, range)
            }.onSuccess { points ->
                _uiState.update {
                    it.copy(
                        stats = points,
                        isRefreshing = false,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun startLiveStream() {
        val session = settingsStore.session.value ?: return
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            client.observeLiveMetrics(session, systemId).collect { point ->
                _uiState.update { state ->
                    val updated = (state.stats + point).takeLast(60)
                    state.copy(
                        stats = updated,
                        livePoint = point,
                    )
                }
            }
        }
    }

    private fun stopLiveStream() {
        liveJob?.cancel()
        liveJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveStream()
    }

    private data class DetailLoadResult(
        val system: BeszelSystem?,
        val details: BeszelSystemDetails?,
        val stats: List<BeszelStatsPoint>,
        val containers: List<BeszelContainer>,
        val services: List<BeszelSystemdService>,
        val smart: List<BeszelSmartDevice>,
    )
}
