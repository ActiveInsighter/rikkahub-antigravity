package me.rerere.rikkahub.data.workflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.util.UUID

private const val CONVERSION_CONFIG_COLLECTION = "aw_file_conversion_configs"
private const val CONVERSION_JOB_COLLECTION = "aw_pdf_to_md_jobs"
private const val JOBS_PAGE_SIZE = 20

class WorkflowConverterRepository(
    private val workflowRepository: WorkflowRepository,
) {
    fun newRecordId(): String = UUID.randomUUID().toString().replace("-", "").take(15)

    suspend fun getPdfConversionConfig(): Result<FileConversionConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val existing = fetchLatestConfig(session)
            if (existing != null) {
                if (existing.systemPrompt.isBlank()) {
                    savePdfConversionConfigInternal(
                        session = session,
                        config = existing,
                        options = existing.options,
                        systemPrompt = DEFAULT_PDF_SYSTEM_PROMPT,
                    )
                } else {
                    existing
                }
            } else {
                val defaults = PdfConversionOptions()
                val body = buildJsonObject {
                    put("id", newRecordId())
                    put("owner", session.userId)
                    put("conversionType", PDF_CONVERSION_TYPE)
                    put("name", "默认配置")
                    put("systemPrompt", DEFAULT_PDF_SYSTEM_PROMPT)
                    put("options", encodeOptions(defaults))
                }
                val created = runCatching {
                    val root = workflowRepository.executeJson(
                        baseUrl = session.baseUrl,
                        path = "/api/collections/$CONVERSION_CONFIG_COLLECTION/records",
                        method = "POST",
                        body = body,
                        token = session.token,
                    ).jsonObject
                    parseConfig(root, session.userId)
                }.getOrElse { err ->
                    fetchLatestConfig(session) ?: throw err
                }
                if (created.systemPrompt.isBlank()) {
                    savePdfConversionConfigInternal(
                        session = session,
                        config = created,
                        options = created.options,
                        systemPrompt = DEFAULT_PDF_SYSTEM_PROMPT,
                    )
                } else {
                    created
                }
            }
        }
    }

    suspend fun savePdfConversionConfig(
        config: FileConversionConfig,
        options: PdfConversionOptions,
        systemPrompt: String,
    ): Result<FileConversionConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            if (config.owner != session.userId) {
                throw WorkflowApiException("配置归属无效", 403)
            }
            savePdfConversionConfigInternal(session, config, options, systemPrompt)
        }
    }

    suspend fun listPdfJobs(page: Int = 1): Result<WorkflowListPage<PdfJob>> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val targetPage = page.coerceAtLeast(1)
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$CONVERSION_JOB_COLLECTION/records",
                method = "GET",
                query = mapOf(
                    "page" to targetPage.toString(),
                    "perPage" to JOBS_PAGE_SIZE.toString(),
                    "sort" to "-created,-id",
                    "filter" to "owner=\"${workflowRepository.quoteFilter(session.userId)}\"",
                ),
                token = session.token,
            ).jsonObject
            workflowRepository.parseListPage(root, targetPage, JOBS_PAGE_SIZE) {
                parseJob(it, session.userId)
            }
        }
    }

    suspend fun createPdfJob(
        id: String,
        title: String,
        sourceUrl: String,
        outputName: String,
        prompt: String,
        configId: String,
        startPage: Int? = null,
        endPage: Int? = null,
        adjacentPages: Boolean = false,
    ): Result<PdfJob> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            val cleanTitle = title.trim()
            val cleanSource = sourceUrl.trim()
            if (cleanTitle.isEmpty()) throw WorkflowApiException("请填写任务名称", 400)
            if (!cleanSource.startsWith("https://", ignoreCase = true)) {
                throw WorkflowApiException("请提供有效的 https:// PDF 链接", 400)
            }
            if (configId.isBlank()) throw WorkflowApiException("转换配置尚未加载", 400)
            if (startPage != null && endPage != null && endPage < startPage) {
                throw WorkflowApiException("结束页不能小于起始页", 400)
            }

            val body = buildJsonObject {
                put("id", id)
                put("owner", session.userId)
                put("title", cleanTitle)
                put("sourceUrl", cleanSource)
                put("outputName", outputName.trim())
                put("prompt", prompt.trim())
                put("configId", configId.trim())
                put("status", "queued")
                if (startPage != null) put("startPage", startPage)
                if (endPage != null) put("endPage", endPage)
                if (adjacentPages) put("adjacentPages", true)
            }
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$CONVERSION_JOB_COLLECTION/records",
                method = "POST",
                body = body,
                token = session.token,
            ).jsonObject
            val job = parseJob(root, session.userId)
            wakeConverterBestEffort(session)
            job
        }
    }

    suspend fun cancelPdfJob(job: PdfJob): Result<PdfJob> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            if (job.owner != session.userId) throw WorkflowApiException("任务归属无效", 403)
            if (!job.status.isActive) throw WorkflowApiException("任务已经结束", 409)
            val body = buildJsonObject {
                put("cancelRequested", true)
            }
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$CONVERSION_JOB_COLLECTION/records/${encodeSegment(job.id)}",
                method = "PATCH",
                body = body,
                token = session.token,
            ).jsonObject
            parseJob(root, session.userId)
        }
    }

    suspend fun rerunPdfJob(newId: String, job: PdfJob): Result<PdfJob> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            if (job.owner != session.userId) throw WorkflowApiException("任务归属无效", 403)
            if (!job.status.isTerminal) throw WorkflowApiException("只能重跑已结束的任务", 409)
            val body = buildJsonObject {
                put("id", newId)
                put("owner", session.userId)
                put("rerunOf", job.id)
            }
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/collections/$CONVERSION_JOB_COLLECTION/records",
                method = "POST",
                body = body,
                token = session.token,
            ).jsonObject
            val created = parseJob(root, session.userId)
            wakeConverterBestEffort(session)
            created
        }
    }

    suspend fun getPdfDownloadUrl(job: PdfJob): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val session = workflowRepository.requireSession()
            if (job.owner != session.userId) throw WorkflowApiException("任务归属无效", 403)
            if (job.file.isBlank()) throw WorkflowApiException("产物暂不可下载，请刷新后重试", 404)
            val root = workflowRepository.executeJson(
                baseUrl = session.baseUrl,
                path = "/api/files/token",
                method = "POST",
                token = session.token,
            ).jsonObject
            val fileToken = root["token"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (fileToken.isEmpty()) throw WorkflowApiException("获取下载凭据失败", 502)
            val encodedToken = URLEncoder.encode(fileToken, "UTF-8")
            "${session.baseUrl}/api/files/$CONVERSION_JOB_COLLECTION/${encodeSegment(job.id)}/${encodeSegment(job.file)}?token=$encodedToken&download=1"
        }
    }

    private fun wakeConverterBestEffort(session: WorkflowSession) {
        runCatching {
            workflowRepository.executeJson(
                baseUrl = DEFAULT_REMOTE_API_URL,
                path = "/api/file-conversion/wake",
                method = "POST",
                token = session.token,
                extraHeaders = mapOf("X-PocketBase-Url" to session.baseUrl),
                authPrefix = "Bearer ",
            )
        }
    }

    private fun fetchLatestConfig(session: WorkflowSession): FileConversionConfig? {
        val filter = "owner=\"${workflowRepository.quoteFilter(session.userId)}\" && conversionType=\"$PDF_CONVERSION_TYPE\""
        val root = workflowRepository.executeJson(
            baseUrl = session.baseUrl,
            path = "/api/collections/$CONVERSION_CONFIG_COLLECTION/records",
            method = "GET",
            query = mapOf(
                "page" to "1",
                "perPage" to "20",
                "sort" to "-updated,-id",
                "filter" to filter,
            ),
            token = session.token,
        ).jsonObject
        val page = workflowRepository.parseListPage(root, 1, 20) {
            parseConfig(it, session.userId)
        }
        return page.items.firstOrNull()
    }

    private fun savePdfConversionConfigInternal(
        session: WorkflowSession,
        config: FileConversionConfig,
        options: PdfConversionOptions,
        systemPrompt: String,
    ): FileConversionConfig {
        validateOptions(options)
        val trimmedPrompt = systemPrompt.trim()
        if (trimmedPrompt.length > MAX_CONVERSION_PROMPT_CHARS) {
            throw WorkflowApiException("系统提示词不能超过 $MAX_CONVERSION_PROMPT_CHARS 个字符", 400)
        }
        val finalPrompt = trimmedPrompt.ifEmpty { DEFAULT_PDF_SYSTEM_PROMPT }
        val body = buildJsonObject {
            put("options", encodeOptions(options))
            put("systemPrompt", finalPrompt)
        }
        val root = workflowRepository.executeJson(
            baseUrl = session.baseUrl,
            path = "/api/collections/$CONVERSION_CONFIG_COLLECTION/records/${encodeSegment(config.id)}",
            method = "PATCH",
            body = body,
            token = session.token,
        ).jsonObject
        return parseConfig(root, session.userId)
    }

    private fun validateOptions(options: PdfConversionOptions) {
        if (options.model.isBlank()) throw WorkflowApiException("请填写主模型 ID", 400)
        if (options.svgEnabled && options.svgModel.isBlank()) throw WorkflowApiException("请填写 SVG 重绘模型 ID", 400)
        if (options.svgPrompt.trim().length > MAX_CONVERSION_PROMPT_CHARS) {
            throw WorkflowApiException("SVG 提示词不能超过 $MAX_CONVERSION_PROMPT_CHARS 个字符", 400)
        }
        if (options.imagesPerRequest !in 1..20) throw WorkflowApiException("每次整理页数需在 1–20 之间", 400)
        if (options.concurrency !in 1..100) throw WorkflowApiException("并发请求数需在 1–100 之间", 400)
        if (options.dpi !in 72..300) throw WorkflowApiException("渲染 DPI 需在 72–300 之间", 400)
        if (options.jpegQuality !in 50..100) throw WorkflowApiException("JPEG 质量需在 50–100 之间", 400)
        if (options.verificationPasses !in 0..3) throw WorkflowApiException("额外审校次数需在 0–3 之间", 400)
        if (options.rpmPerKey !in 1..1000) throw WorkflowApiException("RPM 需在 1–1000 之间", 400)
        if (options.rpdPerKey !in 1..100000) throw WorkflowApiException("RPD 需在 1–100000 之间", 400)
        if (options.maxOutputTokens !in 0..200000) throw WorkflowApiException("输出 Token 上限需在 0–200000 之间", 400)
    }

    private fun encodeOptions(options: PdfConversionOptions): JsonObject = buildJsonObject {
        put("images_per_request", options.imagesPerRequest)
        put("concurrency", options.concurrency)
        put("provider", options.provider.wireValue)
        put("model", options.model.trim())
        put("reasoning_effort", options.reasoningEffort)
        put("max_output_tokens", options.maxOutputTokens)
        put("thinking_level", options.thinkingLevel)
        put("dpi", options.dpi)
        put("image_format", options.imageFormat)
        put("jpeg_quality", options.jpegQuality)
        put("verification_passes", options.verificationPasses)
        put("media_resolution", options.mediaResolution)
        put("rpm_per_key", options.rpmPerKey)
        put("rpd_per_key", options.rpdPerKey)
        put("svg_enabled", options.svgEnabled)
        put("svg_provider", options.svgProvider.wireValue)
        put("svg_model", options.svgModel.trim())
        put("svg_prompt", options.svgPrompt.trim().ifEmpty { DEFAULT_PDF_SVG_PROMPT })
    }

    private fun parseConfig(obj: JsonObject, expectedOwner: String): FileConversionConfig = with(workflowRepository) {
        val owner = obj.str("owner")
        if (owner != expectedOwner) throw WorkflowApiException("转换配置归属无效", 502)
        val optObj = obj["options"] as? JsonObject
        val options = parseOptions(optObj)
        FileConversionConfig(
            id = obj.str("id"),
            owner = owner,
            conversionType = obj.str("conversionType").ifBlank { PDF_CONVERSION_TYPE },
            name = obj.str("name").ifBlank { "默认配置" },
            systemPrompt = obj.str("systemPrompt"),
            options = options,
            revision = obj.int("revision").coerceAtLeast(1),
            created = obj.str("created"),
            updated = obj.str("updated"),
        )
    }

    private fun parseOptions(obj: JsonObject?): PdfConversionOptions {
        if (obj == null) return PdfConversionOptions()
        return PdfConversionOptions(
            imagesPerRequest = obj["images_per_request"]?.jsonPrimitive?.intOrNull ?: 1,
            concurrency = obj["concurrency"]?.jsonPrimitive?.intOrNull ?: 5,
            provider = PdfConversionProvider.fromWire(obj["provider"]?.jsonPrimitive?.contentOrNull.orEmpty()),
            model = obj["model"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "gemini-3.5-flash-lite",
            reasoningEffort = obj["reasoning_effort"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            maxOutputTokens = obj["max_output_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            thinkingLevel = obj["thinking_level"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "high",
            dpi = obj["dpi"]?.jsonPrimitive?.intOrNull ?: 240,
            imageFormat = obj["image_format"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "png",
            jpegQuality = obj["jpeg_quality"]?.jsonPrimitive?.intOrNull ?: 95,
            verificationPasses = obj["verification_passes"]?.jsonPrimitive?.intOrNull ?: 0,
            mediaResolution = obj["media_resolution"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "ultra_high",
            rpmPerKey = obj["rpm_per_key"]?.jsonPrimitive?.intOrNull ?: 15,
            rpdPerKey = obj["rpd_per_key"]?.jsonPrimitive?.intOrNull ?: 500,
            svgEnabled = obj["svg_enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
            svgProvider = PdfConversionProvider.fromWire(obj["svg_provider"]?.jsonPrimitive?.contentOrNull.orEmpty()),
            svgModel = obj["svg_model"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "gemini-3.5-flash-lite",
            svgPrompt = obj["svg_prompt"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: DEFAULT_PDF_SVG_PROMPT,
        )
    }

    private fun parseJob(obj: JsonObject, expectedOwner: String): PdfJob = with(workflowRepository) {
        val owner = obj.str("owner")
        if (owner != expectedOwner) throw WorkflowApiException("转换任务归属无效", 502)
        val optObj = obj["options"] as? JsonObject
        val startPage = obj["startPage"]?.jsonPrimitive?.intOrNull
            ?: optObj?.get("start_page")?.jsonPrimitive?.intOrNull
        val endPage = obj["endPage"]?.jsonPrimitive?.intOrNull
            ?: optObj?.get("end_page")?.jsonPrimitive?.intOrNull
        val adjacent = obj["adjacentPages"]?.jsonPrimitive?.booleanOrNull
            ?: optObj?.get("adjacent_pages")?.jsonPrimitive?.booleanOrNull
            ?: false
        PdfJob(
            id = obj.str("id"),
            owner = owner,
            title = obj.str("title"),
            sourceUrl = obj.str("sourceUrl"),
            outputName = obj.str("outputName"),
            prompt = obj.str("prompt"),
            configId = obj.str("configId"),
            configRevision = obj.int("configRevision").coerceAtLeast(1),
            systemPrompt = obj.str("systemPrompt"),
            status = PdfJobStatus.fromWire(obj.str("status")),
            file = obj.str("file"),
            fileSize = obj["fileSize"]?.jsonPrimitive?.longOrNull ?: 0L,
            githubRunId = obj.str("githubRunId"),
            error = obj.str("error"),
            created = obj.str("created"),
            updated = obj.str("updated"),
            startedAt = obj.str("startedAt"),
            finishedAt = obj.str("finishedAt"),
            cancelRequested = obj.bool("cancelRequested"),
            rerunOf = obj.str("rerunOf"),
            startPage = startPage,
            endPage = endPage,
            adjacentPages = adjacent,
            modelSummary = optObj?.get("model")?.jsonPrimitive?.contentOrNull.orEmpty(),
            dpiSummary = optObj?.get("dpi")?.jsonPrimitive?.intOrNull,
        )
    }

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20")
}
