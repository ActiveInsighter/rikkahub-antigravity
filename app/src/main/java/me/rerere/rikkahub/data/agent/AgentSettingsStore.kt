package me.rerere.rikkahub.data.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant

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
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val enableLatexRendering: Boolean = true,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val conversationSortOrder: ConversationSortOrder = ConversationSortOrder.UPDATE_TIME,
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
        showMessageJumper = showMessageJumper,
        messageJumperOnLeft = messageJumperOnLeft,
        fontSizeRatio = fontSizeRatio,
        codeBlockAutoWrap = codeBlockAutoWrap,
        codeBlockAutoCollapse = codeBlockAutoCollapse,
        showLineNumbers = showLineNumbers,
        enableLatexRendering = enableLatexRendering,
        sendOnEnter = sendOnEnter,
        enableAutoScroll = enableAutoScroll,
        enableMessageGenerationHapticEffect = enableMessageGenerationHapticEffect,
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
