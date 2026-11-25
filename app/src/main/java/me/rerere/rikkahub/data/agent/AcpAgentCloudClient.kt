package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.ModelSnapshot
import me.rerere.ai.ui.ReasoningType
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.workflow.WorkflowApiException
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val ACP_REMOTE_URL = "https://rikka-remote-agent.2212148739lbw.workers.dev"

/**
 * ACP-based Agent Cloud Client that uses the unified remote agent infrastructure.
 * Maps ACP sessions/tasks to the existing AgentCloudClient interface.
 */
class AcpAgentCloudClient(
    private val workflowRepository: WorkflowRepository,
    client: OkHttpClient,
) : AgentCloudClient {
    private val parser = Json { ignoreUnknownKeys = true }
    private val streams = EventSources.createFactory(
        client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    )
    
    // Map conversation IDs to ACP sessions
    private val conversationToSession = ConcurrentHashMap<String, String>()
    // Map job IDs (task IDs) to session IDs for reverse lookup
    private val jobToSession = ConcurrentHashMap<String, String>()
    // Cache session details
    private val sessionCache = ConcurrentHashMap<String, AcpSession>()
    // Cache task details
    private val taskCache = ConcurrentHashMap<String, AcpTask>()
    
    override fun hasSession(): Boolean = workflowRepository.session.value != null
    
    override suspend fun listRemoteThreads(threadRoot: String?): List<RemoteAgentThread> = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        
        // In ACP, we list sessions (each session is a thread)
        val acpSessions = fetchAcpSessions(session)
        
        acpSessions.map { acpSession ->
            sessionCache[acpSession.id] = acpSession
            
            // Get the latest task to determine status
            val tasks = fetchAcpTasks(session, acpSession.id, page = 1, perPage = 1)
            val latestTask = tasks.firstOrNull()
            val status = latestTask?.state?.let { mapAcpStateToStatus(it) } ?: "completed"
            
            RemoteAgentThread(
                jobId = acpSession.id,
                threadRoot = acpSession.id,
                title = acpSession.title,
                status = status,
                created = acpSession.updated,
                updated = acpSession.updated,
                parentMessage = latestTask?.id ?: "",
                archived = false,
                pinned = false,
                folderId = null,
                hasFolderIdField = false,
            )
        }
    }
    
    override suspend fun fetchThreadConversation(
        latestJobId: String,
        previous: RemoteThreadConversation?,
        refreshJob: Boolean,
    ): RemoteThreadConversation = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        
        // latestJobId can be either a session ID or a task ID
        // Try to determine which one it is
        val (sessionId, taskId) = resolveSessionAndTask(session, latestJobId)
        
        // Fetch all tasks for this session
        val tasks = fetchAcpTasks(session, sessionId)
        val latestTask = tasks.firstOrNull { it.id == taskId } ?: tasks.firstOrNull()
        
        if (latestTask == null) {
            throw WorkflowApiException("No tasks found for session")
        }
        
        // Fetch events for the latest task
        val events = fetchAcpEvents(session, latestTask.id, after = previous?.partsCursor ?: 0)
        
        // Convert to message nodes
        val messageNodes = convertTasksToMessageNodes(tasks)
        
        // Convert events to parts
        val parts = convertEventsToParts(latestTask, events, previous?.partsCursor ?: 0)
        
        // Build job representation
        val job = RemoteGeminiJob(
            id = latestTask.id,
            owner = session.userId,
            threadRoot = sessionId,
            parentMessage = "",
            replacesMessage = "",
            title = sessionCache[sessionId]?.title ?: "",
            prompt = latestTask.prompt,
            model = latestTask.config["model"] as? String ?: "antigravity",
            status = mapAcpStateToStatus(latestTask.state),
            error = "",
            cancelRequested = latestTask.state == "canceling",
            githubRunId = "",
            created = latestTask.created,
            updated = latestTask.updated,
            agentModel = sessionCache[sessionId]?.agent ?: "antigravity",
        )
        
        // Build message representation (for the assistant response)
        val message = if (parts.isNotEmpty()) {
            RemoteGeminiMessage(
                id = "${latestTask.id}-msg",
                owner = session.userId,
                job = latestTask.id,
                index = 0,
                part = 0,
                sourceName = "",
                sourceId = "",
                inputText = "",
                status = mapAcpStateToStatus(latestTask.state),
                reply = "",
                error = "",
                finishReason = when (latestTask.state) {
                    "succeeded" -> "STOP"
                    "failed" -> "ERROR"
                    "canceled" -> "CANCELLED"
                    else -> ""
                },
                startedAt = latestTask.created,
                finishedAt = latestTask.updated,
                updated = latestTask.updated,
                streamRevision = latestTask.cursor,
                usage = RemoteGeminiUsage(),
            )
        } else null
        
        RemoteThreadConversation(
            job = job,
            message = message,
            messageNodes = messageNodes,
            isActive = latestTask.state in listOf("scheduled", "queued", "running", "canceling"),
            parts = parts,
            partsCursor = latestTask.cursor,
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
        
        // Find or create ACP session
        val conversationId = parentMessageId ?: title
        val acpSessionId = conversationToSession[conversationId] ?: run {
            // Create new session
            val sessionId = createAcpSession(session, title, agentModelOverride ?: "antigravity")
            conversationToSession[conversationId] = sessionId
            sessionId
        }
        
        // Build config from overrides
        val config = buildJsonObject {
            if (modelOverride != null) put("model", modelOverride)
            if (thinkingLevelOverride != null) put("thought_level", thinkingLevelOverride)
        }
        
        // Build files structure if needed
        val files = if (sources.isNotEmpty() || images.isNotEmpty()) {
            buildJsonObject {
                if (sources.isNotEmpty() || images.isNotEmpty()) {
                    put("inputs", buildJsonArray {
                        // TODO: Upload files to ACP and get their IDs
                    })
                }
                put("outputs", buildJsonArray {})
            }
        } else null
        
        // Submit task to ACP API
        val taskPayload = buildJsonObject {
            put("prompt", prompt)
            put("requestKey", Uuid.random().toString())
            if (config.isNotEmpty()) put("config", config)
            if (files != null) put("files", files)
        }
        
        val task = workflowRepository.executeJsonCancellable(
            baseUrl = ACP_REMOTE_URL,
            path = "/v1/sessions/$acpSessionId/tasks",
            method = "POST",
            body = taskPayload,
            token = session.token,
            authPrefix = "Bearer ",
        ).jsonObject
        
        val taskId = task["id"]?.jsonPrimitive?.content ?: throw WorkflowApiException("No task ID returned")
        val state = task["state"]?.jsonPrimitive?.content ?: "queued"
        
        // Store mappings
        jobToSession[taskId] = acpSessionId
        
        // Cache task details
        taskCache[taskId] = AcpTask(
            id = taskId,
            sessionId = acpSessionId,
            prompt = prompt,
            state = state,
            cursor = task["cursor"]?.jsonPrimitive?.intOrNull ?: 0,
            created = task["created"]?.jsonPrimitive?.content ?: "",
            updated = task["created"]?.jsonPrimitive?.content ?: "",
            config = if (config.isNotEmpty()) config else JsonObject(emptyMap()),
        )
        
        RemoteSubmittedJob(
            jobId = taskId,
            threadRoot = acpSessionId,
            status = mapAcpStateToStatus(state),
        )
    }
    
    override suspend fun cancelJob(jobId: String) = withContext(Dispatchers.IO) {
        val session = workflowRepository.requireSession()
        
        workflowRepository.executeJsonCancellable(
            baseUrl = ACP_REMOTE_URL,
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
    ) {
        // ACP sessions don't support metadata updates yet
        // This is a no-op for now
    }
    
    override suspend fun deleteThread(jobId: String) {
        // ACP doesn't support session deletion
        // This is a no-op for now
    }
    
    override fun buildAssistantUIMessage(
        messageId: Uuid,
        job: RemoteGeminiJob,
        message: RemoteGeminiMessage?,
        parts: List<RemoteAgentPart>,
    ): UIMessage {
        val uiParts = mutableListOf<UIMessagePart>()
        
        for (part in parts) {
            val partObj = part.part
            val partType = partObj["type"]?.jsonPrimitive?.content ?: continue
            
            when (partType) {
                "text" -> {
                    val text = partObj["text"]?.jsonPrimitive?.content ?: continue
                    uiParts.add(UIMessagePart.Text(text))
                }
                "thinking" -> {
                    val text = partObj["text"]?.jsonPrimitive?.content ?: continue
                    uiParts.add(UIMessagePart.Thinking(text))
                }
                "tool_call" -> {
                    val id = partObj["id"]?.jsonPrimitive?.content ?: ""
                    val name = partObj["name"]?.jsonPrimitive?.content ?: ""
                    val input = partObj["input"]?.toString() ?: ""
                    uiParts.add(UIMessagePart.ToolCall(id, name, input, ServerToolStatus.PENDING))
                }
                "tool_result" -> {
                    val callId = partObj["callId"]?.jsonPrimitive?.content ?: ""
                    val result = partObj["result"]?.toString() ?: ""
                    uiParts.add(UIMessagePart.ToolResult(callId, result))
                }
                "artifact" -> {
                    val path = partObj["path"]?.jsonPrimitive?.content ?: ""
                    uiParts.add(UIMessagePart.Text("📎 Generated file: $path"))
                }
            }
        }
        
        return UIMessage(
            id = messageId,
            role = MessageRole.ASSISTANT,
            parts = uiParts,
            createAt = message?.let { 
                runCatching { 
                    Instant.from(DateTimeFormatter.ISO_INSTANT.parse(it.finishedAt)) 
                }.getOrNull() 
            } ?: Instant.now(),
            annotations = emptyList(),
        )
    }
    
    // Helper functions
    
    private suspend fun createAcpSession(
        session: me.rerere.rikkahub.data.workflow.WorkflowSession,
        title: String,
        agent: String,
    ): String {
        // Get list of projects to find the first available one
        val projects = fetchAcpProjects(session)
        val project = projects.firstOrNull() ?: throw WorkflowApiException("No ACP projects available. Please configure projects in the remote agent gateway.")
        
        // Ensure the selected agent is available
        val selectedAgent = if (agent in project.agents) agent else project.agents.firstOrNull() ?: "antigravity"
        
        // Create new session
        val sessionPayload = buildJsonObject {
            put("projectId", project.id)
            put("agent", selectedAgent)
            put("title", title)
            put("requestKey", Uuid.random().toString())
        }
        
        val created = workflowRepository.executeJsonCancellable(
            baseUrl = ACP_REMOTE_URL,
            path = "/v1/sessions",
            method = "POST",
            body = sessionPayload,
            token = session.token,
            authPrefix = "Bearer ",
        ).jsonObject
        
        val sessionId = created["id"]?.jsonPrimitive?.content ?: throw WorkflowApiException("No session ID returned")
        
        // Cache session details
        sessionCache[sessionId] = AcpSession(
            id = sessionId,
            projectId = project.id,
            agent = selectedAgent,
            title = title,
            updated = created["updated"]?.jsonPrimitive?.content ?: "",
            options = created["options"]?.jsonArray ?: JsonArray(emptyList()),
        )
        
        return sessionId
    }
    
    private suspend fun fetchAcpProjects(session: me.rerere.rikkahub.data.workflow.WorkflowSession): List<AcpProject> {
        val response = workflowRepository.executeJsonCancellable(
            baseUrl = ACP_REMOTE_URL,
            path = "/v1/projects",
            method = "GET",
            token = session.token,
            authPrefix = "Bearer ",
        ).jsonObject
        
        val items = response["items"]?.jsonArray ?: return emptyList()
        return items.mapNotNull { el ->
            val obj = el.jsonObject
            AcpProject(
                id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                name = obj["name"]?.jsonPrimitive?.content ?: "",
                agents = obj["agents"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            )
        }
    }
    
    private suspend fun fetchAcpSessions(session: me.rerere.rikkahub.data.workflow.WorkflowSession): List<AcpSession> {
        val sessions = mutableListOf<AcpSession>()
        var page = 1
        
        while (page <= 10) { // Limit to 10 pages = 500 sessions
            val response = workflowRepository.executeJsonCancellable(
                baseUrl = ACP_REMOTE_URL,
                path = "/v1/sessions",
                method = "GET",
                query = mapOf("page" to page.toString()),
                token = session.token,
                authPrefix = "Bearer ",
            ).jsonObject
            
            val items = response["items"]?.jsonArray ?: break
            if (items.isEmpty()) break
            
            for (el in items) {
                val obj = el.jsonObject
                val acpSession = AcpSession(
                    id = obj["id"]?.jsonPrimitive?.content ?: continue,
                    projectId = obj["projectId"]?.jsonPrimitive?.content ?: "",
                    agent = obj["agent"]?.jsonPrimitive?.content ?: "",
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    updated = obj["updated"]?.jsonPrimitive?.content ?: "",
                    options = obj["options"]?.jsonArray ?: JsonArray(emptyList()),
                )
                sessions.add(acpSession)
                sessionCache[acpSession.id] = acpSession
            }
            
            val nextPage = response["nextPage"]?.jsonPrimitive?.intOrNull
            if (nextPage == null) break
            page = nextPage
        }
        
        return sessions
    }
    
    private suspend fun fetchAcpTasks(
        session: me.rerere.rikkahub.data.workflow.WorkflowSession,
        sessionId: String,
        page: Int = 1,
        perPage: Int = 50,
    ): List<AcpTask> {
        val tasks = mutableListOf<AcpTask>()
        var currentPage = page
        
        while (currentPage <= 10) { // Limit pages
            val response = workflowRepository.executeJsonCancellable(
                baseUrl = ACP_REMOTE_URL,
                path = "/v1/sessions/$sessionId/tasks",
                method = "GET",
                query = mapOf("page" to currentPage.toString()),
                token = session.token,
                authPrefix = "Bearer ",
            ).jsonObject
            
            val items = response["items"]?.jsonArray ?: break
            if (items.isEmpty()) break
            
            for (el in items) {
                val obj = el.jsonObject
                val task = AcpTask(
                    id = obj["id"]?.jsonPrimitive?.content ?: continue,
                    sessionId = obj["sessionId"]?.jsonPrimitive?.content ?: "",
                    prompt = obj["prompt"]?.jsonPrimitive?.content ?: "",
                    state = obj["state"]?.jsonPrimitive?.content ?: "",
                    cursor = obj["cursor"]?.jsonPrimitive?.intOrNull ?: 0,
                    created = obj["created"]?.jsonPrimitive?.content ?: "",
                    updated = obj["updated"]?.jsonPrimitive?.content ?: "",
                    config = obj["config"]?.jsonObject ?: JsonObject(emptyMap()),
                )
                tasks.add(task)
                taskCache[task.id] = task
                jobToSession[task.id] = sessionId
            }
            
            if (perPage == 1) break // Only wanted one task
            
            val nextPage = response["nextPage"]?.jsonPrimitive?.intOrNull
            if (nextPage == null) break
            currentPage = nextPage
        }
        
        return tasks
    }
    
    private suspend fun fetchAcpEvents(
        session: me.rerere.rikkahub.data.workflow.WorkflowSession,
        taskId: String,
        after: Int = 0,
    ): List<AcpEvent> {
        val response = workflowRepository.executeJsonCancellable(
            baseUrl = ACP_REMOTE_URL,
            path = "/v1/tasks/$taskId/events",
            method = "GET",
            query = mapOf("after" to after.toString()),
            token = session.token,
            authPrefix = "Bearer ",
        ).jsonObject
        
        val items = response["items"]?.jsonArray ?: return emptyList()
        return items.mapNotNull { el ->
            val obj = el.jsonObject
            AcpEvent(
                sequence = obj["sequence"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                type = obj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                data = obj["data"]?.jsonObject,
            )
        }
    }
    
    private suspend fun resolveSessionAndTask(
        session: me.rerere.rikkahub.data.workflow.WorkflowSession,
        jobId: String,
    ): Pair<String, String> {
        // Check if jobId is in our task cache
        taskCache[jobId]?.let { task ->
            return task.sessionId to task.id
        }
        
        // Check if jobId is mapped as a task
        jobToSession[jobId]?.let { sessionId ->
            return sessionId to jobId
        }
        
        // Assume jobId is a session ID
        // Fetch tasks to get the latest one
        val tasks = fetchAcpTasks(session, jobId, page = 1, perPage = 1)
        val latestTask = tasks.firstOrNull() ?: throw WorkflowApiException("No tasks found")
        
        return jobId to latestTask.id
    }
    
    private fun convertTasksToMessageNodes(tasks: List<AcpTask>): List<MessageNode> {
        return tasks.flatMap { task ->
            listOf(
                // User message
                MessageNode(
                    id = Uuid.random(),
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(task.prompt)),
                    childrenIds = emptyList(),
                    createAt = runCatching {
                        Instant.from(DateTimeFormatter.ISO_INSTANT.parse(task.created))
                    }.getOrElse { Instant.now() },
                ),
                // Assistant message placeholder (will be filled by parts)
                MessageNode(
                    id = Uuid.random(),
                    role = MessageRole.ASSISTANT,
                    parts = emptyList(),
                    childrenIds = emptyList(),
                    createAt = runCatching {
                        Instant.from(DateTimeFormatter.ISO_INSTANT.parse(task.updated))
                    }.getOrElse { Instant.now() },
                ),
            )
        }
    }
    
    private fun convertEventsToParts(task: AcpTask, events: List<AcpEvent>, startSequence: Int): List<RemoteAgentPart> {
        val parts = mutableListOf<RemoteAgentPart>()
        var sequence = startSequence
        
        for (event in events.filter { it.sequence > startSequence }) {
            when (event.type) {
                "update" -> {
                    val data = event.data ?: continue
                    val sessionUpdate = data["sessionUpdate"]?.jsonPrimitive?.content
                    val content = data["content"]?.jsonObject
                    
                    when (sessionUpdate) {
                        "agent_message_chunk" -> {
                            if (content?.get("type")?.jsonPrimitive?.content == "text") {
                                val text = content["text"]?.jsonPrimitive?.content ?: continue
                                parts.add(RemoteAgentPart(
                                    key = "${task.id}-${event.sequence}",
                                    sequence = sequence++,
                                    revision = event.sequence,
                                    part = buildJsonObject {
                                        put("type", "text")
                                        put("text", text)
                                    }
                                ))
                            }
                        }
                        "agent_thought_chunk" -> {
                            if (content?.get("type")?.jsonPrimitive?.content == "text") {
                                val text = content["text"]?.jsonPrimitive?.content ?: continue
                                parts.add(RemoteAgentPart(
                                    key = "${task.id}-${event.sequence}",
                                    sequence = sequence++,
                                    revision = event.sequence,
                                    part = buildJsonObject {
                                        put("type", "thinking")
                                        put("text", text)
                                    }
                                ))
                            }
                        }
                        "tool_call" -> {
                            val toolCall = data["toolCall"]?.jsonObject
                            if (toolCall != null) {
                                parts.add(RemoteAgentPart(
                                    key = "${task.id}-${event.sequence}",
                                    sequence = sequence++,
                                    revision = event.sequence,
                                    part = buildJsonObject {
                                        put("type", "tool_call")
                                        put("id", toolCall["id"]?.jsonPrimitive?.content ?: "")
                                        put("name", toolCall["name"]?.jsonPrimitive?.content ?: "")
                                        put("input", toolCall["input"]?.toString() ?: "")
                                    }
                                ))
                            }
                        }
                        "tool_result" -> {
                            val toolResult = data["toolResult"]?.jsonObject
                            if (toolResult != null) {
                                parts.add(RemoteAgentPart(
                                    key = "${task.id}-${event.sequence}",
                                    sequence = sequence++,
                                    revision = event.sequence,
                                    part = buildJsonObject {
                                        put("type", "tool_result")
                                        put("callId", toolResult["toolCallId"]?.jsonPrimitive?.content ?: "")
                                        put("result", toolResult["result"]?.toString() ?: "")
                                    }
                                ))
                            }
                        }
                    }
                }
                "artifact" -> {
                    val data = event.data ?: continue
                    parts.add(RemoteAgentPart(
                        key = "${task.id}-${event.sequence}",
                        sequence = sequence++,
                        revision = event.sequence,
                        part = buildJsonObject {
                            put("type", "artifact")
                            put("path", data["path"]?.jsonPrimitive?.content ?: "")
                            put("size", data["size"]?.jsonPrimitive?.intOrNull ?: 0)
                        }
                    ))
                }
            }
        }
        
        return parts
    }
    
    private fun mapAcpStateToStatus(state: String): String {
        return when (state) {
            "scheduled", "queued" -> "queued"
            "running", "canceling" -> "running"
            "succeeded" -> "completed"
            "failed", "canceled", "interrupted" -> "failed"
            else -> "queued"
        }
    }
}

// Data classes for ACP API
private data class AcpProject(val id: String, val name: String, val agents: List<String>)
private data class AcpSession(
    val id: String,
    val projectId: String,
    val agent: String,
    val title: String,
    val updated: String,
    val options: JsonArray,
)
private data class AcpTask(
    val id: String,
    val sessionId: String,
    val prompt: String,
    val state: String,
    val cursor: Int,
    val created: String,
    val updated: String,
    val config: JsonObject,
)
private data class AcpEvent(val sequence: Int, val type: String, val data: JsonObject?)
