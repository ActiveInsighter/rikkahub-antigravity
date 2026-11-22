package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.ui.toast.ToastType

internal data class ParsedDecisionResult(
    val model: String,
    val jsonElement: JsonElement,
    val rawJson: String,
)

internal fun parseDecisionResultMarkdown(text: String): ParsedDecisionResult? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("### 结构化决策结果")) return null
    val firstLineEnd = trimmed.indexOf('\n').let { if (it < 0) trimmed.length else it }
    val headerLine = trimmed.substring(0, firstLineEnd)
    val modelName = Regex("""`([^`]+)`""").find(headerLine)?.groupValues?.getOrNull(1).orEmpty()

    val jsonFenceStart = trimmed.indexOf("```json")
    if (jsonFenceStart < 0) return null
    val contentStart = trimmed.indexOf('\n', jsonFenceStart).takeIf { it >= 0 }?.plus(1) ?: return null
    val jsonFenceEnd = trimmed.indexOf("```", contentStart)
    if (jsonFenceEnd < 0) return null
    val rawJson = trimmed.substring(contentStart, jsonFenceEnd).trim()
    val parsed = runCatching { JsonInstant.parseToJsonElement(rawJson) }.getOrNull() ?: return null
    return ParsedDecisionResult(
        model = modelName,
        jsonElement = parsed,
        rawJson = rawJson,
    )
}

@Composable
internal fun DecisionResultCard(
    parsed: ParsedDecisionResult,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val toaster = LocalToaster.current
    var showRawJson by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tag(type = TagType.INFO) {
                    Text("Decision")
                }
                if (parsed.model.isNotBlank()) {
                    Text(
                        text = parsed.model,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(parsed.rawJson))
                        toaster.show(message = "已复制决策 JSON", type = ToastType.Success)
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Copy01,
                        contentDescription = "复制 JSON",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            when (val elem = parsed.jsonElement) {
                is JsonObject -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        elem.entries.forEachIndexed { index, (key, value) ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                )
                            }
                            DecisionFieldRow(key = key, value = value)
                        }
                    }
                }
                else -> {
                    Text(
                        text = parsed.rawJson,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = { showRawJson = !showRawJson },
                ) {
                    Icon(
                        imageVector = if (showRawJson) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(if (showRawJson) "收起原始 JSON" else "查看原始 JSON")
                }
            }

            AnimatedVisibility(visible = showRawJson) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = parsed.rawJson,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DecisionFieldRow(
    key: String,
    value: JsonElement,
) {
    val boolVal = (value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
    val displayText = when (value) {
        is JsonPrimitive -> value.contentOrNull ?: value.toString()
        is JsonArray -> value.joinToString(", ") {
            if (it is JsonPrimitive) it.contentOrNull ?: it.toString() else it.toString()
        }
        is JsonObject -> value.toString()
    }
    val isLongText = displayText.length > 36 || displayText.contains('\n')

    if (isLongText) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = key,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = key,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.38f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                boolVal != null -> {
                    Tag(type = if (boolVal) TagType.SUCCESS else TagType.ERROR) {
                        Text(if (boolVal) "true" else "false")
                    }
                }
                else -> {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.62f),
                    )
                }
            }
        }
    }
}
