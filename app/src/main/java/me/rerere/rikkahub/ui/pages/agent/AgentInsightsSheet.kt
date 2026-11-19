package me.rerere.rikkahub.ui.pages.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Flash
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Time02
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
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
        val rawSteps = (obj["steps"] as? JsonArray)?.mapNotNull { el ->
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
        // 对同一节点（index + type + label）去重，保留最新状态，避免流式更新或重复记录导致步骤数不一致
        val deduplicatedSteps = rawSteps
            .groupBy { Triple(it.index, it.type, it.label) }
            .values
            .map { group -> group.last() }
            .sortedWith(compareBy({ it.index }, { it.elapsedSeconds }))
        AgentTimingsData(
            submittedAt = str("submittedAt"),
            dispatchedAt = str("dispatchedAt"),
            dispatchSeconds = dbl("dispatchSeconds"),
            firstReplyAt = str("firstReplyAt"),
            firstReplySeconds = dbl("firstReplySeconds"),
            completedAt = str("completedAt"),
            totalSeconds = dbl("totalSeconds"),
            status = str("status"),
            steps = deduplicatedSteps,
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
fun AgentMessageTimingBadge(
    message: UIMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timings = remember(message.agentTimingsJson) { parseAgentTimings(message.agentTimingsJson) }
    val label = remember(timings, message.generationTimeMs) {
        when {
            timings?.totalSeconds != null -> formatSecondsLabel(timings.totalSeconds)
            message.generationTimeMs != null && message.generationTimeMs!! > 0L ->
                formatSecondsLabel(message.generationTimeMs!! / 1000.0)
            else -> "执行详情"
        }
    }
    val tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = HugeIcons.Time02,
            contentDescription = "查看执行总结与时间节点",
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
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
                .fillMaxHeight(0.82f)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Agent 执行详情",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                    val statusHint = when {
                        isRunning && !liveElapsedText.isNullOrBlank() -> "运行中 · 已耗时 $liveElapsedText"
                        isRunning -> "运行中"
                        timings?.totalSeconds != null -> "已完成 · 总耗时 ${formatSecondsLabel(timings.totalSeconds)}"
                        message?.generationTimeMs != null && message.generationTimeMs!! > 0L ->
                            "已完成 · 总耗时 ${formatSecondsLabel(message.generationTimeMs!! / 1000.0)}"
                        else -> "执行记录"
                    }
                    Text(
                        text = statusHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Tag(type = if (isRunning) TagType.INFO else TagType.SUCCESS) {
                    Text(if (isRunning) "实时同步" else "已归档")
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
                        message = message,
                        summary = summary,
                        isRunning = isRunning,
                        stepCount = summary?.stepCount?.takeIf { it > 0 }
                            ?: message?.stepCount
                            ?: timings?.steps?.size
                            ?: 0,
                    )

                    AgentInsightTab.TIMINGS -> TimingsTabContent(
                        timings = timings,
                        reportedStepCount = message?.stepCount ?: summary?.stepCount?.takeIf { it > 0 },
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
    message: UIMessage?,
    summary: AgentSummaryData?,
    isRunning: Boolean,
    stepCount: Int,
) {
    val fallbackTools = remember(message?.parts) {
        message?.parts?.filterIsInstance<UIMessagePart.ServerTool>()
            ?.map { it.toolName }
            ?.distinct()
            .orEmpty()
    }
    val fallbackIssues = remember(message?.parts) {
        message?.parts?.filterIsInstance<UIMessagePart.Notice>()
            ?.mapNotNull { it.text.takeIf { t -> t.isNotBlank() } }
            .orEmpty()
    }
    val hasReplyText = remember(message?.parts) {
        message?.parts?.any { it is UIMessagePart.Text && it.text.isNotBlank() } == true
    }

    if (summary == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = HugeIcons.Sparkles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = if (isRunning) "正在等待阶段总结" else "快速总结暂未生成",
                                style = MaterialTheme.typography.titleSmallEmphasized,
                            )
                            Text(
                                text = if (isRunning) {
                                    "任务持续运行期间会定期生成进度总结（当前 $stepCount 步）。"
                                } else if (hasReplyText) {
                                    "本次回复已直接完成输出。"
                                } else {
                                    "暂无阶段性总结记录。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (fallbackTools.isNotEmpty() || fallbackIssues.isNotEmpty()) {
                item {
                    CardGroup(title = { Text("本次执行概况") }) {
                        if (fallbackTools.isNotEmpty()) {
                            item(
                                leadingContent = {
                                    Icon(
                                        imageVector = HugeIcons.Wrench01,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                supportingContent = {
                                    Text(fallbackTools.joinToString("、"))
                                },
                            ) {
                                Text("已调用工具 (${fallbackTools.size})")
                            }
                        }
                        fallbackIssues.forEach { issue ->
                            item(
                                leadingContent = {
                                    Icon(
                                        imageVector = HugeIcons.AlertCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                supportingContent = { Text(issue) },
                            ) {
                                Text("异常提示")
                            }
                        }
                    }
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
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
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
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (summary.model.isNotBlank()) {
                                Tag(type = TagType.INFO) {
                                    Text(summary.model)
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
                CardGroup(title = { Text("任务目标") }) {
                    item(
                        leadingContent = {
                            Icon(
                                imageVector = HugeIcons.Message01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                    ) {
                        Text(
                            text = summary.sentWhat,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (summary.actionsDone.isNotEmpty()) {
            item {
                CardGroup(title = { Text("已执行操作 (${summary.actionsDone.size})") }) {
                    summary.actionsDone.forEach { action ->
                        item(
                            leadingContent = {
                                Icon(
                                    imageVector = HugeIcons.Wrench01,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                            },
                        ) {
                            Text(
                                text = action,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        if (summary.completed.isNotEmpty()) {
            item {
                CardGroup(title = { Text("已完成 (${summary.completed.size})") }) {
                    summary.completed.forEach { done ->
                        item(
                            leadingContent = {
                                Icon(
                                    imageVector = HugeIcons.CheckmarkCircle02,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        ) {
                            Text(
                                text = done,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        if (summary.issues.isNotEmpty()) {
            item {
                val errorItemColors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                )
                CardGroup(title = { Text("遇到的问题 (${summary.issues.size})") }) {
                    summary.issues.forEach { issue ->
                        item(
                            colors = errorItemColors,
                            leadingContent = {
                                Icon(
                                    imageVector = HugeIcons.AlertCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                        ) {
                            Text(
                                text = issue,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimingsTabContent(
    timings: AgentTimingsData?,
    reportedStepCount: Int?,
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
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimingMetricCell(
                        label = "正式发出",
                        value = formatSecondsLabel(timings?.dispatchSeconds),
                        modifier = Modifier.weight(1f),
                    )
                    VerticalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    TimingMetricCell(
                        label = "首个回复",
                        value = formatSecondsLabel(timings?.firstReplySeconds),
                        modifier = Modifier.weight(1f),
                    )
                    VerticalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    TimingMetricCell(
                        label = if (isRunning) "已运行" else "总耗时",
                        value = totalLabel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (timings == null) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "暂无时间节点记录",
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                        Text(
                            text = "任务启动后将自动记录消息发出、首个回复到达以及每个节点的增量耗时。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@LazyColumn
        }

        item {
            CardGroup(title = { Text("关键时间节点") }) {
                item(
                    leadingContent = {
                        MilestoneIconBadge(
                            icon = HugeIcons.ArrowUp02,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = timings.submittedAt.ifBlank { "已进入执行队列" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        TimingTrailingValue(elapsedLabel = "0.0s", deltaLabel = null)
                    },
                ) {
                    Text("任务提交到执行器")
                }

                if (timings.dispatchSeconds != null) {
                    item(
                        leadingContent = {
                            MilestoneIconBadge(
                                icon = HugeIcons.Flash,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = timings.dispatchedAt.ifBlank { "已建立远程 Interaction" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            TimingTrailingValue(
                                elapsedLabel = formatSecondsLabel(timings.dispatchSeconds),
                                deltaLabel = "+${formatSecondsLabel(timings.dispatchSeconds)}",
                            )
                        },
                    ) {
                        Text("正式发往 Antigravity")
                    }
                }

                if (timings.firstReplySeconds != null) {
                    val deltaFromDispatch = timings.dispatchSeconds?.let {
                        (timings.firstReplySeconds - it).coerceAtLeast(0.0)
                    }
                    item(
                        leadingContent = {
                            MilestoneIconBadge(
                                icon = HugeIcons.Message01,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = timings.firstReplyAt.ifBlank { "模型开始返回首个输出/步骤" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            TimingTrailingValue(
                                elapsedLabel = formatSecondsLabel(timings.firstReplySeconds),
                                deltaLabel = deltaFromDispatch?.let { "+${formatSecondsLabel(it)}" },
                            )
                        },
                    ) {
                        Text("收到首个回复")
                    }
                }

                if (timings.totalSeconds != null) {
                    item(
                        leadingContent = {
                            MilestoneIconBadge(
                                icon = HugeIcons.CheckmarkCircle02,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = timings.completedAt.ifBlank { "已完成持久化" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            TimingTrailingValue(
                                elapsedLabel = formatSecondsLabel(timings.totalSeconds),
                                deltaLabel = null,
                            )
                        },
                    ) {
                        Text("执行结束 (${timings.status.ifBlank { "completed" }})")
                    }
                }
            }
        }

        if (timings.steps.isNotEmpty()) {
            val stepsTitle = if (reportedStepCount != null && reportedStepCount > 0) {
                "时间节点 (${timings.steps.size} · 共 $reportedStepCount 步)"
            } else {
                "时间节点 (${timings.steps.size})"
            }
            item {
                CardGroup(title = { Text(stepsTitle) }) {
                    timings.steps.forEachIndexed { idx, step ->
                        val isFailed = step.status == "failed"
                        val stepIcon = when {
                            isFailed -> HugeIcons.AlertCircle
                            step.type == "thought" -> HugeIcons.Brain02
                            step.type == "model_output" -> HugeIcons.Message01
                            step.type.contains("tool", ignoreCase = true) -> HugeIcons.Wrench01
                            else -> HugeIcons.Clock01
                        }
                        val stepTitle = buildString {
                            append("#${idx + 1} · ")
                            append(
                                when {
                                    step.label.isNotBlank() -> step.label
                                    step.type == "thought" -> "深度思考"
                                    step.type == "model_output" -> "模型输出"
                                    else -> step.type.ifBlank { "执行节点" }
                                }
                            )
                        }
                        item(
                            leadingContent = {
                                MilestoneIconBadge(
                                    icon = stepIcon,
                                    containerColor = if (isFailed) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    contentColor = if (isFailed) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "${step.type} · ${if (isFailed) "失败" else "已完成"}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                TimingTrailingValue(
                                    elapsedLabel = formatSecondsLabel(step.elapsedSeconds),
                                    deltaLabel = "+${formatSecondsLabel(step.deltaSeconds)}",
                                )
                            },
                        ) {
                            Text(
                                text = stepTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimingMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMediumEmphasized,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MilestoneIconBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun TimingTrailingValue(
    elapsedLabel: String,
    deltaLabel: String?,
) {
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
