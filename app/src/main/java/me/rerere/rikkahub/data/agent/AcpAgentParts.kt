package me.rerere.rikkahub.data.agent

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.ui.ReasoningType
import me.rerere.ai.ui.ServerToolStatus
import kotlin.time.Instant
import me.rerere.ai.ui.UIMessagePart

internal fun renderAcpParts(
    parts: List<RemoteAgentPart>, taskStatus: String, createdAt: Instant,
): List<UIMessagePart> {
    val rendered = mutableListOf<UIMessagePart>()
    val tools = mutableMapOf<String, Int>()
    val active = isAgentActiveStatus(taskStatus)
    val chunk = StringBuilder()
    var chunkType = ""

    fun flush() {
        if (chunk.isEmpty()) return
        val text = chunk.toString()
        rendered.add(if (chunkType == "agent_thought_chunk") {
            UIMessagePart.Reasoning(
                reasoning = text,
                reasoningType = ReasoningType.REASONING_TEXT,
                createdAt = createdAt,
                finishedAt = createdAt.takeUnless { active },
            )
        } else {
            UIMessagePart.Text(text)
        })
        chunk.clear()
    }

    for (event in parts) {
        val data = event.part
        when (data.text("type")) {
            "update" -> when (val kind = data.text("sessionUpdate")) {
                "agent_message_chunk", "agent_thought_chunk" -> {
                    val content = data["content"] as? JsonObject ?: continue
                    if (content.text("type") != "text") continue
                    if (chunkType != kind) flush()
                    chunkType = kind
                    chunk.append(content.text("text"))
                }
                "tool_call", "tool_call_update" -> {
                    val id = data.text("toolCallId")
                    if (id.isBlank()) continue
                    val index = tools[id]
                    if (index == null) flush()
                    val previous = index?.let { rendered[it] as UIMessagePart.ServerTool }
                    val status = when (data.text("status")) {
                        "completed" -> ServerToolStatus.COMPLETED
                        "failed" -> ServerToolStatus.FAILED
                        "pending", "in_progress" -> if (active) ServerToolStatus.IN_PROGRESS else ServerToolStatus.FAILED
                        else -> previous?.status ?: if (active) ServerToolStatus.IN_PROGRESS else ServerToolStatus.FAILED
                    }
                    val part = UIMessagePart.ServerTool(
                        toolCallId = id,
                        toolName = data.text("title").ifBlank { data.text("name").ifBlank { previous?.toolName ?: id } },
                        input = data["rawInput"]?.takeUnless { it is JsonNull } ?: previous?.input,
                        output = data["rawOutput"]?.takeUnless { it is JsonNull }
                            ?: data["content"]?.takeUnless { it is JsonNull } ?: previous?.output,
                        status = status,
                    )
                    if (index == null) {
                        tools[id] = rendered.size
                        rendered.add(part)
                    } else {
                        rendered[index] = part
                    }
                }
            }
            "artifact" -> {
                flush()
                rendered.add(UIMessagePart.Text("📎 ${data.text("path")}"))
            }
            "error" -> {
                flush()
                rendered.add(UIMessagePart.Notice(title = "ACP 执行失败", text = data.text("code")))
            }
            "stop" -> if (data.text("reason") == "refusal") {
                flush()
                rendered.add(UIMessagePart.Notice(title = "Agent 拒绝了此请求", text = ""))
            }
        }
    }
    flush()
    if (rendered.isNotEmpty()) return rendered
    val placeholder = when (taskStatus) {
        "failed" -> UIMessagePart.Notice(title = "ACP 任务失败", text = "")
        "canceled" -> UIMessagePart.Notice(title = "已取消", text = "")
        else -> UIMessagePart.Text("")
    }
    return listOf(placeholder)
}
