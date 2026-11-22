package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeBlock
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.generateUnifiedDiff
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
 * 未注册的工具 fallback 到接口的默认实现 (通用标题/图标 + JSON 详情)
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

internal fun unwrapJsonElement(element: JsonElement?): JsonElement? {
    if (element == null || element is JsonNull) return null
    if (element is JsonPrimitive && element.isString) {
        val trimmed = element.content.trim()
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        ) {
            runCatching { JsonInstant.parseToJsonElement(trimmed) }.getOrNull()?.let { return it }
        }
    }
    return element
}

internal fun detectLanguageFromPath(path: String?): String {
    val ext = path?.substringAfterLast('.', "")?.lowercase() ?: return "text"
    return when (ext) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "py" -> "python"
        "js", "mjs", "cjs" -> "javascript"
        "ts", "mts", "cts" -> "typescript"
        "tsx" -> "tsx"
        "jsx" -> "jsx"
        "json" -> "json"
        "xml", "html", "htm", "svg" -> ext
        "css", "scss", "sass" -> ext
        "yaml", "yml" -> "yaml"
        "md", "markdown" -> "markdown"
        "sh", "bash", "zsh" -> "bash"
        "sql" -> "sql"
        "rs" -> "rust"
        "go" -> "go"
        "c", "h" -> "c"
        "cpp", "cc", "cxx", "hpp" -> "cpp"
        "cs" -> "csharp"
        "rb" -> "ruby"
        "php" -> "php"
        "swift" -> "swift"
        "dart" -> "dart"
        "toml" -> "toml"
        "ini", "conf", "properties" -> "ini"
        "gradle" -> "groovy"
        "diff", "patch" -> "diff"
        else -> "text"
    }
}

private fun detectLanguageFromKey(key: String, pathHint: String?): String {
    val lower = key.lowercase()
    return when {
        "diff" in lower || "patch" in lower -> "diff"
        "command" in lower || lower == "cmd" || "bash" in lower || "shell" in lower -> "bash"
        "sql" in lower -> "sql"
        "html" in lower -> "html"
        "markdown" in lower || lower == "md" -> "markdown"
        "code" in lower || "content" in lower || "snippet" in lower -> detectLanguageFromPath(pathHint)
        else -> "text"
    }
}

private fun JsonObject.firstString(vararg keys: String): String? {
    for (key in keys) {
        val value = this[key]?.jsonPrimitiveOrNull?.contentOrNull
        if (!value.isNullOrBlank()) return value
    }
    return null
}

