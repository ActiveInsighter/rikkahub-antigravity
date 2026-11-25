package me.rerere.rikkahub.ui.pages.pocketbase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import me.rerere.rikkahub.data.pocketbase.PbBackup
import me.rerere.rikkahub.data.pocketbase.PbCollection
import me.rerere.rikkahub.data.pocketbase.PbCron
import me.rerere.rikkahub.data.pocketbase.PbHealthInfo
import me.rerere.rikkahub.data.pocketbase.PbLogEntry
import me.rerere.rikkahub.data.pocketbase.PbLogStat
import me.rerere.rikkahub.data.pocketbase.PbLogsPage
import me.rerere.rikkahub.data.pocketbase.PbSqlResult
import me.rerere.rikkahub.data.pocketbase.PocketBaseAuthResult
import me.rerere.rikkahub.data.pocketbase.PocketBaseClient
import me.rerere.rikkahub.data.pocketbase.PocketBaseConnection
import me.rerere.rikkahub.data.pocketbase.PocketBaseSession
import me.rerere.rikkahub.data.pocketbase.PocketBaseSettingsStore

enum class PocketBaseTab(val label: String) {
    OVERVIEW("概览"),
    COLLECTIONS("数据表"),
    LOGS("日志"),
    SQL("SQL"),
    OPS("运维"),
}

enum class CollectionFilter(val label: String) {
    ALL("全部"),
    BASE("业务表"),
    AUTH("认证表"),
    VIEW("视图"),
}

enum class LogFilterPreset(val label: String, val filterExpr: String?) {
    ALL("全部", null),
    ERRORS("错误 (>=400)", "data.status >= 400"),
    WARN_ERROR("警告/错误", "level >= 4"),
}

data class PocketBaseUiState(
    val session: PocketBaseSession? = null,
    val connections: List<PocketBaseConnection> = emptyList(),
    val selectedTab: PocketBaseTab = PocketBaseTab.OVERVIEW,
    val isConnecting: Boolean = false,
    val authError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val autoRefreshEnabled: Boolean = false,
    val realtimeEnabled: Boolean = true,
    // Overview & Health
    val health: PbHealthInfo? = null,
    val lastRefreshedAt: Long? = null,
    // Collections
    val collections: List<PbCollection> = emptyList(),
    val collectionSearch: String = "",
    val collectionFilter: CollectionFilter = CollectionFilter.ALL,
    val showSystemCollections: Boolean = false,
    // Logs
    val logsPage: PbLogsPage? = null,
    val logStats: List<PbLogStat> = emptyList(),
    val errorStats: List<PbLogStat> = emptyList(),
    val logPreset: LogFilterPreset = LogFilterPreset.ALL,
    val customLogFilter: String = "",
    val isLoadingLogs: Boolean = false,
    // SQL
    val sqlQuery: String = "SELECT name, type FROM sqlite_master WHERE type IN ('table', 'view') ORDER BY name;",
    val sqlResult: PbSqlResult? = null,
    val sqlError: String? = null,
    val isRunningSql: Boolean = false,
    // Ops (Backups, Crons, Settings)
    val backups: List<PbBackup> = emptyList(),
    val crons: List<PbCron> = emptyList(),
    val serverSettings: JsonObject? = null,
    val isLoadingOps: Boolean = false,
)

