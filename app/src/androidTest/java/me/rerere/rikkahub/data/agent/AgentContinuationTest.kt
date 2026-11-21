package me.rerere.rikkahub.data.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentContinuationTest {
    @Test
    fun continuationRestoresFailedTurnWithStepsAndNoReply() = runBlocking {
        val responses = mapOf(
            "/api/collections/aw_clients/auth-with-password" to
                """{"token":"fixture","record":{"id":"owner","email":"agent@example.test"}}""",
            "/api/anyworkflow/gemini/jobs/next" to """
                {
                  "job":{"id":"next","model":"Antigravity","status":"running","prompt":"继续完成",
                         "parentMessage":"failed","history":[{"role":"user","text":"原始任务"}]},
                  "messages":[{"id":"next-message"}]
                }
            """.trimIndent(),
            "/api/anyworkflow/gemini/jobs/next/messages/next-message" to
                """{"id":"next-message","job":"next","status":"running","streamRevision":0}""",
            "/api/anyworkflow/gemini/jobs/next/messages/next-message/parts" to """{"items":[]}""",
            "/api/collections/aw_gemini_messages/records/failed" to """
                {"id":"failed","job":"first","status":"failed","streamRevision":2,
                 "error":"Antigravity 请求失败（HTTP 400）"}
            """.trimIndent(),
            "/api/collections/aw_gemini_jobs/records/first" to
                """{"id":"first","model":"Antigravity","status":"failed","prompt":"原始任务"}""",
            "/api/anyworkflow/gemini/jobs/first/messages/failed/parts" to """
                {
                  "items":[
                    {"key":"think","type":"reasoning_summary","sequence":1,"revision":1,"content":"分析任务"},
                    {"key":"tool","type":"tool_call","sequence":2,"revision":2,"toolName":"list_dir"}
                  ],
                  "nextCursor":2,"hasMore":false
                }
            """.trimIndent(),
        )
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = responses.getValue(request.url.encodedPath)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody())
                .build()
        }.build()
        val workflow = WorkflowRepository(InstrumentationRegistry.getInstrumentation().context, http, JsonInstant)
        try {
            workflow.login("agent@example.test", "fixture", "https://example.test").getOrThrow()
            val cloud = PocketBaseAgentCloudClient(workflow)
            val thread = cloud.fetchThreadConversation("next")
            val messages = thread.messageNodes.map { it.currentMessage }

            assertEquals(
                listOf(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER, MessageRole.ASSISTANT),
                messages.map { it.role },
            )
            assertEquals("原始任务", (messages[0].parts.single() as UIMessagePart.Text).text)
            assertEquals("分析任务", messages[1].parts.filterIsInstance<UIMessagePart.Reasoning>().single().reasoning)
            assertEquals(1, messages[1].parts.filterIsInstance<UIMessagePart.ServerTool>().size)
            assertEquals("Antigravity 请求失败（HTTP 400）", messages[1].parts.filterIsInstance<UIMessagePart.Notice>().single().text)
            assertEquals("继续完成", (messages[2].parts.single() as UIMessagePart.Text).text)

            val refreshed = cloud.fetchThreadConversation("next", previous = thread, refreshJob = false)
            assertEquals(thread.messageNodes.dropLast(1), refreshed.messageNodes.dropLast(1))
        } finally {
            workflow.logout()
        }
    }
}
