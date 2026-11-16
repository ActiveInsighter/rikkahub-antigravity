package me.rerere.rikkahub.data.workflow

import java.util.UUID

data class StartLink(
    val id: String,
    val label: String,
    val url: String,
) {
    companion object {
        fun createId(): String = "l" + UUID.randomUUID().toString().replace("-", "").take(15)
    }
}

val DEFAULT_WORKFLOW_START_LINKS: List<StartLink> = listOf(
    StartLink(id = "chatgpt", label = "ChatGPT", url = "https://chatgpt.com/"),
    StartLink(id = "chatgpt-images", label = "ChatGPT 图片", url = "https://chatgpt.com/images"),
    StartLink(id = "chatgpt-codex", label = "Codex", url = "https://chatgpt.com/codex"),
    StartLink(id = "claude", label = "Claude", url = "https://claude.ai/new"),
    StartLink(id = "claude-code", label = "Claude Code", url = "https://claude.ai/code"),
)

data class UserPreferencesSnapshot(
    val recordId: String? = null,
    val startLinks: List<StartLink> = DEFAULT_WORKFLOW_START_LINKS,
    val isCustom: Boolean = false,
)

data class SavedAiModel(
    val id: String,
    val owner: String,
    val modelId: String,
    val name: String,
)

data class CatalogModelEntry(
    val id: String,
    val name: String,
    val provider: String,
    val description: String = "",
)

data class ModelCatalogSnapshot(
    val models: List<CatalogModelEntry> = emptyList(),
    val updatedAt: String = "",
)

data class BrowserRuntimeConfig(
    val replyTimeoutMinutes: Int = 30,
    val randomWaitEnabled: Boolean = false,
    val randomWaitMinSeconds: Int = 0,
    val randomWaitMaxSeconds: Int = 0,
    val deferredReplyEnabled: Boolean = false,
    val deferredReplyDelayMinutes: Int = 10,
)

data class RuntimeDefaultsSnapshot(
    val owner: String = "",
    val schemaVersion: Int = 1,
    val revision: Int = 0,
    val browser: BrowserRuntimeConfig = BrowserRuntimeConfig(),
    val defaultStartUrl: String = "https://chatgpt.com/",
)
