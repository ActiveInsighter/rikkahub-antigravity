package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ModelSnapshot
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.workflow.WorkflowApiException
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

internal const val ACP_WORKER_URL = "https://rikka-remote-agent.2212148739lbw.workers.dev"

/**
 * AgentCloudClient backed by the ACP remote-agent Worker.
 *
 * Data model mapping:
 *   ACP session  ←→  thread / conversation  (threadRoot = session ID, jobId = latest task ID)
 *   ACP task     ←→  job turn               (one task per user message)
 *   ACP events   ←→  RemoteAgentPart list   (streamed via polling /v1/tasks/{id}/events)
 *
 * Session continuity: on the first submitMessage the client creates a new ACP session and
 * stores its ID as the PocketBase remoteThreadRoot.  Subsequent turns pass parentMessageId =
 * the previous task ID, which this client uses to look up the existing session.
 *
 * The companion object exposes helpers shared with AgentRepository.
 */
class AcpAgentCloudClient(
    private val workflowRepository: WorkflowRepository,
    @Suppress("UNUSED_PARAMETER") httpClient: OkHttpClient,
) : AgentCloudClient {

    // ── Public interface ─────────────────────────────────────────────────────

    override fun hasSession(): Boolean = workflowRepository.session.value != null

    override suspend fun listRemoteThreads(threadRoot: String?): List<RemoteAgentThread> =
        withContext(Dispatchers.IO) {
            val session = workflowRepository.requireSession()
            val sessions = fetchPagedSessions(session)
            sessions.mapNotNull { s ->
                val latestTask = fetchPagedTasks(session, s.id, limit = 1).firstOrNull()
                RemoteAgentThread(
                    jobId = latestTask?.id ?: return@mapNotNull null,
                    threadRoot = s.id,
                    title = s.title,
                    status = latestTask.state.toStatus(),
                    created = s.updated,
                    updated = s.updated,
                    parentMessage = "",
                    archived = false,
                    pinned = false,
                )
            }.let { all ->
                if (threadRoot != null) all.filter { it.threadRoot == threadRoot } else all
            }
        }

    override suspend fun fetchThreadConversation(
        latestJobId: String,
        previous: RemoteThreadConversation?,
        refreshJob: Boolean,
    ): RemoteThreadConversation = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()

        val task = fetchTask(session, latestJobId)
        val sessionId = task.sessionId

        val prevCursor = previous?.partsCursor?.takeIf {
            previous.job.id == latestJobId
        } ?: 0
        val events = fetchEvents(session, latestJobId, after = prevCursor)

        val prevParts = previous?.parts.orEmpty()
            .takeIf { previous?.job?.id == latestJobId } ?: emptyList()
        val newParts = events.toRemoteAgentParts(latestJobId)
        val mergedParts = mergeParts(prevParts, newParts)
        val newCursor = events.lastOrNull()?.sequence ?: prevCursor

        val isActive = task.state in setOf("scheduled", "queued", "running", "canceling")
        val job = task.toRemoteGeminiJob(sessionId)
        val message = task.toRemoteGeminiMessage(sessionId)

        val nodes: List<MessageNode> = if (previous?.job?.id == latestJobId && previous.messageNodes.isNotEmpty()) {
            // Incremental update: refresh only the last assistant node.
            val last = previous.messageNodes.last()
            val updatedMsg = buildAssistantUIMessage(last.currentMessage.id, job, message, mergedParts)
            previous.messageNodes.dropLast(1) + last.copy(messages = listOf(updatedMsg))
        } else {
            // Full load: build nodes for every past task in this session, then the current one.
            val allTasks = fetchPagedTasks(session, sessionId)
            buildAllTurnNodes(allTasks, latestTaskId = latestJobId, latestParts = mergedParts, session = session)
        }

        RemoteThreadConversation(
            job = job,
            message = message,
            messageNodes = nodes,
            isActive = isActive,
            parts = mergedParts,
            partsCursor = newCursor,
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
        agentModelOverride: String?,
        thinkingLevelOverride: String?,
        systemPromptOverride: String?,
        initialHistory: List<RemoteHistoryTurn>,
    ): RemoteSubmittedJob = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()

        // Find the existing ACP session or create a new one.
        val sessionId = if (parentMessageId != null) {
            // parentMessageId is a task ID from a previous turn → look up its session.
            runCatching { fetchTask(session, parentMessageId).sessionId }
                .getOrNull() ?: createSession(session, title, agentModelOverride ?: "antigravity")
        } else {
            createSession(session, title, agentModelOverride ?: "antigravity")
        }

        val config = buildJsonObject {
            if (!modelOverride.isNullOrBlank()) put("model", modelOverride)
            if (!thinkingLevelOverride.isNullOrBlank()) put("thought_level", thinkingLevelOverride)
        }

        val payload = buildJsonObject {
            put("prompt", prompt)
            put("requestKey", Uuid.random().toString())
            if (config.isNotEmpty()) put("config", config)
        }

        val resp = workflowRepository.executeJsonCancellable(
            baseUrl = ACP_WORKER_URL,
            path = "/v1/sessions/$sessionId/tasks",
            method = "POST",
            body = payload,
            token = session.token,
            authPrefix = "Bearer ",
        ).jsonObject

        val taskId = resp.str("id").ifBlank { throw WorkflowApiException("ACP task ID missing") }
        val state = resp.str("state").ifBlank { "queued" }

        RemoteSubmittedJob(
            jobId = taskId,
            threadRoot = sessionId,
            status = state.toStatus(),
        )
    }

    override suspend fun cancelJob(jobId: String): Unit = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        workflowRepository.executeJsonCancellable(
            baseUrl = ACP_WORKER_URL,
            path = "/v1/tasks/$jobId/cancel",
            method = "POST",
            body = buildJsonObject {},
            token = session.token,
            authPrefix = "Bearer ",
        )
    }

    override suspend fun updateThreadMetadata(
        jobId: String,
        title: String?,
        pinned: Boolean?,
        archived: Boolean?,
        folderId: String?,
    ) = Unit // ACP sessions don't support metadata updates yet

    override suspend fun deleteThread(jobId: String) = Unit // Not supported by ACP

    override fun buildAssistantUIMessage(
        messageId: Uuid,
        job: RemoteGeminiJob,
        message: RemoteGeminiMessage?,
        parts: List<RemoteAgentPart>,
    ): UIMessage {
        val uiParts = mutableListOf<UIMessagePart>()
        val isActive = isAgentActiveStatus(job.status) || isAgentActiveStatus(message?.status.orEmpty())
        val sorted = parts.sortedBy { it.sequence }
        for (part in sorted) {
            when (part.part.str("type")) {
                "text" -> {
                    val t = part.part.str("text")
                    if (t.isNotBlank()) uiParts += UIMessagePart.Text(t)
                }
                "reasoning" -> {
                    val t = part.part.str("text")
                    if (t.isNotBlank()) uiParts += UIMessagePart.Thinking(t)
                }
                "tool_call" -> {
                    uiParts += UIMessagePart.ServerTool(
                        toolCallId = part.part.str("id"),
                        toolName = part.part.str("name"),
                        input = part.part["input"],
                        output = null,
                        status = if (isActive) ServerToolStatus.IN_PROGRESS else ServerToolStatus.COMPLETED,
                    )
                }
                "tool_result" -> {
                    // Merge result into the matching tool_call part if present, otherwise emit notice.
                    val callId = part.part.str("callId")
                    val existing = uiParts.indexOfLast { p ->
                        p is UIMessagePart.ServerTool && p.toolCallId == callId
                    }
                    if (existing >= 0) {
                        val prev = uiParts[existing] as UIMessagePart.ServerTool
                        uiParts[existing] = prev.copy(
                            output = part.part["result"],
                            status = if (isActive) ServerToolStatus.IN_PROGRESS else ServerToolStatus.COMPLETED,
                        )
                    }
                }
                "artifact" -> {
                    val path = part.part.str("path")
                    val size = part.part["size"]?.jsonPrimitive?.intOrNull ?: 0
                    if (path.isNotBlank()) uiParts += UIMessagePart.Text("📎 $path ($size bytes)")
                }
                "approval" -> {
                    uiParts += UIMessagePart.Notice(
                        title = "工具调用待审批",
                        text = part.part.str("toolName").ifBlank { "需要您的审批才能继续" },
                    )
                }
                "error" -> {
                    val code = part.part.str("code")
                    uiParts += UIMessagePart.Notice(title = "执行出错", text = code)
                }
            }
        }
        if (uiParts.isEmpty() && isActive) uiParts += UIMessagePart.Text("")

        val agentName = job.agentModel.ifBlank { job.model }.ifBlank { "Agent" }
        return UIMessage(
            id = messageId,
            role = MessageRole.ASSISTANT,
            parts = uiParts,
            annotations = emptyList(),
            createdAt = PocketBaseAgentCloudClient.parseLocalDateTime(job.created),
            modelSnapshot = ModelSnapshot(displayName = agentName, modelId = job.model),
        )
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun createSession(
        session: me.rerere.rikkahub.data.workflow.WorkflowSession,
        title: String,
        agent: String,
    ): String {
        val projects = fetchProjects(session)
        val project = projects.firstOrNull()
            ?: throw WorkflowApiException("无可用的 ACP 项目，请先在 Gateway 配置项目授权")
        val selectedAgent = if (agent in project.agents) agent else project.agents.firstOrNull() ?: "antigravity"
        val resp = workflowRepository.executeJsonCancellable(
            baseUrl = ACP_WORKER_URL,
            path = "/v1/sessions",
            method = "POST",
            body = buildJsonObject {
                put("projectId", project.id)
                put("agent", selectedAgent)
                put("title", title)
                put("requestKey", Uuid.random().toString())
            },
            token = session.token,
            authPrefix = "Bearer ",
        ).jsonObject
        return resp.str("id").ifBlank { throw WorkflowApiException("ACP session ID missing") }
    }

    private suspend fun fetchProjects(
        session: me.rerere.rikkahub.data.workflow.WorkflowSession,
    ): List<AcpProject> {
        val resp = workflowRepository.executeJsonCancellable(
            baseUrl = ACP_WORKER_URL, path = "/v1/projects", method = "GET",
            token = session.token, authPrefix = "Bearer ",
        ).jsonObject
        return resp["items"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toAcpProject() }
    }

    private suspend fun fetchPagedSessions(
        session: me.rerere.rikkahub.data.workflow.WorkflowSession,
    ): List<AcpSession> {
        val result = mutableListOf<AcpSession>()
        var page = 1
        while (page <= 20) {
            val resp = workflowRepository.executeJsonCancellable(
                baseUrl = ACP_WORKER_URL, path = "/v1/sessions", method = "GET",
                query = mapOf("page" to "$page"),
                token = session.token, authPrefix = "Bearer ",
            ).jsonObject
            val items = resp["items"]?.jsonArray ?: break
            items.forEach { result += it.jsonObject.toAcpSession() }
            page = resp["nextPage"]?.jsonPrimitive?.intOrNull ?: break
        }
        return result
    }

    private suspend fun fetchPagedTasks(
        session: me.rerere.rikkahub.data.workflow.WorkflowSession,
        sessionId: String,
        limit: Int = Int.MAX_VALUE,
    ): List<AcpTaskSnapshot> {
        val result = mutableListOf<AcpTaskSnapshot>()
        var page = 1
        while (page <= 20 && result.size < limit) {
            val resp = workflowRepository.executeJsonCancellable(
                baseUrl = ACP_WORKER_URL,
                path = "/v1/sessions/$sessionId/tasks",
                method = "GET",
                query = mapOf("page" to "$page"),
                token = session.token, authPrefix = "Bearer ",
            ).jsonObject
            val items = resp["items"]?.jsonArray ?: break
            items.forEach { result += it.jsonObject.toAcpTask() }
            page = resp["nextPage"]?.jsonPrimitive?.intOrNull ?: break
        }
        return result.take(limit)
    }

    private suspend fun fetchTask(
        session: me.rerere.rikkahub.data.workflow.WorkflowSession,
        taskId: String,
    ): AcpTaskSnapshot {
        val resp = workflowRepository.executeJsonCancellable(
            baseUrl = ACP_WORKER_URL, path = "/v1/tasks/$taskId", method = "GET",
            token = session.token, authPrefix = "Bearer ",
        ).jsonObject
        return resp.toAcpTask()
    }

    private suspend fun fetchEvents(
        session: me.rerere.rikkahub.data.workflow.WorkflowSession,
        taskId: String,
        after: Int,
    ): List<AcpEvent> {
        val resp = workflowRepository.executeJsonCancellable(
            baseUrl = ACP_WORKER_URL,
            path = "/v1/tasks/$taskId/events",
            method = "GET",
            query = mapOf("after" to "$after"),
            token = session.token, authPrefix = "Bearer ",
        ).jsonObject
        return resp["items"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toAcpEvent() }
    }

    private fun buildTurnNodes(
        job: RemoteGeminiJob,
        message: RemoteGeminiMessage?,
        parts: List<RemoteAgentPart>,
        session: me.rerere.rikkahub.data.workflow.WorkflowSession,
    ): List<MessageNode> {
        val turnId = job.id
        val userMsg = UIMessage(
            id = stableAgentUuid("acp-user:$turnId"),
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text(job.prompt)),
            createdAt = PocketBaseAgentCloudClient.parseLocalDateTime(job.created),
        )
        val assistantMsg = buildAssistantUIMessage(
            messageId = stableAgentUuid("acp-asst:$turnId"),
            job = job,
            message = message,
            parts = parts,
        )
        return listOf(
            MessageNode(id = stableAgentUuid("acp-node-u:$turnId"), messages = listOf(userMsg), selectIndex = 0),
            MessageNode(id = stableAgentUuid("acp-node-a:$turnId"), messages = listOf(assistantMsg), selectIndex = 0),
        )
    }

    /** Build nodes for all tasks in a session, using empty parts for historical turns. */
    private fun buildAllTurnNodes(
        tasks: List<AcpTaskSnapshot>,
        latestTaskId: String,
        latestParts: List<RemoteAgentPart>,
        session: me.rerere.rikkahub.data.workflow.WorkflowSession,
    ): List<MessageNode> = tasks.flatMap { task ->
        val parts = if (task.id == latestTaskId) latestParts else emptyList()
        val job = task.toRemoteGeminiJob(task.sessionId)
        val message = task.toRemoteGeminiMessage(task.sessionId)
        buildTurnNodes(job, message, parts, session)
    }

    private fun mergeParts(
        existing: List<RemoteAgentPart>,
        incoming: List<RemoteAgentPart>,
    ): List<RemoteAgentPart> {
        val byKey = existing.associateByTo(LinkedHashMap()) { it.key }
        for (p in incoming) {
            if (p.revision > (byKey[p.key]?.revision ?: -1)) byKey[p.key] = p
        }
        return byKey.values.sortedBy { it.sequence }
    }

    // ── ACP event → RemoteAgentPart conversion ───────────────────────────────

    private fun List<AcpEvent>.toRemoteAgentParts(taskId: String): List<RemoteAgentPart> {
        val parts = mutableListOf<RemoteAgentPart>()
        // Accumulate consecutive chunks of the same type together.
        var accText = StringBuilder()
        var accThinking = StringBuilder()
        var lastTextSeq = -1
        var lastThinkingSeq = -1

        fun flushText(seq: Int) {
            if (accText.isNotEmpty()) {
                parts += RemoteAgentPart(
                    key = "$taskId-text-$seq",
                    sequence = seq,
                    revision = seq,
                    part = buildJsonObject { put("type", "text"); put("text", accText.toString()) },
                )
                accText = StringBuilder()
            }
        }
        fun flushThinking(seq: Int) {
            if (accThinking.isNotEmpty()) {
                parts += RemoteAgentPart(
                    key = "$taskId-reasoning-$seq",
                    sequence = seq,
                    revision = seq,
                    part = buildJsonObject { put("type", "reasoning"); put("text", accThinking.toString()) },
                )
                accThinking = StringBuilder()
            }
        }

        for (event in this) {
            when (event.type) {
                "update" -> {
                    val data = event.data ?: continue
                    val kind = data.str("sessionUpdate")
                    val content = data["content"]?.jsonObject
                    when (kind) {
                        "agent_message_chunk" -> {
                            if (content?.str("type") == "text") {
                                flushThinking(lastThinkingSeq)
                                accText.append(content.str("text"))
                                lastTextSeq = event.sequence
                            }
                        }
                        "agent_thought_chunk" -> {
                            if (content?.str("type") == "text") {
                                flushText(lastTextSeq)
                                accThinking.append(content.str("text"))
                                lastThinkingSeq = event.sequence
                            }
                        }
                        "tool_call" -> {
                            flushText(lastTextSeq); flushThinking(lastThinkingSeq)
                            val tc = data["toolCall"]?.jsonObject ?: continue
                            parts += RemoteAgentPart(
                                key = "$taskId-tc-${event.sequence}",
                                sequence = event.sequence,
                                revision = event.sequence,
                                part = buildJsonObject {
                                    put("type", "tool_call")
                                    put("id", tc.str("id"))
                                    put("name", tc.str("name"))
                                    tc["input"]?.let { put("input", it) }
                                },
                            )
                        }
                        "tool_result" -> {
                            val tr = data["toolResult"]?.jsonObject ?: continue
                            parts += RemoteAgentPart(
                                key = "$taskId-tr-${event.sequence}",
                                sequence = event.sequence,
                                revision = event.sequence,
                                part = buildJsonObject {
                                    put("type", "tool_result")
                                    put("callId", tr.str("toolCallId"))
                                    tr["result"]?.let { put("result", it) }
                                },
                            )
                        }
                        "config_option_update" -> { /* ignore */ }
                    }
                }
                "approval" -> {
                    flushText(lastTextSeq); flushThinking(lastThinkingSeq)
                    val data = event.data ?: continue
                    parts += RemoteAgentPart(
                        key = "$taskId-approval-${event.sequence}",
                        sequence = event.sequence,
                        revision = event.sequence,
                        part = buildJsonObject {
                            put("type", "approval")
                            put("id", data.str("id"))
                            data["toolCall"]?.let { put("toolCall", it) }
                            data["options"]?.let { put("options", it) }
                        },
                    )
                }
                "artifact" -> {
                    flushText(lastTextSeq); flushThinking(lastThinkingSeq)
                    val data = event.data ?: continue
                    parts += RemoteAgentPart(
                        key = "$taskId-artifact-${event.sequence}",
                        sequence = event.sequence,
                        revision = event.sequence,
                        part = buildJsonObject {
                            put("type", "artifact")
                            put("path", data.str("path"))
                            data["size"]?.let { put("size", it) }
                            data["id"]?.let { put("fileId", it) }
                        },
                    )
                }
                "stop" -> { /* terminal marker, nothing to emit */ }
                "error" -> {
                    flushText(lastTextSeq); flushThinking(lastThinkingSeq)
                    val data = event.data ?: continue
                    parts += RemoteAgentPart(
                        key = "$taskId-error-${event.sequence}",
                        sequence = event.sequence,
                        revision = event.sequence,
                        part = buildJsonObject {
                            put("type", "error")
                            put("code", data.str("code"))
                        },
                    )
                }
            }
        }
        // Flush any remaining accumulated text/thinking.
        flushText(lastTextSeq); flushThinking(lastThinkingSeq)
        return parts
    }
}

