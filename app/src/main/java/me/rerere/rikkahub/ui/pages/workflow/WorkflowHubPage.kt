package me.rerere.rikkahub.ui.pages.workflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.Notification01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.update.usesAntigravityUpdates
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun WorkflowHubPage(
    vm: WorkflowHubVM = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val connectedServicesCount = listOf(
        state.workflowConnected,
        state.pocketBaseConnected,
        state.beszelConnected,
    ).count { it }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("工作流") },
                subtitle = {
                    Text(
                        text = if (connectedServicesCount > 0) {
                            "已连接 $connectedServicesCount 项云端服务 · 编排与运维"
                        } else {
                            "工作流编排、数据库与服务器监控"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (state.isRefreshing) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator(modifier = Modifier.size(20.dp))
                        }
                    } else {
                        IconButton(onClick = vm::refresh) {
                            Icon(HugeIcons.Refresh01, contentDescription = "刷新状态")
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CustomColors.topBarColors.containerColor,
                    scrolledContainerColor = CustomColors.topBarColors.scrolledContainerColor,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Important Notifications / Alerts
            item(key = "notices") {
                if (state.notices.isEmpty()) {
                    WorkflowHealthyBanner(
                        connectedServicesCount = connectedServicesCount,
                    )
                } else {
                    CardGroup(
                        title = { Text("重要通知") },
                    ) {
                        state.notices.forEach { notice ->
                            item(
                                onClick = { navController.navigate(notice.destination) },
                                leadingContent = {
                                    val (icon, containerColor, contentColor) = when (notice.severity) {
                                        HubNoticeSeverity.ERROR -> Triple(
                                            HugeIcons.Alert01,
                                            MaterialTheme.colorScheme.errorContainer,
                                            MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        HubNoticeSeverity.WARNING -> Triple(
                                            HugeIcons.Alert01,
                                            MaterialTheme.colorScheme.tertiaryContainer,
                                            MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                        HubNoticeSeverity.INFO -> Triple(
                                            HugeIcons.Notification01,
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    CardGroupIcon(
                                        icon = icon,
                                        containerColor = containerColor,
                                        contentColor = contentColor,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = notice.subtitle,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
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
                                Text(
                                    text = notice.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // 2. Primary Workflow & Infrastructure Grid
            item(key = "primary_grid") {
                val workflowSubtitle = when {
                    !state.workflowConnected -> "未连接 · 点击登录编排服务"
                    state.activeRunsCount > 0 -> "${state.activeRunsCount} 个执行中 · 计划与资料库"
                    state.recentRunsCount > 0 -> "最近 ${state.recentRunsCount} 条记录 · 计划与模板"
                    else -> "${state.workflowEmail ?: "已连接"} · 计划与资料库"
                }
                val beszelSubtitle = when {
                    !state.beszelConnected -> "未连接 · 点击配置 Beszel"
                    state.beszelSystems.isNotEmpty() -> {
                        val upCount = state.beszelSystems.count { it.isUp }
                        "$upCount / ${state.beszelSystems.size} 台在线 · 指标与告警"
                    }
                    else -> "已连接 · 实时指标与容器"
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WorkflowHubHeroCard(
                        title = "Workflow 编排",
                        subtitle = workflowSubtitle,
                        icon = HugeIcons.GitFork,
                        isConnected = state.workflowConnected,
                        onClick = { navController.navigate(Screen.Workflow) },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val pbSubtitle = if (state.pocketBaseConnected) {
                            state.pocketBaseHost?.removePrefix("https://")?.removePrefix("http://")
                                ?: "已连接"
                        } else {
                            "表结构 · 记录与 SQL"
                        }
                        HubEntryTileCard(
                            title = "数据库",
                            subtitle = pbSubtitle,
                            icon = HugeIcons.Database02,
                            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { navController.navigate(Screen.PocketBase) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 128.dp),
                        )
                        HubEntryTileCard(
                            title = "服务器",
                            subtitle = beszelSubtitle,
                            icon = HugeIcons.CloudServer,
                            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { navController.navigate(Screen.Beszel) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 128.dp),
                        )
                    }
                }
            }

            // 3. More Tools & Entries
            item(key = "more_entries") {
                CardGroup(
                    title = { Text("更多入口") },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.AgentWorkflow) },
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.Sparkles,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        supportingContent = {
                            Text("ACP 远程开发 · 后台任务与审批")
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
                        Text("远程 Agent 任务")
                    }

                    item(
                        onClick = { navController.navigate(Screen.Workspaces) },
                        leadingContent = {
                            CardGroupIcon(
                                icon = HugeIcons.Folder01,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        },
                        supportingContent = {
                            Text("本地沙盒 · SSH 远程终端与文件管理")
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
                }
            }
        }
    }
}

@Composable
private fun WorkflowHealthyBanner(
    connectedServicesCount: Int,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CustomColors.listItemColors.containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (connectedServicesCount > 0) {
                            HugeIcons.CheckmarkCircle02
                        } else {
                            HugeIcons.Notification01
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (connectedServicesCount > 0) {
                        "服务运行正常"
                    } else {
                        "暂无重要通知"
                    },
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (connectedServicesCount > 0) {
                        "工作流、服务器与后台任务均无异常告警"
                    } else {
                        "点击下方入口连接 Workflow、PocketBase 或 Beszel"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WorkflowHubHeroCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isConnected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (isConnected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = "已连接",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HubEntryTileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = CustomColors.listItemColors.containerColor,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconContainerColor,
                    contentColor = iconContentColor,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
