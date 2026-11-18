package me.rerere.rikkahub.ui.pages.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.agent.AgentRepository
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.workflow.WorkflowRepository
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.cardGroupItemShape
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.readBooleanPreference
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.compose.koinInject
import java.util.Calendar
import kotlin.uuid.Uuid

@Composable
fun HomePage() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val agentRepository: AgentRepository = koinInject()
    val workflowRepository: WorkflowRepository = koinInject()

    val session by workflowRepository.session.collectAsStateWithLifecycle()
    val recentAgentConversations by remember(agentRepository) {
        agentRepository.observeConversations(ConversationSortOrder.UPDATE_TIME)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        agentRepository.syncThreadsFromRemote()
    }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "早上好"
            in 12..13 -> "中午好"
            in 14..17 -> "下午好"
            else -> "晚上好"
        }
    }

    fun openChat() {
        val id = if (context.readBooleanPreference("create_new_conversation_on_start", true)) {
            Uuid.random().toString()
        } else {
            context.readStringPreference(
                "lastConversationId",
                Uuid.random().toString(),
            ) ?: Uuid.random().toString()
        }
        navController.navigate(Screen.Chat(id = id))
    }

    fun openAgent(targetId: Uuid = Uuid.random()) {
        navController.navigate(Screen.Agent(id = targetId.toString()))
    }

    fun openWorkflow() {
        navController.navigate(Screen.Workflow)
    }

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Expressive Header
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + slideInVertically { -it / 4 },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "RikkaHub · Antigravity",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Surface(
                            onClick = ::openWorkflow,
                            shape = CircleShape,
                            color = if (session != null) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
                            } else {
                                CustomColors.listItemColors.containerColor
                            },
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (session != null) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            }
                                        )
                                )
                                Text(
                                    text = if (session != null) "云端已连接" else "未连接",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (session != null) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Hero Card: Antigravity Agent
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + slideInVertically { it / 6 },
                ) {
                    val heroShape = RoundedCornerShape(28.dp)
                    val gradient = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                        )
                    )
                    Surface(
                        onClick = { openAgent() },
                        shape = heroShape,
                        color = Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(heroShape),
                    ) {
                        Box(
                            modifier = Modifier
                                .background(gradient)
                                .padding(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = HugeIcons.Sparkles,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                                    ) {
                                        Text(
                                            text = "Action 实时驱动",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Antigravity Agent",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = "发送即触发远端 Action，实时同步推理步骤、工具调用与文件转换产物。",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Button(
                                        onClick = { openAgent() },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                    ) {
                                        Text(
                                            text = "开始新任务",
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            imageVector = HugeIcons.ArrowRight01,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }

                                    val latest = recentAgentConversations.firstOrNull()
                                    if (latest != null) {
                                        Surface(
                                            onClick = { openAgent(latest.id) },
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                        ) {
                                            Text(
                                                text = "继续上次",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2-Column Bento Grid: Chat & Workflow
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + slideInVertically { it / 4 },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BentoWorkspaceCard(
                            title = "Chat 对话",
                            subtitle = "多模型 · 预设 · MCP",
                            icon = HugeIcons.MessageAdd01,
                            iconBg = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = ::openChat,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        BentoWorkspaceCard(
                            title = "Workflow",
                            subtitle = "云端编排 · PDF 转换",
                            icon = HugeIcons.GitFork,
                            iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = ::openWorkflow,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }

            // Recent Agent Conversations in M3 Expressive CardGroup style
            if (recentAgentConversations.isNotEmpty()) {
                item {
                    val items = remember(recentAgentConversations) {
                        recentAgentConversations.take(3)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "最近 Agent 会话",
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 6.dp),
                        )
                        items.forEachIndexed { index, conv ->
                            val timeText = remember(conv.updateAt) {
                                conv.updateAt.toLocalDateTime()
                            }
                            Surface(
                                shape = cardGroupItemShape(index, items.size),
                                color = CustomColors.listItemColors.containerColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardGroupItemShape(index, items.size))
                                    .clickable { openAgent(conv.id) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    CardGroupIcon(
                                        icon = HugeIcons.Sparkles,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = conv.title.ifBlank { "Antigravity Agent" },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = timeText,
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
                    }
                }
            }

            // Quick Settings Strip
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "偏好设置",
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickActionChip(
                            label = "Chat 设置",
                            icon = HugeIcons.Settings03,
                            onClick = { navController.navigate(Screen.Setting) },
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionChip(
                            label = "Agent 界面",
                            icon = HugeIcons.Settings03,
                            onClick = { navController.navigate(Screen.AgentSetting) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoWorkspaceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = CustomColors.listItemColors.containerColor,
        modifier = modifier.clip(shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardGroupIcon(
                    icon = icon,
                    containerColor = iconBg,
                    contentColor = iconTint,
                )
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
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
private fun QuickActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = CustomColors.listItemColors.containerColor,
        modifier = modifier.clip(shape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
