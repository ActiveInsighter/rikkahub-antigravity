package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeBlock
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.ui.components.FormItem

/**
 * 工具调用的渲染上下文, 预解析好工具入参与输出, 避免各渲染器重复解析
 */
data class ToolUIContext(
    val tool: UIMessagePart.Tool,
    /** 工具入参 ([UIMessagePart.Tool.input] 的 JSON 解析结果) */
    val arguments: JsonElement,
    /** 输出文本部件解析出的 JSON, 工具未执行时为 null */
    val content: JsonElement?,
    /** 该工具调用是否在生成中 */
    val loading: Boolean,
    /** 发起该工具调用的助手, 拿不到时为 null */
    val assistant: Assistant? = null,
)

/**
 * 单个工具的 UI 渲染器
 *
 * 在 [ToolUIRegistry] 注册后, 聊天消息中对应的工具调用将使用该渲染器展示;
 * 未注册的工具 fallback 到接口的默认实现 (通用标题/图标 + 结构化详情)
 */
interface ToolUIRenderer {
    /** 渲染器对应的工具名 */
    val toolName: String

    /** 折叠步骤的图标 */
    fun icon(context: ToolUIContext): ImageVector = HugeIcons.Tools

    /** 折叠步骤的标题 */
    @Composable
    fun title(context: ToolUIContext): String =
        stringResource(R.string.chat_message_tool_call_generic, context.tool.toolName)

    /** 步骤展开时是否显示内联摘要 */
    fun hasSummary(context: ToolUIContext): Boolean = false

    /** 步骤展开时的内联摘要 */
    @Composable
    fun Summary(context: ToolUIContext) {
    }

    /** 点击步骤后的详情, 渲染在 BottomSheet 内 */
    @Composable
    fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        DefaultToolPreview(context = context)
    }
}

/** 未注册工具使用的默认渲染器, 全部行为来自 [ToolUIRenderer] 的默认实现 */
private object DefaultToolUIRenderer : ToolUIRenderer {
    override val toolName: String get() = ""
}

/**
 * 工具 UI 渲染器注册表, 为新工具定制渲染时在 [renderers] 中注册即可
 */
object ToolUIRegistry {
    private val renderers: Map<String, ToolUIRenderer> = listOf(
        SearchWebToolUI,
        ScrapeWebToolUI,
        GetTimeInfoToolUI,
        ClipboardToolUI,
        TextToSpeechToolUI,
        GetScreenTimeToolUI,
        CalendarQueryToolUI,
        CalendarCreateToolUI,
        ChartDisplayToolUI,
        UseSkillToolUI,
        RecentChatsToolUI,
        ConversationSearchToolUI,
        EditFileToolUI,
        ReadFileToolUI,
        WriteFileToolUI,
        ShellToolUI,
        PhoneUseToolUI,
    ).plus(MemoryFileToolUI.all).associateBy { it.toolName }

    /** 查找工具对应的渲染器, 未注册时返回默认渲染器 */
    fun resolve(toolName: String): ToolUIRenderer = renderers[toolName] ?: DefaultToolUIRenderer
}

internal fun JsonElement?.getStringContent(key: String): String? =
    this?.jsonObjectOrNull?.get(key)?.jsonPrimitiveOrNull?.contentOrNull

/**
 * 默认工具详情: 通过格式转换层将 Antigravity / Codex / 通用工具的 JSON 入参与出参
 * 渲染为代码块、Diff、元信息与执行状态，而非直接展示原始 JSON。
 *
 * @param headerActions 标题栏右侧的附加操作区
 */
@Composable
fun DefaultToolPreview(
    context: ToolUIContext,
    headerActions: (@Composable () -> Unit)? = null,
) {
    val formatted = remember(context) { formatToolUIContext(context) }
    val images = remember(context.tool.output) {
        context.tool.output.filterIsInstance<UIMessagePart.Image>()
    }
    FormattedToolPreview(
        toolName = context.tool.toolName,
        formatted = formatted,
        truncated = (context.tool.metadata?.get("truncated") as? JsonPrimitive)?.booleanOrNull == true,
        images = images,
        headerActions = headerActions,
    )
}

@Composable
fun ServerToolPreview(
    tool: UIMessagePart.ServerTool,
    formatted: FormattedServerTool = remember(tool) { formatServerTool(tool) },
    headerActions: (@Composable () -> Unit)? = null,
) {
    FormattedToolPreview(
        toolName = tool.toolName,
        formatted = formatted,
        truncated = (tool.metadata?.get("truncated") as? JsonPrimitive)?.booleanOrNull == true,
        images = emptyList(),
        headerActions = headerActions,
    )
}

@Composable
private fun FormattedToolPreview(
    toolName: String,
    formatted: FormattedServerTool,
    truncated: Boolean,
    images: List<UIMessagePart.Image>,
    headerActions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight(0.8f)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = formatted.title.ifBlank { stringResource(R.string.chat_message_tool_call_title) },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Start,
                )
                val metaParts = listOfNotNull(
                    toolName.takeIf { it.isNotBlank() },
                    formatted.statusText,
                )
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (formatted.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (truncated) {
                Text("部分内容已截断", style = MaterialTheme.typography.labelMedium)
            }
            headerActions?.invoke()
        }

        if (formatted.inputSections.isNotEmpty()) {
            FormItem(
                label = {
                    Text(stringResource(R.string.chat_message_tool_call_label, toolName))
                }
            ) {
                ToolDetailSections(formatted.inputSections)
            }
        }

        if (formatted.outputSections.isNotEmpty() || images.isNotEmpty()) {
            FormItem(
                label = {
                    Text(stringResource(R.string.chat_message_tool_call_result))
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolDetailSections(formatted.outputSections)
                    images.fastForEach { part ->
                        ZoomableAsyncImage(
                            model = part.url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolDetailSections(sections: List<ToolDetailSection>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sections.forEach { section ->
            when (section) {
                is ToolDetailSection.Metadata -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        section.items.forEach { (key, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                is ToolDetailSection.Code -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        section.label?.takeIf { it.isNotBlank() }?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HighlightCodeBlock(
                            code = section.code,
                            language = section.language,
                        )
                    }
                }

                is ToolDetailSection.Diff -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        section.path?.takeIf { it.isNotBlank() }?.let { path ->
                            Text(
                                text = path,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HighlightCodeBlock(
                            code = section.diff,
                            language = "diff",
                        )
                    }
                }

                is ToolDetailSection.Text -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        section.label?.takeIf { it.isNotBlank() }?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (section.isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (section.isError) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = section.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (section.isError) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }

                is ToolDetailSection.Chips -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        section.label?.takeIf { it.isNotBlank() }?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            section.items.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
