package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.delay
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ModelSnapshot
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.workflow.DEFAULT_POCKETBASE_URL
import me.rerere.rikkahub.data.workflow.WorkflowApiException
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowSession
import kotlin.uuid.Uuid

internal const val ACP_WORKER_URL = "https://rikka-remote-agent.2212148739lbw.workers.dev"

class AcpAgentCloudClient internal constructor(
    private val accountProvider: () -> WorkflowSession?,
    private val request: AcpRequest,
) : AgentCloudClient {
    constructor(workflow: WorkflowRepository) : this(
        { workflow.session.value },
        AcpRequest { account, path, body, query ->
            workflow.executeJsonCancellable(
                baseUrl = ACP_WORKER_URL, path = "/v1$path", method = if (body == null) "GET" else "POST",
                query = query, body = body, token = account.token, authPrefix = "Bearer ",
            )
        },
    )

    private val parser = Json { ignoreUnknownKeys = true }

    override fun hasSession() = accountProvider()?.baseUrl?.trimEnd('/') == DEFAULT_POCKETBASE_URL

    override suspend fun listRemoteThreads(threadRoot: String?): List<RemoteAgentThread> {
        val api = api()
        val sessions = if (threadRoot == null) api.list<AcpSession>("/sessions") else {
            listOf(api.call<AcpSession>("/sessions/$threadRoot"))
        }
        return sessions.mapNotNull { session ->
            val task = session.latestTask ?: if (session.pinned == null || threadRoot != null) {
                api.page<AcpTaskRef>("/sessions/${session.id}/tasks", 1).items.firstOrNull()
            } else null
            if (task == null) return@mapNotNull null
            RemoteAgentThread(
                jobId = task.id,
                threadRoot = session.id,
                title = session.title,
                status = task.state.toAgentStatus(),
                created = session.created.ifBlank { task.created },
                updated = task.created,
                parentMessage = "",
                archived = false,
                pinned = session.pinned ?: false,
                hasMetadata = session.pinned != null,
            )
        }
    }

    override suspend fun fetchThreadConversation(
        latestJobId: String,
        previous: RemoteThreadConversation?,
        refreshJob: Boolean,
    ): RemoteThreadConversation {
        val api = api()
        val cached = previous?.takeIf { it.job.id == latestJobId }
        val task = if (cached == null || refreshJob) api.call<AcpTask>("/tasks/$latestJobId") else null
        val sessionId = task?.sessionId ?: checkNotNull(cached).job.threadRoot
        val session = if (cached?.acpHistoryComplete != true) api.call<AcpSession>("/sessions/$sessionId") else null
        val events = api.events(latestJobId, cached?.partsCursor ?: 0, task?.cursor ?: 0)
        // Prefer the event response's state. A terminal snapshot may still have unread events.
        val status = events.state.toAgentStatus()
        val active = !events.done
        val parts = cached?.parts.orEmpty() + events.items.map { it.toPart(latestJobId) }
        val error = parts.lastOrNull { it.part.text("type") == "error" }?.part?.text("code").orEmpty()
        val job = (cached?.job ?: checkNotNull(task).toJob(checkNotNull(session))).copy(
            status = if (active && !isAgentActiveStatus(status)) "running" else status,
            error = error,
            cancelRequested = events.state == "canceling",
        )
        val message = job.toMessage(events.cursor)
        var approval = task?.approval(parts)
        var historyComplete = true
        val nodes = if (cached?.acpHistoryComplete == true && cached.messageNodes.isNotEmpty()) {
            val assistant = cached.messageNodes.last()
            cached.messageNodes.dropLast(1) + assistant.copy(
                messages = listOf(buildAssistantUIMessage(assistant.currentMessage.id, job, message, parts)),
            )
        } else {
            val knownNodes = previous?.takeIf {
                it.job.threadRoot == sessionId && it.acpHistoryComplete && !it.isActive
            }?.messageNodes.orEmpty().associateBy { it.id }
            val history = api.list<AcpTask>("/sessions/$sessionId/tasks")
                .dropWhile { it.id != latestJobId }.drop(1).asReversed()
            val priorNodes = history.flatMap { turn ->
                val user = knownNodes[stableAgentUuid("acp-user:${turn.id}")]
                val assistant = knownNodes[stableAgentUuid("acp-assistant:${turn.id}")]
                if (user != null && assistant != null) {
                    listOf(user, assistant)
                } else {
                    val priorEvents = api.events(turn.id, 0, turn.cursor)
                    historyComplete = historyComplete && priorEvents.done
                    val priorJob = turn.toJob(checkNotNull(session)).copy(status = priorEvents.state.toAgentStatus())
                    val priorParts = priorEvents.items.map { it.toPart(turn.id) }
                    if (approval == null && !priorEvents.done) approval = turn.approval(priorParts)
                    turnNodes(priorJob, priorParts)
                }
            }
            priorNodes + turnNodes(job, parts)
        }
        return RemoteThreadConversation(
            if (historyComplete) job else job.copy(status = "running"),
            message, nodes, active || !historyComplete, parts, events.cursor, approval,
            acpHistoryComplete = historyComplete,
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
        threadRoot: String?,
        agentId: String?,
    ): RemoteSubmittedJob {
        val api = api()
        val selectedAgent = agentId ?: "antigravity"
        val taskKey = Uuid.random().toString()
        val existing = threadRoot?.let { root ->
            try {
                api.call<AcpSession>("/sessions/$root")
            } catch (error: WorkflowApiException) {
                if (error.statusCode != 404) throw error
                throw WorkflowApiException("此会话没有可用的 ACP 会话记录，请新建对话。", 404)
            }
        }
        val retry = if (existing != null && replacesMessageId != null) {
            api.call<AcpTask>("/tasks/$replacesMessageId").takeIf {
                it.sessionId == existing.id && it.state in setOf("failed", "canceled", "interrupted")
            }
        } else null
        if (replacesMessageId != null && retry == null) {
            throw WorkflowApiException("ACP 支持重试失败、取消或中断的最新轮次。")
        }
        if (retry != null) {
            val task = api.postWithRetry<AcpTask>("/tasks/${retry.id}/retry", buildJsonObject { put("requestKey", taskKey) })
            return task.toSubmittedJob()
        }
        val history = if (existing == null) initialHistory else emptyList()
        val expandedPrompt = buildAcpPrompt(prompt, sources, images, systemPromptOverride, history)
        // A conversation keeps its original agent and worktree. A model selection from
        // another agent must never be sent as an option to this session's adapter.
        val useSelectedConfig = existing == null || existing.agent == selectedAgent
        val payload = buildJsonObject {
            put("prompt", expandedPrompt)
            put("requestKey", taskKey)
            put("config", buildJsonObject {
                if (useSelectedConfig && !modelOverride.isNullOrBlank()) {
                    put("model", acpModelValue(selectedAgent, modelOverride))
                }
                if (useSelectedConfig && !thinkingLevelOverride.isNullOrBlank()) put("thought_level", thinkingLevelOverride)
            })
        }
        if (payload.toString().toByteArray().size > 65_536) {
            throw WorkflowApiException("ACP 消息超出请求长度限制。请缩短消息或附件。")
        }
        val session = existing ?: run {
            val project = api.list<AcpProject>("/projects").firstOrNull { selectedAgent in it.agents }
                ?: throw WorkflowApiException("当前账号没有启用 ${acpAgentName(selectedAgent)} 的 ACP 项目。")
            api.postWithRetry<AcpSession>("/sessions", buildJsonObject {
                put("projectId", project.id)
                put("agent", selectedAgent)
                put("title", title.trim().take(120).ifBlank { acpAgentName(selectedAgent) })
                put("requestKey", Uuid.random().toString())
            })
        }
        return api.postWithRetry<AcpTask>("/sessions/${session.id}/tasks", payload).toSubmittedJob()
    }

    override suspend fun cancelJob(jobId: String) {
        api().call<AcpTask>("/tasks/$jobId/cancel", buildJsonObject {})
    }

    override suspend fun approveJob(approval: AgentApproval, optionId: String) {
        api().call<AcpTask>("/tasks/${approval.jobId}/approvals/${approval.id}", buildJsonObject {
            put("optionId", optionId)
        })
    }

    override suspend fun updateThreadMetadata(
        jobId: String, title: String?, pinned: Boolean?, archived: Boolean?, folderId: String?,
    ) {
        if (title == null && pinned == null) return
        val api = api()
        val task = api.call<AcpTask>("/tasks/$jobId")
        api.call<AcpSession>("/sessions/${task.sessionId}/metadata", buildJsonObject {
            title?.let { put("title", it.trim().take(120)) }
            pinned?.let { put("pinned", it) }
        })
    }

    override suspend fun deleteThread(jobId: String) {
        val api = api()
        val task = api.call<AcpTask>("/tasks/$jobId")
        api.call<JsonObject>("/sessions/${task.sessionId}/delete", buildJsonObject {})
    }

    override fun buildAssistantUIMessage(
        messageId: Uuid, job: RemoteAgentJob, message: RemoteAgentMessage?, parts: List<RemoteAgentPart>,
    ): UIMessage = UIMessage(
        id = messageId,
        role = MessageRole.ASSISTANT,
        parts = renderAcpParts(parts, job.status, kotlin.time.Instant.fromEpochMilliseconds(parseAgentIsoMillis(job.created))),
        createdAt = parseAgentLocalDateTime(job.created),
        modelSnapshot = ModelSnapshot(job.model, job.agentModel),
    )

    private fun turnNodes(job: RemoteAgentJob, parts: List<RemoteAgentPart>): List<MessageNode> {
        val messages = listOf(
            UIMessage(
                id = stableAgentUuid("acp-user:${job.id}"),
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text(job.prompt)),
                createdAt = parseAgentLocalDateTime(job.created),
            ),
            buildAssistantUIMessage(stableAgentUuid("acp-assistant:${job.id}"), job, null, parts),
        )
        return messages.map { MessageNode(id = it.id, messages = listOf(it)) }
    }

    private fun api(): Api {
        val account = accountProvider() ?: throw WorkflowApiException("请先连接 AnyWorkflow 账号。")
        if (account.baseUrl.trimEnd('/') != DEFAULT_POCKETBASE_URL) {
            throw WorkflowApiException("请使用默认 AnyWorkflow 账号连接 ACP。")
        }
        return Api(account)
    }

    private inner class Api(val account: WorkflowSession) {
        suspend inline fun <reified T> call(
            path: String, body: JsonObject? = null, query: Map<String, String> = emptyMap(),
        ): T = decode(serializer<T>(), path, body, query)

        suspend fun <T> decode(
            serializer: DeserializationStrategy<T>, path: String, body: JsonObject?, query: Map<String, String>,
        ): T = parser.decodeFromJsonElement(serializer, request.execute(account, path, body, query))

        suspend inline fun <reified T> postWithRetry(path: String, body: JsonObject): T {
            try {
                return call(path, body)
            } catch (error: WorkflowApiException) {
                if (error.statusCode != 0 && error.statusCode !in 500..599) throw error
                delay(350)
                return call(path, body)
            }
        }

        suspend inline fun <reified T> page(path: String, page: Int): AcpPage<T> =
            call(path, query = mapOf("page" to page.toString()))

        suspend inline fun <reified T> list(path: String): List<T> = list(path, serializer<AcpPage<T>>())

        suspend fun <T> list(path: String, serializer: DeserializationStrategy<AcpPage<T>>): List<T> {
            val result = mutableListOf<T>()
            var next: Int? = 1
            while (next != null) {
                val page = decode(serializer, path, null, mapOf("page" to next.toString()))
                result.addAll(page.items)
                next = page.nextPage
            }
            return result
        }

        suspend fun events(id: String, after: Int, through: Int): AcpEvents {
            val items = mutableListOf<AcpEvent>()
            var cursor = after
            var batch: AcpEvents
            do {
                batch = call("/tasks/$id/events", query = mapOf("after" to cursor.toString()))
                require(batch.cursor >= cursor && (batch.items.isEmpty() || batch.cursor > cursor))
                items += batch.items
                cursor = batch.cursor
                // Take a snapshot of running turns, and drain terminal turns completely.
                // An empty page signals that we have caught up even while the agent is running.
            } while (batch.items.isNotEmpty() && !batch.done &&
                (cursor < through || batch.items.size == 50 || batch.state.toAgentStatus() in TERMINAL_STATUSES))
            return batch.copy(items = items)
        }
    }
}