// ── Internal ACP data classes ─────────────────────────────────────────────────

private data class AcpProject(val id: String, val name: String, val agents: List<String>)
private data class AcpSession(val id: String, val agent: String, val title: String, val updated: String)
internal data class AcpTaskSnapshot(
    val id: String,
    val sessionId: String,
    val prompt: String,
    val state: String,
    val cursor: Int,
    val created: String,
    val updated: String,
    val config: JsonObject,
    val retryOf: String?,
)
private data class AcpEvent(val sequence: Int, val type: String, val data: JsonObject?)

// ── JsonObject → data class parsers ──────────────────────────────────────────

private fun JsonObject.toAcpProject() = AcpProject(
    id = str("id").ifBlank { return null },
    name = str("name"),
    agents = this["agents"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull },
)

private fun JsonObject.toAcpSession() = AcpSession(
    id = str("id"),
    agent = str("agent"),
    title = str("title"),
    updated = str("updated"),
)

private fun JsonObject.toAcpTask() = AcpTaskSnapshot(
    id = str("id"),
    sessionId = str("sessionId"),
    prompt = str("prompt"),
    state = str("state"),
    cursor = this["cursor"]?.jsonPrimitive?.intOrNull ?: 0,
    created = str("created"),
    updated = str("updated"),
    config = this["config"]?.jsonObject ?: JsonObject(emptyMap()),
    retryOf = str("retryOf").ifBlank { null },
)

