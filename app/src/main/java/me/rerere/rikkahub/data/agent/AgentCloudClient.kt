package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

sealed interface AgentStreamEvent {
    data class ReasoningDelta(val text: String) : AgentStreamEvent
    data class TextDelta(val text: String) : AgentStreamEvent
    data class Completed(val usage: TokenUsage? = null) : AgentStreamEvent
    data class Error(val message: String) : AgentStreamEvent
}

/**
 * Abstraction for the Antigravity File Converter / Cloud Agent API.
 * Swap the implementation in [me.rerere.rikkahub.di.repositoryModule] when
 * connecting to your cloud backend.
 */
interface AgentCloudClient {
    fun streamReply(
        conversationId: Uuid,
        history: List<UIMessage>,
        userMessage: UIMessage,
    ): Flow<AgentStreamEvent>
}

/**
 * Default local preview implementation used before a cloud endpoint is wired.
 * Streams a structured response so message rendering, attachments, and
 * isolated persistence in AgentDatabase can be verified end-to-end.
 */
class LocalPreviewAgentCloudClient : AgentCloudClient {
    override fun streamReply(
        conversationId: Uuid,
        history: List<UIMessage>,
        userMessage: UIMessage,
    ): Flow<AgentStreamEvent> = flow {
        val userText = userMessage.toText().trim()
        val attachments = userMessage.parts.filter {
            it is UIMessagePart.Document || it is UIMessagePart.Image ||
                it is UIMessagePart.Video || it is UIMessagePart.Audio
        }

        emit(
            AgentStreamEvent.ReasoningDelta(
                "已接收会话 `${conversationId}` 的输入（文本长度 ${userText.length}，附件 ${attachments.size} 个）。当前为本地隔离预览通道，等待接入反重力云端 API。\n"
            )
        )
        delay(120)

        val reply = buildString {
            appendLine("### Antigravity Agent（独立工作区）")
            appendLine()
            if (userText.isNotEmpty()) {
                appendLine("已收到你的指令：")
                appendLine("> $userText")
                appendLine()
            }
            if (attachments.isNotEmpty()) {
                appendLine("已附带 **${attachments.size}** 个文件/媒体：")
                attachments.forEachIndexed { idx, part ->
                    val label = when (part) {
                        is UIMessagePart.Document -> "文档: `${part.fileName}` (${part.mime})"
                        is UIMessagePart.Image -> "图片: `${part.url.substringAfterLast('/')}`"
                        is UIMessagePart.Video -> "视频: `${part.url.substringAfterLast('/')}`"
                        is UIMessagePart.Audio -> "音频: `${part.url.substringAfterLast('/')}`"
                        else -> part.toString()
                    }
                    appendLine("${idx + 1}. $label")
                }
                appendLine()
            }
            appendLine("当前 Agent 页面已与 Chat 页面的数据库和设置完全隔离（`agent.db` + `agent_ui_settings`），后续只需在 `AgentCloudClient` 对接反重力文件转换云端接口即可。")
        }

        val chunkSize = 24
        var index = 0
        while (index < reply.length) {
            val end = (index + chunkSize).coerceAtMost(reply.length)
            emit(AgentStreamEvent.TextDelta(reply.substring(index, end)))
            index = end
            delay(20)
        }

        emit(AgentStreamEvent.Completed())
    }
}