internal fun interface AcpRequest {
    suspend fun execute(
        account: WorkflowSession, path: String, body: JsonObject?, query: Map<String, String>,
    ): kotlinx.serialization.json.JsonElement
}

@Serializable
private data class AcpPage<T>(val items: List<T>, val nextPage: Int? = null)

@Serializable
private data class AcpProject(val id: String, val agents: List<String>)

@Serializable
private data class AcpSession(
    val id: String,
    val agent: String,
    val title: String,
    val created: String = "",
    val pinned: Boolean? = null,
    val latestTask: AcpTaskRef? = null,
)

@Serializable
private data class AcpTaskRef(val id: String, val state: String, val created: String)

@Serializable
private data class AcpTask(
    val id: String,
    val sessionId: String,
    val prompt: String,
    val state: String,
    val cursor: Int,
    val created: String,
    val config: JsonObject,
    val retryOf: String? = null,
    val pendingApproval: AcpApproval? = null,
) {
    fun approval(parts: List<RemoteAgentPart>): AgentApproval? = pendingApproval?.let { pending ->
        val data = parts.lastOrNull {
            it.part.text("type") == "approval" && it.part.text("id") == pending.id
        }?.part
        val title = (data?.get("toolCall") as? JsonObject)?.text("title").orEmpty()
        AgentApproval(id, pending.id, title, pending.options.map { AgentApprovalOption(it.optionId, it.name) })
    }

    fun toSubmittedJob() = RemoteSubmittedJob(id, sessionId, state.toAgentStatus())

    fun toJob(session: AcpSession) = RemoteAgentJob(
        id = id, owner = "", threadRoot = sessionId, parentMessage = "", replacesMessage = retryOf.orEmpty(),
        title = session.title,
        prompt = prompt, model = config.text("model").ifBlank { session.agent },
        status = state.toAgentStatus(), error = "", cancelRequested = state == "canceling",
        githubRunId = "", created = created, updated = created, agentModel = acpAgentName(session.agent),
    )
}

