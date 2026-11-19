package me.rerere.rikkahub.ui.pages.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.agent.AgentSettingsStore
import me.rerere.rikkahub.data.agent.AgentUiSettings
import me.rerere.rikkahub.data.agent.DEFAULT_GEMINI_GATEWAY_URL
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
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
                CardGroup(title = { Text("Gemini Gateway 默认配置") }) {
                    formItem(
                        headlineContent = { Text("默认请求地址") },
                    ) {
                        OutlinedTextField(
                            value = uiSettings.gatewayBaseUrl,
                            onValueChange = { value -> update { it.copy(gatewayBaseUrl = value) } },
                            placeholder = { Text(DEFAULT_GEMINI_GATEWAY_URL) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    formItem(
                        headlineContent = { Text("全局网关密钥 (GATEWAY_AUTH_TOKEN)") },
                    ) {
                        OutlinedTextField(
                            value = uiSettings.gatewayApiKey,
                            onValueChange = { value -> update { it.copy(gatewayApiKey = value) } },
                            placeholder = { Text("留空则不发送鉴权头或使用单模型密钥") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                CardGroup(title = { Text("导航与滚动") }) {
                    switchItem(
                        checked = uiSettings.showMessageJumper,
                        onCheckedChange = { checked -> update { it.copy(showMessageJumper = checked) } },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_title)) },
                    )
                    if (uiSettings.showMessageJumper) {
                        switchItem(
                            checked = uiSettings.messageJumperOnLeft,
                            onCheckedChange = { checked -> update { it.copy(messageJumperOnLeft = checked) } },
                            supportingContent = { Text(stringResource(R.string.setting_display_page_message_jumper_position_desc)) },
                            headlineContent = { Text(stringResource(R.string.setting_display_page_message_jumper_position_title)) },
                        )
                    }
                    switchItem(
                        checked = uiSettings.enableAutoScroll,
                        onCheckedChange = { checked -> update { it.copy(enableAutoScroll = checked) } },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_title)) },
                    )
                    switchItem(
                        checked = uiSettings.enableVolumeKeyScroll,
                        onCheckedChange = { checked -> update { it.copy(enableVolumeKeyScroll = checked) } },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_title)) },
                    )
                    if (uiSettings.enableVolumeKeyScroll) {
                        formItem(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_ratio)) },
                            trailingContent = { CardGroupValue("${(uiSettings.volumeKeyScrollRatio * 100).roundToInt()}%") },
                        ) {
                            Slider(
                                value = uiSettings.volumeKeyScrollRatio,
                                onValueChange = { value -> update { it.copy(volumeKeyScrollRatio = value) } },
                                valueRange = 0.25f..1.0f,
                                steps = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            item {
                CardGroup(title = { Text("背景与视觉效果") }) {
                    switchItem(
                        checked = uiSettings.enableBlurEffect,
                        onCheckedChange = { checked -> update { it.copy(enableBlurEffect = checked) } },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_background_effect_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_background_effect_title)) },
                    )
                    if (uiSettings.enableBlurEffect) {
                        formItem(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_background_effect_type)) },
                        ) {
                            ConnectedChoiceButtons(
                                options = BackgroundEffectType.entries,
                                selected = uiSettings.backgroundEffectType,
                                onSelect = { type -> update { it.copy(backgroundEffectType = type) } },
                            ) { type, selected ->
                                ConnectedChoiceLabel(
                                    text = when (type) {
                                        BackgroundEffectType.BLUR -> stringResource(R.string.setting_display_page_background_effect_blur)
                                        BackgroundEffectType.GLASS -> stringResource(R.string.setting_display_page_background_effect_glass)
                                    },
                                    selected = selected,
                                )
                            }
                        }
                    }
                    switchItem(
                        checked = uiSettings.enableMessageGenerationHapticEffect,
                        onCheckedChange = { checked ->
                            update { it.copy(enableMessageGenerationHapticEffect = checked) }
                        },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title)) },
                    )
                }
            }

            item {
                CardGroup(title = { Text("输入与附件") }) {
                    switchItem(
                        checked = uiSettings.sendOnEnter,
                        onCheckedChange = { checked -> update { it.copy(sendOnEnter = checked) } },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_title)) },
                    )
                    switchItem(
                        checked = uiSettings.skipCropImage,
                        onCheckedChange = { checked -> update { it.copy(skipCropImage = checked) } },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_title)) },
                    )
                    switchItem(
                        checked = uiSettings.pasteLongTextAsFile,
                        onCheckedChange = { checked -> update { it.copy(pasteLongTextAsFile = checked) } },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_title)) },
                    )
                    if (uiSettings.pasteLongTextAsFile) {
                        formItem(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_threshold_title)) },
                            trailingContent = { CardGroupValue("${uiSettings.pasteLongTextThreshold}") },
                        ) {
                            Slider(
                                value = uiSettings.pasteLongTextThreshold.toFloat(),
                                onValueChange = { value ->
                                    update { it.copy(pasteLongTextThreshold = value.toInt()) }
                                },
                                valueRange = 100f..10000f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            item {
                CardGroup(title = { Text("消息外观") }) {
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
                            modifier = Modifier.fillMaxWidth(),
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
                CardGroup(title = { Text("思考与渲染") }) {
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
                CardGroup(title = { Text("字体设置") }) {
                    formItem(
                        headlineContent = { Text("消息字体") },
                    ) {
                        val options = listOf(
                            ChatFontFamily.DEFAULT to "默认",
                            ChatFontFamily.SERIF to "衬线",
                            ChatFontFamily.MONOSPACE to "等宽",
                        )
                        ConnectedChoiceButtons(
                            options = options,
                            selected = options.first { it.first == uiSettings.chatFontFamily },
                            onSelect = { (family, _) -> update { it.copy(chatFontFamily = family) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { option, selected ->
                            ConnectedChoiceLabel(text = option.second, selected = selected)
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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
        confirmText = "确定",
        dismissText = "取消",
        onConfirm = {
            settingsStore.reset()
            showResetConfirm = false
            toaster.show("已恢复默认 Agent 界面设置", type = ToastType.Success)
        },
        onDismiss = { showResetConfirm = false },
    ) {
        Text("确定要将 Agent 界面设置恢复为默认值吗？")
    }
}