class PocketBaseVM(
    private val client: PocketBaseClient,
    private val settingsStore: PocketBaseSettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PocketBaseUiState(
            session = settingsStore.activeSession.value,
            connections = settingsStore.connections.value,
            autoRefreshEnabled = settingsStore.autoRefreshEnabled.value,
            realtimeEnabled = settingsStore.realtimeEnabled.value,
        ),
    )
    val uiState: StateFlow<PocketBaseUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.connections.collect { conns ->
                _uiState.update { it.copy(connections = conns) }
            }
        }
        viewModelScope.launch {
            settingsStore.activeSession.collect { session ->
                _uiState.update { it.copy(session = session, authError = null, errorMessage = null) }
                if (session != null) {
                    refreshAll()
                    restartAutoRefreshIfNeeded()
                } else {
                    autoRefreshJob?.cancel()
                    _uiState.update {
                        it.copy(
                            health = null,
                            collections = emptyList(),
                            logsPage = null,
                            logStats = emptyList(),
                            errorStats = emptyList(),
                            backups = emptyList(),
                            crons = emptyList(),
                            serverSettings = null,
                            sqlResult = null,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.autoRefreshEnabled.collect { enabled ->
                _uiState.update { it.copy(autoRefreshEnabled = enabled) }
                restartAutoRefreshIfNeeded()
            }
        }
        viewModelScope.launch {
            settingsStore.realtimeEnabled.collect { enabled ->
                _uiState.update { it.copy(realtimeEnabled = enabled) }
            }
        }
    }

    fun selectTab(tab: PocketBaseTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        val session = _uiState.value.session ?: return
        when (tab) {
            PocketBaseTab.LOGS -> if (_uiState.value.logsPage == null) loadLogs(session)
            PocketBaseTab.OPS -> if (_uiState.value.backups.isEmpty() && _uiState.value.crons.isEmpty()) loadOps(session)
            else -> Unit
        }
    }

    fun loginWithPassword(
        baseUrl: String,
        connectionName: String,
        identity: String,
        password: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, authError = null) }
            when (val res = client.loginWithPassword(baseUrl, connectionName, identity, password)) {
                is PocketBaseAuthResult.Success -> {
                    _uiState.update { it.copy(isConnecting = false, authError = null) }
                }
                is PocketBaseAuthResult.Error -> {
                    _uiState.update { it.copy(isConnecting = false, authError = res.message) }
                }
            }
        }
    }

    fun connectWithToken(
        baseUrl: String,
        connectionName: String,
        token: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, authError = null) }
            when (val res = client.connectWithToken(baseUrl, connectionName, token)) {
                is PocketBaseAuthResult.Success -> {
                    _uiState.update { it.copy(isConnecting = false, authError = null) }
                }
                is PocketBaseAuthResult.Error -> {
                    _uiState.update { it.copy(isConnecting = false, authError = res.message) }
                }
            }
        }
    }

    fun switchConnection(connectionId: String) {
        val session = settingsStore.activateConnection(connectionId)
        if (session == null) {
            _uiState.update { it.copy(authError = "该连接凭据无法解密或已失效，请重新登录") }
        }
    }

    fun deleteConnection(connectionId: String) {
        settingsStore.deleteConnection(connectionId)
    }

    fun disconnect() {
        settingsStore.disconnectSession()
    }

    fun toggleAutoRefresh() {
        settingsStore.setAutoRefreshEnabled(!_uiState.value.autoRefreshEnabled)
    }

    fun toggleRealtime() {
        settingsStore.setRealtimeEnabled(!_uiState.value.realtimeEnabled)
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, actionMessage = null) }
    }

    fun setCollectionSearch(query: String) {
        _uiState.update { it.copy(collectionSearch = query) }
    }

    fun setCollectionFilter(filter: CollectionFilter) {
        _uiState.update { it.copy(collectionFilter = filter) }
    }

    fun toggleShowSystemCollections() {
        _uiState.update { it.copy(showSystemCollections = !it.showSystemCollections) }
    }

    fun refreshAll() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                coroutineScope {
                    val healthDef = async { runCatching { client.getHealth(session) }.getOrNull() }
                    val colsDef = async { client.listCollections(session) }
                    val statsDef = async { runCatching { client.getLogStats(session) }.getOrDefault(emptyList()) }
                    val errStatsDef = async {
                        runCatching { client.getLogStats(session, "data.status >= 400") }.getOrDefault(emptyList())
                    }
                    val logsDef = async { runCatching { client.listLogs(session, page = 1, perPage = 40, filter = activeLogFilter()) }.getOrNull() }
                    val backupsDef = async { runCatching { client.listBackups(session) }.getOrDefault(emptyList()) }
                    val cronsDef = async { runCatching { client.listCrons(session) }.getOrDefault(emptyList()) }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            health = healthDef.await(),
                            collections = colsDef.await(),
                            logStats = statsDef.await(),
                            errorStats = errStatsDef.await(),
                            logsPage = logsDef.await() ?: it.logsPage,
                            backups = backupsDef.await(),
                            crons = cronsDef.await(),
                            lastRefreshedAt = System.currentTimeMillis(),
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "刷新失败",
                    )
                }
            }
        }
    }

    fun setLogPreset(preset: LogFilterPreset) {
        _uiState.update { it.copy(logPreset = preset) }
        _uiState.value.session?.let { loadLogs(it, page = 1) }
    }

    fun setCustomLogFilter(filter: String) {
        _uiState.update { it.copy(customLogFilter = filter) }
    }

    fun applyLogSearch() {
        _uiState.value.session?.let { loadLogs(it, page = 1) }
    }

    fun loadLogsPage(page: Int) {
        _uiState.value.session?.let { loadLogs(it, page = page) }
    }

    private fun activeLogFilter(): String? {
        val preset = _uiState.value.logPreset.filterExpr
        val custom = _uiState.value.customLogFilter.trim().takeIf { it.isNotEmpty() }
        return when {
            preset != null && custom != null -> "($preset) && ($custom)"
            preset != null -> preset
            custom != null -> custom
            else -> null
        }
    }

    private fun loadLogs(session: PocketBaseSession, page: Int = 1) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLogs = true, errorMessage = null) }
            runCatching {
                val filter = activeLogFilter()
                coroutineScope {
                    val logsDef = async { client.listLogs(session, page = page, perPage = 50, filter = filter) }
                    val statsDef = async { runCatching { client.getLogStats(session, filter) }.getOrDefault(emptyList()) }
                    _uiState.update {
                        it.copy(
                            isLoadingLogs = false,
                            logsPage = logsDef.await(),
                            logStats = statsDef.await(),
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingLogs = false, errorMessage = err.message ?: "加载日志失败") }
            }
        }
    }

    fun setSqlQuery(query: String) {
        _uiState.update { it.copy(sqlQuery = query) }
    }

    fun runSql() {
        val session = _uiState.value.session ?: return
        val query = _uiState.value.sqlQuery.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningSql = true, sqlError = null) }
            runCatching {
                client.executeSql(session, query)
            }.onSuccess { result ->
                _uiState.update { it.copy(isRunningSql = false, sqlResult = result, sqlError = null) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isRunningSql = false,
                        sqlError = err.message ?: "SQL 执行失败（请确认 PocketBase >= v0.39）",
                    )
                }
            }
        }
    }

    fun loadOps(session: PocketBaseSession? = _uiState.value.session) {
        val active = session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOps = true) }
            runCatching {
                coroutineScope {
                    val backupsDef = async { runCatching { client.listBackups(active) }.getOrDefault(emptyList()) }
                    val cronsDef = async { runCatching { client.listCrons(active) }.getOrDefault(emptyList()) }
                    val settingsDef = async { runCatching { client.getSettings(active) }.getOrNull() }
                    _uiState.update {
                        it.copy(
                            isLoadingOps = false,
                            backups = backupsDef.await(),
                            crons = cronsDef.await(),
                            serverSettings = settingsDef.await(),
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingOps = false, errorMessage = err.message) }
            }
        }
    }

    fun createBackup(name: String? = null) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOps = true, errorMessage = null) }
            runCatching {
                client.createBackup(session, name)
                val updated = client.listBackups(session)
                _uiState.update {
                    it.copy(
                        isLoadingOps = false,
                        backups = updated,
                        actionMessage = "备份已创建",
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingOps = false, errorMessage = err.message ?: "创建备份失败") }
            }
        }
    }

    fun deleteBackup(key: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOps = true, errorMessage = null) }
            runCatching {
                client.deleteBackup(session, key)
                val updated = client.listBackups(session)
                _uiState.update {
                    it.copy(
                        isLoadingOps = false,
                        backups = updated,
                        actionMessage = "已删除备份 $key",
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingOps = false, errorMessage = err.message ?: "删除备份失败") }
            }
        }
    }

    fun runCron(jobId: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            runCatching {
                client.runCron(session, jobId)
                _uiState.update { it.copy(actionMessage = "已触发定时任务 $jobId") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "触发任务失败") }
            }
        }
    }

    private fun restartAutoRefreshIfNeeded() {
        autoRefreshJob?.cancel()
        if (_uiState.value.session == null || !_uiState.value.autoRefreshEnabled) return
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                refreshAll()
            }
        }
    }
}
