package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AgentGatewayChatTest {
    @Test
    fun `streams a complete reply with cloud and inline images in history`() = runTest {
        val imageUrl = "https://files.example.test/image-sources/drawing.png"
        val inlineImage = "data:image/png;base64,AQID"
        val imageBytes = byteArrayOf(1, 2, 3)
        val answer = "Taylor series. ".repeat(160)
        val events = buildString {
            repeat(160) {
                append("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Taylor series. \"}]}}]}\n\n")
            }
            append("data: {\"candidates\":[{\"finishReason\":\"STOP\"}]}\n\n")
        }

        var imageRequests = 0
        var modelRequests = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val body = when (request.url.host) {
                    "files.example.test" -> {
                        imageRequests++
                        assertNull(request.header("x-goog-api-key"))
                        imageBytes.toResponseBody("image/png".toMediaType())
                    }

                    "gateway.example.test" -> {
                        modelRequests++
                        assertEquals("/v1beta/models/gemini-3.5-flash-lite:streamGenerateContent", request.url.encodedPath)
                        val payload = Buffer().apply { checkNotNull(request.body).writeTo(this) }.readUtf8()
                        val root = Json.parseToJsonElement(payload).jsonObject
                        val messages = root.getValue("contents").jsonArray
                        val images = messages.flatMap { message ->
                            message.jsonObject.getValue("parts").jsonArray.mapNotNull {
                                it.jsonObject["inlineData"]?.jsonObject
                            }
                        }
                        assertEquals(3, images.size)
                        images.forEach { image ->
                            assertEquals("image/png", image.getValue("mimeType").jsonPrimitive.contentOrNull)
                            assertEquals("AQID", image.getValue("data").jsonPrimitive.contentOrNull)
                        }
                        val thinking = root.getValue("generationConfig").jsonObject.getValue("thinkingConfig").jsonObject
                        assertEquals("minimal", thinking.getValue("thinkingLevel").jsonPrimitive.contentOrNull)
                        assertFalse(thinking.getValue("includeThoughts").jsonPrimitive.boolean)
                        events.toResponseBody("text/event-stream".toMediaType())
                    }

                    else -> error("Unexpected host ${request.url.host}")
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            .build()

        val model = AgentModelEntity(
            id = "test",
            modelId = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash Lite",
            baseUrl = "https://gateway.example.test",
            createdAt = 0,
            updatedAt = 0,
        )
        val firstTurn = UIMessage.user("What is this?").let {
            it.copy(parts = it.parts + UIMessagePart.Image(imageUrl))
        }
        val currentTurn = UIMessage.user("Compare both.").let {
            it.copy(parts = it.parts + listOf(UIMessagePart.Image(imageUrl), UIMessagePart.Image(inlineImage)))
        }
        var reply = UIMessage.assistant("").copy(parts = emptyList())
        AgentGatewayClient(client).streamChat(
            modelEntity = model,
            messages = listOf(firstTurn, UIMessage.assistant("A drawing."), currentTurn),
            systemPrompt = "",
            reasoningLevel = ReasoningLevel.OFF,
            temperature = null,
            fallbackBaseUrl = "",
            fallbackApiKey = "gateway-test",
            initialReply = reply,
            onReply = { reply = it },
        )

        assertEquals(answer, reply.toText())
        assertEquals(1, imageRequests)
        assertEquals(1, modelRequests)
        assertEquals(imageUrl, (firstTurn.parts.last() as UIMessagePart.Image).url)
    }
}
