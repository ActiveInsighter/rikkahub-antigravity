package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import me.rerere.rikkahub.data.workflow.DEFAULT_POCKETBASE_URL
import me.rerere.rikkahub.data.workflow.WorkflowApiException
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.data.workflow.WorkflowSession

private const val RemoteAgentUrl = "https://rikka-remote-agent.2212148739lbw.workers.dev"

@Serializable
internal data class RemoteProject(val id: String, val name: String, val agents: List<String>)

@Serializable
internal data class RemoteSession(
    val id: String, val projectId: String, val agent: String, val title: String,
    val options: List<JsonObject> = emptyList(),
)

@Serializable
internal data class RemoteTask(
    val id: String, val sessionId: String, val prompt: String, val state: String,
    val pendingApproval: RemoteApproval? = null,
)

@Serializable
internal data class RemoteApproval(val id: String, val options: List<RemoteApprovalOption>)

@Serializable
internal data class RemoteApprovalOption(val optionId: String, val kind: String, val name: String)

@Serializable
private data class RemotePage<T>(val items: List<T>, val nextPage: Int? = null)

@Serializable
internal data class RemoteEvent(val sequence: Int, val type: String, val data: JsonObject?)

@Serializable
internal data class RemoteEvents(val items: List<RemoteEvent>, val cursor: Int, val done: Boolean)

internal sealed interface RemoteStream {
    data class Event(val value: RemoteEvent) : RemoteStream
    data class State(val state: String, val done: Boolean) : RemoteStream
}

internal class RemoteAgentClient(
    private val repository: WorkflowRepository,
    client: OkHttpClient,
    private val account: WorkflowSession,
) {
    private val parser = Json { ignoreUnknownKeys = true }
    private val streams = EventSources.createFactory(
        client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    )

    init {
        require(account.baseUrl.trimEnd('/') == DEFAULT_POCKETBASE_URL) {
            "远程开发使用默认 AnyWorkflow 账号，请先在 Agent 页面连接。"
        }
    }

    suspend fun projects(): List<RemoteProject> = list("/projects")
    suspend fun sessions(page: Int): Pair<List<RemoteSession>, Int?> = page("/sessions", page)
    suspend fun session(id: String): RemoteSession = call("/sessions/$id")
    suspend fun tasks(id: String, page: Int): Pair<List<RemoteTask>, Int?> = page("/sessions/$id/tasks", page)
    suspend fun task(id: String): RemoteTask = call("/tasks/$id")
    suspend fun events(id: String, after: Int): RemoteEvents = call("/tasks/$id/events", query = mapOf("after" to "$after"))

    suspend fun create(project: RemoteProject, agent: String, key: String): RemoteSession =
        call("/sessions", buildJsonObject {
            put("projectId", project.id)
            put("agent", agent)
            put("title", "${project.name} · ${remoteAgentName(agent)}")
            put("requestKey", key)
        })

    suspend fun submit(
        session: String, prompt: String, key: String, model: String, effort: String, runAt: Long,
    ): RemoteTask = call("/sessions/$session/tasks", buildJsonObject {
        put("prompt", prompt)
        put("requestKey", key)
        put("runAt", runAt)
        put("config", buildJsonObject {
            if (model.isNotEmpty()) put("model", model)
            if (effort.isNotEmpty()) put("thought_level", effort)
        })
    })

    suspend fun cancel(id: String): RemoteTask = call("/tasks/$id/cancel", buildJsonObject {})
    suspend fun retry(id: String, key: String): RemoteTask =
        call("/tasks/$id/retry", buildJsonObject { put("requestKey", key) })

    suspend fun approve(id: String, approval: String, option: String): RemoteTask =
        call("/tasks/$id/approvals/$approval", buildJsonObject { put("optionId", option) })

    fun stream(id: String, after: Int) = callbackFlow<RemoteStream> {
        val request = Request.Builder().url("$RemoteAgentUrl/v1/tasks/$id/events")
            .header("Authorization", "Bearer ${account.token}")
            .header("Accept", "text/event-stream")
            .header("Last-Event-ID", after.toString()).build()
        val source = streams.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val sequence = id?.toIntOrNull()
                val message = runCatching {
                    if (type == "state") {
                        val state = parser.decodeFromString<RemoteEventsState>(data)
                        RemoteStream.State(state.state, state.done)
                    }
                    else if (type != null && sequence != null) {
                        RemoteStream.Event(RemoteEvent(sequence, type, parser.decodeFromString<JsonObject?>(data)))
                    } else null
                }.getOrElse {
                    close(it)
                    return
                }
                if (message != null && trySend(message).isFailure) {
                    close(WorkflowApiException("正在恢复事件流"))
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(WorkflowApiException("远程任务连接中断", response?.code ?: 0))
            }
        })
        awaitClose { source.cancel() }
    }

    private suspend inline fun <reified T> page(path: String, page: Int): Pair<List<T>, Int?> {
        val response = call<RemotePage<T>>(path, query = mapOf("page" to "$page"))
        return response.items to response.nextPage
    }

    private suspend inline fun <reified T> list(path: String): List<T> = buildList {
        var next: Int? = 1
        while (next != null) {
            val (items, following) = page<T>(path, next)
            addAll(items)
            next = following
        }
    }

    private suspend inline fun <reified T> call(
        path: String, body: JsonObject? = null, query: Map<String, String> = emptyMap(),
    ): T = parser.decodeFromJsonElement(
        kotlinx.serialization.serializer<T>(),
        repository.executeJsonCancellable(
            baseUrl = RemoteAgentUrl, path = "/v1$path", method = if (body == null) "GET" else "POST",
            body = body, query = query, token = account.token, authPrefix = "Bearer ",
        ),
    )

    @Serializable
    private data class RemoteEventsState(val state: String, val done: Boolean)
}

internal fun remoteAgentName(id: String): String = when (id) {
    "claude-code" -> "Claude Code"
    "codex" -> "Codex"
    "antigravity" -> "Antigravity"
    "opencode" -> "OpenCode"
    "command-code" -> "Command Code"
    else -> id
}
