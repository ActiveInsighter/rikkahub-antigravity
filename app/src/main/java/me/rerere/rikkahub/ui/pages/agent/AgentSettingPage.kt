package me.rerere.rikkahub.ui.pages.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.agent.AgentUiSettings
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject

/** 字体选项的展示文案；Agent 页不开放自定义字体文件，所以不列 CUSTOM */
private val AGENT_FONT_FAMILIES = listOf(
    ChatFontFamily.DEFAULT,
    ChatFontFamily.SERIF,
    ChatFontFamily.MONOSPACE,
)

private fun ChatFontFamily.label(): String = when (this) {
    ChatFontFamily.DEFAULT -> "默认"
    ChatFontFamily.SERIF -> "衬线"
    ChatFontFamily.MONOSPACE -> "等宽"
    ChatFontFamily.CUSTOM -> "自定义"
}

@Composable
fun AgentSettingPage() {
    val settingsStore: AgentSettingsStore = koinInject()
    val uiSettings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showResetConfirm by remember { mutableStateOf(false) }

    fun update(transform: (AgentUiSettings) -> AgentUiSettings) {
        settingsStore.update(transform)
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Agent 界面设置") },
                subtitle = { Text("仅影响 Agent 页面，与 Chat 设置完全隔离") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "消息外观",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                CardGroup {
                    switchItem(
                        checked = uiSettings.showUserAvatar,
                        onCheckedChange = { checked -> update { it.copy(showUserAvatar = checked) } },
                        headlineContent = { Text("显示用户头像") },
                    )
                    switchItem(
                        checked = uiSettings.showAssistantBubble,
                        onCheckedChange = { checked -> update { it.copy(showAssistantBubble = checked) } },
                        headlineContent = { Text("显示助手消息气泡") },
                    )
                    switchItem(
                        checked = uiSettings.splitAssistantBubbleByParagraph,
                        onCheckedChange = { checked -> update { it.copy(splitAssistantBubbleByParagraph = checked) } },
                        headlineContent = { Text("按段落拆分助手气泡") },
                    )
                    formItem(
                        headlineContent = { Text("气泡不透明度") },
                        trailingContent = {
                            CardGroupValue("${(uiSettings.bubbleOpacity * 100).roundToInt()}%")
                        },
                    ) {
                        Slider(
                            value = uiSettings.bubbleOpacity,
                            onValueChange = { value -> update { it.copy(bubbleOpacity = value) } },
                            valueRange = 0.3f..1.0f,
                        )
                    }
                    switchItem(
                        checked = uiSettings.showDateTimeInMessage,
                        onCheckedChange = { checked -> update { it.copy(showDateTimeInMessage = checked) } },
                        headlineContent = { Text("显示消息时间") },
                    )
                    switchItem(
                        checked = uiSettings.showTokenUsage,
                        onCheckedChange = { checked -> update { it.copy(showTokenUsage = checked) } },
                        headlineContent = { Text("显示 Token 用量") },
                    )
                }
            }

            item {
                Text(
                    text = "思考与渲染",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                CardGroup {
                    switchItem(
                        checked = uiSettings.showThinkingContent,
                        onCheckedChange = { checked -> update { it.copy(showThinkingContent = checked) } },
                        headlineContent = { Text("显示思考过程") },
                    )
                    switchItem(
                        checked = uiSettings.autoCloseThinking,
                        onCheckedChange = { checked -> update { it.copy(autoCloseThinking = checked) } },
                        headlineContent = { Text("生成结束后自动折叠思考") },
                    )
                    switchItem(
                        checked = uiSettings.enableLatexRendering,
                        onCheckedChange = { checked -> update { it.copy(enableLatexRendering = checked) } },
                        headlineContent = { Text("启用 LaTeX 公式渲染") },
                    )
                    switchItem(
                        checked = uiSettings.codeBlockAutoWrap,
                        onCheckedChange = { checked -> update { it.copy(codeBlockAutoWrap = checked) } },
                        headlineContent = { Text("代码块自动换行") },
                    )
                    switchItem(
                        checked = uiSettings.codeBlockAutoCollapse,
                        onCheckedChange = { checked -> update { it.copy(codeBlockAutoCollapse = checked) } },
                        headlineContent = { Text("长代码块自动折叠") },
                    )
                    switchItem(
                        checked = uiSettings.showLineNumbers,
                        onCheckedChange = { checked -> update { it.copy(showLineNumbers = checked) } },
                        headlineContent = { Text("代码块显示行号") },
                    )
                }
            }

            item {
                Text(
                    text = "字体与交互",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                CardGroup {
                    formItem(
                        headlineContent = { Text("消息字体") },
                    ) {
                        ConnectedChoiceButtons(
                            options = AGENT_FONT_FAMILIES,
                            selected = uiSettings.chatFontFamily,
                            onSelect = { family -> update { it.copy(chatFontFamily = family) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { family, selected ->
                            ConnectedChoiceLabel(text = family.label(), selected = selected)
                        }
                    }
                    formItem(
                        headlineContent = { Text("字体缩放") },
                        trailingContent = {
                            CardGroupValue("${(uiSettings.fontSizeRatio * 100).roundToInt()}%")
                        },
                    ) {
                        Slider(
                            value = uiSettings.fontSizeRatio,
                            onValueChange = { value -> update { it.copy(fontSizeRatio = value) } },
                            valueRange = 0.8f..1.4f,
                        )
                    }
                    switchItem(
                        checked = uiSettings.showMessageJumper,
                        onCheckedChange = { checked -> update { it.copy(showMessageJumper = checked) } },
                        headlineContent = { Text("显示消息快速跳转") },
                    )
                    switchItem(
                        checked = uiSettings.messageJumperOnLeft,
                        onCheckedChange = { checked -> update { it.copy(messageJumperOnLeft = checked) } },
                        headlineContent = { Text("消息跳转按钮靠左") },
                    )
                    switchItem(
                        checked = uiSettings.sendOnEnter,
                        onCheckedChange = { checked -> update { it.copy(sendOnEnter = checked) } },
                        headlineContent = { Text("回车发送消息") },
                    )
                    switchItem(
                        checked = uiSettings.enableAutoScroll,
                        onCheckedChange = { checked -> update { it.copy(enableAutoScroll = checked) } },
                        headlineContent = { Text("生成时自动滚动") },
                    )
                    switchItem(
                        checked = uiSettings.enableMessageGenerationHapticEffect,
                        onCheckedChange = { checked -> update { it.copy(enableMessageGenerationHapticEffect = checked) } },
                        headlineContent = { Text("生成触感反馈") },
                    )
                }
            }

            item {
                CardGroup {
                    item(
                        onClick = { showResetConfirm = true },
                        headlineContent = {
                            Text(
                                text = "恢复默认 Agent 界面设置",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        supportingContent = {
                            Text("仅重置 Agent 界面选项，不影响 Chat 设置或会话数据")
                        },
                    )
                }
            }
        }
    }

    RikkaConfirmDialog(
        show = showResetConfirm,
        title = "恢复默认设置",
        confirmText = "恢复默认",
        dismissText = "取消",
        text = { Text("确定要将 Agent 界面设置恢复为默认值吗？") },
        onConfirm = {
            settingsStore.reset()
            showResetConfirm = false
            toaster.show("已恢复默认 Agent 界面设置", type = ToastType.Success)
        },
        onDismiss = { showResetConfirm = false },
    )
}
