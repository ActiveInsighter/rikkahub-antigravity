package me.rerere.rikkahub.ui.pages.workflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Pause
import me.rerere.hugeicons.stroke.Play
import me.rerere.rikkahub.data.workflow.StatusTone
import me.rerere.rikkahub.data.workflow.WorkflowRunSchedule
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.RikkaConfirmDialog

@Composable
fun WorkflowSchedulesTab(
    state: WorkflowSchedulesUiState,
    bottomPadding: Dp,
    onRefresh: () -> Unit,
    onPageChange: (Int) -> Unit,
    onToggle: (WorkflowRunSchedule) -> Unit,
    onDelete: (WorkflowRunSchedule, () -> Unit) -> Unit,
    onOpenRun: (String) -> Unit,
    onOpenCreateRun: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteTarget by remember { mutableStateOf<WorkflowRunSchedule?>(null) }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        if (state.isLoading && state.schedules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ContainedLoadingIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomPadding + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    WorkflowErrorBanner(
                        message = state.errorMessage,
                        onDismiss = onClearError,
                    )
                }

                if (state.schedules.isEmpty()) {
                    item {
                        WorkflowEmptyState(
                            icon = HugeIcons.Calendar03,
                            title = "暂无重复计划",
                            subtitle = "在新建或编辑工作流时开启「每天重复」，即可按固定时区自动定时执行。",
                            actionLabel = "新建定时工作流",
                            onAction = onOpenCreateRun,
                        )
                    }
                } else {
                    items(state.schedules, key = { it.id }) { schedule ->
                        ScheduleExpressiveCard(
                            schedule = schedule,
                            busy = state.isMutating,
                            onOpenSource = { onOpenRun(schedule.sourceRun) },
                            onOpenLastRun = { onOpenRun(schedule.lastRun) },
                            onToggle = { onToggle(schedule) },
                            onDelete = { deleteTarget = schedule },
                        )
                    }

                    item {
                        WorkflowPagerBar(
                            page = state.page,
                            totalPages = state.totalPages,
                            totalItems = state.totalItems,
                            enabled = !state.isRefreshing,
                            onPageChange = onPageChange,
                        )
                    }

                    item {
                        Text(
                            text = "前一次仍在排队或运行时跳过本次；错过超过 15 分钟的执行不补跑；暂停或删除计划不会取消已提交的 Run。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        RikkaConfirmDialog(
            show = true,
            title = "删除重复计划？",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                onDelete(target) { deleteTarget = null }
            },
            onDismiss = { deleteTarget = null },
        ) {
            Text(
                text = "确定要删除「${target.displayTitle}」的每天重复计划吗？后续将不再自动执行，已生成的 Run 和执行历史会保留。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ScheduleExpressiveCard(
    schedule: WorkflowRunSchedule,
    busy: Boolean,
    onOpenSource: () -> Unit,
    onOpenLastRun: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val tone = if (schedule.enabled) StatusTone.SUCCESS else StatusTone.NEUTRAL
    val toneColors = statusToneColors(tone)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = schedule.sourceRun.isNotBlank(), onClick = onOpenSource)
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = schedule.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Calendar03,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "每天 ${schedule.rule.time} · ${schedule.rule.formatUtcOffset()}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                StatusBadgePill(
                    label = if (schedule.enabled) "已启用" else "已暂停",
                    colors = toneColors,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "下次执行",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (schedule.enabled) {
                                formatWorkflowTime(schedule.nextRunAt)
                            } else {
                                "暂停中"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "已跳过",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = buildString {
                                append("${schedule.skippedCount} 次")
                                if (schedule.lastSkippedAt.isNotBlank()) {
                                    append(" · ${formatWorkflowTime(schedule.lastSkippedAt)}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (schedule.lastError.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Alert01,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = schedule.lastError,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onToggle,
                    enabled = !busy,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = if (schedule.enabled) HugeIcons.Pause else HugeIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (schedule.enabled) "暂停" else "恢复")
                }

                if (schedule.lastRun.isNotBlank()) {
                    OutlinedButton(
                        onClick = onOpenLastRun,
                        enabled = !busy,
                        shape = CircleShape,
                    ) {
                        Icon(
                            imageVector = HugeIcons.Clock01,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("最近执行")
                    }
                }

                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = onDelete,
                    enabled = !busy,
                ) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = "删除计划",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
