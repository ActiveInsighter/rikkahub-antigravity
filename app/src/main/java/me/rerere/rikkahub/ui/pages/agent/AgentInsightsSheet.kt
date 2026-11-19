package me.rerere.rikkahub.ui.pages.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Time02
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.rikkahub.data.agent.PocketBaseAgentCloudClient
import me.rerere.rikkahub.ui.components.richtext.MaterialMarkdown
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Duration
import java.util.Locale

internal fun formatAgentElapsed(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = safe % 3600L / 60L
    val seconds = safe % 60L
    return when {
        hours > 0L -> "${hours}时${minutes.toString().padStart(2, '0')}分${seconds.toString().padStart(2, '0')}秒"
        minutes > 0L -> "${minutes}分${seconds.toString().padStart(2, '0')}秒"
        else -> "${seconds}秒"
    }
}

private fun formatTimelineSeconds(seconds: Double): String =
    if (seconds < 60.0) String.format(Locale.US, "%.1f 秒", seconds) else formatAgentElapsed(seconds.toLong())

private data class AgentSummary(
    val request: String,
    val actions: List<String>,
    val results: List<String>,
    val issues: List<String>,
    val overview: String,
)

internal data class AgentTimingEvent(
    val index: Int,
    val type: String,
    val label: String,
    val elapsedSeconds: Double,
    val status: String,
)

internal data class AgentTimings(
    val submittedAt: Long?,
    val dispatchedAt: Long?,
    val dispatchSeconds: Double?,
    val firstReplyAt: Long?,
    val firstReplySeconds: Double?,
    val completedAt: Long?,
    val totalSeconds: Double?,
    val status: String,
    val events: List<AgentTimingEvent>,
) {
    private fun secondsSinceSubmission(timestamp: Long?): Double? {
        val start = submittedAt ?: return null
        return timestamp?.takeIf { it >= start }?.let { (it - start) / 1000.0 }
    }

    // Runner offsets start when the worker claims the job. Anchor them to the submission
    // timestamp when both clocks are available so the running and finished timers agree.
    val queueSeconds: Double
        get() {
            val anchor = when {
                dispatchedAt != null && dispatchSeconds != null -> dispatchedAt to dispatchSeconds
                firstReplyAt != null && firstReplySeconds != null -> firstReplyAt to firstReplySeconds
                completedAt != null && totalSeconds != null -> completedAt to totalSeconds
                else -> return 0.0
            }
            return ((secondsSinceSubmission(anchor.first) ?: anchor.second) - anchor.second).coerceAtLeast(0.0)
        }

    val elapsedSeconds: Double?
        get() = secondsSinceSubmission(completedAt) ?: totalSeconds?.plus(queueSeconds)
}

private fun JsonObject.text(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

private fun JsonObject.seconds(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it >= 0.0 }

private fun JsonObject.timestamp(key: String): Long? =
    PocketBaseAgentCloudClient.parseIsoMillis(text(key)).takeIf { it > 0L }

private fun parseAgentSummary(raw: String?): AgentSummary? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val obj = JsonInstant.parseToJsonElement(raw).jsonObject
        fun entries(key: String) = (obj[key] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        }.orEmpty().distinct()

        val overview = obj.text("summary")
        val results = entries("completed").filterNot { it == overview }
        val request = obj.text("sentWhat")
        AgentSummary(
            request = request.takeUnless { it == overview }.orEmpty(),
            actions = entries("actionsDone").filterNot { it == overview || it in results },
            results = results,
            issues = entries("issues"),
            overview = overview,
        ).takeIf {
            it.overview.isNotEmpty() || it.request.isNotEmpty() ||
                it.actions.isNotEmpty() || it.results.isNotEmpty() || it.issues.isNotEmpty()
        }
    }.getOrNull()
}

