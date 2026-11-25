package me.rerere.rikkahub.data.pocketbase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.common.http.await
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class PocketBaseApiException(val code: Int, message: String) : IOException(message)

class PocketBaseClient(
    private val http: OkHttpClient,
    private val settingsStore: PocketBaseSettingsStore,
) {
    private val sseFactory = EventSources.createFactory(
        http.newBuilder().readTimeout(6, TimeUnit.MINUTES).build(),
    )

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    suspend fun loginWithPassword(
        rawBaseUrl: String,
        connectionName: String,
        identity: String,
        password: String,
    ): PocketBaseAuthResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(rawBaseUrl)
        if (baseUrl.isEmpty() || baseUrl.toHttpUrlOrNull() == null) {
            return@withContext PocketBaseAuthResult.Error("请输入有效的 PocketBase 服务地址")
        }
        val cleanIdentity = identity.trim()
        if (cleanIdentity.isEmpty() || password.isEmpty()) {
            return@withContext PocketBaseAuthResult.Error("请输入超级管理员邮箱和密码")
        }

        val payload = buildJsonObject {
            put("identity", cleanIdentity)
            put("password", password)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/collections/_superusers/auth-with-password")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            http.newCall(request).await().use { response ->
                val body = response.body.string()
                val json = parseJsonObjectOrNull(body)
                if (response.isSuccessful && json != null) {
                    val token = json.stringOrNull("token").orEmpty()
                    if (token.isNotBlank()) {
                        val session = settingsStore.saveAndActivateSession(
                            name = connectionName,
                            baseUrl = baseUrl,
                            token = token,
                            identity = cleanIdentity,
                            authMode = PocketBaseAuthMode.SUPERUSER_PASSWORD,
                        )
                        return@withContext PocketBaseAuthResult.Success(session)
                    }
                }
                val serverMsg = json?.stringOrNull("message")
                val msg = when (response.code) {
                    400 -> "超级管理员账号或密码错误"
                    404 -> "未找到 _superusers 接口，请确认 PocketBase 版本或地址"
                    else -> serverMsg?.takeIf { it.isNotBlank() } ?: "登录失败 (HTTP ${response.code})"
                }
                PocketBaseAuthResult.Error(msg)
            }
        }.getOrElse { error ->
            PocketBaseAuthResult.Error(describeError(error))
        }
    }

    suspend fun connectWithToken(
        rawBaseUrl: String,
        connectionName: String,
        token: String,
    ): PocketBaseAuthResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(rawBaseUrl)
        if (baseUrl.isEmpty() || baseUrl.toHttpUrlOrNull() == null) {
            return@withContext PocketBaseAuthResult.Error("请输入有效的 PocketBase 服务地址")
        }
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            return@withContext PocketBaseAuthResult.Error("请输入管理员 Token")
        }

        val request = Request.Builder()
            .url("$baseUrl/api/collections?page=1&perPage=1")
            .header("Authorization", cleanToken)
            .get()
            .build()

        runCatching {
            http.newCall(request).await().use { response ->
                val body = response.body.string()
                val json = parseJsonObjectOrNull(body)
                if (response.isSuccessful) {
                    val session = settingsStore.saveAndActivateSession(
                        name = connectionName,
                        baseUrl = baseUrl,
                        token = cleanToken,
                        identity = "API Token",
                        authMode = PocketBaseAuthMode.API_TOKEN,
                    )
                    return@withContext PocketBaseAuthResult.Success(session)
                }
                val serverMsg = json?.stringOrNull("message")
                val msg = when (response.code) {
                    401, 403 -> "Token 无效或缺少超级管理员权限"
                    404 -> "未找到 PocketBase 接口，请检查服务地址"
                    else -> serverMsg?.takeIf { it.isNotBlank() } ?: "连接失败 (HTTP ${response.code})"
                }
                PocketBaseAuthResult.Error(msg)
            }
        }.getOrElse { error ->
            PocketBaseAuthResult.Error(describeError(error))
        }
    }

    suspend fun getHealth(session: PocketBaseSession): PbHealthInfo = withContext(Dispatchers.IO) {
        val json = getJsonObject(session, "/api/health")
        val data = json["data"] as? JsonObject
        PbHealthInfo(
            code = json["code"]?.jsonPrimitive?.intOrNull ?: 200,
            message = json.stringOrNull("message") ?: "API is healthy.",
            canBackup = data?.get("canBackup")?.jsonPrimitive?.booleanOrNull,
            realIP = data?.stringOrNull("realIP"),
        )
    }

    suspend fun listCollections(session: PocketBaseSession): List<PbCollection> = withContext(Dispatchers.IO) {
        val json = getJsonObject(
            session = session,
            path = "/api/collections",
            query = mapOf("page" to "1", "perPage" to "200"),
        )
        val items = json["items"] as? JsonArray ?: JsonArray(emptyList())
        items.mapNotNull { (it as? JsonObject)?.let(::parseCollection) }
    }

    suspend fun getCollection(session: PocketBaseSession, idOrName: String): PbCollection = withContext(Dispatchers.IO) {
        val encoded = encodePathSegment(idOrName)
        val json = getJsonObject(session, "/api/collections/$encoded")
        parseCollection(json)
    }

    suspend fun listRecords(
        session: PocketBaseSession,
        collectionName: String,
        page: Int = 1,
        perPage: Int = 30,
        sort: String? = "-created",
        filter: String? = null,
    ): PbRecordsPage = withContext(Dispatchers.IO) {
        val encoded = encodePathSegment(collectionName)
        val query = buildMap {
            put("page", page.coerceAtLeast(1).toString())
            put("perPage", perPage.coerceIn(1, 200).toString())
            if (!sort.isNullOrBlank()) put("sort", sort.trim())
            if (!filter.isNullOrBlank()) put("filter", filter.trim())
        }
        val json = getJsonObject(session, "/api/collections/$encoded/records", query)
        val items = (json["items"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        PbRecordsPage(
            page = json["page"]?.jsonPrimitive?.intOrNull ?: page,
            perPage = json["perPage"]?.jsonPrimitive?.intOrNull ?: perPage,
            totalItems = json["totalItems"]?.jsonPrimitive?.intOrNull ?: items.size,
            totalPages = json["totalPages"]?.jsonPrimitive?.intOrNull ?: 1,
            items = items,
        )
    }

    suspend fun createRecord(
        session: PocketBaseSession,
        collectionName: String,
        data: JsonObject,
    ): JsonObject = withContext(Dispatchers.IO) {
        val encoded = encodePathSegment(collectionName)
        sendJson(session, "POST", "/api/collections/$encoded/records", data)
    }

    suspend fun updateRecord(
        session: PocketBaseSession,
        collectionName: String,
        recordId: String,
        data: JsonObject,
    ): JsonObject = withContext(Dispatchers.IO) {
        val col = encodePathSegment(collectionName)
        val id = encodePathSegment(recordId)
        sendJson(session, "PATCH", "/api/collections/$col/records/$id", data)
    }

    suspend fun deleteRecord(
        session: PocketBaseSession,
        collectionName: String,
        recordId: String,
    ) = withContext(Dispatchers.IO) {
        val col = encodePathSegment(collectionName)
        val id = encodePathSegment(recordId)
        sendEmpty(session, "DELETE", "/api/collections/$col/records/$id")
    }

    suspend fun listLogs(
        session: PocketBaseSession,
        page: Int = 1,
        perPage: Int = 50,
        filter: String? = null,
    ): PbLogsPage = withContext(Dispatchers.IO) {
        val query = buildMap {
            put("page", page.coerceAtLeast(1).toString())
            put("perPage", perPage.coerceIn(1, 200).toString())
            put("sort", "-created")
            if (!filter.isNullOrBlank()) put("filter", filter.trim())
        }
        val json = getJsonObject(session, "/api/logs", query)
        val items = (json["items"] as? JsonArray)?.mapNotNull {
            (it as? JsonObject)?.let(::parseLogEntry)
        } ?: emptyList()
        PbLogsPage(
            page = json["page"]?.jsonPrimitive?.intOrNull ?: page,
            perPage = json["perPage"]?.jsonPrimitive?.intOrNull ?: perPage,
            totalItems = json["totalItems"]?.jsonPrimitive?.intOrNull ?: items.size,
            totalPages = json["totalPages"]?.jsonPrimitive?.intOrNull ?: 1,
            items = items,
        )
    }

    suspend fun getLogStats(
        session: PocketBaseSession,
        filter: String? = null,
    ): List<PbLogStat> = withContext(Dispatchers.IO) {
        val query = if (!filter.isNullOrBlank()) mapOf("filter" to filter.trim()) else emptyMap()
        val element = getJsonElement(session, "/api/logs/stats", query)
        val arr = element as? JsonArray ?: return@withContext emptyList()
        arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            PbLogStat(
                date = obj.stringOrNull("date").orEmpty(),
                total = obj["total"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
    }

    suspend fun listBackups(session: PocketBaseSession): List<PbBackup> = withContext(Dispatchers.IO) {
        val element = getJsonElement(session, "/api/backups")
        val arr = element as? JsonArray ?: return@withContext emptyList()
        arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            PbBackup(
                key = obj.stringOrNull("key").orEmpty(),
                size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                modified = obj.stringOrNull("modified").orEmpty(),
            )
        }
    }

    suspend fun createBackup(session: PocketBaseSession, name: String? = null) = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            if (!name.isNullOrBlank()) put("name", name.trim())
        }
        sendEmptyWithBody(session, "POST", "/api/backups", payload)
    }

    suspend fun deleteBackup(session: PocketBaseSession, key: String) = withContext(Dispatchers.IO) {
        val encoded = encodePathSegment(key)
        sendEmpty(session, "DELETE", "/api/backups/$encoded")
    }

    suspend fun listCrons(session: PocketBaseSession): List<PbCron> = withContext(Dispatchers.IO) {
        val element = getJsonElement(session, "/api/crons")
        val arr = element as? JsonArray ?: return@withContext emptyList()
        arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            PbCron(
                id = obj.stringOrNull("id").orEmpty(),
                expression = obj.stringOrNull("expression").orEmpty(),
            )
        }
    }

    suspend fun runCron(session: PocketBaseSession, jobId: String) = withContext(Dispatchers.IO) {
        val encoded = encodePathSegment(jobId)
        sendEmptyWithBody(session, "POST", "/api/crons/$encoded", JsonObject(emptyMap()))
    }

    suspend fun getSettings(session: PocketBaseSession): JsonObject = withContext(Dispatchers.IO) {
        getJsonObject(session, "/api/settings")
    }

    suspend fun executeSql(session: PocketBaseSession, query: String): PbSqlResult = withContext(Dispatchers.IO) {
        val payload = buildJsonObject { put("query", query) }
        val json = sendJson(session, "POST", "/api/sql", payload)
        val columns = (json["columns"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val rows = (json["rows"] as? JsonArray)?.mapNotNull { rowEl ->
            (rowEl as? JsonArray)?.toList()
        } ?: emptyList()
        PbSqlResult(
            columns = columns,
            rows = rows,
            rowsAffected = json["rowsAffected"]?.jsonPrimitive?.intOrNull,
            execTimeMs = json["execTime"]?.jsonPrimitive?.doubleOrNull,
            rawJson = json,
        )
    }

    fun observeCollection(
        session: PocketBaseSession,
        collectionName: String,
    ): Flow<PbRealtimeEvent> = flow {
        val url = "${session.baseUrl}/api/realtime"
        var retryDelay = 1_500L
        flow {
            sseFrames(url).collect { frame ->
                if (frame.type == "PB_CONNECT") {
                    val clientId = frame.id ?: throw IOException("Missing PocketBase client ID")
                    subscribeTopic(url, session.token, clientId, "$collectionName/*")
                    retryDelay = 1_500L
                } else {
                    val obj = parseJsonObjectOrNull(frame.data) ?: return@collect
                    val action = obj.stringOrNull("action").orEmpty()
                    val record = obj["record"] as? JsonObject ?: return@collect
                    if (action.isNotBlank()) {
                        emit(
                            PbRealtimeEvent(
                                action = action,
                                collectionName = collectionName,
                                record = record,
                            ),
                        )
                    }
                }
            }
        }.retryWhen { error, _ ->
            if (error !is IOException) return@retryWhen false
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
            true
        }.collect { emit(it) }
    }

    private data class SseFrame(val id: String?, val type: String, val data: String)

    private fun sseFrames(url: String): Flow<SseFrame> = callbackFlow {
        val request = Request.Builder().url(url).header("Accept", "text/event-stream").build()
        val stream = sseFactory.newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (type == null) return
                    if (trySend(SseFrame(id, type, data)).isFailure) {
                        close(IOException("PocketBase realtime buffer overflow"))
                        eventSource.cancel()
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    close(IOException("PocketBase realtime closed"))
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    close(IOException("PocketBase realtime failure", t))
                }
            },
        )
        awaitClose { stream.cancel() }
    }.buffer(64)

    private suspend fun subscribeTopic(url: String, token: String, clientId: String, topic: String) {
        val body = buildJsonObject {
            put("clientId", clientId)
            putJsonArray("subscriptions") {
                add(JsonPrimitive(topic))
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", token)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        http.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw PocketBaseApiException(response.code, "Realtime subscribe failed (${response.code})")
            }
        }
    }

    private suspend fun getJsonObject(
        session: PocketBaseSession,
        path: String,
        query: Map<String, String> = emptyMap(),
    ): JsonObject {
        val el = getJsonElement(session, path, query)
        return el as? JsonObject ?: throw PocketBaseApiException(500, "响应不是 JSON 对象")
    }

    private suspend fun getJsonElement(
        session: PocketBaseSession,
        path: String,
        query: Map<String, String> = emptyMap(),
    ): JsonElement {
        val url = buildUrl(session.baseUrl, path, query)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", session.token)
            .get()
            .build()
        http.newCall(request).await().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw toApiException(response.code, body)
            }
            return runCatching { JsonInstant.parseToJsonElement(body) }
                .getOrElse { throw PocketBaseApiException(response.code, "解析响应失败") }
        }
    }

    private suspend fun sendJson(
        session: PocketBaseSession,
        method: String,
        path: String,
        payload: JsonObject,
    ): JsonObject {
        val request = Request.Builder()
            .url("${session.baseUrl}$path")
            .header("Authorization", session.token)
            .method(method, payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        http.newCall(request).await().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw toApiException(response.code, body)
            }
            return parseJsonObjectOrNull(body) ?: JsonObject(emptyMap())
        }
    }

    private suspend fun sendEmpty(session: PocketBaseSession, method: String, path: String) {
        val request = Request.Builder()
            .url("${session.baseUrl}$path")
            .header("Authorization", session.token)
            .method(method, if (method == "DELETE") null else "".toRequestBody(null))
            .build()
        http.newCall(request).await().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw toApiException(response.code, body)
            }
        }
    }

    private suspend fun sendEmptyWithBody(
        session: PocketBaseSession,
        method: String,
        path: String,
        payload: JsonObject,
    ) {
        val request = Request.Builder()
            .url("${session.baseUrl}$path")
            .header("Authorization", session.token)
            .method(method, payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        http.newCall(request).await().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw toApiException(response.code, body)
            }
        }
    }

    private fun buildUrl(baseUrl: String, path: String, query: Map<String, String>): String {
        val httpUrl = "$baseUrl$path".toHttpUrlOrNull() ?: return "$baseUrl$path"
        val builder = httpUrl.newBuilder()
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    private fun toApiException(code: Int, body: String): PocketBaseApiException {
        val json = parseJsonObjectOrNull(body)
        val serverMsg = json?.stringOrNull("message")
        val dataDetails = (json?.get("data") as? JsonObject)?.entries?.joinToString("; ") { (k, v) ->
            val msg = (v as? JsonObject)?.stringOrNull("message") ?: v.toString()
            "$k: $msg"
        }?.takeIf { it.isNotBlank() }
        val msg = when {
            code == 401 -> "认证已过期或无效，请重新登录"
            code == 403 -> "权限不足：${serverMsg ?: "需要超级管理员权限"}"
            !dataDetails.isNullOrBlank() -> "${serverMsg ?: "请求错误"} ($dataDetails)"
            !serverMsg.isNullOrBlank() -> "$serverMsg (HTTP $code)"
            else -> "请求失败 (HTTP $code)"
        }
        return PocketBaseApiException(code, msg)
    }

    private fun parseCollection(obj: JsonObject): PbCollection {
        val fieldsArr = (obj["fields"] as? JsonArray) ?: (obj["schema"] as? JsonArray) ?: JsonArray(emptyList())
        val fields = fieldsArr.mapNotNull { el ->
            val f = el as? JsonObject ?: return@mapNotNull null
            PbField(
                id = f.stringOrNull("id").orEmpty(),
                name = f.stringOrNull("name").orEmpty(),
                type = f.stringOrNull("type") ?: "text",
                required = f["required"]?.jsonPrimitive?.booleanOrNull == true,
                system = f["system"]?.jsonPrimitive?.booleanOrNull == true,
                hidden = f["hidden"]?.jsonPrimitive?.booleanOrNull == true,
                presentable = f["presentable"]?.jsonPrimitive?.booleanOrNull == true,
                values = (f["values"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                maxSelect = f["maxSelect"]?.jsonPrimitive?.intOrNull,
                collectionId = f.stringOrNull("collectionId"),
                min = f["min"]?.jsonPrimitive?.doubleOrNull,
                max = f["max"]?.jsonPrimitive?.doubleOrNull,
                pattern = f.stringOrNull("pattern"),
                rawJson = f,
            )
        }
        val indexes = (obj["indexes"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        return PbCollection(
            id = obj.stringOrNull("id").orEmpty(),
            name = obj.stringOrNull("name").orEmpty(),
            type = obj.stringOrNull("type") ?: "base",
            system = obj["system"]?.jsonPrimitive?.booleanOrNull == true,
            fields = fields,
            indexes = indexes,
            listRule = obj.nullableString("listRule"),
            viewRule = obj.nullableString("viewRule"),
            createRule = obj.nullableString("createRule"),
            updateRule = obj.nullableString("updateRule"),
            deleteRule = obj.nullableString("deleteRule"),
            created = obj.stringOrNull("created").orEmpty(),
            updated = obj.stringOrNull("updated").orEmpty(),
            rawJson = obj,
        )
    }

    private fun parseLogEntry(obj: JsonObject): PbLogEntry {
        val data = obj["data"] as? JsonObject ?: JsonObject(emptyMap())
        return PbLogEntry(
            id = obj.stringOrNull("id").orEmpty(),
            created = obj.stringOrNull("created").orEmpty(),
            level = obj["level"]?.jsonPrimitive?.intOrNull ?: 0,
            message = obj.stringOrNull("message").orEmpty(),
            method = data.stringOrNull("method").orEmpty(),
            url = data.stringOrNull("url").orEmpty(),
            status = data["status"]?.jsonPrimitive?.intOrNull,
            execTimeMs = data["execTime"]?.jsonPrimitive?.doubleOrNull,
            remoteIP = data.stringOrNull("remoteIP").orEmpty(),
            userAgent = data.stringOrNull("userAgent").orEmpty(),
            auth = data.stringOrNull("auth").orEmpty(),
            type = data.stringOrNull("type").orEmpty(),
            data = data,
            rawJson = obj,
        )
    }

    private fun parseJsonObjectOrNull(raw: String): JsonObject? {
        if (raw.isBlank()) return null
        return runCatching { JsonInstant.parseToJsonElement(raw).jsonObject }.getOrNull()
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val el = this[key] ?: return null
        if (el is JsonNull) return null
        return (el as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonObject.nullableString(key: String): String? {
        val el = this[key] ?: return null
        if (el is JsonNull) return null
        return (el as? JsonPrimitive)?.contentOrNull
    }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun describeError(error: Throwable): String {
        val msg = error.message?.takeIf { it.isNotBlank() }
        return msg ?: error.javaClass.simpleName
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
