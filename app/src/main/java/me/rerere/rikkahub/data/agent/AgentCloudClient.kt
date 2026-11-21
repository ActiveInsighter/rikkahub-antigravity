package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.ModelSnapshot
import me.rerere.ai.ui.ReasoningType
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.workflow.DEFAULT_REMOTE_API_URL
import me.rerere.rikkahub.data.workflow.WorkflowApiException
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowSession
import me.rerere.rikkahub.utils.JsonInstant
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val GEMINI_API = "/api/anyworkflow/gemini"
internal const val JOBS_COLLECTION = "aw_gemini_jobs"
internal const val MESSAGES_COLLECTION = "aw_gemini_messages"
private const val RECORD_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

private const val FOLDERS_COLLECTION = "aw_agent_folders"
private const val USAGE_COLLECTION = "aw_agent_usage_records"

data class RemoteAgentThread(
    val jobId: String,
    val threadRoot: String,
    val title: String,
    val status: String,
    val created: String,
    val updated: String,
    val parentMessage: String,
    val archived: Boolean,
    val pinned: Boolean,
    val folderId: String? = null,
    val hasFolderIdField: Boolean = false,
)

data class RemoteAgentFolder(
    val id: String,
    val clientId: String,
    val name: String,
    val sortOrder: Int = 0,
    val created: String = "",
    val updated: String = "",
)

data class RemoteAgentUsageRecord(
    val id: String = "",
    val clientId: String,
    val conversationId: String? = null,
    val assistantId: String? = null,
    val modelId: String? = null,
    val modelName: String? = null,
    val providerId: String? = null,
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val cachedTokens: Long = 0L,
    val reasoningTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val durationMs: Long = 0L,
    val source: String = "agent",
    val createdAt: Long = 0L,
)

data class RemoteAgentConfig(
    val agentModel: String = "",
    val model: String = "Antigravity",
    val systemPrompt: String = "",
    val delimiter: String = "<!-- aw:split -->",
    val concurrency: Int = 16,
    val thinkingLevel: String = "",
    val maxOutputTokens: Int = 8192,
)

data class RemoteGeminiJob(
    val id: String,
    val owner: String,
    val threadRoot: String,
    val parentMessage: String,
    val replacesMessage: String,
    val title: String,
    val prompt: String,
    val model: String,
    val status: String,
    val error: String,
    val cancelRequested: Boolean,
    val githubRunId: String,
    val created: String,
    val updated: String,
    val sources: List<RemoteSourceRef> = emptyList(),
    val history: List<RemoteHistoryTurn> = emptyList(),
    val images: List<String> = emptyList(),
)

data class RemoteSourceRef(
    val id: String,
    val name: String,
)

data class RemoteHistoryTurn(
    val role: String,
    val text: String,
)

data class RemoteGeminiMessage(
    val id: String,
    val owner: String,
    val job: String,
    val index: Int,
    val part: Int,
    val sourceName: String,
    val sourceId: String,
    val inputText: String,
    val status: String,
    val reply: String,
    val error: String,
    val finishReason: String,
    val startedAt: String,
    val finishedAt: String,
    val updated: String,
    val streamRevision: Int?,
    val usage: RemoteGeminiUsage,
    val agentSummaryJson: String? = null,
    val agentTimingsJson: String? = null,
)

data class RemoteGeminiUsage(
    val totalTokenCount: Int = 0,
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val thoughtsTokenCount: Int = 0,
    val cachedContentTokenCount: Int = 0,
    val toolUseTokenCount: Int = 0,
    val agentStepCount: Int = 0,
) {
    fun toTokenUsage(): TokenUsage? {
        if (totalTokenCount <= 0 && promptTokenCount <= 0 && candidatesTokenCount <= 0) return null
        return TokenUsage(
            promptTokens = promptTokenCount,
            completionTokens = candidatesTokenCount,
            cachedTokens = cachedContentTokenCount,
            totalTokens = totalTokenCount.takeIf { it > 0 } ?: (promptTokenCount + candidatesTokenCount),
        )
    }
}

data class RemoteAgentPart(
    val id: String,
    val message: String,
    val key: String,
    val sequence: Int,
    val revision: Int,
    val type: String,
    val status: String,
    val content: String,
    val toolName: String,
    val callId: String,
    val providerType: String,
    val truncated: Boolean,
)

data class RemoteAgentSourceInput(
    val id: String,
    val name: String,
    val content: String,
)

data class RemoteSubmittedJob(
    val jobId: String,
    val threadRoot: String,
    val status: String,
)

data class RemoteThreadConversation(
    val job: RemoteGeminiJob,
    val message: RemoteGeminiMessage?,
    val messageNodes: List<MessageNode>,
    val isActive: Boolean,
    val parts: List<RemoteAgentPart>,
    val partsCursor: Int,
)

interface AgentCloudClient {
    fun hasSession(): Boolean

    suspend fun listRemoteThreads(threadRoot: String? = null): List<RemoteAgentThread>

    suspend fun fetchThreadConversation(
        latestJobId: String,
        previous: RemoteThreadConversation? = null,
        refreshJob: Boolean = true,
    ): RemoteThreadConversation

    suspend fun submitMessage(
        prompt: String,
        sources: List<RemoteAgentSourceInput> = emptyList(),
        images: List<String> = emptyList(),
        parentMessageId: String? = null,
        replacesMessageId: String? = null,
        title: String = "",
        modelOverride: String? = null,
        thinkingLevelOverride: String? = null,
        systemPromptOverride: String? = null,
    ): RemoteSubmittedJob

    suspend fun cancelJob(jobId: String)

    suspend fun updateThreadMetadata(
        jobId: String,
        title: String? = null,
        pinned: Boolean? = null,
        archived: Boolean? = null,
        folderId: String? = null,
    )

    suspend fun deleteThread(jobId: String)

    suspend fun listRemoteFolders(): List<RemoteAgentFolder> = emptyList()

    suspend fun upsertRemoteFolder(folder: RemoteAgentFolder): RemoteAgentFolder? = null

    suspend fun deleteRemoteFolder(remoteId: String) {}

    suspend fun listSchedules(): List<AgentSchedule> = emptyList()

    suspend fun createSchedule(schedule: AgentSchedule): AgentSchedule = schedule

