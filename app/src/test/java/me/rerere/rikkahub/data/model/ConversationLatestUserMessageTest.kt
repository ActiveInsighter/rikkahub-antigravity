package me.rerere.rikkahub.data.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationLatestUserMessageTest {
    @Test
    fun `returns latest non-synthetic non-notice user message timestamp`() {
        val t1 = LocalDateTime(2026, 4, 1, 10, 0, 0)
        val t2 = LocalDateTime(2026, 4, 1, 10, 5, 0)
        val t3 = LocalDateTime(2026, 4, 1, 10, 8, 0)
        val t4 = LocalDateTime(2026, 4, 1, 10, 10, 0)

        val user1 = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("first")), createdAt = t1)
        val assistant1 = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("reply1")), createdAt = t2)
        val user2 = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("second")), createdAt = t2)
        val assistant2 = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("reply2")), createdAt = t3)
        val noticeOnly = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Notice(title = "info", text = "done")),
            createdAt = t4,
        )
        val syntheticUser = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text("internal")),
            createdAt = t4,
            isSynthetic = true,
        )

        val nodes = listOf(
            user1.toMessageNode(),
            assistant1.toMessageNode(),
            user2.toMessageNode(),
            assistant2.toMessageNode(),
            noticeOnly.toMessageNode(),
            syntheticUser.toMessageNode(),
        )

        val expected = t2.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        assertEquals(expected, nodes.latestUserMessageEpochMilli())

        val conv = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = nodes,
        )
        assertEquals(expected, conv.latestUserMessageInstant()?.toEpochMilli())
    }

    @Test
    fun `returns null when no user message exists`() {
        val assistantOnly = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("hi")))
        assertNull(listOf(assistantOnly.toMessageNode()).latestUserMessageEpochMilli())
    }
}
