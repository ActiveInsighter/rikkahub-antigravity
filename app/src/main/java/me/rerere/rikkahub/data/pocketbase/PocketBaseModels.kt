package me.rerere.rikkahub.data.pocketbase

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
enum class PocketBaseAuthMode {
    SUPERUSER_PASSWORD,
    API_TOKEN,
}

@Serializable
data class PocketBaseConnection(
    val id: String,
    val name: String,
    val baseUrl: String,
    val authMode: PocketBaseAuthMode = PocketBaseAuthMode.SUPERUSER_PASSWORD,
    val identity: String = "",
    val encryptedToken: String = "",
    val createdAt: Long = 0L,
    val lastConnectedAt: Long = 0L,
)

data class PocketBaseSession(
    val connectionId: String,
    val name: String,
    val baseUrl: String,
    val token: String,
    val identity: String,
    val authMode: PocketBaseAuthMode,
)

data class PbHealthInfo(
    val code: Int,
    val message: String,
    val canBackup: Boolean? = null,
    val realIP: String? = null,
)

data class PbField(
    val id: String,
    val name: String,
    val type: String,
    val required: Boolean = false,
    val system: Boolean = false,
    val hidden: Boolean = false,
    val presentable: Boolean = false,
    val values: List<String> = emptyList(),
    val maxSelect: Int? = null,
    val collectionId: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val pattern: String? = null,
    val rawJson: JsonObject = JsonObject(emptyMap()),
)

data class PbCollection(
    val id: String,
    val name: String,
    val type: String,
    val system: Boolean = false,
    val fields: List<PbField> = emptyList(),
    val indexes: List<String> = emptyList(),
    val listRule: String? = null,
    val viewRule: String? = null,
    val createRule: String? = null,
    val updateRule: String? = null,
    val deleteRule: String? = null,
    val created: String = "",
    val updated: String = "",
    val rawJson: JsonObject = JsonObject(emptyMap()),
)

data class PbRecordsPage(
    val page: Int,
    val perPage: Int,
    val totalItems: Int,
    val totalPages: Int,
    val items: List<JsonObject>,
)

data class PbLogEntry(
    val id: String,
    val created: String,
    val level: Int,
    val message: String,
    val method: String = "",
    val url: String = "",
    val status: Int? = null,
    val execTimeMs: Double? = null,
    val remoteIP: String = "",
    val userAgent: String = "",
    val auth: String = "",
    val type: String = "",
    val data: JsonObject = JsonObject(emptyMap()),
    val rawJson: JsonObject = JsonObject(emptyMap()),
)

data class PbLogsPage(
    val page: Int,
    val perPage: Int,
    val totalItems: Int,
    val totalPages: Int,
    val items: List<PbLogEntry>,
)

data class PbLogStat(
    val date: String,
    val total: Int,
)

data class PbBackup(
    val key: String,
    val size: Long,
    val modified: String,
)

data class PbCron(
    val id: String,
    val expression: String,
)

data class PbSqlResult(
    val columns: List<String>,
    val rows: List<List<JsonElement>>,
    val rowsAffected: Int? = null,
    val execTimeMs: Double? = null,
    val rawJson: JsonObject = JsonObject(emptyMap()),
)

data class PbRealtimeEvent(
    val action: String,
    val collectionName: String,
    val record: JsonObject,
    val timestamp: Long = System.currentTimeMillis(),
)

sealed interface PocketBaseAuthResult {
    data class Success(val session: PocketBaseSession) : PocketBaseAuthResult
    data class Error(val message: String) : PocketBaseAuthResult
}
