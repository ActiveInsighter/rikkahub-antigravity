package me.rerere.rikkahub.ui.pages.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.readBooleanPreference
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import java.util.Calendar
import kotlin.uuid.Uuid

@Composable
fun HomePage() {
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

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
        val id = if (readBooleanPreference("create_new_conversation_on_start", true)) {
            Uuid.random().toString()
        } else {
            readStringPreference(
                "lastConversationId",
                Uuid.random().toString(),
            ) ?: Uuid.random().toString()
        }
        navController.navigate(Screen.Chat(id = id))
    }

    fun openAgent() {
        navController.navigate(Screen.Agent(id = Uuid.random().toString()))
    }

    fun openWorkflow() {
        navController.navigate(Screen.Workflow)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = "$greeting，RikkaHub",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                subtitle = {
                    Text(
                        text = "选择一个工作区：官方对话、文件转换 Agent 或远程工作流",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + slideInVertically { it / 6 },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        HomeWorkspaceCard(
                            title = "Chat 对话",
                            badge = "官方原版 · 多模型",
                            description = "完整的 RikkaHub 对话体验，支持多服务商、助手预设、MCP 工具、记忆与扩展。",
                            icon = HugeIcons.MessageAdd01,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            iconContainerColor = MaterialTheme.colorScheme.primary,
                            iconTint = MaterialTheme.colorScheme.onPrimary,
                            onClick = ::openChat,
                        )

                        HomeWorkspaceCard(
                            title = "Antigravity Agent",
                            badge = "独立数据库 · 独立 UI 设置",
                            description = "面向 AI 文件转换与反重力 API 的专属智能体前端，与 Chat 完全隔离。",
                            icon = HugeIcons.Sparkles,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                            iconContainerColor = MaterialTheme.colorScheme.tertiary,
                            iconTint = MaterialTheme.colorScheme.onTertiary,
                            onClick = ::openAgent,
                        )

                        HomeWorkspaceCard(
                            title = "Workflow 工作流",
                            badge = "AnyWorkflow Remote",
                            description = "编排远程任务、重复计划、模板收藏、资料库与 PDF → Markdown 文件转换。",
                            icon = HugeIcons.GitFork,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            iconContainerColor = MaterialTheme.colorScheme.secondary,
                            iconTint = MaterialTheme.colorScheme.onSecondary,
                            onClick = ::openWorkflow,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "快捷入口",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { navController.navigate(Screen.Setting) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Settings03,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Chat 设置", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            FilledTonalButton(
                                onClick = { navController.navigate(Screen.AgentSetting) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Settings03,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Agent 界面", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWorkspaceCard(
    title: String,
    badge: String,
    description: String,
    icon: ImageVector,
    containerColor: Color,
    iconContainerColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
