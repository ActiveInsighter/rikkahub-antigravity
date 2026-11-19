package me.rerere.rikkahub.ui.pages.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toJavaLocalDateTime
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
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Flash
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Repeat
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Time02
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.rikkahub.ui.components.message.StatsItem
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Duration
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
        }
            .orEmpty()
            .distinctBy { it.index }
            .sortedBy { it.index }
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
fun AgentMessageDurationChip(
    message: UIMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
) {
    val timings = remember(message.agentTimingsJson) { parseAgentTimings(message.agentTimingsJson) }
    val durationLabel = remember(timings, message.generationTimeMs, message.createdAt, message.finishedAt) {
        val fallbackMs = message.finishedAt?.let { finishedAt ->
            message.generationTimeMs ?: Duration.between(
                message.createdAt.toJavaLocalDateTime(),
                finishedAt.toJavaLocalDateTime(),
            ).toMillis()
        }?.takeIf { it > 0L }
        when {
            timings?.totalSeconds != null -> formatSecondsLabel(timings.totalSeconds)
            fallbackMs != null -> formatSecondsLabel(fallbackMs / 1000.0)
            else -> "执行详情"
        }
    }

    ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = color)) {
        CompositionLocalProvider(LocalContentColor provides color) {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                StatsItem(
                    icon = {
                        Icon(
                            imageVector = HugeIcons.Time02,
                            contentDescription = "查看执行详情",
                            modifier = Modifier.size(12.dp),
                        )
                    },
                    content = {
                        Text(text = durationLabel)
                    },
                )
            }
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
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val summary = remember(message?.agentSummaryJson) { parseAgentSummary(message?.agentSummaryJson) }
    val timings = remember(message?.agentTimingsJson) { parseAgentTimings(message?.agentTimingsJson) }
    val pagerState = rememberPagerState { AgentInsightTab.entries.size }
    val resolvedStepCount = remember(message?.stepCount, timings, summary) {
        message?.stepCount
            ?: timings?.steps?.size?.takeIf { it > 0 }
            ?: summary?.stepCount?.takeIf { it > 0 }
            ?: 0
    }

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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "执行详情",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val statusHint = when {
                        isRunning && !liveElapsedText.isNullOrBlank() -> "运行中 · 已耗时 $liveElapsedText"
                        isRunning -> "运行中"
                        timings?.totalSeconds != null -> "已完成 · 总耗时 ${formatSecondsLabel(timings.totalSeconds)}"
                        message?.generationTimeMs != null && message.generationTimeMs!! > 0 ->
                            "已完成 · 总耗时 ${formatSecondsLabel(message.generationTimeMs!! / 1000.0)}"
                        else -> "执行记录"
                    }
                    Text(
                        text = statusHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Tag(
                    type = if (isRunning) TagType.INFO else TagType.SUCCESS,
                ) {
                    Text(text = if (isRunning) "实时同步" else "已归档")
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
                        timings = timings,
                        isRunning = isRunning,
                        stepCount = resolvedStepCount,
                    )

                    AgentInsightTab.TIMINGS -> TimingsTabContent(
                        timings = timings,
                        isRunning = isRunning,
                        liveElapsedText = liveElapsedText,
                        totalStepCount = message?.stepCount,
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
    timings: AgentTimingsData?,
    isRunning: Boolean,
    stepCount: Int,
) {
    val toolCallsCount = remember(message?.parts) {
        message?.parts?.count { it is UIMessagePart.ServerTool || it is UIMessagePart.Tool } ?: 0
    }
    val reasoningCount = remember(message?.parts) {
        message?.parts?.count { it is UIMessagePart.Reasoning } ?: 0
    }
    val totalDurationLabel = remember(timings, message?.generationTimeMs) {
        when {
            timings?.totalSeconds != null -> formatSecondsLabel(timings.totalSeconds)
            message?.generationTimeMs != null && message.generationTimeMs!! > 0 ->
                formatSecondsLabel(message.generationTimeMs!! / 1000.0)
            else -> "—"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (summary == null) {
            item {
                CardGroup {
                    item(
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.Sparkles,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = if (isRunning) "正在等待阶段总结" else "暂无阶段总结",
                            )
                        },
                        supportingContent = {
                            Text(
                                text = if (isRunning) {
                                    "任务运行每 2 分钟且有新增步骤时将自动生成阶段总结。"
                                } else {
                                    "本次回复未触发定时总结，下方为本地执行统计。"
                                },
                            )
                        },
                    )
                }
            }

            item {
                CardGroup(
                    title = { Text("执行概览") },
                ) {
                    item(
                        leadingContent = { CardGroupIcon(HugeIcons.Repeat) },
                        headlineContent = { Text("执行步骤") },
                        trailingContent = {
                            Text(
                                text = if (stepCount > 0) "$stepCount 步" else "快速回复",
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    if (toolCallsCount > 0) {
                        item(
                            leadingContent = { CardGroupIcon(HugeIcons.Wrench01) },
                            headlineContent = { Text("工具调用") },
                            trailingContent = {
                                Text(
                                    text = "$toolCallsCount 次",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                    if (reasoningCount > 0) {
                        item(
                            leadingContent = { CardGroupIcon(HugeIcons.Brain02) },
                            headlineContent = { Text("深度思考") },
                            trailingContent = {
                                Text(
                                    text = "$reasoningCount 段",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                    item(
                        leadingContent = { CardGroupIcon(HugeIcons.Time02) },
                        headlineContent = { Text("总耗时") },
                        trailingContent = {
                            Text(
                                text = totalDurationLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
            return@LazyColumn
        }

        if (summary.summary.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CardGroupIcon(
                                    icon = HugeIcons.Sparkles,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp),
                                )
                                Text(
                                    text = "阶段总览",
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (summary.model.isNotBlank()) {
                                Tag(type = TagType.INFO) {
                                    Text(text = summary.model)
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
                CardGroup(
                    title = { Text("任务输入") },
                ) {
                    item(
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.Message01,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = summary.sentWhat,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                    )
                }
            }
        }

        if (summary.actionsDone.isNotEmpty()) {
            item {
                CardGroup(
                    title = { Text("已执行操作 (${summary.actionsDone.size})") },
                ) {
                    summary.actionsDone.forEach { action ->
                        item(
                            leadingContent = {
                                CardGroupIcon(
                                    icon = HugeIcons.Wrench01,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = action,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                        )
                    }
                }
            }
        }

        if (summary.completed.isNotEmpty()) {
            item {
                CardGroup(
                    title = { Text("已完成 (${summary.completed.size})") },
                ) {
                    summary.completed.forEach { done ->
                        item(
                            leadingContent = {
                                CardGroupIcon(
                                    icon = HugeIcons.CheckmarkCircle02,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = done,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                        )
                    }
                }
            }
        }

        if (summary.issues.isNotEmpty()) {
            item {
                val issueContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                CardGroup(
                    title = { Text("遇到的问题 (${summary.issues.size})") },
                ) {
                    summary.issues.forEach { issue ->
                        item(
                            leadingContent = {
                                CardGroupIcon(
                                    icon = HugeIcons.AlertCircle,
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = issueContainerColor,
                            ),
                            headlineContent = {
                                Text(
                                    text = issue,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                        )
                    }
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
    totalStepCount: Int?,
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
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TimingMetricCard(
                    label = "正式发出",
                    value = formatSecondsLabel(timings?.dispatchSeconds),
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        bottomStart = 20.dp,
                        topEnd = 4.dp,
                        bottomEnd = 4.dp,
                    ),
                    modifier = Modifier.weight(1f),
                )
                TimingMetricCard(
                    label = "首个回复",
                    value = formatSecondsLabel(timings?.firstReplySeconds),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f),
                )
                TimingMetricCard(
                    label = if (isRunning) "已运行" else "总耗时",
                    value = totalLabel,
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        bottomStart = 4.dp,
                        topEnd = 20.dp,
                        bottomEnd = 20.dp,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (timings == null) {
            item {
                CardGroup {
                    item(
                        leadingContent = { CardGroupIcon(HugeIcons.Time02) },
                        headlineContent = { Text("暂无时间节点记录") },
                        supportingContent = {
                            Text("任务启动后将自动记录正式发出、首个回复及各步骤耗时。")
                        },
                    )
                }
            }
            return@LazyColumn
        }

        item {
            CardGroup(
                title = { Text("关键时间节点") },
            ) {
                item(
                    leadingContent = {
                        CardGroupIcon(
                            icon = HugeIcons.Flash,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    },
                    headlineContent = { Text("任务提交到执行器") },
                    supportingContent = {
                        Text(timings.submittedAt.ifBlank { "已进入执行队列" })
                    },
                    trailingContent = {
                        TimingTrailingValue(elapsedLabel = "0.0s", deltaLabel = null)
                    },
                )

                if (timings.dispatchSeconds != null) {
                    item(
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.Message01,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        },
                        headlineContent = { Text("正式发往 Antigravity") },
                        supportingContent = {
                            Text(timings.dispatchedAt.ifBlank { "已建立远程 Interaction" })
                        },
                        trailingContent = {
                            TimingTrailingValue(
                                elapsedLabel = formatSecondsLabel(timings.dispatchSeconds),
                                deltaLabel = "+${formatSecondsLabel(timings.dispatchSeconds)}",
                            )
                        },
                    )
                }

                if (timings.firstReplySeconds != null) {
                    val deltaFromDispatch = timings.dispatchSeconds?.let {
                        (timings.firstReplySeconds - it).coerceAtLeast(0.0)
                    }
                    item(
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.Sparkles,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        },
                        headlineContent = { Text("收到首个回复") },
                        supportingContent = {
                            Text(timings.firstReplyAt.ifBlank { "模型开始返回首个输出" })
                        },
                        trailingContent = {
                            TimingTrailingValue(
                                elapsedLabel = formatSecondsLabel(timings.firstReplySeconds),
                                deltaLabel = deltaFromDispatch?.let { "+${formatSecondsLabel(it)}" },
                            )
                        },
                    )
                }

                if (timings.totalSeconds != null) {
                    item(
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.CheckmarkCircle02,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        headlineContent = {
                            Text("执行结束 (${timings.status.ifBlank { "completed" }})")
                        },
                        supportingContent = {
                            Text(timings.completedAt.ifBlank { "已完成持久化" })
                        },
                        trailingContent = {
                            TimingTrailingValue(
                                elapsedLabel = formatSecondsLabel(timings.totalSeconds),
                                deltaLabel = null,
                            )
                        },
                    )
                }
            }
        }

        if (timings.steps.isNotEmpty()) {
            val stepHeaderTitle = if (totalStepCount != null && totalStepCount > 0 && totalStepCount != timings.steps.size) {
                "步骤耗时 (${timings.steps.size} / 共 $totalStepCount 步)"
            } else {
                "步骤耗时 (${timings.steps.size})"
            }
            item {
                CardGroup(
                    title = { Text(stepHeaderTitle) },
                ) {
                    timings.steps.forEachIndexed { idx, step ->
                        val isFailed = step.status == "failed"
                        val stepTitle = buildString {
                            append("步骤 ${idx + 1} · ")
                            append(
                                when {
                                    step.label.isNotBlank() -> step.label
                                    step.type == "thought" -> "深度思考"
                                    step.type == "model_output" -> "模型输出"
                                    else -> step.type.ifBlank { "执行步骤" }
                                }
                            )
                        }
                        val stepIcon = when {
                            isFailed -> HugeIcons.AlertCircle
                            step.type == "thought" -> HugeIcons.Brain02
                            step.type == "model_output" -> HugeIcons.Sparkles
                            else -> HugeIcons.Wrench01
                        }
                        item(
                            leadingContent = {
                                CardGroupIcon(
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
                            headlineContent = {
                                Text(
                                    text = stepTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text("${step.type.ifBlank { "step" }} · ${if (isFailed) "失败" else "已完成"}")
                            },
                            trailingContent = {
                                TimingTrailingValue(
                                    elapsedLabel = formatSecondsLabel(step.elapsedSeconds),
                                    deltaLabel = "+${formatSecondsLabel(step.deltaSeconds)}",
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimingMetricCard(
    label: String,
    value: String,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = shape,
        color = CustomColors.listItemColors.containerColor,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (deltaLabel != null) {
            Text(
                text = deltaLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