internal fun parseAgentTimings(raw: String?): AgentTimings? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val obj = JsonInstant.parseToJsonElement(raw).jsonObject
        val events = (obj["steps"] as? JsonArray)?.mapNotNull { element ->
            val event = element as? JsonObject ?: return@mapNotNull null
            val index = (event["index"] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 } ?: return@mapNotNull null
            val seconds = event.seconds("elapsedSeconds") ?: return@mapNotNull null
            AgentTimingEvent(
                index = index,
                type = event.text("type"),
                label = event.text("label"),
                elapsedSeconds = seconds,
                status = event.text("status"),
            )
        }.orEmpty()
        AgentTimings(
            submittedAt = obj.timestamp("submittedAt"),
            dispatchedAt = obj.timestamp("dispatchedAt"),
            dispatchSeconds = obj.seconds("dispatchSeconds"),
            firstReplyAt = obj.timestamp("firstReplyAt"),
            firstReplySeconds = obj.seconds("firstReplySeconds"),
            completedAt = obj.timestamp("completedAt"),
            totalSeconds = obj.seconds("totalSeconds"),
            status = obj.text("status"),
            events = events.associateBy { it.index }.values
                .sortedWith(compareBy(AgentTimingEvent::elapsedSeconds, AgentTimingEvent::index)),
        )
    }.getOrNull()
}

private fun UIMessage.agentElapsedSeconds(timings: AgentTimings?): Double? {
    return timings?.elapsedSeconds
        ?: finishedAt?.let {
            val submitted = timings?.submittedAt
            if (submitted != null) {
                val finished = timings.completedAt ?: it.toInstantForAgent()
                (finished - submitted).takeIf { elapsed -> elapsed >= 0L }
            } else {
                Duration.between(createdAt.toJavaLocalDateTime(), it.toJavaLocalDateTime()).toMillis()
                    .takeIf { elapsed -> elapsed >= 0L }
            }
        }?.div(1000.0)
        ?: generationTimeMs?.takeIf { it >= 0L }?.div(1000.0)
}

private fun kotlinx.datetime.LocalDateTime.toInstantForAgent(): Long =
    toJavaLocalDateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

internal fun UIMessage.agentElapsedLabel(): String? =
    agentElapsedSeconds(parseAgentTimings(agentTimingsJson))?.let { formatAgentElapsed(it.toLong()) }

