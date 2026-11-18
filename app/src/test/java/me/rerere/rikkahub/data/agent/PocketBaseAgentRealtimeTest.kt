package me.rerere.rikkahub.data.agent

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.workflow.WorkflowSession
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PocketBaseAgentRealtimeTest {
    @Test
    fun subscribesWithAuthAndReceivesMultipleJobsAcrossReconnect() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val threads = Executors.newCachedThreadPool()
        val connects = AtomicInteger()
        val subscriptions = ConcurrentHashMap<String, LinkedBlockingQueue<List<String>>>()
        val authorizations = LinkedBlockingQueue<String>()
        server.executor = threads
        server.createContext("/api/realtime") { exchange ->
            when (exchange.requestMethod) {
                "GET" -> {
                    val connection = connects.incrementAndGet()
                    val id = "connection-$connection"
                    val topics = LinkedBlockingQueue<List<String>>()
                    subscriptions[id] = topics
                    exchange.responseHeaders.set("Content-Type", "text/event-stream")
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.bufferedWriter().use { writer ->
                        writer.write("id: $id\nevent: PB_CONNECT\ndata: {\"clientId\":\"$id\"}\n\n")
                        writer.flush()
                        val (jobTopic, messageTopic) = checkNotNull(topics.poll(5, TimeUnit.SECONDS))
                        if (connection == 1) {
                            writer.write("event: $jobTopic\ndata: {\"record\":{\"id\":\"job-a\",\"threadRoot\":\"root-a\"}}\n\n")
                            writer.write("event: $messageTopic\ndata: {\"record\":{\"job\":\"job-b\"}}\n\n")
                        } else {
                            writer.write("event: $messageTopic\ndata: {\"record\":{\"job\":\"job-a\"}}\n\n")
                        }
                        writer.flush()
                    }
                }
                "POST" -> {
                    val body = exchange.requestBody.use { it.readBytes().decodeToString() }
                    val request = JsonInstant.parseToJsonElement(body).jsonObject
                    val id = request.getValue("clientId").jsonPrimitive.content
                    val topics = request.getValue("subscriptions").jsonArray.map { it.jsonPrimitive.content }
                    authorizations.add(exchange.requestHeaders.getFirst("Authorization").orEmpty())
                    exchange.sendResponseHeaders(204, -1)
                    exchange.close()
                    checkNotNull(subscriptions[id]).add(topics)
                }
            }
        }
        server.start()
        val http = OkHttpClient()

        try {
            val session = WorkflowSession(
                token = "test-token", userId = "account", email = "",
                baseUrl = "http://127.0.0.1:${server.address.port}",
            )
            val received = withTimeout(12_000) {
                PocketBaseAgentRealtime(http).observe(session).take(5).toList()
            }

            assertEquals(
                listOf(
                    AgentRealtimeEvent.Subscribed,
                    AgentRealtimeEvent.JobChanged("job-a", "root-a"),
                    AgentRealtimeEvent.MessageChanged("job-b"),
                    AgentRealtimeEvent.Subscribed,
                    AgentRealtimeEvent.MessageChanged("job-a"),
                ),
                received,
            )
            assertEquals(2, connects.get())
            assertEquals(listOf("test-token", "test-token"), authorizations.toList())
            assertTrue(subscriptions.keys.containsAll(listOf("connection-1", "connection-2")))
        } finally {
            server.stop(0)
            threads.shutdownNow()
            http.dispatcher.executorService.shutdownNow()
            http.connectionPool.evictAll()
        }
    }
}