/**
 * 默认工具详情: 解析 JSON 并以代码块、Diff、状态标签等可读形式展示入参与输出
 *
 * @param headerActions 标题栏右侧的附加操作区
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DefaultToolPreview(
    context: ToolUIContext,
    headerActions: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
        .fillMaxHeight(0.8f)
        .padding(16.dp),
    showTitle: Boolean = true,
) {
    val parsedArgs = remember(context.arguments) { unwrapJsonElement(context.arguments) }
    val parsedContent = remember(context.content) { unwrapJsonElement(context.content) }
    val truncated = (context.tool.metadata?.get("truncated") as? JsonPrimitive)?.booleanOrNull == true
    val agentStatus = context.tool.metadata?.get("agentStatus")?.jsonPrimitiveOrNull?.contentOrNull

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showTitle || headerActions != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showTitle) {
                    Text(
                        text = stringResource(R.string.chat_message_tool_call_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                }
                headerActions?.invoke()
            }
        }

        if (agentStatus != null || truncated) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (agentStatus != null) {
                    val (label, type) = when (agentStatus.lowercase()) {
                        "completed", "succeeded" -> "已完成" to TagType.SUCCESS
                        "failed", "error" -> "执行失败" to TagType.ERROR
                        "canceled", "cancelled" -> "已取消" to TagType.WARNING
                        "running", "in_progress" -> "执行中" to TagType.INFO
                        else -> agentStatus to TagType.DEFAULT
                    }
                    Tag(type = type) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (truncated) {
                    Tag(type = TagType.WARNING) {
                        Text("部分内容已截断", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        FormItem(
            label = {
                Text(stringResource(R.string.chat_message_tool_call_label, context.tool.toolName))
            }
        ) {
            StructuredToolArguments(
                toolName = context.tool.toolName,
                arguments = parsedArgs ?: context.arguments,
                providerType = context.tool.metadata?.get("providerType")?.jsonPrimitiveOrNull?.contentOrNull,
            )
        }

        if (context.tool.output.isNotEmpty() || parsedContent != null) {
            FormItem(
                label = {
                    Text(stringResource(R.string.chat_message_tool_call_result))
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgentToolResult(
                        context = context,
                        parsedContent = parsedContent,
                        inputPathHint = (parsedArgs as? JsonObject)?.firstString(
                            "TargetFile", "AbsolutePath", "file_path", "path"
                        ),
                    ) {
                        val pathHint = (parsedArgs as? JsonObject)?.firstString(
                            "TargetFile", "AbsolutePath", "file_path", "path"
                        )
                        context.tool.output.fastForEach { part ->
                            when (part) {
                                is UIMessagePart.Text -> {
                                    val parsedTextJson = remember(part.text) {
                                        runCatching { JsonInstant.parseToJsonElement(part.text.trim()) }.getOrNull()
                                    }
                                    if (parsedTextJson is JsonObject || parsedTextJson is JsonArray) {
                                        StructuredJsonContent(
                                            element = parsedTextJson,
                                            pathHint = pathHint,
                                        )
                                    } else {
                                        HighlightCodeBlock(
                                            code = part.text,
                                            language = detectLanguageFromPath(pathHint),
                                        )
                                    }
                                }

                                is UIMessagePart.Image -> ZoomableAsyncImage(
                                    model = part.url,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StructuredToolArguments(
    toolName: String,
    arguments: JsonElement,
    providerType: String?,
) {
    val obj = arguments as? JsonObject
    if (obj == null) {
        val unwrapped = unwrapJsonElement(arguments)
        if (unwrapped is JsonObject || unwrapped is JsonArray) {
            HighlightCodeBlock(
                code = JsonInstantPretty.encodeToString(unwrapped),
                language = "json",
            )
        } else {
            val text = (unwrapped as? JsonPrimitive)?.contentOrNull ?: arguments.toString()
            HighlightCodeBlock(code = text, language = "text")
        }
        return
    }

    if (obj.isEmpty()) {
        HighlightCodeBlock(code = "{}", language = "json")
        return
    }

    val pathHint = obj.firstString("TargetFile", "AbsolutePath", "file_path", "path", "DirectoryPath", "SearchDirectory", "SearchPath")
    val command = obj.firstString("command", "CommandLine", "cmd")
    val cwd = obj.firstString("cwd", "Cwd")

    if (providerType == "commandExecution" || command != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cwd?.let { KeyValueRow(key = "工作目录", value = it) }
            if (command != null) {
                HighlightCodeBlock(code = command, language = "bash")
            }
            val remaining = obj.filterKeys { it !in setOf("command", "CommandLine", "cmd", "cwd", "Cwd") }
            if (remaining.isNotEmpty()) {
                StructuredJsonObjectFields(obj = JsonObject(remaining), pathHint = pathHint)
            }
        }
        return
    }

    // Antigravity / workspace file edit or write
    val codeContent = obj.firstString("CodeContent", "content")
    val targetContent = obj["TargetContent"]?.jsonPrimitiveOrNull?.contentOrNull
        ?: obj["old_text"]?.jsonPrimitiveOrNull?.contentOrNull
    val replacementContent = obj["ReplacementContent"]?.jsonPrimitiveOrNull?.contentOrNull
        ?: obj["new_text"]?.jsonPrimitiveOrNull?.contentOrNull
    val replacementChunks = (obj["ReplacementChunks"] as? JsonArray)?.mapNotNull { it as? JsonObject }

    if (codeContent != null || (targetContent != null && replacementContent != null) || !replacementChunks.isNullOrEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pathHint?.let { KeyValueRow(key = "文件路径", value = it) }
            obj.firstString("Description", "Instruction")?.let { KeyValueRow(key = "说明", value = it) }
            if (codeContent != null) {
                HighlightCodeBlock(
                    code = codeContent,
                    language = detectLanguageFromPath(pathHint),
                )
            }
            if (targetContent != null && replacementContent != null) {
                val diff = remember(targetContent, replacementContent, pathHint) {
                    generateUnifiedDiff(targetContent, replacementContent, pathHint ?: toolName)
                }
                HighlightCodeBlock(code = diff, language = "diff")
            }
            replacementChunks?.forEachIndexed { idx, chunk ->
                val oldText = chunk["TargetContent"]?.jsonPrimitiveOrNull?.contentOrNull
                val newText = chunk["ReplacementContent"]?.jsonPrimitiveOrNull?.contentOrNull
                if (oldText != null && newText != null) {
                    val start = chunk["StartLine"]?.jsonPrimitiveOrNull?.contentOrNull
                    val end = chunk["EndLine"]?.jsonPrimitiveOrNull?.contentOrNull
                    val rangeLabel = if (start != null && end != null) "变更 #${idx + 1} (L$start-$end)" else "变更 #${idx + 1}"
                    Text(rangeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val diff = remember(oldText, newText, pathHint) {
                        generateUnifiedDiff(oldText, newText, pathHint ?: toolName)
                    }
                    HighlightCodeBlock(code = diff, language = "diff")
                }
            }
            val handledKeys = setOf(
                "TargetFile", "AbsolutePath", "file_path", "path",
                "Description", "Instruction",
                "CodeContent", "content",
                "TargetContent", "old_text",
                "ReplacementContent", "new_text",
                "ReplacementChunks",
            )
            val remaining = obj.filterKeys { it !in handledKeys }
            if (remaining.isNotEmpty()) {
                StructuredJsonObjectFields(obj = JsonObject(remaining), pathHint = pathHint)
            }
        }
        return
    }

    StructuredJsonObjectFields(obj = obj, pathHint = pathHint)
}

@Composable
private fun StructuredJsonContent(
    element: JsonElement,
    pathHint: String? = null,
) {
    when (val unwrapped = unwrapJsonElement(element) ?: element) {
        is JsonObject -> {
            if (unwrapped.isEmpty()) {
                HighlightCodeBlock(code = "{}", language = "json")
            } else {
                StructuredJsonObjectFields(obj = unwrapped, pathHint = pathHint)
            }
        }

        is JsonArray -> {
            HighlightCodeBlock(
                code = JsonInstantPretty.encodeToString(unwrapped),
                language = "json",
            )
        }

        is JsonPrimitive -> {
            HighlightCodeBlock(
                code = unwrapped.contentOrNull ?: unwrapped.toString(),
                language = detectLanguageFromPath(pathHint),
            )
        }

        else -> {
            HighlightCodeBlock(code = unwrapped.toString(), language = "text")
        }
    }
}

@Composable
private fun StructuredJsonObjectFields(
    obj: JsonObject,
    pathHint: String? = null,
) {
    val scalarEntries = mutableListOf<Pair<String, String>>()
    val richEntries = mutableListOf<Pair<String, JsonElement>>()

    obj.entries.forEach { (key, rawValue) ->
        val value = unwrapJsonElement(rawValue) ?: rawValue
        when {
            value is JsonNull -> Unit
            value is JsonPrimitive -> {
                val text = value.contentOrNull ?: value.toString()
                if (!value.isString || (!text.contains('\n') && text.length <= 120)) {
                    scalarEntries += key to text
                } else {
                    richEntries += key to value
                }
            }

            else -> richEntries += key to value
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (scalarEntries.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                scalarEntries.forEach { (key, value) ->
                    KeyValueRow(key = key, value = value)
                }
            }
        }
        richEntries.forEach { (key, value) ->
            Text(
                text = key,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (value) {
                is JsonPrimitive -> {
                    HighlightCodeBlock(
                        code = value.contentOrNull ?: value.toString(),
                        language = detectLanguageFromKey(key, pathHint),
                    )
                }

                else -> {
                    HighlightCodeBlock(
                        code = JsonInstantPretty.encodeToString(value),
                        language = "json",
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentToolResult(
    context: ToolUIContext,
    parsedContent: JsonElement? = unwrapJsonElement(context.content),
    inputPathHint: String? = null,
    fallback: @Composable () -> Unit,
) {
    val type = context.tool.metadata?.get("providerType")?.jsonPrimitiveOrNull?.contentOrNull
    if (parsedContent == null) {
        fallback()
        return
    }
    if (parsedContent is JsonArray) {
        HighlightCodeBlock(
            code = JsonInstantPretty.encodeToString(parsedContent),
            language = "json",
        )
        return
    }
    val output = parsedContent as? JsonObject
    if (output == null) {
        fallback()
        return
    }

    when (type) {
        "commandExecution" -> {
            val exitCode = output["exitCode"]?.jsonPrimitiveOrNull?.contentOrNull
            val duration = output["durationMs"]?.jsonPrimitiveOrNull?.contentOrNull
            val log = output["aggregatedOutput"]?.jsonPrimitiveOrNull?.contentOrNull
            if (exitCode == null && duration == null && log == null) {
                fallback()
                return
            }
            val details = listOfNotNull(exitCode?.let { "退出码 $it" }, duration?.let { "${it} ms" })
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
            if (!log.isNullOrBlank()) HighlightCodeBlock(code = log, language = "text")
        }

        "fileChange" -> {
            val files = (output["changes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            if (files.isEmpty()) {
                fallback()
                return
            }
            files.forEach { file ->
                val path = file["path"]?.jsonPrimitiveOrNull?.contentOrNull
                val diff = file["diff"]?.jsonPrimitiveOrNull?.contentOrNull
                if (!path.isNullOrBlank()) Text(path, style = MaterialTheme.typography.labelMedium)
                if (!diff.isNullOrBlank()) HighlightCodeBlock(code = diff, language = "diff")
            }
        }

        else -> {
            // Antigravity / general structured output: command results, diffs, file contents, or JSON fields
            val exitCode = output.firstString("exitCode", "exit_code", "returncode")
            val duration = output.firstString("durationMs", "duration_ms")
            val status = output.firstString("status")
            val stdout = output.firstString("aggregatedOutput", "stdout", "output")
            val stderr = output.firstString("stderr", "error")
            val diff = output.firstString("diff", "patch")
            val changes = (output["changes"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

            if (exitCode != null || duration != null || status != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    exitCode?.let { code ->
                        Tag(type = if (code == "0") TagType.SUCCESS else TagType.ERROR) {
                            Text("退出码 $code", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    duration?.let { ms ->
                        Tag(type = TagType.DEFAULT) {
                            Text("$ms ms", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    status?.let { st ->
                        Tag(type = TagType.INFO) {
                            Text(st, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (changes.isNotEmpty()) {
                changes.forEach { file ->
                    val path = file["path"]?.jsonPrimitiveOrNull?.contentOrNull
                    val fileDiff = file["diff"]?.jsonPrimitiveOrNull?.contentOrNull
                    if (!path.isNullOrBlank()) Text(path, style = MaterialTheme.typography.labelMedium)
                    if (!fileDiff.isNullOrBlank()) HighlightCodeBlock(code = fileDiff, language = "diff")
                }
            }

            if (!diff.isNullOrBlank()) {
                HighlightCodeBlock(code = diff, language = "diff")
            }

            if (!stdout.isNullOrBlank()) {
                val parsedStdout = remember(stdout) {
                    runCatching { JsonInstant.parseToJsonElement(stdout.trim()) }.getOrNull()
                }
                if (parsedStdout is JsonObject || parsedStdout is JsonArray) {
                    StructuredJsonContent(element = parsedStdout, pathHint = inputPathHint)
                } else {
                    HighlightCodeBlock(code = stdout, language = "text")
                }
            }

            if (!stderr.isNullOrBlank()) {
                Text(
                    text = "错误输出",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                HighlightCodeBlock(code = stderr, language = "text")
            }

            val handledKeys = setOf(
                "exitCode", "exit_code", "returncode",
                "durationMs", "duration_ms", "status",
                "aggregatedOutput", "stdout", "output",
                "stderr", "error", "diff", "patch", "changes",
            )
            val remaining = output.filterKeys { it !in handledKeys }
            if (remaining.isNotEmpty()) {
                StructuredJsonObjectFields(obj = JsonObject(remaining), pathHint = inputPathHint)
            }
        }
    }
}
