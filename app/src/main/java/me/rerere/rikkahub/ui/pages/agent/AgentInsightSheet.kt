package me.rerere.rikkahub.ui.pages.agent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.rikkahub.data.agent.AgentProgressSummary
import me.rerere.rikkahub.data.agent.AgentTimeline
import me.rerere.rikkahub.data.agent.formatAgentOffsetMs
import me.rerere.rikkahub.data.agent.parseAgentSummary
import me.rerere.rikkahub.data.agent.parseAgentTimeline

enum class AgentInsightTab {
    SUMMARY,
    TIMELINE,
}

@Composable
internal fun AgentInsightSheet(
    message: UIMessage?,
    isRunning: Boolean,
    liveElapsedSeconds: Long? = null,
    initialTab: AgentInsightTab = AgentInsightTab.SUMMARY,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Expanded)
    val summary = remember(message?.agentSummaryJson) { message?.parseAgentSummary() }
    val timeline = remember(message?.agentTimelineJson) { message?.parseAgentTimeline() }
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var dragAccumulated by remember { mutableFloatStateOf(0f) }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .pointerInput(activeTab) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulated = 0f },
                        onHorizontalDrag = { _, dragAmount -> dragAccumulated += dragAmount },
                        onDragEnd = {
                            if (dragAccumulated < -72f && activeTab == AgentInsightTab.SUMMARY) {
                                activeTab = AgentInsightTab.TIMELINE
                            } else if (dragAccumulated > 72f && activeTab == AgentInsightTab.TIMELINE) {
                                activeTab = AgentInsightTab.SUMMARY
                            }
                            dragAccumulated = 0f
                        },
                        onDragCancel = { dragAccumulated = 0f },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InsightHeaderTabs(
                activeTab = activeTab,
                onSelectTab = { activeTab = it },
                isRunning = isRunning,
                summary = summary,
            )

            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "AgentInsightTabTransition",
            ) { tab ->
                when (tab) {
                    AgentInsightTab.SUMMARY -> SummaryTabContent(
                        summary = summary,
                        isRunning = isRunning,
                    )

                    AgentInsightTab.TIMELINE -> TimelineTabContent(
                        timeline = timeline,
                        isRunning = isRunning,
                        liveElapsedSeconds = liveElapsedSeconds,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightHeaderTabs(
    activeTab: AgentInsightTab,
    onSelectTab: (AgentInsightTab) -> Unit,
    isRunning: Boolean,
    summary: AgentProgressSummary?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = HugeIcons.Sparkles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = "Agent 运行洞察",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    val subtitle = buildString {
                        append(if (isRunning) "执行中" else "已完成")
                        if (!summary?.model.isNullOrBlank()) {
                            append(" · ")
                            append(summary?.model)
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (summary != null && summary.stepCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "${summary.stepCount} 步",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SegmentedTabPill(
                    label = "运行总结",
                    icon = HugeIcons.Sparkles,
                    selected = activeTab == AgentInsightTab.SUMMARY,
                    onClick = { onSelectTab(AgentInsightTab.SUMMARY) },
                    modifier = Modifier.weight(1f),
                )
                SegmentedTabPill(
                    label = "时间节点",
                    icon = HugeIcons.Clock01,
                    selected = activeTab == AgentInsightTab.TIMELINE,
                    onClick = { onSelectTab(AgentInsightTab.TIMELINE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SegmentedTabPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SummaryTabContent(
    summary: AgentProgressSummary?,
    isRunning: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (summary == null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (isRunning) "正在等待首次总结生成" else "暂无运行总结",
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    Text(
                        text = if (isRunning) {
                            "Agent 运行期间每 2 分钟且有新增步骤时，会自动调用快速模型总结当前发送内容、执行操作、完成情况与问题。"
                        } else {
                            "该消息尚未记录快速模型总结，您可以左右滑动查看关键时间节点。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Column
        }

        if (summary.overview.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "当前总览",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = summary.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (summary.sentSummary.isNotBlank()) {
            InsightSectionCard(
                title = "发送内容",
                icon = HugeIcons.Sparkles,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = summary.sentSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (summary.actions.isNotEmpty()) {
            InsightListCard(
                title = "已执行操作",
                icon = HugeIcons.CheckList,
                items = summary.actions,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                bulletColor = MaterialTheme.colorScheme.primary,
            )
        }

        if (summary.completed.isNotEmpty()) {
            InsightListCard(
                title = "已完成事项",
                icon = HugeIcons.CheckList,
                items = summary.completed,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                bulletColor = MaterialTheme.colorScheme.secondary,
            )
        }

        if (summary.issues.isNotEmpty()) {
            InsightListCard(
                title = "遇到的问题",
                icon = HugeIcons.Alert01,
                items = summary.issues,
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                bulletColor = MaterialTheme.colorScheme.error,
            )
        } else {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = "运行过程未发现明显异常或阻塞",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightSectionCard(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
            content()
        }
    }
}

@Composable
private fun InsightListCard(
    title: String,
    icon: ImageVector,
    items: List<String>,
    containerColor: Color,
    bulletColor: Color,
) {
    InsightSectionCard(
        title = title,
        icon = icon,
        containerColor = containerColor,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(bulletColor),
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineTabContent(
    timeline: AgentTimeline?,
    isRunning: Boolean,
    liveElapsedSeconds: Long?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val totalDisplay = when {
            timeline?.totalDurationMs != null -> formatAgentOffsetMs(timeline.totalDurationMs)
            liveElapsedSeconds != null -> formatAgentOffsetMs(liveElapsedSeconds * 1000L)
            else -> "—"
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimelineKpiCard(
                label = "正式发出",
                value = formatAgentOffsetMs(timeline?.submittedOffsetMs),
                modifier = Modifier.weight(1f),
            )
            TimelineKpiCard(
                label = "首个回复",
                value = formatAgentOffsetMs(timeline?.firstReplyOffsetMs),
                modifier = Modifier.weight(1f),
            )
            TimelineKpiCard(
                label = if (isRunning) "已运行" else "总耗时",
                value = totalDisplay,
                modifier = Modifier.weight(1f),
            )
        }

        if (timeline == null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (isRunning) "正在同步关键时间节点…" else "暂无时间节点记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp),
                )
            }
            return@Column
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                val nodes = buildList {
                    add(TimelineNodeUi("任务启动", "开始调度执行", "0.0秒", null, isAccent = false))
                    timeline.submittedOffsetMs?.let {
                        add(TimelineNodeUi("正式发出消息", "已提交至 Antigravity", formatAgentOffsetMs(it), "+${formatAgentOffsetMs(it)}", isAccent = true))
                    }
                    timeline.firstReplyOffsetMs?.let {
                        add(TimelineNodeUi("收到首个回复", "模型开始返回结果", formatAgentOffsetMs(it), null, isAccent = true))
                    }
                    timeline.steps.forEachIndexed { idx, step ->
                        add(
                            TimelineNodeUi(
                                title = "步骤 ${idx + 1} · ${step.label.ifBlank { step.type }}",
                                subtitle = step.type,
                                offset = formatAgentOffsetMs(step.offsetMs),
                                delta = if (step.deltaMs > 0L) "+${formatAgentOffsetMs(step.deltaMs)}" else null,
                                isAccent = false,
                            )
                        )
                    }
                    timeline.totalDurationMs?.let {
                        add(TimelineNodeUi("执行结束", "全部步骤已完成", formatAgentOffsetMs(it), null, isAccent = true))
                    }
                }

                nodes.forEachIndexed { index, node ->
                    TimelineNodeRow(
                        node = node,
                        isLast = index == nodes.lastIndex,
                    )
                }
            }
        }
    }
}

private data class TimelineNodeUi(
    val title: String,
    val subtitle: String,
    val offset: String,
    val delta: String?,
    val isAccent: Boolean,
)

@Composable
private fun TimelineKpiCard(
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
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmallEmphasized,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TimelineNodeRow(
    node: TimelineNodeUi,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(if (node.isAccent) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (node.isAccent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    ),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(vertical = 2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 2.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = node.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (node.isAccent) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = node.offset,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (node.subtitle.isNotBlank()) {
                    Text(
                        text = node.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (node.delta != null) {
                    Text(
                        text = "间隔 ${node.delta}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}