    suspend fun updateSchedule(schedule: AgentSchedule): AgentSchedule = schedule

    suspend fun deleteSchedule(id: String) {}

    suspend fun triggerSchedule(id: String): RemoteSubmittedJob? = null

    suspend fun listRemoteUsageRecords(): List<RemoteAgentUsageRecord> = emptyList()

    suspend fun uploadUsageRecord(record: RemoteAgentUsageRecord): RemoteAgentUsageRecord? = null

    fun buildAssistantUIMessage(
        messageId: Uuid,
        job: RemoteGeminiJob,
        message: RemoteGeminiMessage?,
        parts: List<RemoteAgentPart>,
    ): UIMessage
}

class PocketBaseAgentCloudClient(
    private val workflowRepository: WorkflowRepository,
) : AgentCloudClient {
    private val random = SecureRandom()

    override fun hasSession(): Boolean = workflowRepository.session.value != null

    override suspend fun listRemoteThreads(threadRoot: String?): List<RemoteAgentThread> = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        val owner = workflowRepository.quoteFilter(session.userId)
        val filter = if (threadRoot == null) {
            "owner=\"$owner\""
        } else {
            val root = workflowRepository.quoteFilter(threadRoot)
            "owner=\"$owner\" && (threadRoot=\"$root\" || id=\"$root\")"
        }
        val latestByRoot = LinkedHashMap<String, RemoteAgentThread>()
        var page = 1
        do {
            val response = workflowRepository.executeJsonCancellable(
                baseUrl = session.baseUrl,
                path = "/api/collections/$JOBS_COLLECTION/records",
                method = "GET",
                query = mapOf(
                    "page" to page.toString(),
                    "perPage" to if (threadRoot == null) "100" else "1",
                    "sort" to "-created,-id",
                    "filter" to filter,
                    "fields" to "id,title,status,created,updated,parentMessage,archived,threadRoot,pinned,folder_id",
                ),
                token = session.token,
            ).jsonObject
            val totalPages = response["totalPages"]?.jsonPrimitive?.intOrNull ?: 1
            for (item in response["items"]?.jsonArray.orEmpty()) {
                val obj = item.jsonObject
                val id = obj.str("id").ifBlank { continue }
                val root = obj.str("threadRoot").ifBlank { id }
                if (root in latestByRoot) continue
                latestByRoot[root] = RemoteAgentThread(
                    jobId = id,
                    threadRoot = root,
                    title = obj.str("title"),
                    status = obj.str("status"),
                    created = obj.str("created"),
                    updated = obj.str("updated"),
                    parentMessage = obj.str("parentMessage"),
                    archived = obj["archived"]?.jsonPrimitive?.booleanOrNull ?: false,
                    pinned = obj["pinned"]?.jsonPrimitive?.booleanOrNull ?: false,
                    folderId = obj.str("folder_id").trim().ifBlank { null },
                    hasFolderIdField = obj.containsKey("folder_id"),
                )
            }
            page++
        } while (threadRoot == null && page <= totalPages)

        latestByRoot.values.filterNot { it.archived }
    }

    override suspend fun fetchThreadConversation(
        latestJobId: String,
        previous: RemoteThreadConversation?,
        refreshJob: Boolean,
    ): RemoteThreadConversation = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        val previousMessage = previous?.message
        val useCachedJob = !refreshJob && previous != null && previousMessage != null
        val (snapshotJob, messageIds) = if (useCachedJob && previous != null && previousMessage != null) {
            previous.job to listOf(previousMessage.id)
        } else {
            fetchJobView(session, latestJobId)
        }
        val message = messageIds.firstOrNull()?.let { fetchMessage(session, latestJobId, it) }
        val job = if (useCachedJob && message != null && isActiveStatus(snapshotJob.status) &&
            isActiveStatus(previousMessage?.status.orEmpty()) && !isActiveStatus(message.status)
        ) {
            fetchJobView(session, latestJobId).first
        } else {
            snapshotJob
        }
        val cached = previous?.takeIf { it.job.id == job.id && it.message?.id == message?.id }
        var cursor = cached?.partsCursor ?: 0
        var parts = cached?.parts.orEmpty()
        if (message != null && (cached == null || message.streamRevision == null || message.streamRevision > cursor)) {
            val (newParts, nextCursor) = drainParts(session, job.id, message.id, startCursor = cursor)
            val partsByKey = parts.associateByTo(LinkedHashMap()) { it.key }
            for (part in newParts) {
                if (part.revision > (partsByKey[part.key]?.revision ?: -1)) {
                    partsByKey[part.key] = part
                }
            }
            parts = partsByKey.values.sortedBy { it.sequence }
            cursor = nextCursor
        }

        val nodes = if (cached != null) {
            val assistantNode = cached.messageNodes.last()
            cached.messageNodes.dropLast(1) + assistantNode.copy(
                messages = listOf(buildAssistantUIMessage(assistantNode.currentMessage.id, job, message, parts)),
            )
        } else {
            val ancestors = fetchAncestors(session, job)
            val ancestorParts = ancestors.mapNotNull { (ancestorJob, ancestor) ->
                if (ancestor.streamRevision == 0) null else {
                    ancestor.id to drainParts(session, ancestorJob.id, ancestor.id, startCursor = 0).first
                }
            }.toMap()
            buildConversationNodes(job, message, parts, ancestors, ancestorParts)
        }
        RemoteThreadConversation(
            job = job,
            message = message,
            messageNodes = nodes,
            isActive = isActiveStatus(job.status) || message?.status?.let(::isActiveStatus) == true,
            parts = parts,
            partsCursor = cursor,
        )
    }

    override suspend fun submitMessage(
        prompt: String,
        sources: List<RemoteAgentSourceInput>,
        images: List<String>,
        parentMessageId: String?,
        replacesMessageId: String?,
        title: String,
        modelOverride: String?,
        thinkingLevelOverride: String?,
        systemPromptOverride: String?,
    ): RemoteSubmittedJob = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        val config = fetchConfigBestEffort(session)
        val jobId = newRecordId()
        val payload = buildSubmitJobPayload(
            jobId = jobId,
            config = config,
            prompt = prompt,
            sources = sources,
            images = images,
            parentMessageId = parentMessageId,
            replacesMessageId = replacesMessageId,
            title = title,
            modelOverride = modelOverride,
            thinkingLevelOverride = thinkingLevelOverride,
            systemPromptOverride = systemPromptOverride,
        )

        val created = workflowRepository.executeJsonCancellable(
            baseUrl = session.baseUrl,
            path = "$GEMINI_API/jobs",
            method = "POST",
            body = payload,
            token = session.token,
        ).jsonObject

        wakeConverterBestEffort(session)

        val createdId = created.str("id").ifBlank { jobId }
        val threadRoot = created.str("threadRoot").ifBlank { createdId }
        val status = created.str("status").ifBlank { "queued" }
        RemoteSubmittedJob(
            jobId = createdId,
            threadRoot = threadRoot,
            status = status,
        )
    }

    override suspend fun cancelJob(jobId: String) = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        workflowRepository.executeJsonCancellable(
            baseUrl = session.baseUrl,
            path = "$GEMINI_API/jobs/$jobId/cancel",
            method = "POST",
            body = buildJsonObject {},
            token = session.token,
        )
        wakeConverterBestEffort(session)
    }

    override suspend fun updateThreadMetadata(
        jobId: String,
        title: String?,
        pinned: Boolean?,
        archived: Boolean?,
        folderId: String?,
    ) {
        withContext(Dispatchers.IO) {
            val session = workflowRepository.requireSession()
            val body = buildJsonObject {
                if (title != null) put("title", title)
                if (pinned != null) put("pinned", pinned)
                if (archived != null) put("archived", archived)
                if (folderId != null) put("folderId", folderId)
            }
            workflowRepository.executeJsonCancellable(
                baseUrl = session.baseUrl,
                path = "$GEMINI_API/jobs/$jobId/metadata",
                method = "PATCH",
                body = body,
                token = session.token,
            )
        }
    }

    override suspend fun deleteThread(jobId: String) {
        withContext(Dispatchers.IO) {
            val session = workflowRepository.requireSession()
            workflowRepository.executeJsonCancellable(
                baseUrl = session.baseUrl,
                path = "$GEMINI_API/threads/$jobId",
                method = "DELETE",
                token = session.token,
            )
        }
    }

    override suspend fun listRemoteFolders(): List<RemoteAgentFolder> = withContext(Dispatchers.IO) {
        val session = workflowRepository.session.value ?: return@withContext emptyList()
        runCatching {
            val res = workflowRepository.executeJsonCancellable(
                baseUrl = session.baseUrl,
                path = "/api/collections/$FOLDERS_COLLECTION/records?perPage=200&sort=sort_order,created",
                method = "GET",
                token = session.token,
            ).jsonObject
            res["items"]?.jsonArray?.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj.str("id").ifBlank { return@mapNotNull null }
                RemoteAgentFolder(
                    id = id,
                    clientId = obj.str("client_id").ifBlank { id },
                    name = obj.str("name"),
                    sortOrder = obj["sort_order"]?.jsonPrimitive?.intOrNull ?: 0,
                    created = obj.str("created"),
                    updated = obj.str("updated"),
                )
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    override suspend fun upsertRemoteFolder(folder: RemoteAgentFolder): RemoteAgentFolder? = withContext(Dispatchers.IO) {
        val session = workflowRepository.session.value ?: return@withContext null
        runCatching {
            val body = buildJsonObject {
                put("owner", session.userId)
                put("client_id", folder.clientId)
                put("name", folder.name)
                put("sort_order", folder.sortOrder)
            }
            val obj = if (folder.id.isNotBlank()) {
                workflowRepository.executeJsonCancellable(
                    baseUrl = session.baseUrl,
                    path = "/api/collections/$FOLDERS_COLLECTION/records/${folder.id}",
                    method = "PATCH",
                    body = body,
                    token = session.token,
                ).jsonObject
            } else {
                workflowRepository.executeJsonCancellable(
                    baseUrl = session.baseUrl,
                    path = "/api/collections/$FOLDERS_COLLECTION/records",
                    method = "POST",
                    body = body,
                    token = session.token,
                ).jsonObject
            }
            val id = obj.str("id").ifBlank { return@runCatching null }
            RemoteAgentFolder(
                id = id,
                clientId = obj.str("client_id").ifBlank { folder.clientId },
                name = obj.str("name").ifBlank { folder.name },
                sortOrder = obj["sort_order"]?.jsonPrimitive?.intOrNull ?: folder.sortOrder,
                created = obj.str("created"),
                updated = obj.str("updated"),
            )
        }.getOrNull()
    }

    override suspend fun deleteRemoteFolder(remoteId: String) {
        withContext(Dispatchers.IO) {
            val session = workflowRepository.session.value ?: return@withContext
            if (remoteId.isBlank()) return@withContext
            runCatching {
                workflowRepository.executeJsonCancellable(
                    baseUrl = session.baseUrl,
                    path = "/api/collections/$FOLDERS_COLLECTION/records/$remoteId",
                    method = "DELETE",
                    token = session.token,
                )
            }
        }
    }

    override suspend fun listRemoteUsageRecords(): List<RemoteAgentUsageRecord> = withContext(Dispatchers.IO) {
        val session = workflowRepository.session.value ?: return@withContext emptyList()
        runCatching {
            val res = workflowRepository.executeJsonCancellable(
                baseUrl = session.baseUrl,
                path = "/api/collections/$USAGE_COLLECTION/records?perPage=200&sort=-created_at_ms",
                method = "GET",
                token = session.token,
            ).jsonObject
            res["items"]?.jsonArray?.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj.str("id").ifBlank { return@mapNotNull null }
                RemoteAgentUsageRecord(
                    id = id,
                    clientId = obj.str("client_id").ifBlank { id },
                    conversationId = obj.str("conversation_id").ifBlank { null },
                    assistantId = obj.str("assistant_id").ifBlank { null },
                    modelId = obj.str("model_id").ifBlank { null },
                    modelName = obj.str("model_name").ifBlank { null },
                    providerId = obj.str("provider_id").ifBlank { null },
                    promptTokens = obj["prompt_tokens"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                    completionTokens = obj["completion_tokens"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                    cachedTokens = obj["cached_tokens"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                    reasoningTokens = obj["reasoning_tokens"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                    totalTokens = obj["total_tokens"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                    durationMs = obj["duration_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                    source = obj.str("source").ifBlank { "agent" },
                    createdAt = obj["created_at_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                )
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    override suspend fun uploadUsageRecord(record: RemoteAgentUsageRecord): RemoteAgentUsageRecord? = withContext(Dispatchers.IO) {
        val session = workflowRepository.session.value ?: return@withContext null
        runCatching {
            val body = buildJsonObject {
                put("owner", session.userId)
                put("client_id", record.clientId)
                record.conversationId?.let { put("conversation_id", it) }
                record.assistantId?.let { put("assistant_id", it) }
                record.modelId?.let { put("model_id", it) }
                record.modelName?.let { put("model_name", it) }
                record.providerId?.let { put("provider_id", it) }
                put("prompt_tokens", record.promptTokens)
                put("completion_tokens", record.completionTokens)
                put("cached_tokens", record.cachedTokens)
                put("reasoning_tokens", record.reasoningTokens)
                put("total_tokens", record.totalTokens)
                put("duration_ms", record.durationMs)
                put("source", record.source)
                put("created_at_ms", record.createdAt)
            }
            val obj = workflowRepository.executeJsonCancellable(
                baseUrl = session.baseUrl,
                path = "/api/collections/$USAGE_COLLECTION/records",
                method = "POST",
                body = body,
                token = session.token,
            ).jsonObject
            val id = obj.str("id").ifBlank { return@runCatching null }
            record.copy(id = id)
        }.getOrNull()
    }

    override fun buildAssistantUIMessage(
        messageId: Uuid,
        job: RemoteGeminiJob,
        message: RemoteGeminiMessage?,
        parts: List<RemoteAgentPart>,
    ): UIMessage {
        val uiParts = mutableListOf<UIMessagePart>()
        val annotations = mutableListOf<UIMessageAnnotation>()

        val toolResultsByCallId = LinkedHashMap<String, RemoteAgentPart>()
        val consumedResultKeys = HashSet<String>()
        for (part in parts) {
            if (part.type == "tool_result" && part.callId.isNotBlank()) {
                toolResultsByCallId.putIfAbsent(part.callId, part)
            }
        }

        val isHistorical = message != null && message.job.isNotBlank() && message.job != job.id
        val isRunning = if (isHistorical) {
            message?.status?.let(::isActiveStatus) == true
        } else {
            isActiveStatus(job.status) || (message != null && isActiveStatus(message.status))
        }
        val startMs = parseIsoMillis(message?.startedAt.orEmpty())
        val endMs = parseIsoMillis(message?.finishedAt.orEmpty())
        val baseReasoningMs = startMs.takeIf { it > 0L }
            ?: parseIsoMillis(job.created).takeIf { it > 0L }
            ?: 1L
        val messageFinishedInstant = endMs.takeIf { it > 0L }?.let { kotlin.time.Instant.fromEpochMilliseconds(it) }
        val messageUpdatedInstant = parseIsoMillis(message?.updated.orEmpty())
            .takeIf { it > 0L }
            ?.let { kotlin.time.Instant.fromEpochMilliseconds(it) }

        var hasTextPart = false
        for (part in parts) {
            if (part.type == "status") continue
            if (part.type == "tool_result" && part.key in consumedResultKeys) continue

            when (part.type) {
                "reasoning_summary" -> {
                    if (part.content.isNotBlank()) {
                        uiParts += UIMessagePart.Reasoning(
                            reasoning = part.content,
                            reasoningType = ReasoningType.SUMMARY_TEXT,
                            createdAt = kotlin.time.Instant.fromEpochMilliseconds(baseReasoningMs + part.sequence.coerceAtLeast(0)),
                            finishedAt = if (part.status == "running") {
                                null
                            } else {
                                messageFinishedInstant
                                    ?: messageUpdatedInstant
                                    ?: kotlin.time.Instant.fromEpochMilliseconds(baseReasoningMs + part.sequence.coerceAtLeast(0) + 1000L)
                            },
                        )
                    }
                }
                "text" -> {
                    val text = buildString {
                        append(part.content)
                        if (part.truncated) append("\n\n[内容过长已截断]")
                    }
                    if (text.isNotBlank()) {
                        hasTextPart = true
                        uiParts += UIMessagePart.Text(text = text)
                    }
                }
                "tool_call" -> {
                    val pairedResult = if (part.callId.isNotBlank()) {
                        toolResultsByCallId[part.callId]?.also { consumedResultKeys += it.key }
                    } else null
                    val status = when {
                        part.status == "failed" || pairedResult?.status == "failed" -> ServerToolStatus.FAILED
                        pairedResult != null || part.status == "completed" -> ServerToolStatus.COMPLETED
                        else -> ServerToolStatus.IN_PROGRESS
                    }
                    uiParts += UIMessagePart.ServerTool(
                        toolCallId = part.callId.ifBlank { part.key },
                        toolName = part.toolName.ifBlank { part.providerType.ifBlank { "tool" } },
                        input = parseJsonOrPrimitive(part.content),
                        output = pairedResult?.let { parseJsonOrPrimitive(it.content) },
                        status = status,
                    )
                }
                "tool_result" -> {
                    val status = if (part.status == "failed") ServerToolStatus.FAILED else ServerToolStatus.COMPLETED
                    uiParts += UIMessagePart.ServerTool(
                        toolCallId = part.callId.ifBlank { part.key },
                        toolName = part.toolName.ifBlank { part.providerType.ifBlank { "tool_result" } },
                        input = null,
                        output = parseJsonOrPrimitive(part.content),
                        status = status,
                    )
                }
                "annotations" -> {
                    parseAnnotations(part.content, annotations)
                }
                "image" -> {
                    val uri = runCatching {
                        JsonInstant.parseToJsonElement(part.content).jsonObject.str("uri")
                    }.getOrDefault("")
                    if (uri.isNotBlank()) {
                        uiParts += UIMessagePart.Image(url = uri)
                    }
                }
            }
        }

        val reply = message?.reply.orEmpty()
        if (reply.isNotBlank()) {
            val streamedText = uiParts.filterIsInstance<UIMessagePart.Text>().joinToString("\n\n") { it.text }
            if (!hasTextPart || (!isRunning && streamedText.trim() != reply.trim())) {
                uiParts.removeAll { it is UIMessagePart.Text }
                uiParts += UIMessagePart.Text(text = reply)
            }
        }

        // If an in-place updated text part has an earlier sequence than later tool/reasoning steps,
        // move the final reply text after those steps so steps never appear after the final reply.
        val lastTextIndex = uiParts.indexOfLast { it is UIMessagePart.Text && it.text.isNotBlank() }
        if (lastTextIndex != -1 && lastTextIndex < uiParts.lastIndex) {
            val hasTrailingSteps = uiParts.subList(lastTextIndex + 1, uiParts.size)
                .any { it is UIMessagePart.Reasoning || it is UIMessagePart.ServerTool }
            if (hasTrailingSteps && (!isRunning || (message != null && message.reply.isNotBlank()))) {
                val finalText = uiParts.removeAt(lastTextIndex)
                val insertIndex = uiParts.indexOfLast { it is UIMessagePart.Reasoning || it is UIMessagePart.ServerTool } + 1
                uiParts.add(insertIndex, finalText)
            }
        }

        val canceled = isCanceledStatus(message?.status) || (!isHistorical && isCanceledStatus(job.status))
        if (!canceled && message != null && message.status != "succeeded" && message.error.isNotBlank()) {
            uiParts += UIMessagePart.Notice(
                title = "执行出错",
                text = message.error,
            )
        } else if (!canceled && !isHistorical && message?.status != "succeeded" && job.error.isNotBlank()) {
            uiParts += UIMessagePart.Notice(
                title = "任务异常",
                text = job.error,
            )
        }

        if (uiParts.isEmpty() && isRunning) {
            uiParts += UIMessagePart.Text(text = "")
        }

        val durationMs = if (startMs > 0L && endMs >= startMs) endMs - startMs else null
        val finishedAt = if (!isRunning) {
            (messageFinishedInstant ?: messageUpdatedInstant ?: kotlin.time.Instant.fromEpochMilliseconds(parseIsoMillis(job.updated)))
                .toLocalDateTime(TimeZone.currentSystemDefault())
        } else null

        val usage = message?.usage?.toTokenUsage()
        val stepCount = message?.usage?.agentStepCount?.takeIf { it > 0 }

        return UIMessage(
            id = messageId,
            role = MessageRole.ASSISTANT,
            parts = uiParts,
            annotations = annotations,
            createdAt = parseLocalDateTime(if (isHistorical) message?.startedAt?.ifBlank { job.created } ?: job.created else job.created),
            finishedAt = finishedAt,
            modelSnapshot = ModelSnapshot(
                displayName = "Antigravity Agent",
                modelId = job.model.ifBlank { "Antigravity" },
            ),
            usage = usage,
            generationTimeMs = durationMs,
            stepCount = stepCount,
            agentSummaryJson = message?.agentSummaryJson,
            agentTimingsJson = message?.agentTimingsJson,
        )
    }

    private fun buildConversationNodes(
        job: RemoteGeminiJob,
        currentMessage: RemoteGeminiMessage?,
        currentParts: List<RemoteAgentPart>,
        ancestors: List<Pair<RemoteGeminiJob, RemoteGeminiMessage>>,
        ancestorParts: Map<String, List<RemoteAgentPart>>,
    ): List<MessageNode> = buildList {
        // Prompt history can omit empty model replies. Its user entries still identify turns
        // for legacy history whose parent records are unavailable.
        val firstLoadedTurn = job.history.indices
            .filter { job.history[it].role == "user" }
            .takeLast(ancestors.size)
            .firstOrNull() ?: job.history.size
        job.history.take(firstLoadedTurn).forEachIndexed { index, turn ->
            val user = turn.role == "user"
            val id = "${job.id}-h-$index"
            val (text, images) = extractImageUrlsAndStripMarker(turn.text)
            val message = UIMessage(
                id = stableUuid(if (user) "aw-user:$id" else "aw-asst:$id"),
                role = if (user) MessageRole.USER else MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text(text)) + images.map { UIMessagePart.Image(url = it) },
                createdAt = parseLocalDateTime(job.created),
                modelSnapshot = if (user) null else ModelSnapshot(
                    displayName = "Antigravity Agent",
                    modelId = job.model.ifBlank { "Antigravity" },
                ),
            )
            add(MessageNode(
                id = stableUuid(if (user) "aw-node-u:$id" else "aw-node-a:$id"),
                messages = listOf(message),
                selectIndex = 0,
            ))
        }
        for ((ancestorJob, ancestor) in ancestors) {
            addAll(buildTurnNodes(ancestorJob, ancestor, ancestorParts[ancestor.id].orEmpty()))
        }
        addAll(buildTurnNodes(job, currentMessage, currentParts))
    }

    private fun buildTurnNodes(
        job: RemoteGeminiJob,
        currentMessage: RemoteGeminiMessage?,
        currentParts: List<RemoteAgentPart>,
    ): List<MessageNode> {
        val currentId = currentMessage?.id ?: job.id
        val currentUserParts = mutableListOf<UIMessagePart>()
        val source = job.sources.firstOrNull { it.id == currentMessage?.sourceId || it.name == currentMessage?.sourceName }
        val userPromptText = if (source != null) {
            job.prompt
        } else {
            listOf(job.prompt, currentMessage?.inputText.orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n")
        }
        val (cleanCurrentPrompt, currentImages) = extractImageUrlsAndStripMarker(userPromptText, job.images)
        if (cleanCurrentPrompt.isNotBlank() || (source == null && currentImages.isEmpty())) {
            currentUserParts += UIMessagePart.Text(cleanCurrentPrompt)
        }
        currentImages.forEach { url ->
            currentUserParts += UIMessagePart.Image(url = url)
        }
        if (source != null) {
            currentUserParts += UIMessagePart.Document(
                url = source.id,
                fileName = source.name.ifBlank { "document.md" },
                mime = "text/markdown",
            )
        }
        val currentUserMsg = UIMessage(
            id = stableUuid("aw-user:$currentId"),
            role = MessageRole.USER,
            parts = currentUserParts,
            createdAt = parseLocalDateTime(job.created),
        )

        val currentAssistantMsg = buildAssistantUIMessage(
            messageId = stableUuid("aw-asst:$currentId"),
            job = job,
            message = currentMessage,
            parts = currentParts,
        )
        return listOf(
            MessageNode(id = stableUuid("aw-node-u:$currentId"), messages = listOf(currentUserMsg), selectIndex = 0),
            MessageNode(id = stableUuid("aw-node-a:$currentId"), messages = listOf(currentAssistantMsg), selectIndex = 0),
        )
    }

    private suspend fun fetchConfigBestEffort(session: WorkflowSession): RemoteAgentConfig {
        return runCatching {
            val root = workflowRepository.executeJsonCancellable(
                baseUrl = session.baseUrl,
                path = "$GEMINI_API/config",
                method = "GET",
                token = session.token,
            ).jsonObject
            val cfg = root["config"]?.jsonObject ?: return@runCatching RemoteAgentConfig()
            RemoteAgentConfig(
                agentModel = cfg.str("agentModel"),
                model = cfg.str("model").ifBlank { "Antigravity" },
                systemPrompt = cfg.str("systemPrompt"),
                delimiter = cfg.str("delimiter").ifBlank { "<!-- aw:split -->" },
                concurrency = cfg["concurrency"]?.jsonPrimitive?.intOrNull ?: 16,
                thinkingLevel = cfg.str("thinkingLevel"),
                maxOutputTokens = cfg["maxOutputTokens"]?.jsonPrimitive?.intOrNull?.takeIf { it in 256..65536 } ?: 8192,
            )
        }.getOrDefault(RemoteAgentConfig())
    }

    private suspend fun fetchJobView(session: WorkflowSession, jobId: String): Pair<RemoteGeminiJob, List<String>> {
        val root = workflowRepository.executeJsonCancellable(
            baseUrl = session.baseUrl,
            path = "$GEMINI_API/jobs/$jobId",
            method = "GET",
            token = session.token,
        ).jsonObject
        val jobObj = root["job"]?.jsonObject ?: throw IllegalStateException("无效的 Job 响应")
        val job = parseJob(jobObj)
        val messageIds = root["messages"]?.jsonArray.orEmpty().mapNotNull { el ->
            el.jsonObject.str("id").takeIf { it.isNotBlank() }
        }
        return job to messageIds
    }

    private suspend fun fetchMessage(session: WorkflowSession, jobId: String, messageId: String): RemoteGeminiMessage {
        val obj = workflowRepository.executeJsonCancellable(
            baseUrl = session.baseUrl,
            path = "$GEMINI_API/jobs/$jobId/messages/$messageId",
            method = "GET",
            token = session.token,
        ).jsonObject
        return parseMessage(obj)
    }

    private suspend fun drainParts(
        session: WorkflowSession,
        jobId: String,
        messageId: String,
        startCursor: Int,
    ): Pair<List<RemoteAgentPart>, Int> {
        val collected = mutableListOf<RemoteAgentPart>()
        var cursor = startCursor
        var guard = 0
        while (guard < 50) {
            guard++
            val page = workflowRepository.executeJsonCancellable(
                baseUrl = session.baseUrl,
                path = "$GEMINI_API/jobs/$jobId/messages/$messageId/parts",
                method = "GET",
                query = mapOf("after" to cursor.toString()),
                token = session.token,
            ).jsonObject
            val items = page["items"]?.jsonArray.orEmpty()
            for (el in items) {
                collected += parsePart(el.jsonObject)
            }
            val nextCursor = page["nextCursor"]?.jsonPrimitive?.intOrNull ?: cursor
            val hasMore = page["hasMore"]?.jsonPrimitive?.booleanOrNull ?: false
            cursor = nextCursor
            if (!hasMore || items.isEmpty()) break
        }
        return collected.sortedBy { it.sequence } to cursor
    }

    private suspend fun fetchAncestors(
        session: WorkflowSession,
        job: RemoteGeminiJob,
    ): List<Pair<RemoteGeminiJob, RemoteGeminiMessage>> {
        val ancestors = mutableListOf<Pair<RemoteGeminiJob, RemoteGeminiMessage>>()
        val visited = HashSet<String>()
        var parentId = job.parentMessage
        // PocketBase caps prompt history at 40 entries. A failed turn can have only a user entry.
        while (parentId.isNotBlank() && ancestors.size < 40 && visited.add(parentId)) {
            try {
                val message = parseMessage(workflowRepository.executeJsonCancellable(
                    baseUrl = session.baseUrl,
                    path = "/api/collections/$MESSAGES_COLLECTION/records/$parentId",
                    method = "GET",
                    token = session.token,
                ).jsonObject)
                val parentJob = parseJob(workflowRepository.executeJsonCancellable(
                    baseUrl = session.baseUrl,
                    path = "/api/collections/$JOBS_COLLECTION/records/${message.job}",
                    method = "GET",
                    query = mapOf("fields" to "id,parentMessage,prompt,model,status,error,created,updated,sources,images"),
                    token = session.token,
                ).jsonObject)
                ancestors += parentJob to message
                parentId = parentJob.parentMessage
            } catch (error: WorkflowApiException) {
                if (error.statusCode != 404) throw error
                break
            }
        }
        return ancestors.asReversed()
    }

    private suspend fun wakeConverterBestEffort(session: WorkflowSession) {
        runCatching {
            workflowRepository.executeJsonCancellable(
                baseUrl = DEFAULT_REMOTE_API_URL,
                path = "/api/file-conversion/wake",
                method = "POST",
                token = session.token,
                extraHeaders = mapOf("X-PocketBase-Url" to session.baseUrl),
                authPrefix = "Bearer ",
            )
        }
    }

    private fun parseJob(obj: JsonObject): RemoteGeminiJob {
        val sources = obj["sources"]?.jsonArray.orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            RemoteSourceRef(id = o.str("id"), name = o.str("name"))
        }
        val history = obj["history"]?.jsonArray.orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            RemoteHistoryTurn(role = o.str("role"), text = o.str("text"))
        }
        val images = obj["images"]?.jsonArray.orEmpty().mapNotNull { el ->
            (el as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        }
        return RemoteGeminiJob(
            id = obj.str("id"),
            owner = obj.str("owner"),
            threadRoot = obj.str("threadRoot"),
            parentMessage = obj.str("parentMessage"),
            replacesMessage = obj.str("replacesMessage"),
            title = obj.str("title"),
            prompt = obj.str("prompt"),
            model = obj.str("model"),
            status = obj.str("status"),
            error = obj.str("error"),
            cancelRequested = obj["cancelRequested"]?.jsonPrimitive?.booleanOrNull ?: false,
            githubRunId = obj.str("githubRunId"),
            created = obj.str("created"),
            updated = obj.str("updated"),
            sources = sources,
            history = history,
            images = images,
        )
    }

    private fun parseMessage(obj: JsonObject): RemoteGeminiMessage {
        val usageObj = obj["usage"] as? JsonObject
        val usage = RemoteGeminiUsage(
            totalTokenCount = usageObj?.get("totalTokenCount")?.jsonPrimitive?.intOrNull ?: 0,
            promptTokenCount = usageObj?.get("promptTokenCount")?.jsonPrimitive?.intOrNull ?: 0,
            candidatesTokenCount = usageObj?.get("candidatesTokenCount")?.jsonPrimitive?.intOrNull ?: 0,
            thoughtsTokenCount = usageObj?.get("thoughtsTokenCount")?.jsonPrimitive?.intOrNull ?: 0,
            cachedContentTokenCount = usageObj?.get("cachedContentTokenCount")?.jsonPrimitive?.intOrNull ?: 0,
            toolUseTokenCount = usageObj?.get("toolUseTokenCount")?.jsonPrimitive?.intOrNull ?: 0,
            agentStepCount = usageObj?.get("agentStepCount")?.jsonPrimitive?.intOrNull ?: 0,
        )
        return RemoteGeminiMessage(
            id = obj.str("id"),
            owner = obj.str("owner"),
            job = obj.str("job"),
            index = obj["index"]?.jsonPrimitive?.intOrNull ?: 0,
            part = obj["part"]?.jsonPrimitive?.intOrNull ?: 0,
            sourceName = obj.str("sourceName"),
            sourceId = obj.str("sourceId"),
            inputText = obj.str("inputText"),
            status = obj.str("status"),
            reply = obj.str("reply"),
            error = obj.str("error"),
            finishReason = obj.str("finishReason"),
            startedAt = obj.str("startedAt"),
            finishedAt = obj.str("finishedAt"),
            updated = obj.str("updated"),
            streamRevision = obj["streamRevision"]?.jsonPrimitive?.intOrNull,
            usage = usage,
            agentSummaryJson = jsonFieldString(obj, "agentSummary"),
            agentTimingsJson = jsonFieldString(obj, "agentTimings"),
        )
    }

    private fun jsonFieldString(obj: JsonObject, key: String): String? {
        val el = obj[key] ?: return null
        return when (el) {
            is JsonObject -> if (el.isEmpty()) null else el.toString()
            is JsonPrimitive -> el.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            else -> null
        }
    }

    private fun parsePart(obj: JsonObject): RemoteAgentPart {
        return RemoteAgentPart(
            id = obj.str("id"),
            message = obj.str("message"),
            key = obj.str("key"),
            sequence = obj["sequence"]?.jsonPrimitive?.intOrNull ?: 0,
            revision = obj["revision"]?.jsonPrimitive?.intOrNull ?: 0,
            type = obj.str("type"),
            status = obj.str("status"),
            content = obj.str("content"),
            toolName = obj.str("toolName"),
            callId = obj.str("callId"),
            providerType = obj.str("providerType"),
            truncated = obj["truncated"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    private fun parseJsonOrPrimitive(raw: String): JsonElement? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { JsonInstant.parseToJsonElement(trimmed) }
            .getOrElse { JsonPrimitive(trimmed) }
    }

    private fun parseAnnotations(raw: String, out: MutableList<UIMessageAnnotation>) {
        val arr = runCatching { JsonInstant.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return
        for (item in arr) {
            val obj = item as? JsonObject ?: continue
            val url = obj.str("url")
            if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                out += UIMessageAnnotation.UrlCitation(
                    title = obj.str("title").ifBlank { url },
                    url = url,
                )
            }
        }
    }

    private fun newRecordId(): String {
        val sb = StringBuilder(15)
        while (sb.length < 15) {
            val b = random.nextInt(256)
            if (b >= 252) continue
            sb.append(RECORD_ID_ALPHABET[b % RECORD_ID_ALPHABET.length])
        }
        return sb.toString()
    }

    override suspend fun listSchedules(): List<AgentSchedule> = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        val root = workflowRepository.executeJsonCancellable(
            baseUrl = session.baseUrl,
            path = "$GEMINI_API/schedules",
            method = "GET",
            token = session.token,
        ).jsonObject
        val items = root["items"]?.jsonArray ?: return@withContext emptyList()
        items.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            obj.toAgentSchedule()
        }
    }

    override suspend fun createSchedule(schedule: AgentSchedule): AgentSchedule = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        val obj = workflowRepository.executeJsonCancellable(
            baseUrl = session.baseUrl,
            path = "$GEMINI_API/schedules",
            method = "POST",
            body = schedule.toPayloadJson(),
            token = session.token,
        ).jsonObject
        obj.toAgentSchedule()
    }

    override suspend fun updateSchedule(schedule: AgentSchedule): AgentSchedule = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        val obj = workflowRepository.executeJsonCancellable(
            baseUrl = session.baseUrl,
            path = "$GEMINI_API/schedules/${schedule.id}",
            method = "PATCH",
            body = schedule.toPayloadJson(),
            token = session.token,
        ).jsonObject
        obj.toAgentSchedule()
    }

    override suspend fun deleteSchedule(id: String) = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        workflowRepository.executeJsonCancellable(
            baseUrl = session.baseUrl,
            path = "$GEMINI_API/schedules/$id",
            method = "DELETE",
            token = session.token,
        )
        Unit
    }

    override suspend fun triggerSchedule(id: String): RemoteSubmittedJob? = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        val obj = workflowRepository.executeJsonCancellable(
            baseUrl = session.baseUrl,
            path = "$GEMINI_API/schedules/$id/trigger",
            method = "POST",
            body = buildJsonObject {},
            token = session.token,
        ).jsonObject
        wakeConverterBestEffort(session)
        val jobId = obj.str("jobId").ifBlank { obj.str("id") }
        if (jobId.isBlank()) null else RemoteSubmittedJob(
            jobId = jobId,
            threadRoot = obj.str("threadRoot").ifBlank { jobId },
            status = obj.str("status").ifBlank { "queued" },
        )
    }

    private fun AgentSchedule.toPayloadJson(): JsonObject = buildJsonObject {
        put("title", title)
        put("prompt", prompt)
        put("systemPrompt", systemPrompt)
        put("model", model.ifBlank { "Antigravity" })
        put("thinkingLevel", thinkingLevel)
        put("cron", cron)
        put("timezone", timezone.ifBlank { "Asia/Shanghai" })
        put("enabled", enabled)
    }

    private fun JsonObject.toAgentSchedule(): AgentSchedule = AgentSchedule(
        id = str("id"),
        title = str("title"),
        prompt = str("prompt"),
        systemPrompt = str("systemPrompt"),
        model = str("model").ifBlank { "Antigravity" },
        thinkingLevel = str("thinkingLevel"),
        cron = str("cron").ifBlank { "0 9 * * *" },
        timezone = str("timezone").ifBlank { "Asia/Shanghai" },
        enabled = this["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
        lastRunAt = str("lastRunAt"),
        nextRunAt = str("nextRunAt"),
        lastJobId = str("lastJobId"),
        lastStatus = str("lastStatus"),
        lastError = str("lastError"),
    )

    companion object {
        private val IMAGE_MARKER_REGEX = Regex("""<!--\s*aw:images:(\[[\s\S]*?])\s*-->""")

        internal fun extractImageUrlsAndStripMarker(
            text: String,
            extraImages: List<String> = emptyList(),
        ): Pair<String, List<String>> {
            val urls = LinkedHashSet<String>()
            extraImages.forEach { url ->
                val trimmed = url.trim()
                if (trimmed.isNotEmpty()) urls += trimmed
            }
            val cleaned = IMAGE_MARKER_REGEX.replace(text) { match ->
                val rawJson = match.groupValues.getOrNull(1).orEmpty()
                runCatching {
                    JsonInstant.parseToJsonElement(rawJson).jsonArray.forEach { el ->
                        val url = (el as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                        if (url.isNotEmpty()) urls += url
                    }
                }
                ""
            }.trim()
            return cleaned to urls.toList()
        }

        internal fun buildSubmitJobPayload(
            jobId: String,
            config: RemoteAgentConfig,
            prompt: String,
            sources: List<RemoteAgentSourceInput>,
            images: List<String> = emptyList(),
            parentMessageId: String? = null,
            replacesMessageId: String? = null,
            title: String = "",
            modelOverride: String? = null,
            thinkingLevelOverride: String? = null,
            systemPromptOverride: String? = null,
        ): JsonObject {
            val selectedModel = modelOverride?.trim()?.takeIf { it.isNotBlank() } ?: if (
                config.model.equals("antigravity", ignoreCase = true) ||
                config.model.startsWith("antigravity-", ignoreCase = true)
            ) {
                config.model
            } else {
                "Antigravity"
            }
            val isAntigravityModel =
                selectedModel.equals("antigravity", ignoreCase = true) ||
                selectedModel.startsWith("antigravity-", ignoreCase = true)
            val selectedThinkingLevel = thinkingLevelOverride ?: config.thinkingLevel
            val selectedSystemPrompt = systemPromptOverride ?: config.systemPrompt
            val cleanImages = images.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val imagesJson = if (cleanImages.isNotEmpty()) {
                buildJsonArray { cleanImages.forEach { add(JsonPrimitive(it)) } }
            } else null
            val finalPrompt = if (imagesJson != null) {
                val base = prompt.trimEnd()
                if (base.isEmpty()) "<!-- aw:images:$imagesJson -->" else "$base\n<!-- aw:images:$imagesJson -->"
            } else {
                prompt
            }
            return buildJsonObject {
                put("id", jobId)
                put("responseVersion", 1)
                put("title", title)
                put("prompt", finalPrompt)
                put("systemPrompt", selectedSystemPrompt)
                put("text", "")
                if (imagesJson != null) {
                    put("images", imagesJson)
                }
                put(
                    "sources",
                    buildJsonArray {
                        for (src in sources) {
                            add(
                                buildJsonObject {
                                    put("id", src.id)
                                    put("name", src.name)
                                    put("content", src.content)
                                }
                            )
                        }
                    }
                )
                put("delimiter", config.delimiter.ifBlank { "<!-- aw:split -->" })
                put("model", selectedModel)
                if (isAntigravityModel && config.agentModel.isNotBlank()) {
                    put("agentModel", config.agentModel)
                }
                put("concurrency", config.concurrency.coerceIn(1, 16))
                put("thinkingLevel", selectedThinkingLevel)
                put("maxOutputTokens", config.maxOutputTokens.coerceIn(256, 65536))
                if (!parentMessageId.isNullOrBlank()) {
                    put("parentMessage", parentMessageId)
                }
                if (!replacesMessageId.isNullOrBlank()) {
                    put("replacesMessage", replacesMessageId)
                }
            }
        }

        private fun isCanceledStatus(status: String?): Boolean = status == "canceled" || status == "cancelled"

        fun isActiveStatus(status: String): Boolean = when (status) {
            "queued", "dispatching", "pending", "running" -> true
            else -> false
        }

        fun stableUuid(seed: String): Uuid {
            val jUuid = java.util.UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8))
            return Uuid.fromLongs(jUuid.mostSignificantBits, jUuid.leastSignificantBits)
        }

        fun parseIsoMillis(raw: String): Long {
            if (raw.isBlank()) return 0L
            return runCatching {
                Instant.parse(raw.replace(' ', 'T')).toEpochMilli()
            }.getOrElse {
                runCatching {
                    java.time.OffsetDateTime.parse(raw.replace(' ', 'T'), DateTimeFormatter.ISO_DATE_TIME)
                        .toInstant()
                        .toEpochMilli()
                }.getOrDefault(0L)
            }
        }

        fun parseLocalDateTime(raw: String): LocalDateTime {
            val millis = parseIsoMillis(raw)
            val instant = if (millis > 0L) {
                kotlin.time.Instant.fromEpochMilliseconds(millis)
            } else {
                Clock.System.now()
            }
            return instant.toLocalDateTime(TimeZone.currentSystemDefault())
        }
    }
}

private fun JsonObject.str(key: String): String =
    this[key]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
