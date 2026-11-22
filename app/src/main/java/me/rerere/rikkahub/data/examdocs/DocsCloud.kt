package me.rerere.rikkahub.data.examdocs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.utils.SecretCipher
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
internal data class DocsUser(val id: String, val email: String)

@Serializable
internal data class DocsSession(
    val user: DocsUser,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_at") val expiresAt: Long = 0,
)

internal data class DocsAccountState(
    val restoring: Boolean = true,
    val user: DocsUser? = null,
    val needsLogin: Boolean = false,
)

internal class DocsCloudError(val status: Int) : IOException(
    when (status) {
        400, 401, 403, 422 -> "认证失败，请检查邮箱、验证码或登录状态"
        429 -> "操作过于频繁，请稍后再试"
        else -> "Exam Docs 服务暂时无法连接（HTTP $status）"
    },
)

internal class DocsCloudClient(okHttpClient: OkHttpClient) {
    private val publishableKey = BuildConfig.EXAM_DOCS_PUBLISHABLE_KEY
    val configured = publishableKey.startsWith("sb_publishable_")
    private val client = okHttpClient.newBuilder().apply {
        interceptors().clear()
        networkInterceptors().clear()
    }
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun sendOtp(email: String) {
        post("auth/v1/otp", buildJsonObject {
            put("email", email)
            put("create_user", true)
        })
    }

    suspend fun verifyOtp(email: String, code: String): DocsSession =
        parseSession(post("auth/v1/verify", buildJsonObject {
            put("email", email)
            put("token", code)
            put("type", "email")
        }))

    suspend fun refresh(refreshToken: String, userId: String): DocsSession =
        parseSession(post("auth/v1/token?grant_type=refresh_token", buildJsonObject {
            put("refresh_token", refreshToken)
        }), userId)

    suspend fun logout(accessToken: String) {
        post("auth/v1/logout?scope=local", null, accessToken)
    }

    suspend fun exchange(request: DocsSyncRequest, accessToken: String): DocsSyncResponse =
        docsJson.decodeFromString(post(
            "rest/v1/rpc/exam_sync",
            docsJson.encodeToJsonElement(DocsSyncRequest.serializer(), request),
            accessToken,
        ))

    private fun parseSession(raw: String, expectedUser: String? = null): DocsSession {
        val session = docsJson.decodeFromString<DocsSession>(raw)
        require(UUID.fromString(session.user.id).toString() == session.user.id)
        require(!session.accessToken.isNullOrBlank() && !session.refreshToken.isNullOrBlank())
        require(expectedUser == null || session.user.id == expectedUser)
        return session
    }

    private suspend fun post(path: String, payload: JsonElement?, accessToken: String? = null): String =
        suspendCancellableCoroutine { continuation ->
            check(configured) { "此构建未配置 Exam Docs 登录" }
            val request = Request.Builder()
                .url("$DEFAULT_EXAM_DOCS_BASE_URL/api/supabase/$path")
                .header("apikey", publishableKey)
                .header("Accept", "application/json")
                .post((payload?.toString() ?: "").toRequestBody("application/json".toMediaType()))
                .apply { if (accessToken != null) header("Authorization", "Bearer $accessToken") }
                .build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            if (!it.isSuccessful) throw DocsCloudError(it.code)
                            val body = it.body
                            if (body.source().request(16L * 1024 * 1024 + 1)) throw IOException("同步响应过大")
                            body.string()
                        }
                        continuation.resume(result)
                    } catch (error: IOException) {
                        continuation.resumeWithException(error)
                    }
                }
            })
        }
}

internal class DocsAccountRepository(context: Context, private val cloud: DocsCloudClient) {
    private val prefs: SharedPreferences = context.getSharedPreferences("docs-account", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val _state = MutableStateFlow(DocsAccountState())
    val state = _state.asStateFlow()
    val configured get() = cloud.configured
    private var session: DocsSession? = null
    private var initialized = false

    init {
        scope.launch { lock.withLock { restore() } }
    }

    private fun restore() {
        if (initialized) return
        session = prefs.getString("session", null)?.let {
            runCatching { docsJson.decodeFromString<DocsSession>(SecretCipher.decrypt(it)) }.getOrNull()
        }
        initialized = true
        publish()
    }

    suspend fun sendOtp(email: String) = cloud.sendOtp(email.trim())

    suspend fun verifyOtp(email: String, code: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            restore()
            persist(cloud.verifyOtp(email.trim(), code.trim()))
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val token = lock.withLock {
            restore()
            val currentToken = session?.accessToken
            persist(null)
            currentToken
        }
        if (token != null) scope.launch { runCatching { cloud.logout(token) } }
    }

    suspend fun accessToken(userId: String, forced: Boolean = false): String? = withContext(Dispatchers.IO) {
        lock.withLock {
            restore()
            val current = session?.takeIf { it.user.id == userId } ?: return@withLock null
            val refreshToken = current.refreshToken ?: return@withLock null
            if (!forced && current.expiresAt > Instant.now().epochSecond + 60) return@withLock current.accessToken
            try {
                val updated = cloud.refresh(refreshToken, userId)
                persist(updated)
                updated.accessToken
            } catch (error: DocsCloudError) {
                if (error.status !in setOf(400, 401, 403, 422)) throw error
                persist(DocsSession(current.user))
                null
            }
        }
    }

    private fun persist(next: DocsSession?) {
        val encrypted = next?.let { SecretCipher.encrypt(docsJson.encodeToString(it)) }
        val editor = prefs.edit()
        if (encrypted == null) editor.remove("session") else editor.putString("session", encrypted)
        if (!editor.commit()) throw IOException("无法保存登录状态")
        session = next
        publish()
    }

    private fun publish() {
        val current = session
        _state.value = DocsAccountState(false, current?.user, current != null && current.refreshToken == null)
    }
}