@Serializable
private data class AcpApproval(val id: String, val options: List<AcpApprovalOption>)

@Serializable
private data class AcpApprovalOption(val optionId: String, val name: String)

@Serializable
internal data class AcpEvent(val sequence: Int, val type: String, val data: JsonObject?) {
    fun toPart(taskId: String) = RemoteAgentPart(
        key = "$taskId:$sequence", sequence = sequence, revision = sequence,
        part = buildJsonObject {
            data?.forEach { (key, value) -> put(key, value) }
            put("type", type)
        },
    )
}

@Serializable
private data class AcpEvents(val items: List<AcpEvent>, val cursor: Int, val state: String, val done: Boolean)

private val TERMINAL_STATUSES = setOf("succeeded", "failed", "canceled")

internal fun String.toAgentStatus(): String = when (this) {
    "scheduled", "queued" -> "queued"
    "running", "canceling" -> "running"
    "succeeded" -> "succeeded"
    "canceled" -> "canceled"
    else -> "failed"
}

private fun RemoteAgentJob.toMessage(cursor: Int) = RemoteAgentMessage(
    id = id, owner = owner, job = id, index = 0, part = 0, sourceName = "", sourceId = "",
    inputText = prompt, status = status, reply = "", error = error, finishReason = "",
    startedAt = created, finishedAt = updated, updated = updated, streamRevision = cursor,
    usage = RemoteAgentUsage(),
)

