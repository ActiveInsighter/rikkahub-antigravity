package me.rerere.rikkahub.data.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant

val DEFAULT_AGENT_MODELS: List<String> = listOf(
    "Antigravity",
    "Codex",
    "gemini-3.5-flash-lite",
    "gemini-3-flash-preview",
    "gemini-3-pro-preview",
    "gemini-2.5-pro",
    "gemini-2.5-flash",
)

val DEFAULT_ANTIGRAVITY_SUB_MODELS: List<String> = listOf(
    "gemini-4-hydrogen",
)

val DEFAULT_CODEX_SUB_MODELS: List<String> = listOf(
    "gpt-6.1-terra",
    "gpt-6.1-sol",
    "gpt-6-astra",
    "gpt-6-sol",
    "gpt-6-luna",
)

@Serializable
data class AgentSchedule(
    val id: String,
    val title: String,
    val prompt: String,
    val systemPrompt: String = "",
    val model: String = "Antigravity",
    val thinkingLevel: String = "",
    val cron: String = "0 9 * * *",
    val timezone: String = "Asia/Shanghai",
    val enabled: Boolean = true,
    val lastRunAt: String = "",
    val nextRunAt: String = "",
    val lastJobId: String = "",
    val lastStatus: String = "",
    val lastError: String = "",
)

/**
 * Isolated UI-only settings for the Agent page.
 * Completely separated from Chat's [me.rerere.rikkahub.data.datastore.SettingsStore].
 */
@Serializable
data class AgentUiSettings(
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val splitAssistantBubbleByParagraph: Boolean = false,
    val bubbleOpacity: Float = 1.0f,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateTimeInMessage: Boolean = false,
    val showTokenUsage: Boolean = true,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    val collapsedVisibleSteps: Int = 2,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val enableLatexRendering: Boolean = true,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 0.5f,
    val enableBlurEffect: Boolean = true,
    val backgroundEffectType: BackgroundEffectType = BackgroundEffectType.BLUR,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val pasteLongTextAsFile: Boolean = true,
    val pasteLongTextThreshold: Int = 2000,
    val skipCropImage: Boolean = false,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val conversationSortOrder: ConversationSortOrder = ConversationSortOrder.UPDATE_TIME,
    val selectedAgentModel: String = "Antigravity",
    val selectedAgentModelId: String = "",
    val antigravitySubModel: String = "",
    val codexSubModel: String = "",
    val gatewayBaseUrl: String = DEFAULT_GEMINI_GATEWAY_URL,
    val gatewayApiKey: String = "",
    val customGeminiModels: List<String> = emptyList(),
    val localAgentSchedules: List<AgentSchedule> = emptyList(),
) {
    fun toDisplaySetting(): DisplaySetting = DisplaySetting(
        showUserAvatar = showUserAvatar,
        showAssistantBubble = showAssistantBubble,
        splitAssistantBubbleByParagraph = splitAssistantBubbleByParagraph,
        bubbleOpacity = bubbleOpacity,
        showModelIcon = showModelIcon,
        showModelName = showModelName,
        showDateTimeInMessage = showDateTimeInMessage,
        showTokenUsage = showTokenUsage,
        showThinkingContent = showThinkingContent,
        autoCloseThinking = autoCloseThinking,
        collapsedVisibleSteps = collapsedVisibleSteps,
        showMessageJumper = showMessageJumper,
        messageJumperOnLeft = messageJumperOnLeft,
        fontSizeRatio = fontSizeRatio,
        codeBlockAutoWrap = codeBlockAutoWrap,
        codeBlockAutoCollapse = codeBlockAutoCollapse,
        showLineNumbers = showLineNumbers,
        enableLatexRendering = enableLatexRendering,
        sendOnEnter = sendOnEnter,
        enableAutoScroll = enableAutoScroll,
        enableVolumeKeyScroll = enableVolumeKeyScroll,
        volumeKeyScrollRatio = volumeKeyScrollRatio,
        enableBlurEffect = enableBlurEffect,
        backgroundEffectType = backgroundEffectType,
        enableMessageGenerationHapticEffect = enableMessageGenerationHapticEffect,
        pasteLongTextAsFile = pasteLongTextAsFile,
        pasteLongTextThreshold = pasteLongTextThreshold,
        skipCropImage = skipCropImage,
        chatFontFamily = chatFontFamily,
        conversationSortOrder = conversationSortOrder,
    )

    fun toCompositionSettings(base: Settings = Settings.dummy()): Settings =
        base.copy(displaySetting = toDisplaySetting())
}

class AgentSettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(load())
    val settingsFlow: StateFlow<AgentUiSettings> = _settingsFlow.asStateFlow()

    val current: AgentUiSettings
        get() = _settingsFlow.value

    fun update(transform: (AgentUiSettings) -> AgentUiSettings) {
        val next = transform(_settingsFlow.value)
        _settingsFlow.value = next
        save(next)
    }

    fun reset() {
        val defaults = AgentUiSettings()
        _settingsFlow.value = defaults
        save(defaults)
    }

    private fun load(): AgentUiSettings {
        val raw = prefs.getString(KEY_UI_SETTINGS, null) ?: return AgentUiSettings()
        return runCatching {
            JsonInstant.decodeFromString<AgentUiSettings>(raw)
        }.getOrDefault(AgentUiSettings())
    }

    private fun save(settings: AgentUiSettings) {
        val encoded = JsonInstant.encodeToString(settings)
        prefs.edit { putString(KEY_UI_SETTINGS, encoded) }
    }

    companion object {
        private const val PREFS_NAME = "agent_ui_settings"
        private const val KEY_UI_SETTINGS = "ui_settings_json"
    }
}
