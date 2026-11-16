package me.rerere.rikkahub.data.workflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import kotlin.math.ceil

private const val LIBRARY_FOLDER_COLLECTION = "aw_library_folders"
private const val RUN_FAVORITE_COLLECTION = "aw_run_favorites"
private const val TEMPLATE_COLLECTION = "aw_templates"
private const val DEFAULT_PAGE_SIZE = 100
private const val MAX_TITLE_LENGTH = 512
private const val MAX_PLAN_TEXT_BYTES = 2 * 1024 * 1024

class WorkflowLibraryRepository(
    private val workflowRepository: WorkflowRepository,
) {
    // -----------------------------------------------------------------------
    // 收藏与模板 (PocketBase)
    // -----------------------------------------------------------------------

    suspend fun listLibrary(): Result<WorkflowLibrarySnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            coroutineScope {
                val foldersDeferred = async { listAllFolders(session) }
                val favoritesDeferred = async { listAllFavorites(session) }
                val templatesDeferred = async { listAllTemplates(session) }

                val folders = foldersDeferred.await()
                val favorites = favoritesDeferred.await()
                val templates = templatesDeferred.await()

                WorkflowLibrarySnapshot(
                    folders = folders,
                    favoriteFolders = scopedFolders(
                        folders = folders,
                        scope = LibraryFolderScope.FAVORITE,
                        referencedFolderIds = favorites.map { it.folder },
                    ),
                    templateFolders = scopedFolders(
                        folders = folders,
                        scope = LibraryFolderScope.TEMPLATE,
                        referencedFolderIds = templates.map { it.folder },
                    ),
                    favorites = favorites,
                    templates = templates,
                )
            }
        }
    }

    suspend fun getRunFavoriteForRun(runId: String): Result<RunFavorite?> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val cleanRunId = requiredId(runId, "Run ID")
            val filter = "owner=\"${workflowRepository.quoteFilter(session.userId)}\" && run=\"${workflowRepository.quoteFilter(cleanRunId)}\""
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$RUN_FAVORITE_COLLECTION/records",
                method = "GET",
                query = mapOf(
                    "page" to "1",
                    "perPage" to "5",
                    "sort" to "-updated",
                    "expand" to "run,folder",
                    "filter" to filter,
                ),
                token = session.token,
            ).jsonObject
            val page = workflowRepository.parseListPage(root, 1, 5) { parseFavorite(it, session.userId) }
            page.items.firstOrNull()
        }
    }

    suspend fun createRunFavorite(runId: String, folderId: String = ""): Result<RunFavorite> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val body = buildJsonObject {
                put("owner", session.userId)
                put("folder", optionalId(folderId))
                put("run", requiredId(runId, "Run ID"))
                put("note", "")
                put("sortOrder", 0)
            }
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$RUN_FAVORITE_COLLECTION/records",
                method = "POST",
                query = mapOf("expand" to "run,folder"),
                body = body,
                token = session.token,
            ).jsonObject
            parseFavorite(root, session.userId)
        }
    }

    suspend fun moveRunFavorite(favoriteId: String, folderId: String = ""): Result<RunFavorite> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val body = buildJsonObject {
                put("folder", optionalId(folderId))
            }
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$RUN_FAVORITE_COLLECTION/records/${requiredId(favoriteId, "收藏 ID")}",
                method = "PATCH",
                query = mapOf("expand" to "run,folder"),
                body = body,
                token = session.token,
            ).jsonObject
            parseFavorite(root, session.userId)
        }
    }

    suspend fun deleteRunFavorite(favoriteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$RUN_FAVORITE_COLLECTION/records/${requiredId(favoriteId, "收藏 ID")}",
                method = "DELETE",
                token = session.token,
            )
            Unit
        }
    }

    suspend fun createLibraryFolder(
        name: String,
        scope: LibraryFolderScope,
        parentId: String = "",
    ): Result<LibraryFolder> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val body = buildJsonObject {
                put("owner", session.userId)
                put("parent", optionalId(parentId))
                put("name", bounded(name, 120, "目录名称", required = true))
                put("scope", scope.wireValue)
                put("sortOrder", 0)
            }
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$LIBRARY_FOLDER_COLLECTION/records",
                method = "POST",
                body = body,
                token = session.token,
            ).jsonObject
            parseFolder(root, session.userId)
        }
    }

    suspend fun updateLibraryFolder(
        folderId: String,
        name: String? = null,
        parentId: String? = null,
    ): Result<LibraryFolder> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val body = buildJsonObject {
                if (name != null) put("name", bounded(name, 120, "目录名称", required = true))
                if (parentId != null) put("parent", optionalId(parentId))
            }
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$LIBRARY_FOLDER_COLLECTION/records/${requiredId(folderId, "目录 ID")}",
                method = "PATCH",
                body = body,
                token = session.token,
            ).jsonObject
            parseFolder(root, session.userId)
        }
    }

    suspend fun deleteLibraryFolder(
        folderId: String,
        scope: LibraryFolderScope? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val cleanId = requiredId(folderId, "目录 ID")
            val foreignScope = when (scope) {
                LibraryFolderScope.FAVORITE -> LibraryFolderScope.TEMPLATE
                LibraryFolderScope.TEMPLATE -> LibraryFolderScope.FAVORITE
                null -> null
            }
            val library = listLibrary().getOrThrow()
            val parent = library.folders.firstOrNull { it.id == cleanId }?.parent.orEmpty()
            val target = if (parent == cleanId) "" else parent

            coroutineScope {
                val jobs = mutableListOf<kotlinx.coroutines.Deferred<Any>>()
                for (child in library.folders) {
                    if (child.parent == cleanId && child.id != cleanId && child.scope != foreignScope) {
                        jobs += async { updateLibraryFolder(child.id, parentId = target).getOrThrow() }
                    }
                }
                if (scope != LibraryFolderScope.TEMPLATE) {
                    for (fav in library.favorites) {
                        if (fav.folder == cleanId) {
                            jobs += async { moveRunFavorite(fav.id, target).getOrThrow() }
                        }
                    }
                }
                if (scope != LibraryFolderScope.FAVORITE) {
                    for (tpl in library.templates) {
                        if (tpl.folder == cleanId) {
                            jobs += async { updateWorkflowTemplate(tpl.id, folderId = target).getOrThrow() }
                        }
                    }
                }
                jobs.awaitAll()
            }

            workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$LIBRARY_FOLDER_COLLECTION/records/$cleanId",
                method = "DELETE",
                token = session.token,
            )
            Unit
        }
    }

    suspend fun createWorkflowTemplate(
        title: String,
        description: String = "",
        planText: String,
        folderId: String = "",
        sourceRunId: String = "",
        tags: List<String> = emptyList(),
    ): Result<WorkflowTemplate> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val cleanTags = normalizeTags(tags)
            val body = buildJsonObject {
                put("owner", session.userId)
                put("folder", optionalId(folderId))
                put("title", bounded(title, MAX_TITLE_LENGTH, "模板名称", required = true))
                put("description", bounded(description, 4096, "模板描述"))
                put("planText", validatePlanText(planText))
                put("sourceRun", optionalId(sourceRunId))
                put("tags", buildJsonArray { cleanTags.forEach { add(JsonPrimitive(it)) } })
                put("sortOrder", 0)
            }
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$TEMPLATE_COLLECTION/records",
                method = "POST",
                query = mapOf("expand" to "folder"),
                body = body,
                token = session.token,
            ).jsonObject
            parseTemplate(root, session.userId)
        }
    }

    suspend fun createWorkflowTemplateFromRun(run: WorkflowRun): Result<WorkflowTemplate> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            if (run.owner != session.userId) {
                throw WorkflowApiException("当前 Run 不属于登录账号", 403)
            }
            createWorkflowTemplate(
                title = run.title.ifBlank { "未命名模板" },
                planText = run.planText,
                sourceRunId = run.id,
            ).getOrThrow()
        }
    }

    suspend fun updateWorkflowTemplate(
        id: String,
        title: String? = null,
        description: String? = null,
        planText: String? = null,
        folderId: String? = null,
        tags: List<String>? = null,
    ): Result<WorkflowTemplate> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val cleanTags = tags?.let { normalizeTags(it) }
            val body = buildJsonObject {
                if (title != null) put("title", bounded(title, MAX_TITLE_LENGTH, "模板名称", required = true))
                if (description != null) put("description", bounded(description, 4096, "模板描述"))
                if (planText != null) put("planText", validatePlanText(planText))
                if (folderId != null) put("folder", optionalId(folderId))
                if (cleanTags != null) put("tags", buildJsonArray { cleanTags.forEach { add(JsonPrimitive(it)) } })
            }
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$TEMPLATE_COLLECTION/records/${requiredId(id, "模板 ID")}",
                method = "PATCH",
                query = mapOf("expand" to "folder"),
                body = body,
                token = session.token,
            ).jsonObject
            parseTemplate(root, session.userId)
        }
    }

    suspend fun duplicateWorkflowTemplate(template: WorkflowTemplate): Result<WorkflowTemplate> {
        val suffix = "（副本）"
        val base = if (template.title.endsWith(suffix)) {
            template.title.removeSuffix(suffix).trim()
        } else {
            template.title
        }
        val nextTitle = base.take((MAX_TITLE_LENGTH - suffix.length).coerceAtLeast(1)) + suffix
        return createWorkflowTemplate(
            title = nextTitle,
            description = template.description,
            planText = template.planText,
            folderId = template.folder,
            sourceRunId = template.sourceRun,
            tags = template.tags,
        )
    }

    suspend fun deleteWorkflowTemplate(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$TEMPLATE_COLLECTION/records/${requiredId(id, "模板 ID")}",
                method = "DELETE",
                token = session.token,
            )
            Unit
        }
    }

    // -----------------------------------------------------------------------
    // 资料库 (Worker /api/library/...)
    // -----------------------------------------------------------------------

    suspend fun listLibraryDrive(): Result<LibraryDriveData> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            var unavailable = 0
            for (attempt in 0 until 50) {
                val imported = executeLibraryJson(session, "/api/library/files/import", "POST").jsonObject
                val done = imported["done"]?.jsonPrimitive?.booleanOrNull == true
                unavailable = imported["unavailable"]?.jsonPrimitive?.intOrNull ?: 0
                if (done) break
            }

            coroutineScope {
                val foldersDeferred = async {
                    val root = executeLibraryJson(session, "/api/library/folders", "GET").jsonObject
                    val arr = root["items"] as? JsonArray
                        ?: throw WorkflowApiException("资料库目录数据格式无效", 502)
                    arr.map { parseLibraryFileFolder(it.jsonObject) }
                }
                val filesDeferred = async {
                    val items = mutableListOf<LibraryFile>()
                    var cursor = ""
                    for (page in 0 until 20) {
                        val query = buildMap {
                            if (cursor.isNotEmpty()) put("cursor", cursor)
                            put("limit", "1000")
                        }
                        val root = executeLibraryJson(
                            session = session,
                            path = "/api/library/files",
                            method = "GET",
                            query = query,
                        ).jsonObject
                        val arr = root["items"] as? JsonArray
                            ?: throw WorkflowApiException("资料库文件数据格式无效", 502)
                        items.addAll(arr.map { parseLibraryFile(it.jsonObject) })
                        cursor = root["cursor"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (cursor.isEmpty()) break
                    }
                    items
                }

                LibraryDriveData(
                    files = filesDeferred.await(),
                    folders = foldersDeferred.await(),
                    unavailableCount = unavailable,
                )
            }
        }
    }

    suspend fun uploadLibraryMarkdown(
        fileName: String,
        contentBytes: ByteArray,
    ): Result<LibraryFile> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val trimmedName = fileName.trim()
            val cleanName = when {
                trimmedName.endsWith(".md", ignoreCase = true) -> trimmedName
                trimmedName.endsWith(".markdown", ignoreCase = true) ->
                    trimmedName.dropLast(".markdown".length) + ".md"
                else -> throw WorkflowApiException("请选择 .md 格式的 Markdown 文件", 400)
            }
            val normalizedBytes = if (
                contentBytes.size >= 3 &&
                contentBytes[0] == 0xEF.toByte() &&
                contentBytes[1] == 0xBB.toByte() &&
                contentBytes[2] == 0xBF.toByte()
            ) {
                contentBytes.copyOfRange(3, contentBytes.size)
            } else {
                contentBytes
            }
            if (normalizedBytes.isEmpty() || normalizedBytes.size > MAX_PLAN_TEXT_BYTES) {
                throw WorkflowApiException("请选择不超过 2 MiB 的 .md 文件", 400)
            }
            val text = normalizedBytes.toString(Charsets.UTF_8)
            if (text.isBlank() || text.contains('\u0000')) {
                throw WorkflowApiException("文件内容为空或不是有效的 UTF-8 Markdown", 400)
            }
            val encodedName = URLEncoder.encode(cleanName, "UTF-8").replace("+", "%20")
            val rawBody = normalizedBytes.toRequestBody("text/plain; charset=utf-8".toMediaType())
            val root = executeLibraryJson(
                session = session,
                path = "/api/library/files/markdown",
                method = "PUT",
                extraHeaders = mapOf("X-File-Name" to encodedName),
                rawBody = rawBody,
            ).jsonObject
            parseLibraryFile(root)
        }
    }

    suspend fun uploadLibraryPdf(
        fileName: String,
        fileSize: Long,
        openStream: () -> InputStream?,
        onProgress: (Int) -> Unit = {},
    ): Result<LibraryFile> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val cleanName = fileName.trim()
            if (!cleanName.endsWith(".pdf", ignoreCase = true)) {
                throw WorkflowApiException("请选择 .pdf 文件", 400)
            }
            if (fileSize < 5L) {
                throw WorkflowApiException("PDF 文件无效或为空", 400)
            }
            if (fileSize > 2L * 1024L * 1024L * 1024L) {
                throw WorkflowApiException("PDF 不能超过 2 GiB", 400)
            }
            onProgress(0)

            val initBody = buildJsonObject {
                put("fileName", cleanName)
                put("size", fileSize)
            }
            val initRoot = executeLibraryJson(
                session = session,
                path = "/api/library/files/uploads/multipart",
                method = "POST",
                body = initBody,
            ).jsonObject

            val uploadId = initRoot["uploadId"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val ticket = initRoot["ticket"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val partSize = initRoot["partSize"]?.jsonPrimitive?.longOrNull ?: (32L * 1024L * 1024L)
            if (uploadId.isEmpty() || ticket.isEmpty() || partSize <= 0L) {
                throw WorkflowApiException("上传初始化响应无效，请重试", 502)
            }
            val encodedUploadId = URLEncoder.encode(uploadId, "UTF-8").replace("+", "%20")

            val parts = mutableListOf<Pair<Int, String>>()
            var uploadedBytes = 0L

            try {
                val stream = openStream()
                    ?: throw WorkflowApiException("无法读取所选 PDF 文件", 400)
                stream.use { input ->
                    val totalParts = ceil(fileSize.toDouble() / partSize.toDouble()).toInt().coerceAtLeast(1)
                    for (partNumber in 1..totalParts) {
                        val expectedLen = minOf(partSize, fileSize - uploadedBytes).toInt()
                        if (expectedLen <= 0) break
                        val buffer = ByteArray(expectedLen)
                        var readTotal = 0
                        while (readTotal < expectedLen) {
                            val n = input.read(buffer, readTotal, expectedLen - readTotal)
                            if (n < 0) break
                            readTotal += n
                        }
                        if (readTotal != expectedLen) {
                            throw WorkflowApiException("读取 PDF 分片失败，文件大小不一致", 400)
                        }
                        if (partNumber == 1) {
                            if (buffer.size < 5 || String(buffer, 0, 5, Charsets.US_ASCII) != "%PDF-") {
                                throw WorkflowApiException("文件内容不是有效的 PDF", 400)
                            }
                        }

                        val partResult = uploadMultipartPartWithRetry(
                            session = session,
                            encodedUploadId = encodedUploadId,
                            partNumber = partNumber,
                            ticket = ticket,
                            bytes = buffer,
                        )
                        parts += partNumber to partResult
                        uploadedBytes += expectedLen
                        val percent = ((uploadedBytes * 95L) / fileSize).toInt().coerceIn(1, 95)
                        onProgress(percent)
                    }
                }

                val completeBody = buildJsonObject {
                    put(
                        "parts",
                        buildJsonArray {
                            for ((num, etag) in parts) {
                                add(
                                    buildJsonObject {
                                        put("partNumber", num)
                                        put("etag", etag)
                                    }
                                )
                            }
                        }
                    )
                }
                val completedRoot = executeLibraryJson(
                    session = session,
                    path = "/api/library/files/uploads/multipart/$encodedUploadId/complete",
                    method = "POST",
                    body = completeBody,
                    extraHeaders = mapOf("X-Upload-Ticket" to ticket),
                ).jsonObject
                onProgress(100)
                parseLibraryFile(completedRoot)
            } catch (err: Throwable) {
                runCatching {
                    executeLibraryJson(
                        session = session,
                        path = "/api/library/files/uploads/multipart/$encodedUploadId",
                        method = "DELETE",
                        extraHeaders = mapOf("X-Upload-Ticket" to ticket),
                    )
                }
                throw err
            }
        }
    }

    private fun uploadMultipartPartWithRetry(
        session: WorkflowSession,
        encodedUploadId: String,
        partNumber: Int,
        ticket: String,
        bytes: ByteArray,
    ): String {
        var lastError: Throwable? = null
        for (attempt in 0 until 3) {
            try {
                val rawBody = bytes.toRequestBody("application/octet-stream".toMediaType())
                val root = executeLibraryJson(
                    session = session,
                    path = "/api/library/files/uploads/multipart/$encodedUploadId/parts/$partNumber",
                    method = "PUT",
                    extraHeaders = mapOf("X-Upload-Ticket" to ticket),
                    rawBody = rawBody,
                ).jsonObject
                val etag = root["etag"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (etag.isEmpty()) {
                    throw WorkflowApiException("分片上传响应无效，请重试", 502)
                }
                return etag
            } catch (err: Throwable) {
                lastError = err
                val retryable = err is IOException ||
                    (err is WorkflowApiException && (err.statusCode == 0 || err.statusCode == 408 || err.statusCode == 429 || err.statusCode >= 500))
                if (!retryable || attempt >= 2) throw err
                Thread.sleep(500L shl attempt)
            }
        }
        throw lastError ?: WorkflowApiException("分片上传失败", 502)
    }

    suspend fun readLibraryMarkdown(fileId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val root = executeLibraryJson(
                session = session,
                path = "/api/library/files/${requiredId(fileId, "文件 ID")}/text",
                method = "GET",
            ).jsonObject
            root["content"]?.jsonPrimitive?.contentOrNull
                ?: throw WorkflowApiException("无法读取 Markdown 内容", 502)
        }
    }

    suspend fun updateLibraryFile(
        fileId: String,
        fileName: String? = null,
        folderId: String? = null,
    ): Result<LibraryFile> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val body = buildJsonObject {
                if (fileName != null) put("fileName", bounded(fileName, 255, "文件名", required = true))
                if (folderId != null) put("folder", folderId.trim())
            }
            val root = executeLibraryJson(
                session = session,
                path = "/api/library/files/${requiredId(fileId, "文件 ID")}",
                method = "PATCH",
                body = body,
            ).jsonObject
            parseLibraryFile(root)
        }
    }

    suspend fun deleteLibraryFile(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            executeLibraryJson(
                session = session,
                path = "/api/library/files/${requiredId(fileId, "文件 ID")}",
                method = "DELETE",
            )
            Unit
        }
    }

    suspend fun createLibraryFileFolder(
        name: String,
        parentId: String = "",
    ): Result<LibraryFileFolder> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val body = buildJsonObject {
                put("name", bounded(name, 120, "目录名称", required = true))
                put("parent", parentId.trim())
            }
            val root = executeLibraryJson(
                session = session,
                path = "/api/library/folders",
                method = "POST",
                body = body,
            ).jsonObject
            parseLibraryFileFolder(root)
        }
    }

    suspend fun updateLibraryFileFolder(
        folderId: String,
        name: String? = null,
        parentId: String? = null,
    ): Result<LibraryFileFolder> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val body = buildJsonObject {
                if (name != null) put("name", bounded(name, 120, "目录名称", required = true))
                if (parentId != null) put("parent", parentId.trim())
            }
            val root = executeLibraryJson(
                session = session,
                path = "/api/library/folders/${requiredId(folderId, "目录 ID")}",
                method = "PATCH",
                body = body,
            ).jsonObject
            parseLibraryFileFolder(root)
        }
    }

    suspend fun deleteLibraryFileFolder(folderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            executeLibraryJson(
                session = session,
                path = "/api/library/folders/${requiredId(folderId, "目录 ID")}",
                method = "DELETE",
            )
            Unit
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private fun executeLibraryJson(
        session: WorkflowSession,
        path: String,
        method: String,
        query: Map<String, String> = emptyMap(),
        body: JsonObject? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        rawBody: okhttp3.RequestBody? = null,
    ) = workflowRepository.executeJson(
        baseUrl = DEFAULT_REMOTE_API_URL,
        path = path,
        method = method,
        query = query,
        body = body,
        token = session.token,
        extraHeaders = extraHeaders + ("X-PocketBase-Url" to session.baseUrl),
        rawBody = rawBody,
        authPrefix = "Bearer ",
    )

    private fun listAllFolders(session: WorkflowSession): List<LibraryFolder> {
        val result = mutableListOf<LibraryFolder>()
        var page = 1
        while (true) {
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$LIBRARY_FOLDER_COLLECTION/records",
                method = "GET",
                query = mapOf(
                    "page" to page.toString(),
                    "perPage" to DEFAULT_PAGE_SIZE.toString(),
                    "sort" to "+sortOrder,+name",
                    "filter" to "owner=\"${workflowRepository.quoteFilter(session.userId)}\"",
                ),
                token = session.token,
            ).jsonObject
            val parsed = workflowRepository.parseListPage(root, page, DEFAULT_PAGE_SIZE) {
                parseFolder(it, session.userId)
            }
            result.addAll(parsed.items)
            if (page >= parsed.totalPages) break
            page++
        }
        return result
    }

    private fun listAllFavorites(session: WorkflowSession): List<RunFavorite> {
        val result = mutableListOf<RunFavorite>()
        var page = 1
        while (true) {
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$RUN_FAVORITE_COLLECTION/records",
                method = "GET",
                query = mapOf(
                    "page" to page.toString(),
                    "perPage" to DEFAULT_PAGE_SIZE.toString(),
                    "sort" to "-updated",
                    "expand" to "run,folder",
                    "filter" to "owner=\"${workflowRepository.quoteFilter(session.userId)}\"",
                ),
                token = session.token,
            ).jsonObject
            val parsed = workflowRepository.parseListPage(root, page, DEFAULT_PAGE_SIZE) {
                parseFavorite(it, session.userId)
            }
            result.addAll(parsed.items)
            if (page >= parsed.totalPages) break
            page++
        }
        return result
    }

    private fun listAllTemplates(session: WorkflowSession): List<WorkflowTemplate> {
        val result = mutableListOf<WorkflowTemplate>()
        var page = 1
        while (true) {
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$TEMPLATE_COLLECTION/records",
                method = "GET",
                query = mapOf(
                    "page" to page.toString(),
                    "perPage" to DEFAULT_PAGE_SIZE.toString(),
                    "sort" to "-updated",
                    "expand" to "folder",
                    "filter" to "owner=\"${workflowRepository.quoteFilter(session.userId)}\"",
                ),
                token = session.token,
            ).jsonObject
            val parsed = workflowRepository.parseListPage(root, page, DEFAULT_PAGE_SIZE) {
                parseTemplate(it, session.userId)
            }
            result.addAll(parsed.items)
            if (page >= parsed.totalPages) break
            page++
        }
        return result
    }

    private fun parseFolder(obj: JsonObject, expectedOwner: String): LibraryFolder = with(workflowRepository) {
        val owner = obj.str("owner")
        if (owner != expectedOwner) throw WorkflowApiException("目录数据归属无效", 502)
        LibraryFolder(
            id = obj.str("id"),
            owner = owner,
            parent = obj.str("parent"),
            name = obj.str("name"),
            scope = LibraryFolderScope.fromWire(obj.str("scope")),
            sortOrder = obj.int("sortOrder"),
            created = obj.str("created"),
            updated = obj.str("updated"),
        )
    }

    private fun parseFavorite(obj: JsonObject, expectedOwner: String): RunFavorite = with(workflowRepository) {
        val owner = obj.str("owner")
        if (owner != expectedOwner) throw WorkflowApiException("收藏数据归属无效", 502)
        val expand = obj["expand"] as? JsonObject
        val runRecord = (expand?.get("run") as? JsonObject)?.let { parseRun(it) }
        val folderRecord = (expand?.get("folder") as? JsonObject)?.let { parseFolder(it, expectedOwner) }
        RunFavorite(
            id = obj.str("id"),
            owner = owner,
            folder = obj.str("folder"),
            run = obj.str("run"),
            note = obj.str("note"),
            sortOrder = obj.int("sortOrder"),
            created = obj.str("created"),
            updated = obj.str("updated"),
            runRecord = runRecord,
            folderRecord = folderRecord,
        )
    }

    private fun parseTemplate(obj: JsonObject, expectedOwner: String): WorkflowTemplate = with(workflowRepository) {
        val owner = obj.str("owner")
        if (owner != expectedOwner) throw WorkflowApiException("模板数据归属无效", 502)
        val expand = obj["expand"] as? JsonObject
        val folderRecord = (expand?.get("folder") as? JsonObject)?.let { parseFolder(it, expectedOwner) }
        val tags = (obj["tags"] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() }
        }.orEmpty()
        WorkflowTemplate(
            id = obj.str("id"),
            owner = owner,
            folder = obj.str("folder"),
            title = obj.str("title"),
            description = obj.str("description"),
            planText = obj.str("planText"),
            sourceRun = obj.str("sourceRun"),
            tags = tags,
            sortOrder = obj.int("sortOrder"),
            created = obj.str("created"),
            updated = obj.str("updated"),
            folderRecord = folderRecord,
        )
    }

    private fun parseLibraryFile(obj: JsonObject): LibraryFile {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val fileName = obj["fileName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val sourceUrl = obj["sourceUrl"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val createdAt = obj["createdAt"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: createdAt
        val size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L
        if (id.isEmpty() || fileName.isEmpty() || sourceUrl.isEmpty()) {
            throw WorkflowApiException("资料库文件数据格式无效", 502)
        }
        return LibraryFile(
            id = id,
            fileName = fileName,
            size = size,
            sourceUrl = sourceUrl,
            createdAt = createdAt,
            updatedAt = updatedAt,
            folder = obj["folder"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            expiresAt = obj["expiresAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    private fun parseLibraryFileFolder(obj: JsonObject): LibraryFileFolder {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (id.isEmpty() || name.isEmpty()) {
            throw WorkflowApiException("资料库目录数据格式无效", 502)
        }
        val createdAt = obj["createdAt"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: createdAt
        return LibraryFileFolder(
            id = id,
            name = name,
            parent = obj["parent"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun requiredId(value: String, label: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > 240) {
            throw WorkflowApiException("${label}无效", 400)
        }
        return trimmed
    }

    private fun optionalId(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length > 240) throw WorkflowApiException("ID 无效", 400)
        return trimmed
    }

    private fun bounded(value: String, maximum: Int, label: String, required: Boolean = false): String {
        val trimmed = value.trim()
        if (required && trimmed.isEmpty()) throw WorkflowApiException("${label}不能为空", 400)
        if (trimmed.length > maximum) throw WorkflowApiException("${label}过长", 400)
        return trimmed
    }

    private fun validatePlanText(value: String): String {
        if (value.trim().isEmpty() || value.toByteArray(Charsets.UTF_8).size > MAX_PLAN_TEXT_BYTES) {
            throw WorkflowApiException("模板计划无效或超过 2 MiB", 400)
        }
        return value
    }

    private fun normalizeTags(tags: List<String>): List<String> {
        val result = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (raw in tags) {
            val tag = raw.trim()
            if (tag.isEmpty() || !seen.add(tag)) continue
            if (tag.length > 64) throw WorkflowApiException("标签过长", 400)
            result.add(tag)
        }
        if (result.size > 20) throw WorkflowApiException("标签过多", 400)
        return result
    }
}
