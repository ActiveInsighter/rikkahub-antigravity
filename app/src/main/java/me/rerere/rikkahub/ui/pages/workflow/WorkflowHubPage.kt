package me.rerere.rikkahub.ui.pages.workflow

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.Notification03
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Terminal
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.agent.AgentRepository
import me.rerere.rikkahub.data.update.usesAntigravityUpdates
import me.rerere.rikkahub.data.workflow.DispatchStatus
import me.rerere.rikkahub.ui.components.nav.MainTab
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.beszel.BeszelVM
import me.rerere.rikkahub.ui.pages.pocketbase.PocketBaseVM
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private data class HubNotification(
    val id: String,
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val onClick: () -> Unit,
)

@Composable
fun WorkflowHubPage(
    workflowVm: WorkflowVM = koinViewModel(),
    beszelVm: BeszelVM = koinViewModel(),
    pocketBaseVm: PocketBaseVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val agentRepository: AgentRepository = koinInject()

    val workflowState by workflowVm.uiState.collectAsStateWithLifecycle()
    val beszelState by beszelVm.uiState.collectAsStateWithLifecycle()
    val pbState by pocketBaseVm.uiState.collectAsStateWithLifecycle()
    val activeAgentIds by remember(agentRepository) {
        agentRepository.observeActiveConversationIds()
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val agentStatusMap by remember(agentRepository) {
        agentRepository.observeConversationStatusMap()
    }.collectAsStateWithLifecycle(initialValue = emptyMap())

    val unreadAgents = remember(agentStatusMap) {
        agentStatusMap.values.count { it.hasUnread }
    }
    val activeRunsCount = remember(workflowState.runs) {
        workflowState.runs.count { it.status.isActive }
    }
    val failedRunsCount = remember(workflowState.runs) {
        workflowState.runs.count { it.status == DispatchStatus.FAILED }
    }
    val onlineSystemsCount = remember(beszelState.systems) {
        beszelState.systems.count { it.isUp }
    }
    val downSystemsCount = remember(beszelState.systems) {
        beszelState.systems.count { it.isDown }
    }
    val isRefreshing = workflowState.isRefreshing || beszelState.isRefreshing || pbState.isLoading

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val notifications = buildList {
        if (beszelState.activeAlertCount > 0 || downSystemsCount > 0) {
            val detailParts = buildList {
                if (beszelState.activeAlertCount > 0) add("${beszelState.activeAlertCount} 条活跃告警")
                if (downSystemsCount > 0) add("$downSystemsCount 台节点离线")
            }
            add(
                HubNotification(
                    id = "beszel_alert",
                    title = "服务器异常告警",
                    detail = detailParts.joinToString(" · "),
                    icon = HugeIcons.Alert02,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = { navController.navigate(Screen.Beszel) },
                )
            )
        }
        if (failedRunsCount > 0) {
            add(
                HubNotification(
                    id = "workflow_failed",
                    title = "工作流执行失败",
                    detail = "最近有 $failedRunsCount 个任务执行失败，点击查看详情",
                    icon = HugeIcons.Alert02,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = { navController.navigate(Screen.Workflow) },
                )
            )
        }
        if (activeRunsCount > 0) {
            add(
                HubNotification(
                    id = "workflow_running",
                    title = "工作流运行中",
                    detail = "当前有 $activeRunsCount 个云端任务正在排队或执行",
                    icon = HugeIcons.GitFork,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { navController.navigate(Screen.Workflow) },
                )
            )
        }
        if (activeAgentIds.isNotEmpty() || unreadAgents > 0) {
            val detail = buildList {
                if (activeAgentIds.isNotEmpty()) add("${activeAgentIds.size} 个 Agent 运行中")
                if (unreadAgents > 0) add("$unreadAgents 条未读结果")
            }.joinToString(" · ")
            add(
                HubNotification(
                    id = "agent_activity",
                    title = "Agent 后台动态",
                    detail = detail,
                    icon = HugeIcons.Sparkles,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { navController.switchTab(MainTab.AGENT) },
                )
            )
        }
        if (pbState.errorMessage != null) {
            add(
                HubNotification(
                    id = "pb_error",
                    title = "数据库异常提醒",
                    detail = pbState.errorMessage.orEmpty(),
                    icon = HugeIcons.Database02,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = { navController.navigate(Screen.PocketBase) },
                )
            )
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("工作流") },
                subtitle = {
                    val connectedCount = listOfNotNull(
                        workflowState.session,
                        beszelState.session,
                        pbState.session,
                    ).size
                    Text(
                        text = if (connectedCount > 0) {
                            "已连接 $connectedCount/3 项服务 · 云端编排与运维中心"
                        } else {
                            "云端编排、服务器监控与数据库"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (workflowState.session != null) workflowVm.refresh()
                            if (beszelState.session != null) beszelVm.refresh()
                            if (pbState.session != null) pocketBaseVm.refreshAll()
                        },
                        enabled = !isRefreshing,
                    ) {
                        if (isRefreshing) {
                            LoadingIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(HugeIcons.Refresh03, contentDescription = "刷新状态")
                        }
                    }
                    if (usesAntigravityUpdates) {
                        IconButton(onClick = { navController.navigate(Screen.ApkDownloads) }) {
                            Icon(HugeIcons.Download01, contentDescription = "下载与更新")
                        }
                    }
                    IconButton(onClick = { navController.navigate(Screen.Setting) }) {
                        Icon(HugeIcons.Settings03, contentDescription = "设置")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Important Notifications & Live Status
            item(key = "notifications") {
                if (notifications.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = CustomColors.listItemColors.containerColor,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    imageVector = HugeIcons.CheckmarkCircle02,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "重要通知",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "当前无告警或失败任务，各项服务运行正常",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Notification03,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "重要通知 (${notifications.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        notifications.forEach { notice ->
                            Surface(
                                onClick = notice.onClick,
                                shape = RoundedCornerShape(20.dp),
                                color = notice.containerColor,
                                contentColor = notice.contentColor,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = notice.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = notice.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = notice.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = notice.contentColor.copy(alpha = 0.85f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Icon(
                                        imageVector = HugeIcons.ArrowRight01,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Core Entries: Workflow, Beszel Server, PocketBase Database
            item(key = "core_entries") {
                CardGroup(
                    title = { Text("工作流与基础设施") },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.Workflow) },
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.GitFork,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        supportingContent = {
                            val subtitle = when {
                                workflowState.session != null && activeRunsCount > 0 ->
                                    "${workflowState.session?.email} · $activeRunsCount 个任务运行中"
                                workflowState.session != null ->
                                    "已连接 · ${workflowState.session?.email}"
                                else -> "云端编排 · 计划、模板、资料库与文件转换"
                            }
                            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            EntryTrailing(isConnected = workflowState.session != null)
                        },
                    ) {
                        Text("Workflow 工作流")
                    }

                    item(
                        onClick = { navController.navigate(Screen.Beszel) },
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.CloudServer,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        },
                        supportingContent = {
                            val subtitle = when {
                                beszelState.session != null && beszelState.systems.isNotEmpty() ->
                                    "在线 $onlineSystemsCount/${beszelState.systems.size} 台 · 告警 ${beszelState.activeAlertCount}"
                                beszelState.session != null ->
                                    "已连接 · ${beszelState.session?.email}"
                                else -> "Beszel 监控 · 实时指标、容器与告警"
                            }
                            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            EntryTrailing(
                                isConnected = beszelState.session != null,
                                hasAlert = beszelState.activeAlertCount > 0 || downSystemsCount > 0,
                            )
                        },
                    ) {
                        Text("服务器监控")
                    }

                    item(
                        onClick = { navController.navigate(Screen.PocketBase) },
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.Database02,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        },
                        supportingContent = {
                            val subtitle = when {
                                pbState.session != null && pbState.collections.isNotEmpty() ->
                                    "${pbState.session?.email} · ${pbState.collections.size} 个集合"
                                pbState.session != null ->
                                    "已连接 · ${pbState.session?.email}"
                                else -> "PocketBase 控制台 · 表结构、记录、日志与 SQL"
                            }
                            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            EntryTrailing(
                                isConnected = pbState.session != null,
                                hasAlert = pbState.errorMessage != null,
                            )
                        },
                    ) {
                        Text("数据库")
                    }
                }
            }

            // 3. Secondary Tools & System
            item(key = "tools_entries") {
                CardGroup(
                    title = { Text("更多工具") },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.Workspaces) },
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.Terminal,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        supportingContent = {
                            Text("本地与 SSH 工作区、终端与文件管理")
                        },
                        trailingContent = {
                            Icon(
                                imageVector = HugeIcons.ArrowRight01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    ) {
                        Text("工作区与终端")
                    }

                    if (usesAntigravityUpdates) {
                        item(
                            onClick = { navController.navigate(Screen.ApkDownloads) },
                            leadingContent = {
                                CardGroupIcon(
                                    icon = HugeIcons.Download01,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            supportingContent = {
                                Text("查看与安装最新 Antigravity 构建")
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = HugeIcons.ArrowRight01,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        ) {
                            Text("下载与更新")
                        }
                    }

                    item(
                        onClick = { navController.navigate(Screen.Setting) },
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.Settings03,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        supportingContent = {
                            Text("模型提供商、助手与外观偏好")
                        },
                        trailingContent = {
                            Icon(
                                imageVector = HugeIcons.ArrowRight01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    ) {
                        Text("应用设置")
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryTrailing(
    isConnected: Boolean,
    hasAlert: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasAlert) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
        } else if (isConnected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34C759))
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
