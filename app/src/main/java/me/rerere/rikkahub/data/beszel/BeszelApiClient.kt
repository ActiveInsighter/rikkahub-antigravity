package me.rerere.rikkahub.data.beszel

import android.annotation.SuppressLint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class BeszelApiException(
    val statusCode: Int = 0,
    override val message: String,
) : Exception(message)

class BeszelApiClient(
    private val baseClient: OkHttpClient,
    private val json: Json,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val insecureClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        baseClient.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun clientFor(allowInsecureTls: Boolean): OkHttpClient =
        if (allowInsecureTls) insecureClient else baseClient

    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    suspend fun authenticate(
        serverUrl: String,
        identity: String,
        password: String,
        allowInsecureTls: Boolean,
    ): PocketBaseAuthResponse = withContext(Dispatchers.IO) {
        val baseUrl = normalizeUrl(serverUrl)
        val url = "$baseUrl/api/collections/users/auth-with-password".toHttpUrlOrNull()
            ?: throw BeszelApiException(message = "服务器地址格式无效")

        val bodyJson = buildJsonObject {
            put("identity", identity.trim())
            put("password", password)
        }
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(bodyJson).toRequestBody(jsonMediaType))
            .build()

        val responseBody = execute(clientFor(allowInsecureTls), request)
        val auth = json.decodeFromString<PocketBaseAuthResponse>(responseBody)
        if (auth.token.isBlank()) {
            throw BeszelApiException(message = "认证失败：未返回有效 Token")
        }
        auth
    }

    suspend fun fetchSystems(config: BeszelConfig): List<BeszelSystem> = withContext(Dispatchers.IO) {
        val url = buildUrl(
            config = config,
            path = "/api/collections/systems/records",
            queryParams = mapOf(
                "perPage" to "200",
                "sort" to "+name",
                "fields" to "id,name,host,port,info,status,updated",
            ),
        )
        val list = decodeList(get(config, url))
        list.items.map(BeszelSystem::fromJson)
    }

    suspend fun fetchSystemDetails(
        config: BeszelConfig,
        system: BeszelSystem,
    ): BeszelSystemDetails? = withContext(Dispatchers.IO) {
        if (system.hasLegacyDetails) {
            return@withContext BeszelSystemDetails.fromLegacySystem(system)
        }
        runCatching {
            val url = buildUrl(
                config = config,
                path = "/api/collections/system_details/records/${system.id}",
            )
            val body = get(config, url)
            val obj = json.decodeFromString<JsonObject>(body)
            BeszelSystemDetails.fromJson(obj)
        }.getOrNull()
    }

    suspend fun fetchSystemStats(
        config: BeszelConfig,
        systemId: String,
        range: BeszelChartRange,
    ): List<BeszelStatsPoint> = withContext(Dispatchers.IO) {
        val sinceUtc = formatPbUtcTimestamp(System.currentTimeMillis() - range.spanMillis)
        val filter = "system='$systemId' && created > '$sinceUtc' && type='${range.type}'"
        val url = buildUrl(
            config = config,
            path = "/api/collections/system_stats/records",
            queryParams = mapOf(
                "perPage" to "500",
                "sort" to "created",
                "fields" to "created,stats",
                "filter" to filter,
            ),
        )
        val list = decodeList(get(config, url))
        list.items.mapNotNull { item ->
            val created = item["created"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val stats = item["stats"] as? JsonObject ?: return@mapNotNull null
            val ts = parsePbTimestamp(created) ?: return@mapNotNull null
            BeszelStatsPoint(
                created = created,
                timestampMillis = ts,
                stats = stats,
            )
        }
    }

    suspend fun fetchContainers(
        config: BeszelConfig,
        systemId: String,
    ): List<BeszelContainer> = withContext(Dispatchers.IO) {
        val url = buildUrl(
            config = config,
            path = "/api/collections/containers/records",
            queryParams = mapOf(
                "perPage" to "500",
                "filter" to "system='$systemId'",
                "fields" to "id,name,image,ports,cpu,memory,net,health,status,updatable,updated",
            ),
        )
        val list = decodeList(get(config, url))
        val items = list.items.map(BeszelContainer::fromJson)
        filterFresh(items) { it.updated }.sortedBy { it.name.lowercase() }
    }

    suspend fun fetchSystemdServices(
        config: BeszelConfig,
        systemId: String,
    ): List<BeszelSystemdService> = withContext(Dispatchers.IO) {
        val url = buildUrl(
            config = config,
            path = "/api/collections/systemd_services/records",
            queryParams = mapOf(
                "perPage" to "500",
                "filter" to "system='$systemId'",
                "fields" to "id,name,state,sub,cpu,memory,updated",
            ),
        )
        val list = decodeList(get(config, url))
        val items = list.items.map(BeszelSystemdService::fromJson)
        filterFresh(items) { it.updated }.sortedWith(
            compareByDescending<BeszelSystemdService> { it.isFailed }
                .thenBy { it.name.lowercase() }
        )
    }

    suspend fun fetchAlertHistory(
        config: BeszelConfig,
        perPage: Int = 50,
    ): List<BeszelAlertEvent> = withContext(Dispatchers.IO) {
        val url = buildUrl(
            config = config,
            path = "/api/collections/alerts_history/records",
            queryParams = mapOf(
                "perPage" to perPage.toString(),
                "expand" to "system",
                "fields" to "id,name,monitor_name,value,system,created,resolved,expand.system.name",
                "sort" to "-created",
            ),
        )
        val list = decodeList(get(config, url))
        list.items.map(BeszelAlertEvent::fromJson)
    }

    private fun <T> filterFresh(items: List<T>, updatedSelector: (T) -> Long): List<T> {
        if (items.isEmpty()) return items
        val newest = items.maxOf(updatedSelector)
        return items.filter { newest - updatedSelector(it) <= FRESHNESS_WINDOW_MS }
    }

    private fun buildUrl(
        config: BeszelConfig,
        path: String,
        queryParams: Map<String, String> = emptyMap(),
    ): okhttp3.HttpUrl {
        val baseUrl = normalizeUrl(config.serverUrl)
        val builder = "$baseUrl$path".toHttpUrlOrNull()?.newBuilder()
            ?: throw BeszelApiException(message = "服务器地址格式无效")
        queryParams.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private fun get(config: BeszelConfig, url: okhttp3.HttpUrl): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.token}")
            .get()
            .build()
        return execute(clientFor(config.allowInsecureTls), request)
    }

    private fun execute(client: OkHttpClient, request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val msg = extractPocketBaseError(body) ?: when (response.code) {
                    400 -> "请求参数或账号密码错误 (400)"
                    401 -> "登录已过期或无权限 (401)"
                    403 -> "没有访问权限 (403)"
                    404 -> "接口不存在，请确认服务器地址为 Beszel Hub 地址 (404)"
                    else -> "请求失败 (${response.code})"
                }
                throw BeszelApiException(statusCode = response.code, message = msg)
            }
            return body
        }
    }

    private fun extractPocketBaseError(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val obj = json.decodeFromString<JsonObject>(body)
            obj["message"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun decodeList(body: String): PocketBaseListResponse =
        json.decodeFromString(PocketBaseListResponse.serializer(), body)

    private fun formatPbUtcTimestamp(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(millis))
    }

    private fun parsePbTimestamp(raw: String): Long? {
        val normalized = raw.trim().replace("T", " ").removeSuffix("Z")
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(normalized)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    companion object {
        private const val FRESHNESS_WINDOW_MS = 70_000L
    }
}