@Composable
internal fun AgentElapsedButton(
    elapsed: String,
    onClick: () -> Unit,
    showIcon: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "查看执行详情", onClick = onClick)
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            Icon(HugeIcons.Time02, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
        Text(
            text = elapsed,
            color = color,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

private enum class AgentInsightTab(override val icon: ImageVector, private val title: String) : PagerTab {
    TIMINGS(HugeIcons.Time02, "时间节点"),
    SUMMARY(HugeIcons.Sparkles, "执行总结");

    override val label: String
        @Composable get() = title
}

@Composable
internal fun AgentInsightsSheet(
    message: UIMessage?,
    isRunning: Boolean,
    liveElapsedText: String?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val summary = remember(message?.agentSummaryJson) { parseAgentSummary(message?.agentSummaryJson) }
    val timings = remember(message?.agentTimingsJson) { parseAgentTimings(message?.agentTimingsJson) }
    val elapsed = remember(message, timings) { message?.agentElapsedSeconds(timings) }
    val duration = if (isRunning) liveElapsedText else elapsed?.let { formatAgentElapsed(it.toLong()) }
    val status = when {
        isRunning -> "运行中"
        message == null -> "暂无执行记录"
        timings?.status in setOf("failed", "partial", "error") -> "执行失败"
        timings?.status in setOf("canceled", "cancelled") -> "已取消"
        message.finishedAt != null -> "执行结束"
        else -> "执行记录"
    }
    val pagerState = rememberPagerState { AgentInsightTab.entries.size }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f),
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("执行详情", style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(
                    text = if (duration == null) status else "$status · $duration",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PagerTabs(
                pagerState = pagerState,
                tabs = AgentInsightTab.entries,
                contentPadding = PaddingValues(horizontal = 24.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                when (AgentInsightTab.entries[page]) {
                    AgentInsightTab.SUMMARY -> SummaryTab(summary, isRunning, message?.toText())
                    AgentInsightTab.TIMINGS -> TimingsTab(timings, isRunning, elapsed, message?.stepCount)
                }
            }
        }
    }
}

@Composable
private fun SummaryTab(summary: AgentSummary?, isRunning: Boolean, reply: String?) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (summary == null) {
            item {
                if (!isRunning && !reply.isNullOrBlank()) {
                    SummarySection("本轮回复", listOf(reply))
                } else {
                    DetailsHint(if (isRunning) "等待生成执行总结…" else "本轮暂无执行总结")
                }
            }
        } else {
            if (summary.overview.isNotEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        MaterialMarkdown(
                            content = summary.overview,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }
            if (summary.results.isNotEmpty()) item { SummarySection("已完成", summary.results) }
            if (summary.issues.isNotEmpty()) item { SummarySection("问题与阻塞", summary.issues) }
            if (summary.actions.isNotEmpty()) item { SummarySection("执行过程", summary.actions) }
            if (summary.request.isNotEmpty()) item { SummarySection("本轮请求", listOf(summary.request)) }
        }
    }
}

@Composable
private fun SummarySection(title: String, entries: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmallEmphasized)
        entries.forEach { entry ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entries.size > 1) {
                    Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                }
                MaterialMarkdown(
                    content = entry,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TimingsTab(timings: AgentTimings?, isRunning: Boolean, elapsed: Double?, reportedSteps: Int?) {
    val offset = timings?.queueSeconds ?: 0.0
    val events = timings?.events.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {
        if (timings == null) {
            item {
                DetailsHint(
                    if (isRunning) "等待接收运行事件…" else "本轮暂无运行事件记录",
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        } else {
            item { TimingRow(HugeIcons.Time02, "提交请求", elapsed = 0.0) }
            timings.dispatchSeconds?.let {
                item { TimingRow(HugeIcons.ArrowUp02, "发往模型", elapsed = offset + it) }
            }
            timings.firstReplySeconds?.let {
                item { TimingRow(HugeIcons.Message01, "收到首个响应", elapsed = offset + it) }
            }

            if (events.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("运行事件（${events.size}）", style = MaterialTheme.typography.titleSmallEmphasized)
                        if (reportedSteps != null && reportedSteps != events.size) {
                            Text(
                                "用量报告 $reportedSteps 步。工具调用和返回分别记录为事件。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(events, key = { it.index }) { event ->
                    val (icon, title) = when {
                        event.type == "thought" -> HugeIcons.Brain02 to "思考"
                        event.type in setOf("model_output", "text") -> HugeIcons.Message01 to "模型输出"
                        event.type.endsWith("_call") -> HugeIcons.Wrench01 to "调用工具"
                        event.type.endsWith("_result") -> HugeIcons.CheckmarkCircle02 to "工具返回"
                        else -> HugeIcons.Time02 to "运行事件"
                    }
                    val name = event.label.takeIf { it != event.type && it.isNotBlank() }
                        ?: event.type.removeSuffix("_call").removeSuffix("_result")
                            .takeIf { event.type.endsWith("_call") || event.type.endsWith("_result") }
                    val state = when (event.status) {
                        "failed", "error" -> "失败"
                        "running", "in_progress" -> if (isRunning) "进行中" else null
                        else -> null
                    }
                    TimingRow(
                        icon = if (state == "失败") HugeIcons.AlertCircle else icon,
                        title = title,
                        detail = listOfNotNull(name, state).joinToString(" · ").ifEmpty { null },
                        elapsed = offset + event.elapsedSeconds,
                        failed = state == "失败",
                    )
                }
            } else if (isRunning) {
                item {
                    DetailsHint("等待接收运行事件…", modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
        if (!isRunning && elapsed != null) {
            item { TimingRow(HugeIcons.CheckmarkCircle02, "执行结束", elapsed = elapsed) }
        }
    }
}

@Composable
private fun DetailsHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TimingRow(
    icon: ImageVector,
    title: String,
    elapsed: Double,
    detail: String? = null,
    failed: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        modifier = Modifier.padding(horizontal = 8.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (failed) colors.error else colors.primary,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = detail?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        trailingContent = {
            Text(
                text = formatTimelineSeconds(elapsed),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = colors.onSurfaceVariant,
            )
        },
    )
}
