package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.rerere.common.http.await
import me.rerere.rikkahub.data.workflow.WorkflowSession
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal sealed interface AgentRealtimeEvent {
    data object Subscribed : AgentRealtimeEvent
    data class JobChanged(val jobId: String, val threadRoot: String) : AgentRealtimeEvent
    data class MessageChanged(val jobId: String) : AgentRealtimeEvent
}

internal class PocketBaseAgentRealtime(private val http: OkHttpClient) {
    private val events = EventSources.createFactory(
        http.newBuilder().readTimeout(6, TimeUnit.MINUTES).build()
    )

    fun observe(session: WorkflowSession): Flow<AgentRealtimeEvent> = flow {
        val url = "${session.baseUrl}/api/realtime"
        var retryDelay = 1_000L
        flow {
            frames(url).collect { frame ->
                if (frame.type == "PB_CONNECT") {
                    subscribe(url, session.token, frame.id ?: throw IOException("Missing PocketBase client ID"))
                    retryDelay = 1_000L
                    emit(AgentRealtimeEvent.Subscribed)
                } else {
                    parseEvent(frame)?.let { emit(it) }
                }
            }
        }.retryWhen { error, _ ->
            if (error !is IOException) return@retryWhen false
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
            true
        }.collect { emit(it) }
    }

    private data class Frame(val id: String?, val type: String, val data: String)

    private fun frames(url: String): Flow<Frame> = callbackFlow {
        val request = Request.Builder().url(url).header("Accept", "text/event-stream").build()
        val stream = events.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (type == null) return
                if (trySend(Frame(id, type, data)).isFailure) {
                    close(IOException("PocketBase realtime buffer overflow"))
                    eventSource.cancel()
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close(IOException("PocketBase realtime disconnected"))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(IOException("PocketBase realtime disconnected", t))
            }
        })
        awaitClose { stream.cancel() }
    }.buffer(128)

    private suspend fun subscribe(url: String, token: String, clientId: String) {
        val body = buildJsonObject {
            put("clientId", clientId)
            putJsonArray("subscriptions") {
                add(JsonPrimitive(topic(JOBS_COLLECTION, "id,threadRoot")))
                // The server updates message.streamRevision in the same transaction as progress parts.
                add(JsonPrimitive(topic(MESSAGES_COLLECTION, "job")))
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", token)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = http.newCall(request)
        call.timeout().timeout(20, TimeUnit.SECONDS)
        try {
            call.await().use { response ->
                if (!response.isSuccessful) throw IOException("PocketBase subscription failed (${response.code})")
            }
        } finally {
            call.cancel()
        }
    }

    private fun topic(collection: String, fields: String): String {
        val options = buildJsonObject {
            putJsonObject("query") { put("fields", fields) }
        }
        return "$collection/*?options=${URLEncoder.encode(options.toString(), "UTF-8")}"
    }

    private fun parseEvent(frame: Frame): AgentRealtimeEvent? {
        val payload = runCatching { JsonInstant.parseToJsonElement(frame.data) as? JsonObject }.getOrNull()
        val record = payload?.get("record") as? JsonObject ?: return null
        fun field(name: String) = (record[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return when (frame.type.substringBefore('?')) {
            "$JOBS_COLLECTION/*", JOBS_COLLECTION -> field("id").takeIf { it.isNotBlank() }?.let { id ->
                AgentRealtimeEvent.JobChanged(id, field("threadRoot").ifBlank { id })
            }
            "$MESSAGES_COLLECTION/*", MESSAGES_COLLECTION -> field("job").takeIf { it.isNotBlank() }
                ?.let { AgentRealtimeEvent.MessageChanged(it) }
            else -> null
        }
    }
}