internal fun acpAgentName(agent: String): String = when (agent) {
    "antigravity" -> "Antigravity"
    "codex" -> "Codex"
    "claude-code" -> "Claude Code"
    "command-code" -> "Command Code"
    "opencode" -> "OpenCode"
    else -> agent
}

/**
 * Adapters advertise stable picker values, not upstream model ids. Sending the raw id
 * makes the Gateway reject the turn with `unsupported_model`, taking the thinking level
 * down with it because the whole config fails.
 */
internal fun acpModelValue(agent: String, modelId: String): String {
    val clean = modelId.trim().removePrefix("models/")
    if (agent != "claude-code") return clean
    return when (clean) {
        "claude-sonnet-5-5" -> "sonnet"
        "claude-opus-5-5" -> "opus"
        "claude-haiku-5-5" -> "haiku"
        else -> clean
    }
}

internal fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

internal fun buildAcpPrompt(
    prompt: String, sources: List<RemoteAgentSourceInput>, images: List<String>, instructions: String?,
    history: List<RemoteHistoryTurn>,
): String = buildString {
    instructions?.takeIf { it.isNotBlank() }?.let { append("会话指令：\n").append(it).append("\n\n") }
    if (history.isNotEmpty()) {
        appendLine("此前对话：")
        for (turn in history) {
            appendLine(if (turn.role == "user") "用户：" else "助手：")
            appendLine(turn.text)
            turn.images.forEach { appendLine(it) }
        }
        appendLine()
        appendLine("当前消息：")
    }
    append(prompt)
    sources.forEach { append("\n\n附件 ").append(it.name).append("：\n").append(it.content) }
    images.forEach { append("\n图片链接：").append(it) }
}.trim().also {
    if (it.isBlank() || it.length > 48_000 || it.toByteArray().size > 60_000) {
        throw WorkflowApiException("ACP 消息为空或超出长度限制。请缩短消息或附件。")
    }
}
