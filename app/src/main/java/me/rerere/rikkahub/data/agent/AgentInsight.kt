package me.rerere.rikkahub.data.agent

import kotlinx.serialization.Serializable
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.utils.JsonInstant
import java.util.Locale

@Serializable
data class AgentProgressSummary(
    val updatedAt: String = "",
    val stepCount: Int = 0,
    val model: String = "",
    val status: String = "",
    val overview: String = "",
    val sentSummary: String = "",
    val actions: List<String> = emptyList(),
    val completed: List<String> = emptyList(),
    val issues: List<String> = emptyList(),
)

@Serializable
data class AgentTimelineStep(
    val index: Int = 0,
    val key: String = "",
    val type: String = "",
    val label: String = "",
    val receivedAt: String = "",
    val offsetMs: Long = 0L,
    val deltaMs: Long = 0L,
)

@Serializable
data class AgentTimeline(
    val startedAt: String = "",
    val submittedAt: String? = null,
    val submittedOffsetMs: Long? = null,
    val firstReplyAt: String? = null,
    val firstReplyOffsetMs: Long? = null,
    val finishedAt: String? = null,
    val totalDurationMs: Long? = null,
    val steps: List<AgentTimelineStep> = emptyList(),
)

fun UIMessage.parseAgentSummary(): AgentProgressSummary? {
    val raw = agentSummaryJson?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { JsonInstant.decodeFromString<AgentProgressSummary>(raw) }.getOrNull()
}

fun UIMessage.parseAgentTimeline(): AgentTimeline? {
    val raw = agentTimelineJson?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { JsonInstant.decodeFromString<AgentTimeline>(raw) }.getOrNull()
}

fun formatAgentElapsedSeconds(totalSeconds: Long): String {
    val sec = totalSeconds.coerceAtLeast(0L)
    val hours = sec / 3600L
    val minutes = (sec % 3600L) / 60L
    val seconds = sec % 60L
    return when {
        hours > 0L -> String.format(Locale.US, "%d小时%02d分%02d秒", hours, minutes, seconds)
        minutes > 0L -> String.format(Locale.US, "%d分%02d秒", minutes, seconds)
        else -> "${seconds}秒"
    }
}

fun formatAgentOffsetMs(offsetMs: Long?): String {
    if (offsetMs == null || offsetMs < 0L) return "—"
    return when {
        offsetMs < 1000L -> "${offsetMs}ms"
        offsetMs < 60_000L -> String.format(Locale.US, "%.1f秒", offsetMs / 1000.0)
        else -> formatAgentElapsedSeconds(offsetMs / 1000L)
    }
}
