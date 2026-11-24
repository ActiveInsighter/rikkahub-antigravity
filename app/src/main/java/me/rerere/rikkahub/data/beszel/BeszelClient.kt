package me.rerere.rikkahub.data.beszel

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.common.http.await
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.JsonInstantPretty
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

class BeszelAuthException(message: String) : IOException(message)

class BeszelClient(
    private val http: OkHttpClient,
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
        identity: String,
        password: String,
    ): BeszelAuthResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(rawBaseUrl)
        if (baseUrl.isEmpty()) {
            return@withContext BeszelAuthResult.Error("请输入 Beszel 服务器链接")
        }
        val cleanIdentity = identity.trim()
        if (cleanIdentity.isEmpty() || password.isEmpty()) {
            return@withContext BeszelAuthResult.Error("请输入账号和密码")
        }

        val payload = buildJsonObject {
            put("identity", cleanIdentity)
            put("password", password)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/collections/users/auth-with-password")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            http.newCall(request).await().use { response ->
                val body = response.body.string()
                val json = parseJsonObjectOrNull(body)
                if (response.isSuccessful && json != null) {
                    val token = json.stringOrNull("token").orEmpty()
                    val record = json.objectOrNull("record")
                    if (token.isNotBlank()) {
                        return@withContext BeszelAuthResult.Success(
                            BeszelSession(
                                baseUrl = baseUrl,
                                identity = cleanIdentity,
                                token = token,
                                userId = record?.stringOrNull("id").orEmpty(),
                                userEmail = record?.stringOrNull("email").orEmpty(),
                                userName = record?.stringOrNull("name").orEmpty(),
                            ),
                        )
                    }
                }
                if (response.code == 401 && json != null) {
                    val mfaId = json.stringOrNull("mfaId")
                    if (!mfaId.isNullOrBlank()) {
                        val otpRes = requestOtpInternal(baseUrl, cleanIdentity)
                        return@withContext BeszelAuthResult.MfaRequired(
                            mfaId = mfaId,
                            otpId = otpRes,
                            email = cleanIdentity,
                        )
                    }
                }
                val serverMsg = json?.stringOrNull("message")
                val msg = when (response.code) {
                    400 -> "账号或密码错误"
                    404 -> "未找到 Beszel 接口，请检查服务器链接"
                    else -> serverMsg?.takeIf { it.isNotBlank() } ?: "登录失败 (HTTP ${response.code})"
                }
                BeszelAuthResult.Error(msg)
            }
        }.getOrElse { error ->
            BeszelAuthResult.Error(describeNetworkError(error))
        }
    }

    suspend fun verifyOtp(
        rawBaseUrl: String,
        identity: String,
        otpId: String,
        otpCode: String,
        mfaId: String?,
    ): BeszelAuthResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(rawBaseUrl)
        val payload = buildJsonObject {
            put("otpId", otpId)
            put("password", otpCode.trim())
            if (!mfaId.isNullOrBlank()) {
                put("mfaId", mfaId)
            }
        }
        val request = Request.Builder()
            .url("$baseUrl/api/collections/users/auth-with-otp")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            http.newCall(request).await().use { response ->
                val body = response.body.string()
                val json = parseJsonObjectOrNull(body)
                if (response.isSuccessful && json != null) {
                    val token = json.stringOrNull("token").orEmpty()
                    val record = json.objectOrNull("record")
                    if (token.isNotBlank()) {
                        return@withContext BeszelAuthResult.Success(
                            BeszelSession(
                                baseUrl = baseUrl,
                                identity = identity.trim(),
                                token = token,
                                userId = record?.stringOrNull("id").orEmpty(),
                                userEmail = record?.stringOrNull("email").orEmpty(),
                                userName = record?.stringOrNull("name").orEmpty(),
                            ),
                        )
                    }
                }
                val msg = json?.stringOrNull("message")?.takeIf { it.isNotBlank() }
                    ?: "验证码错误或已过期 (HTTP ${response.code})"
                BeszelAuthResult.Error(msg)
            }
        }.getOrElse { error ->
            BeszelAuthResult.Error(describeNetworkError(error))
        }
    }

    private suspend fun requestOtpInternal(baseUrl: String, email: String): String {
        val payload = buildJsonObject { put("email", email) }
        val request = Request.Builder()
            .url("$baseUrl/api/collections/users/request-otp")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        http.newCall(request).await().use { response ->
            val body = response.body.string()
            val json = parseJsonObjectOrNull(body)
            if (!response.isSuccessful) {
                throw IOException(json?.stringOrNull("message") ?: "请求验证码失败 (HTTP ${response.code})")
            }
            return json?.stringOrNull("otpId")
                ?: throw IOException("服务器未返回 otpId")
        }
    }

    suspend fun refreshAuth(session: BeszelSession): BeszelSession = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${session.baseUrl}/api/collections/users/auth-refresh")
            .header("Authorization", "Bearer ${session.token}")
            .post("".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val json = executeJsonObject(request)
        val newToken = json.stringOrNull("token").orEmpty().ifBlank { session.token }
        val record = json.objectOrNull("record")
        session.copy(
            token = newToken,
            userId = record?.stringOrNull("id") ?: session.userId,
            userEmail = record?.stringOrNull("email") ?: session.userEmail,
            userName = record?.stringOrNull("name") ?: session.userName,
        )
    }

    suspend fun fetchHubInfo(session: BeszelSession): BeszelHubInfo = withContext(Dispatchers.IO) {
        val request = authorizedGet(session, "/api/beszel/info")
        val json = executeJsonObject(request)
        BeszelHubInfo(
            version = json.stringOrNull("v").orEmpty(),
            checkUpdates = json.booleanValue("cu"),
        )
    }

    suspend fun fetchSystems(session: BeszelSession): List<BeszelSystem> = withContext(Dispatchers.IO) {
        val url = "${session.baseUrl}/api/collections/systems/records".toHttpUrl().newBuilder()
            .addQueryParameter("perPage", "500")
            .addQueryParameter("sort", "+name")
            .addQueryParameter("fields", "id,name,host,port,info,status,updated")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()
        val items = executePocketBaseList(request)
        items.map { r ->
            BeszelSystem(
                id = r.stringOrNull("id").orEmpty(),
                name = r.stringOrNull("name").orEmpty(),
                host = r.stringOrNull("host").orEmpty(),
                port = r.stringOrNull("port").orEmpty(),
                status = r.stringOrNull("status").orEmpty(),
                updated = r.stringOrNull("updated").orEmpty(),
                info = r.objectOrNull("info") ?: JsonObject(emptyMap()),
            )
        }
    }

    suspend fun fetchSystemDetails(
        session: BeszelSession,
        system: BeszelSystem,
    ): BeszelSystemDetails? = withContext(Dispatchers.IO) {
        if (system.hasLegacyDetails) {
            return@withContext BeszelSystemDetails.fromLegacyInfo(system)
        }
        runCatching {
            val request = authorizedGet(session, "/api/collections/system_details/records/${system.id}")
            val obj = executeJsonObject(request)
            BeszelSystemDetails.fromRecord(obj)
        }.getOrNull()
    }

    suspend fun fetchStats(
        session: BeszelSession,
        systemId: String,
        range: BeszelChartRange,
    ): List<BeszelStatsPoint> = withContext(Dispatchers.IO) {
        val since = formatPbTimestamp(range.sinceInstant())
        val filter = "system='$systemId' && created > '$since' && type='${range.type}'"
        val url = "${session.baseUrl}/api/collections/system_stats/records".toHttpUrl().newBuilder()
            .addQueryParameter("perPage", "500")
            .addQueryParameter("filter", filter)
            .addQueryParameter("fields", "created,stats")
            .addQueryParameter("sort", "created")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()
        val items = executePocketBaseList(request)
        items.mapNotNull { r ->
            val created = parsePbInstant(r.stringOrNull("created")) ?: return@mapNotNull null
            val stats = r.objectOrNull("stats") ?: return@mapNotNull null
            BeszelStatsPoint(time = created, stats = stats)
        }
    }

    suspend fun fetchContainers(
        session: BeszelSession,
        systemId: String,
    ): List<BeszelContainer> = withContext(Dispatchers.IO) {
        val url = "${session.baseUrl}/api/collections/containers/records".toHttpUrl().newBuilder()
            .addQueryParameter("perPage", "2000")
            .addQueryParameter("filter", "system='$systemId'")
            .addQueryParameter("fields", "id,name,image,updatable,ports,cpu,memory,net,health,status,system,updated")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()
        val items = executePocketBaseList(request).map { r ->
            BeszelContainer(
                id = r.stringOrNull("id").orEmpty(),
                name = r.stringOrNull("name").orEmpty(),
                image = r.stringOrNull("image").orEmpty(),
                ports = r.stringOrNull("ports").orEmpty(),
                status = r.stringOrNull("status").orEmpty(),
                health = r.intValue("health"),
                cpu = r.doubleValue("cpu"),
                memoryMb = r.doubleValue("memory"),
                netBytesPerSec = r.doubleValue("net"),
                updatable = r.booleanValue("updatable"),
                updated = r.longValue("updated"),
            )
        }
        filterFresh(items) { it.updated }.sortedBy { it.name.lowercase() }
    }

    suspend fun fetchServices(
        session: BeszelSession,
        systemId: String,
    ): List<BeszelSystemdService> = withContext(Dispatchers.IO) {
        val url = "${session.baseUrl}/api/collections/systemd_services/records".toHttpUrl().newBuilder()
            .addQueryParameter("perPage", "500")
            .addQueryParameter("filter", "system='$systemId'")
            .addQueryParameter("fields", "id,name,state,sub,cpu,cpuPeak,memory,memPeak,updated")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()
        val items = executePocketBaseList(request).map { r ->
            BeszelSystemdService(
                id = r.stringOrNull("id").orEmpty(),
                name = r.stringOrNull("name").orEmpty(),
                state = r.intValue("state"),
                sub = r.intValue("sub"),
                cpu = r.doubleValue("cpu"),
                cpuPeak = r.doubleValue("cpuPeak"),
                memoryBytes = r.doubleValue("memory"),
                memPeakBytes = r.doubleValue("memPeak"),
                updated = r.longValue("updated"),
            )
        }
        filterFresh(items) { it.updated }.sortedWith(
            compareByDescending<BeszelSystemdService> { it.isFailed }.thenBy { it.name.lowercase() },
        )
    }

    suspend fun fetchSmartDevices(
        session: BeszelSession,
        systemId: String,
    ): List<BeszelSmartDevice> = withContext(Dispatchers.IO) {
        val url = "${session.baseUrl}/api/collections/smart_devices/records".toHttpUrl().newBuilder()
            .addQueryParameter("perPage", "500")
            .addQueryParameter("filter", "system='$systemId'")
            .addQueryParameter("sort", "name")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()
        executePocketBaseList(request).map { r ->
            BeszelSmartDevice(
                id = r.stringOrNull("id").orEmpty(),
                name = r.stringOrNull("name").orEmpty(),
                model = r.stringOrNull("model").orEmpty(),
                serial = r.stringOrNull("serial").orEmpty(),
                firmware = r.stringOrNull("firmware").orEmpty(),
                state = r.stringOrNull("state").orEmpty(),
                type = r.stringOrNull("type").orEmpty(),
                capacityBytes = r.doubleValue("capacity"),
                tempCelsius = r.doubleOrNull("temp"),
                hours = r.intValue("hours"),
                cycles = r.intValue("cycles"),
                updated = r.stringOrNull("updated").orEmpty(),
            )
        }
    }

    suspend fun refreshSmart(session: BeszelSession, systemId: String) = withContext(Dispatchers.IO) {
        val url = "${session.baseUrl}/api/beszel/smart/refresh".toHttpUrl().newBuilder()
            .addQueryParameter("system", systemId)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.token}")
            .post("".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        http.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw IOException("刷新 S.M.A.R.T. 失败 (HTTP ${response.code})")
            }
        }
    }

    suspend fun fetchContainerLogs(
        session: BeszelSession,
        systemId: String,
        containerId: String,
    ): String = withContext(Dispatchers.IO) {
        val url = "${session.baseUrl}/api/beszel/containers/logs".toHttpUrl().newBuilder()
            .addQueryParameter("system", systemId)
            .addQueryParameter("container", containerId)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()
        val json = executeJsonObject(request)
        json.stringOrNull("logs").orEmpty()
    }

    suspend fun fetchContainerInfo(
        session: BeszelSession,
        systemId: String,
        containerId: String,
    ): String = withContext(Dispatchers.IO) {
        val url = "${session.baseUrl}/api/beszel/containers/info".toHttpUrl().newBuilder()
            .addQueryParameter("system", systemId)
            .addQueryParameter("container", containerId)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()
        val json = executeJsonObject(request)
        val infoEl = json["info"] ?: return@withContext ""
        if (infoEl is JsonPrimitive && infoEl.isString) {
            val raw = infoEl.content
            return@withContext runCatching {
                val parsed = JsonInstant.parseToJsonElement(raw)
                JsonInstantPretty.encodeToString(JsonElement.serializer(), parsed)
            }.getOrDefault(raw)
        }
        JsonInstantPretty.encodeToString(JsonElement.serializer(), infoEl)
    }

    suspend fun fetchServiceLogs(
        session: BeszelSession,
        systemId: String,
        serviceName: String,
    ): String = withContext(Dispatchers.IO) {
        val url = "${session.baseUrl}/api/beszel/systemd/logs".toHttpUrl().newBuilder()
            .addQueryParameter("system", systemId)
            .addQueryParameter("service", serviceName)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()
        val json = executeJsonObject(request)
        json.stringOrNull("logs").orEmpty()
    }

    suspend fun fetchAlertHistory(
        session: BeszelSession,
        perPage: Int = 100,
    ): List<BeszelAlertEvent> = withContext(Dispatchers.IO) {
        val url = "${session.baseUrl}/api/collections/alerts_history/records".toHttpUrl().newBuilder()
            .addQueryParameter("page", "1")
            .addQueryParameter("perPage", perPage.toString())
            .addQueryParameter("expand", "system")
            .addQueryParameter("fields", "id,name,monitor_name,value,system,created,resolved,expand.system.name")
            .addQueryParameter("sort", "-created")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()
        executePocketBaseList(request).map { r ->
            val expandedSystemName = r.objectOrNull("expand")
                ?.objectOrNull("system")
                ?.stringOrNull("name")
                .orEmpty()
            BeszelAlertEvent(
                id = r.stringOrNull("id").orEmpty(),
                name = r.stringOrNull("name").orEmpty(),
                systemId = r.stringOrNull("system").orEmpty(),
                systemName = expandedSystemName,
                monitorName = r.stringOrNull("monitor_name").orEmpty(),
                value = r.doubleValue("value"),
                created = parsePbInstant(r.stringOrNull("created")),
                resolved = parsePbInstant(r.stringOrNull("resolved")),
            )
        }
    }

    suspend fun fetchAlertRules(session: BeszelSession): List<BeszelAlertRule> = withContext(Dispatchers.IO) {
        val url = "${session.baseUrl}/api/collections/alerts/records".toHttpUrl().newBuilder()
            .addQueryParameter("perPage", "500")
            .addQueryParameter("fields", "id,name,system,value,min,triggered")
            .addQueryParameter("sort", "updated")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()
        executePocketBaseList(request).map { r ->
            BeszelAlertRule(
                id = r.stringOrNull("id").orEmpty(),
                name = r.stringOrNull("name").orEmpty(),
                systemId = r.stringOrNull("system").orEmpty(),
                value = r.doubleValue("value"),
                min = r.intValue("min"),
                triggered = r.booleanValue("triggered"),
            )
        }
    }

    suspend fun deleteAlertHistory(session: BeszelSession, id: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${session.baseUrl}/api/collections/alerts_history/records/$id")
            .header("Authorization", "Bearer ${session.token}")
            .delete()
            .build()
        http.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw IOException("删除告警记录失败 (HTTP ${response.code})")
            }
        }
    }

    /**
     * Subscribes to PocketBase realtime SSE `rt_metrics?system=<systemId>`
     * and emits live [BeszelStatsPoint] samples (~1/s).
     */
    fun observeLiveMetrics(session: BeszelSession, systemId: String): Flow<BeszelStatsPoint> = flow {
        val url = "${session.baseUrl}/api/realtime"
        var retryDelay = 1_000L
        flow {
            sseFrames(url).collect { frame ->
                if (frame.type == "PB_CONNECT") {
                    val clientId = frame.id ?: throw IOException("Missing PocketBase client ID")
                    subscribeRealtime(url, session.token, clientId, systemId)
                    retryDelay = 1_000L
                } else if (frame.type.startsWith("rt_metrics")) {
                    val obj = parseJsonObjectOrNull(frame.data)
                    val stats = obj?.objectOrNull("stats")
                    if (stats != null) {
                        emit(BeszelStatsPoint(time = Instant.now(), stats = stats))
                    }
                }
            }
        }.retryWhen { error, _ ->
            if (error !is IOException) return@retryWhen false
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(15_000L)
            true
        }.collect { emit(it) }
    }

    private data class SseFrame(val id: String?, val type: String, val data: String)

    private fun sseFrames(url: String): Flow<SseFrame> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .build()
        val stream = sseFactory.newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    if (type == null) return
                    if (trySend(SseFrame(id, type, data)).isFailure) {
                        close(IOException("Realtime buffer overflow"))
                        eventSource.cancel()
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    close(IOException("Realtime disconnected"))
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?,
                ) {
                    close(IOException("Realtime failed", t))
                }
            },
        )
        awaitClose { stream.cancel() }
    }.buffer(64)

    private suspend fun subscribeRealtime(
        url: String,
        token: String,
        clientId: String,
        systemId: String,
    ) {
        val topic = "${"rt_metrics"}?system=$systemId"
        val body = buildJsonObject {
            put("clientId", clientId)
            putJsonArray("subscriptions") {
                add(JsonPrimitive(topic))
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        http.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Realtime subscribe failed (HTTP ${response.code})")
            }
        }
    }

    private fun authorizedGet(session: BeszelSession, path: String): Request =
        Request.Builder()
            .url("${session.baseUrl}$path")
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()

    private suspend fun executeJsonObject(request: Request): JsonObject {
        http.newCall(request).await().use { response ->
            val body = response.body.string()
            if (response.code == 401 || response.code == 403) {
                throw BeszelAuthException("登录状态已失效，请重新登录")
            }
            if (!response.isSuccessful) {
                val msg = parseJsonObjectOrNull(body)?.stringOrNull("message")
                throw IOException(msg?.takeIf { it.isNotBlank() } ?: "请求失败 (HTTP ${response.code})")
            }
            return parseJsonObjectOrNull(body) ?: throw IOException("服务器返回格式异常")
        }
    }

    private suspend fun executePocketBaseList(request: Request): List<JsonObject> {
        val json = executeJsonObject(request)
        val items = json["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { it as? JsonObject }
    }

    private fun parseJsonObjectOrNull(raw: String): JsonObject? {
        if (raw.isBlank()) return null
        return runCatching {
            JsonInstant.parseToJsonElement(raw).jsonObject
        }.getOrNull()
    }

    private fun <T> filterFresh(items: List<T>, updatedMs: (T) -> Long): List<T> {
        if (items.isEmpty()) return items
        val newest = items.maxOf(updatedMs)
        return items.filter { newest - updatedMs(it) <= FRESHNESS_MS }
    }

    private fun describeNetworkError(error: Throwable): String {
        val msg = error.message.orEmpty()
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) -> "无法解析服务器域名，请检查链接"
            msg.contains("Failed to connect", ignoreCase = true) -> "无法连接到服务器，请检查地址和端口"
            msg.contains("timeout", ignoreCase = true) -> "连接超时，请检查网络"
            msg.contains("SSL", ignoreCase = true) || msg.contains("Cert", ignoreCase = true) ->
                "SSL 证书校验失败，请检查 HTTPS 配置"
            else -> msg.ifBlank { "网络连接失败" }
        }
    }

    companion object {
        private const val FRESHNESS_MS = 70_000L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
