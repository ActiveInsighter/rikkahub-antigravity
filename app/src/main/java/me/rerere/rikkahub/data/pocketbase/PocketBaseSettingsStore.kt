package me.rerere.rikkahub.data.pocketbase

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SecretCipher
import java.util.UUID

private const val PB_PREFS_NAME = "pocketbase_console_prefs"
private const val KEY_CONNECTIONS = "pb_connections"
private const val KEY_ACTIVE_ID = "pb_active_connection_id"
private const val KEY_AUTO_REFRESH = "pb_auto_refresh"
private const val KEY_REALTIME_ENABLED = "pb_realtime_enabled"

class PocketBaseSettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PB_PREFS_NAME, Context.MODE_PRIVATE)

    private val _connections = MutableStateFlow(loadConnections())
    val connections: StateFlow<List<PocketBaseConnection>> = _connections.asStateFlow()

    private val _activeSession = MutableStateFlow(loadActiveSession())
    val activeSession: StateFlow<PocketBaseSession?> = _activeSession.asStateFlow()

    private val _autoRefreshEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_REFRESH, false))
    val autoRefreshEnabled: StateFlow<Boolean> = _autoRefreshEnabled.asStateFlow()

    private val _realtimeEnabled = MutableStateFlow(prefs.getBoolean(KEY_REALTIME_ENABLED, true))
    val realtimeEnabled: StateFlow<Boolean> = _realtimeEnabled.asStateFlow()

    private fun loadConnections(): List<PocketBaseConnection> {
        val raw = prefs.getString(KEY_CONNECTIONS, null) ?: return emptyList()
        return runCatching {
            JsonInstant.decodeFromString<List<PocketBaseConnection>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun loadActiveSession(): PocketBaseSession? {
        val activeId = prefs.getString(KEY_ACTIVE_ID, null) ?: return null
        val conn = _connections.value.firstOrNull { it.id == activeId } ?: return null
        val token = decryptToken(conn.encryptedToken)
        if (token.isNullOrBlank()) return null
        return PocketBaseSession(
            connectionId = conn.id,
            name = conn.name,
            baseUrl = conn.baseUrl,
            token = token,
            identity = conn.identity,
            authMode = conn.authMode,
        )
    }

    private fun encryptToken(token: String): String {
        if (token.isEmpty()) return ""
        return runCatching { SecretCipher.encrypt(token) }.getOrDefault(token)
    }

    private fun decryptToken(stored: String): String? {
        if (stored.isEmpty()) return null
        return if (stored.startsWith("v1:")) {
            runCatching { SecretCipher.decrypt(stored) }.getOrNull()
        } else {
            stored
        }
    }

    fun saveAndActivateSession(
        name: String,
        baseUrl: String,
        token: String,
        identity: String,
        authMode: PocketBaseAuthMode,
        existingId: String? = null,
    ): PocketBaseSession {
        val now = System.currentTimeMillis()
        val normalizedName = name.trim().ifEmpty {
            baseUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")
        }
        val currentList = _connections.value.toMutableList()
        val existingIndex = currentList.indexOfFirst {
            (existingId != null && it.id == existingId) ||
                (it.baseUrl == baseUrl && it.identity == identity && it.authMode == authMode)
        }
        val connId = if (existingIndex >= 0) currentList[existingIndex].id else UUID.randomUUID().toString()
        val createdAt = if (existingIndex >= 0) currentList[existingIndex].createdAt else now
        val newConn = PocketBaseConnection(
            id = connId,
            name = normalizedName,
            baseUrl = baseUrl,
            authMode = authMode,
            identity = identity,
            encryptedToken = encryptToken(token),
            createdAt = createdAt,
            lastConnectedAt = now,
        )
        if (existingIndex >= 0) {
            currentList[existingIndex] = newConn
        } else {
            currentList.add(0, newConn)
        }
        persistConnections(currentList, connId)
        val session = PocketBaseSession(
            connectionId = connId,
            name = normalizedName,
            baseUrl = baseUrl,
            token = token,
            identity = identity,
            authMode = authMode,
        )
        _activeSession.value = session
        return session
    }

    fun activateConnection(connectionId: String): PocketBaseSession? {
        val conn = _connections.value.firstOrNull { it.id == connectionId } ?: return null
        val token = decryptToken(conn.encryptedToken) ?: return null
        val updated = _connections.value.map {
            if (it.id == connectionId) it.copy(lastConnectedAt = System.currentTimeMillis()) else it
        }
        persistConnections(updated, connectionId)
        val session = PocketBaseSession(
            connectionId = conn.id,
            name = conn.name,
            baseUrl = conn.baseUrl,
            token = token,
            identity = conn.identity,
            authMode = conn.authMode,
        )
        _activeSession.value = session
        return session
    }

    fun deleteConnection(connectionId: String) {
        val updated = _connections.value.filterNot { it.id == connectionId }
        val activeId = if (_activeSession.value?.connectionId == connectionId) null else prefs.getString(KEY_ACTIVE_ID, null)
        persistConnections(updated, activeId)
        if (_activeSession.value?.connectionId == connectionId) {
            _activeSession.value = null
        }
    }

    fun disconnectSession() {
        prefs.edit { remove(KEY_ACTIVE_ID) }
        _activeSession.value = null
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_REFRESH, enabled) }
        _autoRefreshEnabled.value = enabled
    }

    fun setRealtimeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_REALTIME_ENABLED, enabled) }
        _realtimeEnabled.value = enabled
    }

    private fun persistConnections(list: List<PocketBaseConnection>, activeId: String?) {
        prefs.edit {
            putString(KEY_CONNECTIONS, JsonInstant.encodeToString(list))
            if (activeId != null) {
                putString(KEY_ACTIVE_ID, activeId)
            } else {
                remove(KEY_ACTIVE_ID)
            }
        }
        _connections.value = list
    }
}
