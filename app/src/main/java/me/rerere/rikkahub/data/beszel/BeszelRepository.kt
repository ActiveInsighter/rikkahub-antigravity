package me.rerere.rikkahub.data.beszel

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class BeszelSystemDetailBundle(
    val details: BeszelSystemDetails?,
    val stats: List<BeszelStatsPoint>,
    val containers: List<BeszelContainer>,
    val services: List<BeszelSystemdService>,
)

class BeszelRepository(
    context: Context,
    private val apiClient: BeszelApiClient,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<BeszelConfig?> = _config.asStateFlow()

    private val _systems = MutableStateFlow<List<BeszelSystem>>(emptyList())
    val systems: StateFlow<List<BeszelSystem>> = _systems.asStateFlow()

    private fun loadConfig(): BeszelConfig? {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return null
        return runCatching { json.decodeFromString<BeszelConfig>(raw) }.getOrNull()
    }

    private fun saveConfig(config: BeszelConfig?) {
        _config.value = config
        prefs.edit {
            if (config == null) {
                remove(KEY_CONFIG)
            } else {
                putString(KEY_CONFIG, json.encodeToString(config))
            }
        }
    }

    suspend fun login(
        serverUrl: String,
        identity: String,
        password: String,
        allowInsecureTls: Boolean,
    ): List<BeszelSystem> {
        val normalizedUrl = apiClient.normalizeUrl(serverUrl)
        val auth = apiClient.authenticate(
            serverUrl = normalizedUrl,
            identity = identity,
            password = password,
            allowInsecureTls = allowInsecureTls,
        )
        val newConfig = BeszelConfig(
            serverUrl = normalizedUrl,
            identity = identity.trim(),
            password = password,
            token = auth.token,
            allowInsecureTls = allowInsecureTls,
        )
        val fetched = apiClient.fetchSystems(newConfig)
        saveConfig(newConfig)
        _systems.value = fetched
        return fetched
    }

    fun logout() {
        _systems.value = emptyList()
        val current = _config.value
        // Retain serverUrl/identity/allowInsecureTls for convenience, clear token & password
        if (current != null) {
            saveConfig(
                current.copy(
                    password = "",
                    token = "",
                )
            )
        } else {
            saveConfig(null)
        }
    }

    suspend fun fetchSystems(): List<BeszelSystem> {
        val result = withAutoReauth { cfg -> apiClient.fetchSystems(cfg) }
        _systems.value = result
        return result
    }

    suspend fun fetchSystemDetailBundle(
        system: BeszelSystem,
        range: BeszelChartRange,
    ): BeszelSystemDetailBundle = withAutoReauth { cfg ->
        coroutineScope {
            val detailsDeferred = async { apiClient.fetchSystemDetails(cfg, system) }
            val statsDeferred = async { apiClient.fetchSystemStats(cfg, system.id, range) }
            val containersDeferred = async {
                runCatching { apiClient.fetchContainers(cfg, system.id) }.getOrDefault(emptyList())
            }
            val servicesDeferred = async {
                runCatching { apiClient.fetchSystemdServices(cfg, system.id) }.getOrDefault(emptyList())
            }
            BeszelSystemDetailBundle(
                details = detailsDeferred.await(),
                stats = statsDeferred.await(),
                containers = containersDeferred.await(),
                services = servicesDeferred.await(),
            )
        }
    }

    suspend fun fetchSystemStatsOnly(
        systemId: String,
        range: BeszelChartRange,
    ): List<BeszelStatsPoint> = withAutoReauth { cfg ->
        apiClient.fetchSystemStats(cfg, systemId, range)
    }

    suspend fun fetchAlertHistory(): List<BeszelAlertEvent> = withAutoReauth { cfg ->
        apiClient.fetchAlertHistory(cfg)
    }

    private suspend fun <T> withAutoReauth(block: suspend (BeszelConfig) -> T): T {
        val current = _config.value?.takeIf { it.isConfigured }
            ?: throw BeszelApiException(message = "尚未连接 Beszel 服务器")
        return try {
            block(current)
        } catch (e: BeszelApiException) {
            if (e.statusCode == 401 && current.identity.isNotBlank() && current.password.isNotBlank()) {
                val auth = apiClient.authenticate(
                    serverUrl = current.serverUrl,
                    identity = current.identity,
                    password = current.password,
                    allowInsecureTls = current.allowInsecureTls,
                )
                val refreshed = current.copy(token = auth.token)
                saveConfig(refreshed)
                block(refreshed)
            } else {
                throw e
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "beszel_prefs"
        private const val KEY_CONFIG = "beszel_config"
    }
}
