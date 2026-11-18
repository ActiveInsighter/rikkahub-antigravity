package me.rerere.rikkahub.ui.pages.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Flash
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Time02
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.utils.JsonInstant
import java.util.Locale

internal fun formatAgentElapsed(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val seconds = safe % 60L
    return when {
        hours > 0L -> "${hours}时${minutes.toString().padStart(2, '0')}分${seconds.toString().padStart(2, '0')}秒"
        minutes > 0L -> "${minutes}分${seconds.toString().padStart(2, '0')}秒"
        else -> "${seconds}秒"
    }
}

private fun formatSecondsLabel(seconds: Double?): String {
    if (seconds == null || seconds < 0.0) return "—"
    return when {
        seconds < 60.0 -> String.format(Locale.US, "%.1fs", seconds)
        else -> formatAgentElapsed(seconds.toLong())
    }
}

data class AgentSummaryData(
    val sentWhat: String = "",
    val actionsDone: List<String> = emptyList(),
    val completed: List<String> = emptyList(),
    val issues: List<String> = emptyList(),
    val summary: String = "",
    val updatedAt: String = "",
    val stepCount: Int = 0,
    val model: String = "",
)

data class AgentTimingStep(
    val index: Int,
    val type: String,
    val label: String,
    val elapsedSeconds: Double,
    val deltaSeconds: Double,
    val status: String,
)

data class AgentTimingsData(
    val submittedAt: String = "",
    val dispatchedAt: String = "",
    val dispatchSeconds: Double? = null,
    val firstReplyAt: String = "",
    val firstReplySeconds: Double? = null,
    val completedAt: String = "",
    val totalSeconds: Double? = null,
    val status: String = "",
    val steps: List<AgentTimingStep> = emptyList(),
)

internal fun parseAgentSummary(raw: String?): AgentSummaryData? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val obj = JsonInstant.parseToJsonElement(text).jsonObject
        fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        fun list(key: String) = (obj[key] as? JsonArray)?.mapNotNull {
            it.jsonPrimitive.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() }
        }.orEmpty()
        AgentSummaryData(
            sentWhat = str("sentWhat"),
            actionsDone = list("actionsDone"),
            completed = list("completed"),
            issues = list("issues"),
            summary = str("summary"),
            updatedAt = str("updatedAt"),
            stepCount = obj["stepCount"]?.jsonPrimitive?.intOrNull ?: 0,
            model = str("model"),
        )
    }.getOrNull()
}

internal fun parseAgentTimings(raw: String?): AgentTimingsData? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val obj = JsonInstant.parseToJsonElement(text).jsonObject
        fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        fun dbl(key: String) = obj[key]?.jsonPrimitive?.doubleOrNull
        val steps = (obj["steps"] as? JsonArray)?.mapNotNull { el ->
            val sObj = el as? JsonObject ?: return@mapNotNull null
            AgentTimingStep(
                index = sObj["index"]?.jsonPrimitive?.intOrNull ?: 0,
                type = sObj["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                label = sObj["label"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                elapsedSeconds = sObj["elapsedSeconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                deltaSeconds = sObj["deltaSeconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                status = sObj["status"]?.jsonPrimitive?.contentOrNull ?: "completed",
            )
        }.orEmpty()
        AgentTimingsData(
            submittedAt = str("submittedAt"),
            dispatchedAt = str("dispatchedAt"),
            dispatchSeconds = dbl("dispatchSeconds"),
            firstReplyAt = str("firstReplyAt"),
            firstReplySeconds = dbl("firstReplySeconds"),
            completedAt = str("completedAt"),
            totalSeconds = dbl("totalSeconds"),
            status = str("status"),
            steps = steps,
        )
    }.getOrNull()
}

private enum class AgentInsightTab(
    override val icon: ImageVector,
    private val title: String,
) : PagerTab {
    SUMMARY(HugeIcons.Sparkles, "执行总结"),
    TIMINGS(HugeIcons.Time02, "时间节点");

    override val label: String
        @Composable get() = title
}

