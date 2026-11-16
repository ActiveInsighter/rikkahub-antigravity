package me.rerere.rikkahub.data.workflow

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom

private const val PREFS_NAME = "anyworkflow_remote_prefs"
private const val KEY_SESSION = "session_v1"
private const val KEY_BASE_URL = "base_url_v1"

const val DEFAULT_POCKETBASE_URL = "https://pb.any1.tech"
private const val AUTH_COLLECTION = "aw_clients"
private const val RUN_COLLECTION = "aw_dispatch_runs"
private const val RUN_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

class WorkflowApiException(
    message: String,
    val statusCode: Int = 0,
) : Exception(message)

class WorkflowRepository(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    private val _baseUrl = MutableStateFlow(loadBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<WorkflowSession?> = _session.asStateFlow()

    private fun loadBaseUrl(): String {
        val raw = prefs.getString(KEY_BASE_URL, null)?.trim().orEmpty()
        return if (raw.isNotEmpty()) {
            runCatching { normalizeBaseUrl(raw) }.getOrDefault(DEFAULT_POCKETBASE_URL)
        } else {
            DEFAULT_POCKETBASE_URL
        }
    }

    private fun loadSession(): WorkflowSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            val parsed = json.decodeFromString<WorkflowSession>(raw)
            if (parsed.token.isNotBlank() && parsed.userId.isNotBlank() && parsed.baseUrl.isNotBlank()) {
                parsed
            } else {
                null
            }
        }.getOrNull()
    }

    private fun saveSession(session: WorkflowSession) {
        prefs.edit {
            putString(KEY_BASE_URL, session.baseUrl)
            putString(KEY_SESSION, json.encodeToString(session))
        }
        _baseUrl.value = session.baseUrl
        _session.value = session
    }

    fun logout() {
        prefs.edit { remove(KEY_SESSION) }
        _session.value = null
    }

    suspend fun login(
        identity: String,
        password: String,
        baseUrlInput: String,
    ): Result<WorkflowSession> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmedIdentity = identity.trim()
            if (trimmedIdentity.isEmpty() || password.isEmpty()) {
                throw WorkflowApiException("请输入邮箱和密码", 400)
            }
            val normalizedUrl = normalizeBaseUrl(baseUrlInput)
            val body = buildJsonObject {
                put("identity", trimmedIdentity)
                put("password", password)
            }
            val root = executeJson(
                baseUrl = normalizedUrl,
                path = "/api/collections/$AUTH_COLLECTION/auth-with-password",
                method = "POST",
                body = body,
                token = null,
            ).jsonObject

            val token = root["token"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val record = root["record"]?.jsonObject
            val userId = record?.get("id")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val email = record?.get("email")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val name = record?.get("name")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

            if (token.isEmpty() || userId.isEmpty() || email.isEmpty()) {
                throw WorkflowApiException("登录响应无效", 502)
            }

            WorkflowSession(
                token = token,
                userId = userId,
                email = email,
                name = name,
                baseUrl = normalizedUrl,
            ).also { saveSession(it) }
        }
    }

    suspend fun listRuns(
        page: Int = 1,
        perPage: Int = 30,
        filter: RunListFilter = RunListFilter.ALL,
        search: String = "",
    ): Result<WorkflowRunPage> = withContext(Dispatchers.IO) {
        runCatching {
            val activeSession = requireSession()
            val filterExpr = buildString {
                append(runFilterExpression(activeSession.userId, filter))
                val trimmed = search.trim()
                if (trimmed.isNotEmpty()) {
                    val quoted = quoteFilter(trimmed)
                    append(" && (title~\"$quoted\" || id=\"$quoted\")")
                }
            }
            val root = executeJson(
                baseUrl = activeSession.baseUrl,
                path = "/api/collections/$RUN_COLLECTION/records",
                method = "GET",
                query = mapOf(
                    "page" to page.toString(),
                    "perPage" to perPage.toString(),
                    "sort" to "-updated",
                    "filter" to filterExpr,
                ),
                token = activeSession.token,
            ).jsonObject

            val itemsArray = root["items"] as? JsonArray
                ?: throw WorkflowApiException("接口返回的数据格式无效", 502)

            val runs = itemsArray.map { element ->
                val run = parseRun(element.jsonObject)
                if (run.owner != activeSession.userId) {
                    throw WorkflowApiException("接口返回的数据归属无效", 502)
                }
                run
            }

            WorkflowRunPage(
                page = root["page"]?.jsonPrimitive?.intOrNull ?: page,
                perPage = root["perPage"]?.jsonPrimitive?.intOrNull ?: perPage,
                totalItems = root["totalItems"]?.jsonPrimitive?.intOrNull ?: runs.size,
                totalPages = (root["totalPages"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1),
                items = runs,
            )
        }
    }

    suspend fun createRun(
        titleInput: String,
        planText: String,
        status: DispatchStatus,
    ): Result<WorkflowRun> = withContext(Dispatchers.IO) {
        runCatching {
            val activeSession = requireSession()
            require(status == DispatchStatus.DRAFT || status == DispatchStatus.QUEUED) {
                "新建 Run 仅支持草稿或排队状态"
            }
            val checkedPlan = planText.trim()
            if (checkedPlan.isEmpty()) {
                throw WorkflowApiException("工作流定义不能为空", 400)
            }

            val meta = parsePlanMeta(checkedPlan, fallbackTitle = titleInput)
            val runId = newRunId()
            val body = buildJsonObject {
                put("id", runId)
                put("owner", activeSession.userId)
                put("title", meta.title)
                put("planText", planText)
                put("planChecksum", "")
                put("executionMode", meta.mode.wireValue)
                put("maxConcurrency", meta.maxConcurrency)
                put("status", status.wireValue)
                put("requestedAction", DispatchRequestedAction.NONE.wireValue)
                put("commandVersion", 0)
            }

            val root = executeJson(
                baseUrl = activeSession.baseUrl,
                path = "/api/collections/$RUN_COLLECTION/records",
                method = "POST",
                body = body,
                token = activeSession.token,
            ).jsonObject

            parseRun(root)
        }
    }

    suspend fun commandRun(
        run: WorkflowRun,
        action: DispatchRequestedAction,
    ): Result<WorkflowRun> = withContext(Dispatchers.IO) {
        runCatching {
            val activeSession = requireSession()
            if (run.owner != activeSession.userId) {
                throw WorkflowApiException("当前 Run 不属于登录账号", 403)
            }
            if (!run.status.isActive) {
                throw WorkflowApiException("当前状态不能执行控制命令", 409)
            }
            val body = buildJsonObject {
                put("requestedAction", action.wireValue)
                put("commandVersion", run.commandVersion + 1)
            }
            val root = executeJson(
                baseUrl = activeSession.baseUrl,
                path = "/api/collections/$RUN_COLLECTION/records/${run.id}",
                method = "PATCH",
                body = body,
                token = activeSession.token,
            ).jsonObject

            parseRun(root)
        }
    }

    suspend fun cloneRun(
        source: WorkflowRun,
        targetStatus: DispatchStatus,
    ): Result<WorkflowRun> = withContext(Dispatchers.IO) {
        runCatching {
            val activeSession = requireSession()
            if (source.owner != activeSession.userId) {
                throw WorkflowApiException("当前 Run 不属于登录账号", 403)
            }
            if (source.planText.isBlank()) {
                throw WorkflowApiException("此 Run 没有可复制的工作流定义", 409)
            }
            val isDraft = targetStatus == DispatchStatus.DRAFT
            val suffix = if (isDraft) " · 草稿" else " · 重跑"
            val baseTitle = source.title.trim().ifEmpty { "未命名工作流" }
            val title = baseTitle.take((512 - suffix.length).coerceAtLeast(1)) + suffix
            val origin = if (isDraft) DispatchRunOrigin.EDITED_RERUN else DispatchRunOrigin.RERUN
            val meta = parsePlanMeta(source.planText, fallbackTitle = title)
            val runId = newRunId()

            val body = buildJsonObject {
                put("id", runId)
                put("owner", activeSession.userId)
                put("title", title)
                put("planText", source.planText)
                put("planChecksum", "")
                put("executionMode", meta.mode.wireValue)
                put("maxConcurrency", meta.maxConcurrency)
                put("status", targetStatus.wireValue)
                put("requestedAction", DispatchRequestedAction.NONE.wireValue)
                put("commandVersion", 0)
                put("parentRun", source.id)
                put("origin", origin.wireValue)
            }

            val root = executeJson(
                baseUrl = activeSession.baseUrl,
                path = "/api/collections/$RUN_COLLECTION/records",
                method = "POST",
                body = body,
                token = activeSession.token,
            ).jsonObject

            parseRun(root)
        }
    }

    suspend fun deleteRun(run: WorkflowRun): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val activeSession = requireSession()
            if (run.owner != activeSession.userId) {
                throw WorkflowApiException("当前 Run 不属于登录账号", 403)
            }
            if (!run.canDelete) {
                throw WorkflowApiException("进行中的 Run 不能删除，请先取消并等待结束", 409)
            }
            executeJson(
                baseUrl = activeSession.baseUrl,
                path = "/api/collections/$RUN_COLLECTION/records/${run.id}",
                method = "DELETE",
                token = activeSession.token,
            )
            Unit
        }
    }

    private fun requireSession(): WorkflowSession =
        _session.value ?: throw WorkflowApiException("请先连接 AnyWorkflow", 401)

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        val parsed = trimmed.toHttpUrlOrNull()
            ?: throw WorkflowApiException("PocketBase 地址格式无效", 400)
        val isLocal = parsed.host == "localhost" || parsed.host == "127.0.0.1"
        if (parsed.scheme != "https" && !isLocal) {
            throw WorkflowApiException("PocketBase 地址必须使用 HTTPS", 400)
        }
        return "${parsed.scheme}://${parsed.host}" +
            if ((parsed.scheme == "https" && parsed.port != 443) || (parsed.scheme == "http" && parsed.port != 80)) {
                ":${parsed.port}"
            } else {
                ""
            }
    }

    private fun executeJson(
        baseUrl: String,
        path: String,
        method: String,
        query: Map<String, String> = emptyMap(),
        body: JsonObject? = null,
        token: String?,
    ): JsonElement {
        val httpUrlBuilder = (baseUrl + path).toHttpUrlOrNull()?.newBuilder()
            ?: throw WorkflowApiException("请求地址无效", 400)
        for ((k, v) in query) {
            httpUrlBuilder.addQueryParameter(k, v)
        }

        val requestBody = body?.toString()?.toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url(httpUrlBuilder.build())
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", token)
        }
        when (method) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(requestBody ?: "".toRequestBody("application/json".toMediaType()))
            "PATCH" -> requestBuilder.patch(requestBody ?: "".toRequestBody("application/json".toMediaType()))
            "DELETE" -> requestBuilder.delete(requestBody)
            else -> requestBuilder.method(method, requestBody)
        }

        val response = try {
            okHttpClient.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw WorkflowApiException("无法连接 AnyWorkflow，请检查网络或服务器地址", 0)
        }

        response.use { res ->
            if (res.code == 204) return JsonObject(emptyMap())
            val text = res.body?.string().orEmpty()
            val payload = if (text.isNotBlank()) {
                runCatching { json.parseToJsonElement(text) }.getOrNull()
            } else {
                null
            }

            if (!res.isSuccessful) {
                if ((res.code == 401 || res.code == 403) && token != null) {
                    logout()
                }
                val fallback = "请求失败（${res.code}）"
                val message = extractErrorMessage(payload, fallback)
                throw WorkflowApiException(message, res.code)
            }

            return payload ?: JsonObject(emptyMap())
        }
    }

    private fun extractErrorMessage(payload: JsonElement?, fallback: String): String {
        val obj = payload as? JsonObject ?: return fallback
        var message = obj["message"]?.jsonPrimitive?.contentOrNull?.trim()
            ?.takeIf { it.isNotEmpty() } ?: fallback
        val dataObj = obj["data"] as? JsonObject
        if (dataObj != null) {
            for ((field, value) in dataObj.entries) {
                val fieldMsg = (value as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                if (!fieldMsg.isNullOrBlank()) {
                    message += "（$field：$fieldMsg）"
                    break
                }
            }
        }
        return message
    }

    private fun runFilterExpression(ownerId: String, filter: RunListFilter): String {
        val owner = "owner=\"${quoteFilter(ownerId)}\""
        return when (filter) {
            RunListFilter.ALL -> owner
            RunListFilter.DRAFT -> "$owner && status=\"draft\""
            RunListFilter.ACTIVE -> "$owner && (status=\"queued\" || status=\"running\")"
            RunListFilter.DONE -> "$owner && status=\"succeeded\""
            RunListFilter.FAILED -> "$owner && status=\"failed\""
            RunListFilter.CANCELED -> "$owner && status=\"canceled\""
        }
    }

    private fun quoteFilter(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun newRunId(): String {
        val sb = StringBuilder(15)
        while (sb.length < 15) {
            val b = random.nextInt(256)
            if (b >= 252) continue
            sb.append(RUN_ID_ALPHABET[b % RUN_ID_ALPHABET.length])
        }
        return sb.toString()
    }

    private data class ParsedPlanMeta(
        val title: String,
        val mode: DispatchExecutionMode,
        val maxConcurrency: Int,
    )

    private fun parsePlanMeta(planText: String, fallbackTitle: String): ParsedPlanMeta {
        var title = fallbackTitle.trim()
        var mode = DispatchExecutionMode.SERIAL
        var maxConcurrency = 1

        val runRegex = Regex("^\\s*@run\\s*=\\s*(.*?)\\s*$", RegexOption.IGNORE_CASE)
        val modeRegex = Regex("^\\s*@mode\\s*=\\s*(serial|parallel)\\s*$", RegexOption.IGNORE_CASE)
        val concRegex = Regex("^\\s*@maxConcurrency\\s*=\\s*(\\d+)\\s*$", RegexOption.IGNORE_CASE)

        for (line in planText.lineSequence()) {
            runRegex.matchEntire(line)?.let { m ->
                val v = m.groupValues[1].trim()
                if (v.isNotEmpty() && title.isEmpty()) title = v
            }
            modeRegex.matchEntire(line)?.let { m ->
                mode = DispatchExecutionMode.fromWire(m.groupValues[1].lowercase())
            }
            concRegex.matchEntire(line)?.let { m ->
                maxConcurrency = m.groupValues[1].toIntOrNull()?.coerceIn(1, 16) ?: 1
            }
        }
        if (title.isEmpty()) title = "Cloud run"
        if (mode == DispatchExecutionMode.SERIAL) maxConcurrency = 1
        return ParsedPlanMeta(title = title, mode = mode, maxConcurrency = maxConcurrency)
    }

    private fun parseRun(obj: JsonObject): WorkflowRun {
        val id = obj.str("id")
        return WorkflowRun(
            id = id,
            owner = obj.str("owner"),
            title = obj.str("title"),
            planText = obj.str("planText"),
            familyId = obj.str("familyId").ifEmpty { id },
            versionNumber = obj.int("versionNumber").takeIf { it > 0 } ?: 1,
            versionMajor = obj.int("versionMajor").takeIf { it > 0 } ?: 1,
            versionMinor = obj.int("versionMinor").takeIf { it >= 0 } ?: 0,
            parentRun = obj.str("parentRun"),
            origin = DispatchRunOrigin.fromWire(obj.str("origin")),
            executionMode = DispatchExecutionMode.fromWire(obj.str("executionMode")),
            maxConcurrency = obj.int("maxConcurrency").takeIf { it > 0 } ?: 1,
            status = DispatchStatus.fromWire(obj.str("status")),
            requestedAction = DispatchRequestedAction.fromWire(obj.str("requestedAction")),
            commandVersion = obj.int("commandVersion"),
            scheduledAt = obj.str("scheduledAt"),
            totalTasks = obj.int("totalTasks"),
            completedTasks = obj.int("completedTasks"),
            lastError = obj.str("lastError"),
            startedAt = obj.str("startedAt"),
            endedAt = obj.str("endedAt"),
            created = obj.str("created"),
            updated = obj.str("updated"),
        )
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0
}