private fun JsonObject.toAcpEvent(): AcpEvent? = AcpEvent(
    sequence = this["sequence"]?.jsonPrimitive?.intOrNull ?: return null,
    type = str("type").ifBlank { return null },
    data = this["data"]?.jsonObject,
)

// ── AcpTaskSnapshot → Gemini-compatible view objects ─────────────────────────

private fun AcpTaskSnapshot.toStatus(): String = state.toStatus()

private fun String.toStatus(): String = when (this) {
    "scheduled", "queued" -> "queued"
    "running", "canceling" -> "running"
    "succeeded" -> "completed"
    "failed", "canceled", "interrupted" -> "failed"
    else -> "queued"
}

internal fun AcpTaskSnapshot.toRemoteGeminiJob(sessionId: String) = RemoteGeminiJob(
    id = id,
    owner = "",
    threadRoot = sessionId,
    parentMessage = "",
    replacesMessage = retryOf ?: "",
    title = prompt.take(80),
    prompt = prompt,
    model = config.str("model").ifBlank { "antigravity" },
    status = state.toStatus(),
    error = "",
    cancelRequested = state == "canceling",
    githubRunId = "",
    created = created,
    updated = updated,
    agentModel = config.str("model").ifBlank { "antigravity" },
)

internal fun AcpTaskSnapshot.toRemoteGeminiMessage(sessionId: String) = RemoteGeminiMessage(
    id = "$id-msg",
    owner = "",
    job = id,
    index = 0,
    part = 0,
    sourceName = "",
    sourceId = "",
    inputText = "",
    status = state.toStatus(),
    reply = "",
    error = "",
    finishReason = when (state) {
        "succeeded" -> "STOP"
        "failed", "interrupted" -> "ERROR"
        "canceled" -> "CANCELLED"
        else -> ""
    },
    startedAt = created,
    finishedAt = updated,
    updated = updated,
    streamRevision = cursor,
    usage = RemoteGeminiUsage(),
)

private fun JsonObject.str(key: String): String =
    this[key]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