@Composable
fun AgentMessageInsightsFooter(
    message: UIMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = remember(message.agentSummaryJson) { parseAgentSummary(message.agentSummaryJson) }
    val timings = remember(message.agentTimingsJson) { parseAgentTimings(message.agentTimingsJson) }
    val durationBadge = remember(timings, message.generationTimeMs) {
        when {
            timings?.totalSeconds != null -> formatSecondsLabel(timings.totalSeconds)
            message.generationTimeMs != null && message.generationTimeMs!! > 0 ->
                formatAgentElapsed(message.generationTimeMs!! / 1000L)
            else -> null
        }
    }
    val subtitle = when {
        summary?.summary?.isNotBlank() == true -> summary.summary
        timings != null && timings.steps.isNotEmpty() -> "${timings.steps.size} 个执行步骤 · 点击查看总结与时间节点"
        else -> "点击查看执行总结与关键时间节点"
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(30.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Sparkles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "执行总结与耗时",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (durationBadge != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = durationBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentInsightsSheet(
    message: UIMessage?,
    isRunning: Boolean,
    liveElapsedText: String?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val summary = remember(message?.agentSummaryJson) { parseAgentSummary(message?.agentSummaryJson) }
    val timings = remember(message?.agentTimingsJson) { parseAgentTimings(message?.agentTimingsJson) }
    val pagerState = rememberPagerState { AgentInsightTab.entries.size }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Agent 执行详情",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    val statusHint = when {
                        isRunning && !liveElapsedText.isNullOrBlank() -> "运行中 · 已耗时 $liveElapsedText"
                        isRunning -> "运行中"
                        timings?.totalSeconds != null -> "已完成 · 总耗时 ${formatSecondsLabel(timings.totalSeconds)}"
                        else -> "执行记录"
                    }
                    Text(
                        text = statusHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val badgeColor = if (isRunning) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
                val badgeTextColor = if (isRunning) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
                Surface(
                    shape = CircleShape,
                    color = badgeColor,
                ) {
                    Text(
                        text = if (isRunning) "实时同步" else "已归档",
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            PagerTabs(
                pagerState = pagerState,
                tabs = AgentInsightTab.entries,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (AgentInsightTab.entries[page]) {
                    AgentInsightTab.SUMMARY -> SummaryTabContent(
                        summary = summary,
                        isRunning = isRunning,
                        stepCount = message?.stepCount ?: timings?.steps?.size ?: 0,
                    )

                    AgentInsightTab.TIMINGS -> TimingsTabContent(
                        timings = timings,
                        isRunning = isRunning,
                        liveElapsedText = liveElapsedText,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryTabContent(
    summary: AgentSummaryData?,
    isRunning: Boolean,
    stepCount: Int,
) {
    if (summary == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = HugeIcons.Sparkles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Text(
                        text = if (isRunning) "正在等待首次阶段总结" else "暂无快速模型总结",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isRunning) {
                            "Agent 运行期间每 2 分钟且有新增步骤时，将自动调用快速模型生成进度总结（当前步骤数：$stepCount）。"
                        } else {
                            "该消息暂无总结记录（可能为 0 步骤快速回复，或 PocketBase 尚未迁移 agentSummary 字段）。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (summary.summary.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Sparkles,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "阶段总览",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (summary.model.isNotBlank()) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                ) {
                                    Text(
                                        text = summary.model,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = summary.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        if (summary.sentWhat.isNotBlank()) {
            item {
                SummarySectionCard(
                    title = "发送内容",
                    icon = HugeIcons.Message01,
                    accent = MaterialTheme.colorScheme.primary,
                    items = listOf(summary.sentWhat),
                )
            }
        }

        if (summary.actionsDone.isNotEmpty()) {
            item {
                SummarySectionCard(
                    title = "已执行操作",
                    icon = HugeIcons.Wrench01,
                    accent = MaterialTheme.colorScheme.tertiary,
                    items = summary.actionsDone,
                )
            }
        }

        if (summary.completed.isNotEmpty()) {
            item {
                SummarySectionCard(
                    title = "已完成",
                    icon = HugeIcons.CheckmarkCircle02,
                    accent = MaterialTheme.colorScheme.secondary,
                    items = summary.completed,
                )
            }
        }

        item {
            SummarySectionCard(
                title = "遇到的问题",
                icon = HugeIcons.AlertCircle,
                accent = if (summary.issues.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                containerColor = if (summary.issues.isNotEmpty()) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                items = if (summary.issues.isNotEmpty()) summary.issues else listOf("暂未发现阻塞或报错"),
            )
        }
    }
}

@Composable
private fun SummarySectionCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    items: List<String>,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            items.forEach { entry ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.8f)),
                    )
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimingsTabContent(
    timings: AgentTimingsData?,
    isRunning: Boolean,
    liveElapsedText: String?,
) {
    val totalLabel = when {
        timings?.totalSeconds != null -> formatSecondsLabel(timings.totalSeconds)
        isRunning && !liveElapsedText.isNullOrBlank() -> liveElapsedText
        else -> "—"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimingMetricCard(
                    label = "正式发出",
                    value = formatSecondsLabel(timings?.dispatchSeconds),
                    modifier = Modifier.weight(1f),
                )
                TimingMetricCard(
                    label = "首个回复",
                    value = formatSecondsLabel(timings?.firstReplySeconds),
                    modifier = Modifier.weight(1f),
                )
                TimingMetricCard(
                    label = if (isRunning) "已运行" else "总耗时",
                    value = totalLabel,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (timings == null) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "暂无时间节点记录",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "任务启动后将自动记录消息正式发出、首个回复到达以及每个步骤的增量耗时。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@LazyColumn
        }

        item {
            Text(
                text = "关键时间节点",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            TimelineMilestoneRow(
                title = "任务提交到执行器",
                subtitle = timings.submittedAt.ifBlank { "已进入执行队列" },
                elapsedLabel = "0.0s",
                deltaLabel = null,
                dotColor = MaterialTheme.colorScheme.primary,
                showConnector = true,
            )
        }

        if (timings.dispatchSeconds != null) {
            item {
                TimelineMilestoneRow(
                    title = "正式发往 Antigravity",
                    subtitle = timings.dispatchedAt.ifBlank { "已建立远程 Interaction" },
                    elapsedLabel = formatSecondsLabel(timings.dispatchSeconds),
                    deltaLabel = "+${formatSecondsLabel(timings.dispatchSeconds)}",
                    dotColor = MaterialTheme.colorScheme.tertiary,
                    showConnector = timings.firstReplySeconds != null || timings.steps.isNotEmpty() || timings.totalSeconds != null,
                )
            }
        }

        if (timings.firstReplySeconds != null) {
            item {
                val deltaFromDispatch = timings.dispatchSeconds?.let { (timings.firstReplySeconds - it).coerceAtLeast(0.0) }
                TimelineMilestoneRow(
                    title = "收到首个回复",
                    subtitle = timings.firstReplyAt.ifBlank { "模型开始返回首个输出/步骤" },
                    elapsedLabel = formatSecondsLabel(timings.firstReplySeconds),
                    deltaLabel = deltaFromDispatch?.let { "+${formatSecondsLabel(it)}" },
                    dotColor = MaterialTheme.colorScheme.secondary,
                    showConnector = timings.steps.isNotEmpty() || timings.totalSeconds != null,
                )
            }
        }

        if (timings.steps.isNotEmpty()) {
            item {
                Text(
                    text = "步骤到达耗时 (${timings.steps.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            itemsIndexed(
                items = timings.steps,
                key = { idx, step -> "${step.index}-$idx" },
            ) { idx, step ->
                val isFailed = step.status == "failed"
                val stepTitle = buildString {
                    append("步骤 #${step.index + 1} · ")
                    append(
                        when {
                            step.label.isNotBlank() -> step.label
                            step.type == "thought" -> "深度思考"
                            step.type == "model_output" -> "模型输出"
                            else -> step.type.ifBlank { "执行步骤" }
                        }
                    )
                }
                TimelineMilestoneRow(
                    title = stepTitle,
                    subtitle = "${step.type} · ${if (isFailed) "失败" else "已完成"}",
                    elapsedLabel = formatSecondsLabel(step.elapsedSeconds),
                    deltaLabel = "+${formatSecondsLabel(step.deltaSeconds)}",
                    dotColor = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    showConnector = idx < timings.steps.lastIndex || timings.totalSeconds != null,
                )
            }
        }

        if (timings.totalSeconds != null) {
            item {
                TimelineMilestoneRow(
                    title = "执行结束 (${timings.status.ifBlank { "completed" }})",
                    subtitle = timings.completedAt.ifBlank { "已完成持久化" },
                    elapsedLabel = formatSecondsLabel(timings.totalSeconds),
                    deltaLabel = null,
                    dotColor = MaterialTheme.colorScheme.primary,
                    showConnector = false,
                )
            }
        }
    }
}

@Composable
private fun TimingMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TimelineMilestoneRow(
    title: String,
    subtitle: String,
    elapsedLabel: String,
    deltaLabel: String?,
    dotColor: Color,
    showConnector: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(18.dp),
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = elapsedLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (deltaLabel != null) {
                        Text(
                            text = deltaLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
