package me.rerere.rikkahub.ui.pages.beszel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.beszel.BeszelAlertEvent
import me.rerere.rikkahub.data.beszel.BeszelAlertRule
import me.rerere.rikkahub.data.beszel.BeszelAuthException
import me.rerere.rikkahub.data.beszel.BeszelAuthResult
import me.rerere.rikkahub.data.beszel.BeszelClient
import me.rerere.rikkahub.data.beszel.BeszelHubInfo
import me.rerere.rikkahub.data.beszel.BeszelSession
import me.rerere.rikkahub.data.beszel.BeszelSettingsStore
import me.rerere.rikkahub.data.beszel.BeszelSystem

enum class BeszelSystemFilter(val label: String) {
    ALL("全部"),
    UP("在线"),
    DOWN("离线"),
}

data class BeszelMfaState(
    val baseUrl: String,
    val identity: String,
    val mfaId: String,
    val otpId: String,
)

data class BeszelUiState(
    val session: BeszelSession? = null,
    val initialBaseUrl: String = "",
    val initialIdentity: String = "",
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val mfaState: BeszelMfaState? = null,
    val isLoadingSystems: Boolean = false,
    val isRefreshing: Boolean = false,
    val systemsError: String? = null,
    val systems: List<BeszelSystem> = emptyList(),
    val hubInfo: BeszelHubInfo? = null,
    val alertRules: List<BeszelAlertRule> = emptyList(),
    val alertHistory: List<BeszelAlertEvent> = emptyList(),
    val isLoadingAlerts: Boolean = false,
    val searchQuery: String = "",
    val filter: BeszelSystemFilter = BeszelSystemFilter.ALL,
    val autoRefreshEnabled: Boolean = true,
) {
    val activeAlertCount: Int
        get() = alertRules.count { it.triggered } + alertHistory.count { it.isActive }
}

class BeszelVM(
    private val client: BeszelClient,
    private val settingsStore: BeszelSettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BeszelUiState(
            session = settingsStore.session.value,
            initialBaseUrl = settingsStore.savedCredentials.value.baseUrl,
            initialIdentity = settingsStore.savedCredentials.value.identity,
        ),
    )
    val uiState: StateFlow<BeszelUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.session.collect { session ->
                _uiState.update { it.copy(session = session) }
                if (session != null) {
                    loadDashboard(showSpinner = _uiState.value.systems.isEmpty())
                    startPolling()
                } else {
                    stopPolling()
                    _uiState.update {
                        it.copy(
                            systems = emptyList(),
                            hubInfo = null,
                            alertRules = emptyList(),
                            alertHistory = emptyList(),
                            systemsError = null,
                        )
                    }
                }
            }
        }
    }

    fun login(baseUrl: String, identity: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginError = null) }
            when (val result = client.loginWithPassword(baseUrl, identity, password)) {
                is BeszelAuthResult.Success -> {
                    settingsStore.saveSession(result.session)
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            loginError = null,
                            mfaState = null,
                        )
                    }
                }

                is BeszelAuthResult.MfaRequired -> {
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            loginError = null,
                            mfaState = BeszelMfaState(
                                baseUrl = client.normalizeBaseUrl(baseUrl),
                                identity = identity.trim(),
                                mfaId = result.mfaId,
                                otpId = result.otpId,
                            ),
                        )
                    }
                }

                is BeszelAuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            loginError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun verifyOtp(otpCode: String) {
        val mfa = _uiState.value.mfaState ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginError = null) }
            when (
                val result = client.verifyOtp(
                    rawBaseUrl = mfa.baseUrl,
                    identity = mfa.identity,
                    otpId = mfa.otpId,
                    otpCode = otpCode,
                    mfaId = mfa.mfaId,
                )
            ) {
                is BeszelAuthResult.Success -> {
                    settingsStore.saveSession(result.session)
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            loginError = null,
                            mfaState = null,
                        )
                    }
                }

                is BeszelAuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            loginError = result.message,
                        )
                    }
                }

                is BeszelAuthResult.MfaRequired -> {
                    _uiState.update { it.copy(isLoggingIn = false) }
                }
            }
        }
    }

    fun cancelMfa() {
        _uiState.update { it.copy(mfaState = null, loginError = null) }
    }

    fun logout() {
        stopPolling()
        settingsStore.clearSession()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilter(filter: BeszelSystemFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun toggleAutoRefresh() {
        val next = !_uiState.value.autoRefreshEnabled
        _uiState.update { it.copy(autoRefreshEnabled = next) }
        if (next && _uiState.value.session != null) {
            startPolling()
        } else {
            stopPolling()
        }
    }

    fun refresh() {
        loadDashboard(showSpinner = false)
    }

    fun loadAlerts() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAlerts = true) }
            runCatching {
                val rules = client.fetchAlertRules(session)
                val history = client.fetchAlertHistory(session)
                rules to history
            }.onSuccess { (rules, history) ->
                _uiState.update {
                    it.copy(
                        alertRules = rules,
                        alertHistory = history,
                        isLoadingAlerts = false,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingAlerts = false) }
            }
        }
    }

    fun deleteAlertHistory(id: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            runCatching {
                client.deleteAlertHistory(session, id)
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(alertHistory = state.alertHistory.filterNot { it.id == id })
                }
            }
        }
    }

    private fun loadDashboard(showSpinner: Boolean) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingSystems = showSpinner,
                    isRefreshing = !showSpinner,
                    systemsError = null,
                )
            }
            runCatching {
                val systems = client.fetchSystems(session)
                val info = runCatching { client.fetchHubInfo(session) }.getOrNull()
                val rules = runCatching { client.fetchAlertRules(session) }.getOrDefault(emptyList())
                val history = runCatching { client.fetchAlertHistory(session, perPage = 50) }.getOrDefault(emptyList())
                Triple(systems, info, rules to history)
            }.onSuccess { (systems, info, alerts) ->
                _uiState.update {
                    it.copy(
                        systems = systems,
                        hubInfo = info ?: it.hubInfo,
                        alertRules = alerts.first,
                        alertHistory = alerts.second,
                        isLoadingSystems = false,
                        isRefreshing = false,
                        systemsError = null,
                    )
                }
            }.onFailure { error ->
                if (error is BeszelAuthException) {
                    settingsStore.clearSession()
                    _uiState.update {
                        it.copy(
                            isLoadingSystems = false,
                            isRefreshing = false,
                            loginError = error.message,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingSystems = false,
                            isRefreshing = false,
                            systemsError = error.message ?: "获取服务器列表失败",
                        )
                    }
                }
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        if (!_uiState.value.autoRefreshEnabled) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000L)
                val session = _uiState.value.session ?: break
                if (!_uiState.value.autoRefreshEnabled) break
                runCatching {
                    client.fetchSystems(session)
                }.onSuccess { systems ->
                    _uiState.update { it.copy(systems = systems, systemsError = null) }
                }.onFailure { error ->
                    if (error is BeszelAuthException) {
                        settingsStore.clearSession()
                    }
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
