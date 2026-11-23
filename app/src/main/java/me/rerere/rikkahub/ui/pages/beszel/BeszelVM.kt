package me.rerere.rikkahub.ui.pages.beszel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.beszel.BeszelAlertEvent
import me.rerere.rikkahub.data.beszel.BeszelChartRange
import me.rerere.rikkahub.data.beszel.BeszelConfig
import me.rerere.rikkahub.data.beszel.BeszelContainer
import me.rerere.rikkahub.data.beszel.BeszelRepository
import me.rerere.rikkahub.data.beszel.BeszelStatsPoint
import me.rerere.rikkahub.data.beszel.BeszelSystem
import me.rerere.rikkahub.data.beszel.BeszelSystemDetails
import me.rerere.rikkahub.data.beszel.BeszelSystemdService

enum class BeszelStatusFilter {
    ALL, UP, DOWN
}

data class BeszelUiState(
    val serverUrl: String = "",
    val identity: String = "",
    val password: String = "",
    val allowInsecureTls: Boolean = false,
    val isConnecting: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val statusFilter: BeszelStatusFilter = BeszelStatusFilter.ALL,
    val autoRefresh: Boolean = true,
    val lastUpdatedMillis: Long = 0L,
    val alerts: List<BeszelAlertEvent> = emptyList(),
    val isLoadingAlerts: Boolean = false,
)

class BeszelVM(
    private val repository: BeszelRepository,
) : ViewModel() {

    val config: StateFlow<BeszelConfig?> = repository.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.config.value)

    val systems: StateFlow<List<BeszelSystem>> = repository.systems
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.systems.value)

    private val _uiState = MutableStateFlow(
        BeszelUiState(
            serverUrl = repository.config.value?.serverUrl.orEmpty(),
            identity = repository.config.value?.identity.orEmpty(),
            allowInsecureTls = repository.config.value?.allowInsecureTls ?: false,
        )
    )
    val uiState: StateFlow<BeszelUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        if (repository.config.value?.isConfigured == true) {
            refreshSystems()
        }
        startAutoRefreshLoop()
    }

    fun updateServerUrl(value: String) {
        _uiState.update { it.copy(serverUrl = value, error = null) }
    }

    fun updateIdentity(value: String) {
        _uiState.update { it.copy(identity = value, error = null) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun updateAllowInsecureTls(value: Boolean) {
        _uiState.update { it.copy(allowInsecureTls = value, error = null) }
    }

    fun updateSearchQuery(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun updateStatusFilter(filter: BeszelStatusFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
    }

    fun toggleAutoRefresh() {
        _uiState.update { it.copy(autoRefresh = !it.autoRefresh) }
    }

    fun connect() {
        val state = _uiState.value
        val url = state.serverUrl.trim()
        val identity = state.identity.trim()
        val password = state.password

        if (url.isEmpty()) {
            _uiState.update { it.copy(error = "请输入 Beszel 服务器地址") }
            return
        }
        if (identity.isEmpty()) {
            _uiState.update { it.copy(error = "请输入账号或邮箱") }
            return
        }
        if (password.isEmpty()) {
            _uiState.update { it.copy(error = "请输入密码") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }
            try {
                repository.login(
                    serverUrl = url,
                    identity = identity,
                    password = password,
                    allowInsecureTls = state.allowInsecureTls,
                )
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        password = "",
                        error = null,
                        lastUpdatedMillis = System.currentTimeMillis(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        error = e.message ?: "连接失败，请检查服务器地址和账号密码",
                    )
                }
            }
        }
    }

    fun refreshSystems(silent: Boolean = false) {
        if (repository.config.value?.isConfigured != true) return
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
            }
            try {
                repository.fetchSystems()
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = null,
                        lastUpdatedMillis = System.currentTimeMillis(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = if (silent) it.error else (e.message ?: "刷新失败"),
                    )
                }
            }
        }
    }

    fun loadAlerts() {
        if (repository.config.value?.isConfigured != true) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAlerts = true) }
            val alerts = runCatching { repository.fetchAlertHistory() }.getOrDefault(emptyList())
            _uiState.update { it.copy(alerts = alerts, isLoadingAlerts = false) }
        }
    }

    fun logout() {
        repository.logout()
        val saved = repository.config.value
        _uiState.update {
            BeszelUiState(
                serverUrl = saved?.serverUrl.orEmpty(),
                identity = saved?.identity.orEmpty(),
                allowInsecureTls = saved?.allowInsecureTls ?: false,
            )
        }
    }

    private fun startAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000L)
                if (_uiState.value.autoRefresh &&
                    repository.config.value?.isConfigured == true &&
                    !_uiState.value.isRefreshing
                ) {
                    refreshSystems(silent = true)
                }
            }
        }
    }
}

data class BeszelDetailUiState(
    val system: BeszelSystem? = null,
    val details: BeszelSystemDetails? = null,
    val stats: List<BeszelStatsPoint> = emptyList(),
    val containers: List<BeszelContainer> = emptyList(),
    val services: List<BeszelSystemdService> = emptyList(),
    val range: BeszelChartRange = BeszelChartRange.HOUR_1,
    val isLoading: Boolean = true,
    val isChartLoading: Boolean = false,
    val error: String? = null,
)

class BeszelDetailVM(
    private val systemId: String,
    private val repository: BeszelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BeszelDetailUiState(
            system = repository.systems.value.find { it.id == systemId }
        )
    )
    val uiState: StateFlow<BeszelDetailUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun selectRange(range: BeszelChartRange) {
        if (_uiState.value.range == range) return
        _uiState.update { it.copy(range = range, isChartLoading = true) }
        viewModelScope.launch {
            try {
                val stats = repository.fetchSystemStatsOnly(systemId, range)
                _uiState.update {
                    it.copy(
                        stats = stats,
                        isChartLoading = false,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isChartLoading = false,
                        error = e.message ?: "加载图表数据失败",
                    )
                }
            }
        }
    }

    fun refresh() {
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val latestSystems = runCatching { repository.fetchSystems() }.getOrNull()
                val system = latestSystems?.find { it.id == systemId }
                    ?: repository.systems.value.find { it.id == systemId }
                    ?: _uiState.value.system

                if (system == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "未找到该服务器节点")
                    }
                    return@launch
                }

                val bundle = repository.fetchSystemDetailBundle(system, _uiState.value.range)
                _uiState.update {
                    it.copy(
                        system = system,
                        details = bundle.details,
                        stats = bundle.stats,
                        containers = bundle.containers,
                        services = bundle.services,
                        isLoading = false,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载服务器详情失败",
                    )
                }
            }
        }
    }
}
