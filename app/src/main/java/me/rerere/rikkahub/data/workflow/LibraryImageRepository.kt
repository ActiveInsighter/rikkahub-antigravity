package me.rerere.rikkahub.data.workflow

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.net.URLEncoder

data class LibraryImageItem(
    val id: String,
    val sourceUrl: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val createdAt: String,
)

data class LibraryImagePage(
    val items: List<LibraryImageItem>,
    val cursor: String = "",
)

class LibraryImageRepository(
    private val context: Context,
    private val workflowRepository: WorkflowRepository,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val _pendingReuseUrls = MutableStateFlow<List<String>>(emptyList())
    val pendingReuseUrls: StateFlow<List<String>> = _pendingReuseUrls.asStateFlow()

    val isSignedIn: Boolean
        get() = workflowRepository.session.value != null

    fun queueReuseImages(urls: List<String>) {
        if (urls.isNotEmpty()) {
            _pendingReuseUrls.value = (_pendingReuseUrls.value + urls).distinct()
        }
    }

    fun consumeReuseImages(): List<String> {
        val current = _pendingReuseUrls.value
        if (current.isNotEmpty()) {
            _pendingReuseUrls.value = emptyList()
        }
        return current
    }

    suspend fun uploadImage(
        uri: Uri,
        onProgress: (Float) -> Unit,
    ): Result<LibraryImageItem> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val (fileName, mimeType, bytes) = readImagePayload(uri)
            val encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
            val mediaType = mimeType.toMediaType()
            val body = ProgressByteArrayRequestBody(
                bytes = bytes,
                mediaType = mediaType,
                onProgress = onProgress,
            )

            val request = Request.Builder()
                .url("${DEFAULT_REMOTE_API_URL.trimEnd('/')}/api/library/images")
                .put(body)
                .header("Authorization", "Bearer ${session.token}")
                .header("X-PocketBase-Url", session.baseUrl)
                .header("X-File-Name", encodedName)
                .header("Content-Type", mimeType)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                    throw WorkflowApiException(
                        message = message?.takeIf { it.isNotBlank() } ?: "图片上传失败 (${response.code})",
                        statusCode = response.code,
                    )
                }
                onProgress(1f)
                val obj = json.parseToJsonElement(raw).jsonObject
                parseImageItem(obj)
            }
        }
    }

    suspend fun listImages(
        cursor: String = "",
        query: String = "",
        limit: Int = 100,
    ): Result<LibraryImagePage> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val queryParams = buildMap {
                put("limit", limit.coerceIn(1, 1000).toString())
                if (cursor.isNotBlank()) put("cursor", cursor)
                if (query.isNotBlank()) put("q", query.trim())
            }
            val root = workflowRepository.executeJson(
                baseUrl = DEFAULT_REMOTE_API_URL,
                path = "/api/library/images",
                method = "GET",
                query = queryParams,
                token = session.token,
                extraHeaders = mapOf("X-PocketBase-Url" to session.baseUrl),
                authPrefix = "Bearer ",
            ).jsonObject
            val items = root["items"]?.jsonArray?.mapNotNull { el ->
                runCatching { parseImageItem(el.jsonObject) }.getOrNull()
            }.orEmpty()
            val nextCursor = root["cursor"]?.jsonPrimitive?.contentOrNull.orEmpty()
            LibraryImagePage(items = items, cursor = nextCursor)
        }
    }

    suspend fun deleteImage(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            workflowRepository.executeJson(
                baseUrl = DEFAULT_REMOTE_API_URL,
                path = "/api/library/images/${id.trim()}",
                method = "DELETE",
                token = session.token,
                extraHeaders = mapOf("X-PocketBase-Url" to session.baseUrl),
                authPrefix = "Bearer ",
            )
            Unit
        }
    }

    private fun parseImageItem(obj: JsonObject): LibraryImageItem = LibraryImageItem(
        id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        sourceUrl = obj["sourceUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        fileName = obj["fileName"]?.jsonPrimitive?.contentOrNull ?: "image.png",
        mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull ?: "image/png",
        size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L,
        createdAt = obj["createdAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )

    private fun readImagePayload(uri: Uri): Triple<String, String, ByteArray> {
        val bytes = when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: throw IOException("无效的图片路径")
                File(path).readBytes()
            }
            else -> {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("无法读取图片文件")
            }
        }
        if (bytes.isEmpty()) throw IOException("图片内容为空")

        val rawName = resolveFileName(uri)
        val rawMime = (context.contentResolver.getType(uri) ?: guessMimeFromName(rawName)).lowercase()
        val normalizedMime = when (rawMime) {
            "image/jpg", "image/jpeg" -> "image/jpeg"
            "image/webp" -> "image/webp"
            "image/gif" -> "image/gif"
            "image/png" -> "image/png"
            else -> guessMimeFromMagic(bytes) ?: "image/png"
        }
        val ext = when (normalizedMime) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "png"
        }
        val finalName = if (rawName.contains('.')) rawName else "$rawName.$ext"
        return Triple(finalName, normalizedMime, bytes)
    }

    private fun resolveFileName(uri: Uri): String {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "image"
    }

    private fun guessMimeFromName(name: String): String = when {
        name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
        name.endsWith(".webp", ignoreCase = true) -> "image/webp"
        name.endsWith(".gif", ignoreCase = true) -> "image/gif"
        else -> "image/png"
    }

    private fun guessMimeFromMagic(bytes: ByteArray): String? {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
            return "image/png"
        }
        if (bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) {
            return "image/gif"
        }
        if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()) {
            return "image/webp"
        }
        return null
    }
}

private class ProgressByteArrayRequestBody(
    private val bytes: ByteArray,
    private val mediaType: MediaType,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType = mediaType
    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val total = bytes.size.coerceAtLeast(1)
        var offset = 0
        val chunkSize = 16 * 1024
        var lastReported = -1f
        while (offset < bytes.size) {
            val count = minOf(chunkSize, bytes.size - offset)
            sink.write(bytes, offset, count)
            offset += count
            val progress = (offset.toFloat() / total).coerceIn(0f, 0.99f)
            if (progress - lastReported >= 0.03f || offset == bytes.size) {
                lastReported = progress
                onProgress(progress)
            }
        }
    }
}
