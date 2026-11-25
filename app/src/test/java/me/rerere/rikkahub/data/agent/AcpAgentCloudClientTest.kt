package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.workflow.DEFAULT_POCKETBASE_URL
import me.rerere.rikkahub.data.workflow.WorkflowApiException
import me.rerere.rikkahub.data.workflow.WorkflowSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpAgentCloudClientTest {
    private val account = WorkflowSession("test", "owner", "owner@example.test", baseUrl = DEFAULT_POCKETBASE_URL)
    private val session = """{"id":"session","title":"Thread","agent":"codex","pinned":false}"""

    @Test
    fun firstTurnUsesAnAuthorizedProjectAndContinuationUsesItsSession() = runBlocking {
        val keys = mutableListOf<String>()
        val client: AgentCloudClient = AcpAgentCloudClient({ account }, AcpRequest { _, path, body, query ->
            when (path) {
                "/projects" -> if (query["page"] == "1") {
                    json("""{"items":[{"id":"other","agents":["antigravity"]}],"nextPage":2}""")
                } else {
                    json("""{"items":[{"id":"selected","agents":["codex"]}],"nextPage":null}""")
                }
                "/sessions" -> {
                    assertEquals("selected", body?.text("projectId"))
                    assertEquals("codex", body?.text("agent"))
                    json(session)
                }
                "/sessions/session" -> {
                    assertEquals(null, body)
                    json(session)
                }
                "/sessions/session/tasks" -> {
                    assertNotNull(body)
                    keys += body!!.text("requestKey")
                    if (keys.size == 1) throw WorkflowApiException("lost response", 502)
                    if (keys.size == 3) {
                        assertEquals("follow up", body.text("prompt"))
                        assertTrue(body.getValue("config").jsonObject.isEmpty())
                    }
                    json(task(if (keys.size == 2) "first" else "second", "scheduled", 0))
                }
                else -> error("Unexpected $path")
            }
        })

        val first = client.submitMessage(prompt = "original", title = "Thread", agentId = "codex")
        val second = client.submitMessage(
            prompt = "follow up",
            threadRoot = first.threadRoot,
            agentId = "antigravity",
            modelOverride = "other-model",
            initialHistory = listOf(RemoteHistoryTurn("user", "must not be replayed")),
        )
        assertEquals("session", second.threadRoot)
        assertEquals("second", second.jobId)
        assertEquals(keys[0], keys[1])
        assertTrue(keys[1] != keys[2])
    }

    @Test
    fun claudeCodeModelsUseTheAdapterPickerValues() {
        assertEquals("sonnet", acpModelValue("claude-code", "claude-sonnet-5-5"))
        assertEquals("opus", acpModelValue("claude-code", "claude-opus-5-5"))
        assertEquals("haiku", acpModelValue("claude-code", "models/claude-haiku-5-5"))
        assertEquals("claude-fable-5-1[1m]", acpModelValue("claude-code", "claude-fable-5-1[1m]"))
        assertEquals("gpt-6.2-sol", acpModelValue("codex", "gpt-6.2-sol"))
    }

    @Test
    fun missingSessionDoesNotCreateAnUnrelatedConversation() = runBlocking {
        val client: AgentCloudClient = AcpAgentCloudClient({ account }, AcpRequest { _, path, _, _ ->
            assertEquals("/sessions/old", path)
            throw WorkflowApiException("not found", 404)
        })
        val result = runCatching { client.submitMessage(prompt = "follow up", threadRoot = "old") }
        assertEquals(404, (result.exceptionOrNull() as WorkflowApiException).statusCode)
    }

    @Test
    fun loadsOlderRepliesAndReusesNodesAndEventCursorAcrossTurns() = runBlocking {
        var afterFirstPoll = false
        val cursors = mutableListOf<String>()
        val client = AcpAgentCloudClient({ account }, AcpRequest { _, path, _, query ->
            when (path) {
                "/sessions/session" -> json(session)
                "/sessions/session/tasks" -> if (query["page"] == "1") {
                    val tasks = if (afterFirstPoll) task("new", "running", 2) + "," + task("latest", "succeeded", 51)
                    else task("latest", "succeeded", 51)
                    json("""{"items":[$tasks],"nextPage":2}""")
                } else {
                    json("""{"items":[${task("old", "succeeded", 1)}],"nextPage":null}""")
                }
                "/tasks/latest" -> json(task("latest", "succeeded", 51))
                "/tasks/old/events" -> {
                    assertFalse("The finalized earlier reply should be reused", afterFirstPoll)
                    json(batch("succeeded", 1, true, chunk(1, "older answer")))
                }
                "/tasks/latest/events" -> {
                    val cursor = query.getValue("after")
                    cursors += cursor
                    when (cursor) {
                        "0" -> json(batch("succeeded", 50, false, (1..50).joinToString(",") { chunk(it, "x") }))
                        "50" -> json(batch("succeeded", 51, true, chunk(51, " final")))
                        "51" -> json(batch("succeeded", 51, true, ""))
                        else -> error("Unexpected cursor $cursor")
                    }
                }
                "/tasks/new" -> json(task("new", if (cursors.size > 3) "succeeded" else "running", 2))
                "/tasks/new/events" -> {
                    val cursor = query.getValue("after")
                    cursors += cursor
                    if (cursor == "0") json(batch("running", 2, false, """
                        {"sequence":1,"type":"update","data":{"sessionUpdate":"tool_call","toolCallId":"a","title":"Read"}},
                        ${chunk(2, "first")}
                    """.trimIndent())) else json(batch("succeeded", 4, true, """
                        {"sequence":3,"type":"update","data":{"sessionUpdate":"tool_call_update","toolCallId":"a","status":"completed"}},
                        ${chunk(4, " second")}
                    """.trimIndent()))
                }
                else -> error("Unexpected $path")
            }
        })

        val restored = client.fetchThreadConversation("latest")
        assertFalse(restored.isActive)
        assertEquals(4, restored.messageNodes.size)
        assertEquals("older answer", restored.messageNodes[1].currentMessage.toText())
        assertEquals("x".repeat(50) + " final", restored.messageNodes[3].currentMessage.toText())
        val unchanged = client.fetchThreadConversation("latest", restored)
        afterFirstPoll = true
        val running = client.fetchThreadConversation("new", unchanged)
        assertTrue(running.isActive)
        assertSame(restored.messageNodes[1], running.messageNodes[1])
        val completed = client.fetchThreadConversation("new", running)
        assertFalse(completed.isActive)
        assertEquals(listOf("0", "50", "51", "0", "2"), cursors)
        val parts = completed.messageNodes.last().currentMessage.parts
        assertEquals(ServerToolStatus.COMPLETED, (parts[0] as UIMessagePart.ServerTool).status)
        assertEquals("first second", (parts[1] as UIMessagePart.Text).text)
    }

    private fun json(raw: String): JsonElement = Json.parseToJsonElement(raw)

    private fun task(id: String, state: String, cursor: Int): String {
        val day = mapOf("old" to "01", "latest" to "02", "new" to "03")[id] ?: "04"
        return """{"id":"$id","sessionId":"session","prompt":"ask $id","state":"$state","cursor":$cursor,"created":"2026-11-${day}T12:00:00Z","config":{}}"""
    }

    private fun batch(state: String, cursor: Int, done: Boolean, items: String) =
        """{"state":"$state","cursor":$cursor,"done":$done,"items":[$items]}"""

    private fun chunk(sequence: Int, value: String) =
        """{"sequence":$sequence,"type":"update","data":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"$value"}}}"""
}
