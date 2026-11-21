package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.ModelSnapshot
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AgentChatReplyTest {
    private val initialReply = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = emptyList(),
        modelSnapshot = ModelSnapshot(displayName = "Gemini Flash-Lite", modelId = "gemini-3.5-flash-lite"),
    )

    @Test
    fun `completed stream keeps its original answer without retrying`() = runTest {
        val replies = mutableListOf<UIMessage>()
        collectAgentChatReply(
            initialReply,
            flowOf(
                StreamChunk.ReasoningDelta("thought", "considering"),
                StreamChunk.TextDelta("text", "Taylor "),
                StreamChunk.TextDelta("text", "series"),
                StreamChunk.Finish("STOP"),
            ),
            onReply = { replies += it },
            retry = { error("A normal reply should not retry") },
        )

        assertEquals("Taylor series", replies.last().toText().trim())
        assertTrue(replies.last().parts.first() is UIMessagePart.Reasoning)
    }

    @Test
    fun `thought-only stream is replaced by a single complete reply`() = runTest {
        val replies = mutableListOf<UIMessage>()
        val recovered = UIMessage.assistant("Recovered answer")
        var retries = 0
        collectAgentChatReply(
            initialReply,
            flowOf(
                StreamChunk.ReasoningDelta("thought", "considering"),
                StreamChunk.TextDelta("text", " "),
                StreamChunk.Finish(),
            ),
            onReply = { replies += it },
            retry = {
                retries++
                TextGenerationResult("retry-id", "gemini-3.5-flash-lite", recovered, "STOP")
            },
        )

        assertEquals(1, retries)
        assertTrue(replies.first().parts.single() is UIMessagePart.Reasoning)
        assertEquals(initialReply.id, replies.last().id)
        assertEquals(initialReply.modelSnapshot, replies.last().modelSnapshot)
        assertEquals(recovered.parts, replies.last().parts)
        assertNotNull(replies.last().finishedAt)
    }

    @Test
    fun `partial replies recover after EOF output limit or transport failure`() = runTest {
        val endings = listOf(
            flowOf(StreamChunk.Finish()),
            flowOf(StreamChunk.Finish("MAX_TOKENS")),
            flow<StreamChunk> { throw IOException("Connection reset") },
        )
        endings.forEach { ending ->
            val replies = mutableListOf<UIMessage>()
            var retries = 0
            collectAgentChatReply(
                initialReply,
                flow {
                    emit(StreamChunk.TextDelta("text", "Partial"))
                    ending.collect { emit(it) }
                },
                onReply = { replies += it },
                retry = {
                    retries++
                    TextGenerationResult("retry-id", "gemini-3.5-flash-lite", UIMessage.assistant("Complete"), "STOP")
                },
            )

            assertEquals(1, retries)
            assertEquals("Partial", replies.first().toText())
            assertEquals("Complete", replies.last().toText())
        }
    }

    @Test
    fun `second truncated reply reports failure and keeps the original partial output`() = runTest {
        val replies = mutableListOf<UIMessage>()
        val failed = runCatching {
            collectAgentChatReply(
                initialReply,
                flowOf(StreamChunk.TextDelta("text", "Partial"), StreamChunk.Finish("MAX_TOKENS")),
                onReply = { replies += it },
                retry = {
                    TextGenerationResult("retry-id", "gemini-3.5-flash-lite", UIMessage.assistant("Still partial"), "MAX_TOKENS")
                },
            )
        }

        assertEquals("Gemini 重试后仍未完成回复（MAX_TOKENS）", failed.exceptionOrNull()?.message)
        assertEquals("Partial", replies.last().toText())
    }

    @Test
    fun `empty retry reports failure and retains the streamed thinking`() = runTest {
        val replies = mutableListOf<UIMessage>()
        val failed = runCatching {
            collectAgentChatReply(
                initialReply,
                flowOf(StreamChunk.ReasoningDelta("thought", "considering"), StreamChunk.Finish("MAX_TOKENS")),
                onReply = { replies += it },
                retry = { TextGenerationResult("retry-id", "gemini-3.5-flash-lite", initialReply, "STOP") },
            )
        }

        assertEquals("Gemini 重试后仍未返回正文（STOP）", failed.exceptionOrNull()?.message)
        assertEquals("considering", (replies.last().parts.single() as UIMessagePart.Reasoning).reasoning)
    }

    @Test
    fun `explicit upstream block reports its reason without retrying`() = runTest {
        val failed = runCatching {
            collectAgentChatReply(
                initialReply,
                flowOf(StreamChunk.Finish("SAFETY")),
                onReply = {},
                retry = { error("A blocked reply should not retry") },
            )
        }

        assertEquals("Gemini 未正常完成回复（SAFETY）", failed.exceptionOrNull()?.message)
    }
}
