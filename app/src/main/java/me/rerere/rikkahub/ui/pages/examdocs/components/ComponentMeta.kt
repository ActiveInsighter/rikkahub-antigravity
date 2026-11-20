package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val metaJson = Json { ignoreUnknownKeys = true }

internal data class ComponentMeta(
    val rawTitle: String?,
    val json: JsonObject?,
) {
    fun string(key: String): String? =
        json?.get(key)?.jsonPrimitive?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    fun boolean(key: String): Boolean? =
        json?.get(key)?.jsonPrimitive?.booleanOrNull

    fun stringList(key: String): List<String> =
        (json?.get(key) as? JsonArray)?.mapNotNull {
            it.jsonPrimitive.takeIf { p -> p.isString }?.content?.takeIf(String::isNotBlank)
        }.orEmpty()

    val displayTitle: String?
        get() = string("title") ?: rawTitle?.takeUnless { it.trimStart().startsWith('{') }
}

internal fun parseComponentMeta(raw: String?): ComponentMeta {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return ComponentMeta(null, null)
    if (!trimmed.startsWith('{')) return ComponentMeta(trimmed, null)
    val parsed = runCatching { metaJson.parseToJsonElement(trimmed).jsonObject }.getOrNull()
    return ComponentMeta(trimmed, parsed)
}

internal data class TonePalette(
    val container: Color,
    val accent: Color,
    val onContainer: Color,
)

@Composable
internal fun resolveTonePalette(tone: String?): TonePalette {
    val colors = MaterialTheme.colorScheme
    return when (tone?.lowercase()) {
        "tip", "idea", "success" -> TonePalette(
            container = colors.secondaryContainer.copy(alpha = 0.55f),
            accent = colors.secondary,
            onContainer = colors.onSecondaryContainer,
        )
        "warning", "warn" -> TonePalette(
            container = colors.tertiaryContainer.copy(alpha = 0.55f),
            accent = colors.tertiary,
            onContainer = colors.onTertiaryContainer,
        )
        "danger", "error" -> TonePalette(
            container = colors.errorContainer.copy(alpha = 0.55f),
            accent = colors.error,
            onContainer = colors.onErrorContainer,
        )
        "info", "primary" -> TonePalette(
            container = colors.primaryContainer.copy(alpha = 0.45f),
            accent = colors.primary,
            onContainer = colors.onPrimaryContainer,
        )
        else -> TonePalette(
            container = colors.surfaceContainerLow,
            accent = colors.primary,
            onContainer = colors.onSurface,
        )
    }
}
