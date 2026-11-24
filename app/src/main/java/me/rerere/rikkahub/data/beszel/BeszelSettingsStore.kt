package me.rerere.rikkahub.data.beszel

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.rikkahub.utils.JsonInstant

private const val BESZEL_PREFS_NAME = "beszel_prefs"
private const val KEY_SESSION = "beszel_session"
private const val KEY_CREDENTIALS = "beszel_saved_credentials"

class BeszelSettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(BESZEL_PREFS_NAME, Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<BeszelSession?> = _session.asStateFlow()

    private val _savedCredentials = MutableStateFlow(loadCredentials())
    val savedCredentials: StateFlow<BeszelSavedCredentials> = _savedCredentials.asStateFlow()

    private fun loadSession(): BeszelSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            JsonInstant.decodeFromString<BeszelSession>(raw)
        }.getOrNull()
    }

    private fun loadCredentials(): BeszelSavedCredentials {
        val raw = prefs.getString(KEY_CREDENTIALS, null) ?: return BeszelSavedCredentials()
        return runCatching {
            JsonInstant.decodeFromString<BeszelSavedCredentials>(raw)
        }.getOrDefault(BeszelSavedCredentials())
    }

    fun saveSession(session: BeszelSession) {
        val credentials = BeszelSavedCredentials(
            baseUrl = session.baseUrl,
            identity = session.identity,
        )
        prefs.edit {
            putString(KEY_SESSION, JsonInstant.encodeToString(session))
            putString(KEY_CREDENTIALS, JsonInstant.encodeToString(credentials))
        }
        _session.value = session
        _savedCredentials.value = credentials
    }

    fun clearSession() {
        prefs.edit {
            remove(KEY_SESSION)
        }
        _session.value = null
    }
}
