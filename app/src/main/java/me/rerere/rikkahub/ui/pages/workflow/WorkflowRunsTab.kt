package me.rerere.rikkahub.ui.pages.workflow

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.Pause
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.data.workflow.DispatchRequestedAction
import me.rerere.rikkahub.data.workflow.DispatchStatus
import me.rerere.rikkahub.data.workflow.RunListFilter
import me.rerere.rikkahub.data.workflow.WorkflowRun
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.cardGroupItemShape
import me.rerere.rikkahub.ui.theme.CustomColors

@Composable
internal fun WorkflowRunsTab(
    modifier: Modifier = Modifier,
    bottomPadding: Dp,
    state: WorkflowUiState,
    favoriteRunIds: Set<String>,
    onRefresh: () -> Unit,
    onFilterChange: (RunListFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onSelectRun: (WorkflowRun) -> Unit,
    onEditDraft: (WorkflowRun) -> Unit,
    onEditAsNewVersion: (WorkflowRun) -> Unit,
    onCommandRun: (WorkflowRun, DispatchRequestedAction) -> Unit,
    onCloneRun: (WorkflowRun, DispatchStatus) -> Unit,
    onToggleFavorite: (WorkflowRun) -> Unit,
    onSaveAsTemplate: (WorkflowRun) -> Unit,
    onRequestDelete: (WorkflowRun) -> Unit,
    onClearError: () -> Unit,
    onOpenCreate: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomPadding + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "filter_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RunListFilter.entries.forEach { filter ->
                        val selected = state.filter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { onFilterChange(filter) },
                            label = { Text(filter.label) },
                            shape = CircleShape,
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = HugeIcons.Tick01,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
            }

            item(key = "search_and_status") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.searchInput,
                        onValueChange = onSearchChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = CircleShape,
                        placeholder = { Text("搜索工作流名称或 Run ID") },
                        leadingIcon = {
                            Icon(
                                imageVector = HugeIcons.Search01,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            if (state.searchInput.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(
                                        imageVector = HugeIcons.Cancel01,
                                        contentDescription = "清除搜索",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.debouncedQuery.isNotEmpty()) {
                                "搜索“${state.debouncedQuery}”"
                            } else {
                                "按更新时间排序"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = when {
                                state.isRefreshing -> "更新中…"
                                state.isLoading -> "加载中…"
                                else -> "共 ${state.totalItems} 条"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                item(key = "error_banner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = HugeIcons.Alert01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                onClearError()
                                onRefresh()
                            }) {
                                Text("重试", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            if (state.isLoading && state.runs.isEmpty()) {
                item(key = "loading_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
                    }
                }
            } else if (state.runs.isEmpty()) {
                item(key = "empty_state") {
                    WorkflowRunsEmptyState(
                        filter = state.filter,
                        hasSearch = state.debouncedQuery.isNotEmpty(),
                        onResetFilter = { onFilterChange(RunListFilter.ALL) },
                        onClearSearch = { onSearchChange("") },
                        onCreateNew = onOpenCreate,
                    )
                }
            } else {
                item(key = "runs_group") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.runs.forEachIndexed { index, run ->
                            RunCardItem(
                                run = run,
                                index = index,
                                totalCount = state.runs.size,
                                isMutating = state.isMutating,
                                isFavorited = run.id in favoriteRunIds,
                                onClick = { onSelectRun(run) },
                                onEditDraft = { onEditDraft(run) },
                                onEditAsNewVersion = { onEditAsNewVersion(run) },
                                onCommand = { action -> onCommandRun(run, action) },
                                onClone = { status -> onCloneRun(run, status) },
                                onToggleFavorite = { onToggleFavorite(run) },
                                onSaveAsTemplate = { onSaveAsTemplate(run) },
                                onDelete = { onRequestDelete(run) },
                            )
                        }
                    }
                }

                if (state.totalPages > 1) {
                    item(key = "pager") {
                        WorkflowPagerBar(
                            page = state.page,
                            totalPages = state.totalPages,
                            totalItems = state.totalItems,
                            enabled = !state.isRefreshing,
                            onPageChange = onPageChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunCardItem(
    run: WorkflowRun,
    index: Int,
    totalCount: Int,
    isMutating: Boolean,
    isFavorited: Boolean,
    onClick: () -> Unit,
    onEditDraft: () -> Unit,
    onEditAsNewVersion: () -> Unit,
    onCommand: (DispatchRequestedAction) -> Unit,
    onClone: (DispatchStatus) -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveAsTemplate: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusMeta = run.statusMeta()
    val toneColors = statusToneColors(statusMeta.tone)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val baseShape = cardGroupItemShape(index, totalCount)
    val animatedCorner by animateDpAsState(
        targetValue = if (isPressed) 20.dp else 4.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val shape = if (isPressed) RoundedCornerShape(animatedCorner) else baseShape

    val actions = remember(run, isMutating, isFavorited) {
        buildList {
            if (run.canEditDraft) {
                add(
                    ItemAction(
                        text = "编辑草稿",
                        icon = HugeIcons.Edit03,
                        enabled = !isMutating,
                        onClick = onEditDraft,
                    )
                )
            }
            if (run.canEditAsNewVersion) {
                add(
                    ItemAction(
                        text = "基于此版本编辑",
                        icon = HugeIcons.Edit03,
                        enabled = !isMutating,
                        onClick = onEditAsNewVersion,
                    )
                )
            }
            if (run.canPause) {
                add(
                    ItemAction(
                        text = "暂停",
                        icon = HugeIcons.Pause,
                        enabled = !isMutating,
                        onClick = { onCommand(DispatchRequestedAction.PAUSE) },
                    )
                )
            }
            if (run.canResume) {
                add(
                    ItemAction(
                        text = "恢复",
                        icon = HugeIcons.Play,
                        enabled = !isMutating,
                        onClick = { onCommand(DispatchRequestedAction.RESUME) },
                    )
                )
            }
            if (run.canCancel) {
                add(
                    ItemAction(
                        text = "取消运行",
                        icon = HugeIcons.StopCircle,
                        enabled = !isMutating,
                        onClick = { onCommand(DispatchRequestedAction.CANCEL) },
                    )
                )
            }
            add(
                ItemAction(
                    text = if (isFavorited) "取消收藏" else "收藏",
                    icon = HugeIcons.Favourite,
                    enabled = !isMutating,
                    onClick = onToggleFavorite,
                )
            )
            if (run.planText.isNotBlank()) {
                add(
                    ItemAction(
                        text = "存为模板",
                        icon = HugeIcons.Book02,
                        enabled = !isMutating,
                        onClick = onSaveAsTemplate,
                    )
                )
                add(
                    ItemAction(
                        text = "复制为草稿",
                        icon = HugeIcons.Copy01,
                        enabled = !isMutating,
                        onClick = { onClone(DispatchStatus.DRAFT) },
                    )
                )
                add(
                    ItemAction(
                        text = "完整重跑",
                        icon = HugeIcons.Refresh03,
                        enabled = !isMutating,
                        onClick = { onClone(DispatchStatus.QUEUED) },
                    )
                )
            }
            if (run.canDelete) {
                add(
                    ItemAction(
                        text = "删除",
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        enabled = !isMutating,
                        onClick = onDelete,
                    )
                )
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        shape = shape,
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (run.status == DispatchStatus.RUNNING) {
                    ContainedLoadingIndicator(modifier = Modifier.size(40.dp))
                } else {
                    RunStatusCircle(status = run.status, toneColors = toneColors)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = run.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusBadgePill(label = statusMeta.label, colors = toneColors)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = HugeIcons.GitFork,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = run.versionLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            text = formatRelativeTime(run.updated),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (run.canEditDraft) {
                        FilledTonalIconButton(
                            onClick = onEditDraft,
                            enabled = !isMutating,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Edit03,
                                contentDescription = "编辑草稿",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (actions.isNotEmpty()) {
                        ItemActionMenu(actions = actions)
                    }
                }
            }

            if (run.status.isActive || run.totalTasks > 0) {
                val progress = if (run.totalTasks > 0) {
                    (run.completedTasks.toFloat() / run.totalTasks.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Row(
                    modifier = Modifier.padding(start = 54.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (run.status.isActive) {
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f),
                            color = toneColors.content,
                            trackColor = toneColors.container,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text(
                        text = "${run.completedTasks} / ${run.totalTasks} 任务",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RunStatusCircle(
    status: DispatchStatus,
    toneColors: ToneColors,
) {
    val icon: ImageVector = when (status) {
        DispatchStatus.DRAFT -> HugeIcons.Edit03
        DispatchStatus.QUEUED -> HugeIcons.Clock01
        DispatchStatus.RUNNING -> HugeIcons.Zap
        DispatchStatus.SUCCEEDED -> HugeIcons.Tick01
        DispatchStatus.FAILED -> HugeIcons.Alert01
        DispatchStatus.CANCELED -> HugeIcons.Cancel01
    }
    val container by animateColorAsState(
        targetValue = toneColors.container,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val content by animateColorAsState(
        targetValue = toneColors.content,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = content,
        )
    }
}

@Composable
private fun WorkflowRunsEmptyState(
    filter: RunListFilter,
    hasSearch: Boolean,
    onResetFilter: () -> Unit,
    onClearSearch: () -> Unit,
    onCreateNew: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HugeIcons.Zap,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = when {
                    hasSearch -> "没有匹配的工作流"
                    filter == RunListFilter.ALL -> "还没有工作流"
                    else -> "暂无「${filter.label}」状态的工作流"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = when {
                    hasSearch -> "尝试更换关键词或清除搜索条件"
                    filter != RunListFilter.ALL -> "切换筛选条件查看其他状态的 Run"
                    else -> "点击右下角 + 号创建一个新的工作流 Run"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasSearch) {
                    FilledTonalButton(onClick = onClearSearch, shape = CircleShape) {
                        Text("清除搜索")
                    }
                } else if (filter != RunListFilter.ALL) {
                    FilledTonalButton(onClick = onResetFilter, shape = CircleShape) {
                        Text("查看全部")
                    }
                } else {
                    Button(onClick = onCreateNew, shape = CircleShape) {
                        Icon(HugeIcons.Add01, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新建工作流")
                    }
                }
            }
        }
    }
}
