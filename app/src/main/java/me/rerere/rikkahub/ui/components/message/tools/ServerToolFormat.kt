package me.rerere.rikkahub.ui.components.message.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import java.util.Locale

enum class ServerToolKind {
    CODE_EXECUTION,
    COMMAND_EXECUTION,
    FILE_CHANGE,
    WEB_SEARCH,
    URL_CONTEXT,
    MCP_TOOL,
    GENERIC,
}

data class ServerToolSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

data class ServerToolUrlItem(
    val url: String,
    val status: String? = null,
    val content: String? = null,
)

sealed interface ServerToolBlock {
    data class Code(val label: String?, val code: String, val language: String) : ServerToolBlock
    data class Diff(val path: String?, val kind: String?, val diff: String) : ServerToolBlock
    data class Chips(val label: String?, val items: List<String>) : ServerToolBlock
    data class SearchResults(val results: List<ServerToolSearchResult>) : ServerToolBlock
    data class UrlItems(val items: List<ServerToolUrlItem>) : ServerToolBlock
    data class KeyValues(val label: String?, val pairs: List<Pair<String, String>>) : ServerToolBlock
    data class Json(val label: String?, val prettyJson: String) : ServerToolBlock
    data class Text(val label: String?, val text: String, val isError: Boolean = false) : ServerToolBlock
}

data class ParsedServerTool(
    val kind: ServerToolKind,
    val title: String,
    val durationLabel: String?,
    val exitCode: Int?,
    val isError: Boolean,
    val badges: List<String>,
    val inputBlocks: List<ServerToolBlock>,
    val outputBlocks: List<ServerToolBlock>,
)

fun unwrapJsonElement(element: JsonElement?): JsonElement? {
    if (element == null || element is JsonNull) return null
    if (element is JsonPrimitive && element.isString) {
        val raw = element.content.trim()
        if ((raw.startsWith("{") && raw.endsWith("}")) || (raw.startsWith("[") && raw.endsWith("]"))) {
            val parsed = runCatching { JsonInstant.parseToJsonElement(raw) }.getOrNull()
            if (parsed != null && parsed !is JsonNull) {
                return unwrapJsonElement(parsed)
            }
        }
    }
    return element
}

fun parseJsonTextOrNull(raw: String): JsonElement? {
    val trimmed = raw.trim()
    if (!(trimmed.startsWith("{") && trimmed.endsWith("}")) && !(trimmed.startsWith("[") && trimmed.endsWith("]"))) {
        return null
    }
    val parsed = runCatching { JsonInstant.parseToJsonElement(trimmed) }.getOrNull() ?: return null
    return unwrapJsonElement(parsed)
}

fun inferCodeLanguage(languageHint: String? = null, pathHint: String? = null): String {
    val normalizedHint = languageHint?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
    if (normalizedHint != null) {
        return when (normalizedHint) {
            "py", "python3" -> "python"
            "sh", "shell", "zsh" -> "bash"
            "js" -> "javascript"
            "ts" -> "typescript"
            "yml" -> "yaml"
            "md" -> "markdown"
            "kt" -> "kotlin"
            else -> normalizedHint
        }
    }
    val ext = pathHint?.substringAfterLast('.', "")?.lowercase(Locale.US)
    return when (ext) {
        "kt", "kts" -> "kotlin"
        "py" -> "python"
        "js", "mjs", "cjs", "jsx" -> "javascript"
        "ts", "mts", "cts", "tsx" -> "typescript"
        "sh", "bash", "zsh" -> "bash"
        "json", "jsonl" -> "json"
        "md", "markdown" -> "markdown"
        "yml", "yaml" -> "yaml"
        "xml" -> "xml"
        "html", "htm" -> "html"
        "css", "scss" -> "css"
        "sql" -> "sql"
        "diff", "patch" -> "diff"
        "java" -> "java"
        "go" -> "go"
        "rs" -> "rust"
        "c", "h" -> "c"
        "cpp", "cc", "cxx", "hpp" -> "cpp"
        else -> "text"
    }
}

private fun JsonObject.firstString(vararg keys: String): String? {
    for (key in keys) {
        val value = this[key]?.jsonPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotBlank() }
        if (value != null) return value
    }
    return null
}

private fun JsonObject.firstInt(vararg keys: String): Int? {
    for (key in keys) {
        val value = this[key]?.jsonPrimitiveOrNull?.intOrNull
        if (value != null) return value
    }
    return null
}

private fun JsonObject.firstLong(vararg keys: String): Long? {
    for (key in keys) {
        val value = this[key]?.jsonPrimitiveOrNull?.longOrNull
        if (value != null) return value
    }
    return null
}

private fun formatDurationLabel(ms: Long?): String? {
    if (ms == null || ms < 0L) return null
    return if (ms >= 1000L) {
        String.format(Locale.US, "%.1fs", ms / 1000.0)
    } else {
        "${ms}ms"
    }
}

private fun formatOutcomeLabel(outcome: String?): String? {
    return when (outcome?.uppercase(Locale.US)) {
        null, "" -> null
        "OUTCOME_OK", "OK", "SUCCESS", "SUCCEEDED", "COMPLETED" -> "成功"
        "OUTCOME_FAILED", "FAILED", "ERROR" -> "失败"
        "OUTCOME_DEADLINE_EXCEEDED", "TIMEOUT", "TIMED_OUT" -> "超时"
        "OUTCOME_CANCELED", "CANCELED", "CANCELLED" -> "已取消"
        else -> outcome
    }
}

fun parseServerTool(tool: UIMessagePart.ServerTool): ParsedServerTool {
    val providerType = tool.metadata?.get("providerType")?.jsonPrimitiveOrNull?.contentOrNull
    val normalizedName = (providerType ?: tool.toolName).lowercase(Locale.US)
    val inputEl = unwrapJsonElement(tool.input)
    val outputEl = unwrapJsonElement(tool.output)
    val inputObj = inputEl as? JsonObject
    val outputObj = outputEl as? JsonObject

    val kind = resolveServerToolKind(normalizedName, tool.toolName.lowercase(Locale.US), inputObj, outputObj)

    val durationMs = outputObj?.firstLong("durationMs", "duration_ms", "elapsedMs", "elapsed_ms")
    val durationLabel = formatDurationLabel(durationMs)
    val exitCode = outputObj?.firstInt("exitCode", "exit_code", "returnCode", "return_code")
    val outcomeRaw = outputObj?.firstString("outcome", "status")
    val outcomeLabel = formatOutcomeLabel(outcomeRaw)
    val isErrorOutcome = tool.status == ServerToolStatus.FAILED ||
        (exitCode != null && exitCode != 0) ||
        outcomeRaw?.uppercase(Locale.US) in setOf("OUTCOME_FAILED", "FAILED", "ERROR", "OUTCOME_DEADLINE_EXCEEDED", "TIMEOUT")

    val badges = buildList {
        when {
            tool.status == ServerToolStatus.IN_PROGRESS -> add("执行中")
            isErrorOutcome -> add("失败")
            else -> add("已完成")
        }
        outcomeLabel?.takeIf { it != "成功" || exitCode == null }?.let {
            if (it !in this) add("结果: $it")
        }
        exitCode?.let { add("退出码 $it") }
        durationLabel?.let { add(it) }
        inputObj?.firstString("language", "lang")?.let { add(it.uppercase(Locale.US)) }
        inputObj?.firstString("cwd", "workdir", "working_directory", "workingDirectory")?.let { add("目录: $it") }
        if ((tool.metadata?.get("truncated") as? JsonPrimitive)?.booleanOrNull == true) {
            add("部分截断")
        }
    }

    val title = buildServerToolTitle(kind, tool.toolName, inputObj, outputObj)
    val inputBlocks = buildInputBlocks(kind, inputEl)
    val outputBlocks = buildOutputBlocks(kind, inputObj, outputEl)

    return ParsedServerTool(
        kind = kind,
        title = title,
        durationLabel = durationLabel,
        exitCode = exitCode,
        isError = isErrorOutcome,
        badges = badges,
        inputBlocks = inputBlocks,
        outputBlocks = outputBlocks,
    )
}

private fun resolveServerToolKind(
    normalizedName: String,
    rawLowerName: String,
    inputObj: JsonObject?,
    outputObj: JsonObject?,
): ServerToolKind {
    return when {
        normalizedName in setOf("code_execution", "codeexecution", "execute_code", "run_code") ||
            rawLowerName in setOf("code_execution", "codeexecution") -> ServerToolKind.CODE_EXECUTION

        normalizedName in setOf(
            "commandexecution", "command_execution", "shell", "bash",
            "run_command", "execute_command", "terminal", "exec_command",
        ) || inputObj?.firstString("command", "cmd") != null -> ServerToolKind.COMMAND_EXECUTION

        normalizedName in setOf(
            "filechange", "file_change", "edit_file", "write_file", "create_file",
            "read_file", "view_file", "patch_file", "replace_file_content", "multi_replace_file_content",
        ) || outputObj?.get("changes") is JsonArray -> ServerToolKind.FILE_CHANGE

        normalizedName in setOf("google_search", "web_search", "websearch", "search_web") ||
            inputObj?.get("queries") is JsonArray -> ServerToolKind.WEB_SEARCH

        normalizedName in setOf("url_context", "urlcontext", "fetch_url", "web_fetch", "scrape_web") ||
            inputObj?.get("urls") is JsonArray -> ServerToolKind.URL_CONTEXT

        normalizedName in setOf("mcptoolcall", "mcp_tool_call") -> ServerToolKind.MCP_TOOL

        inputObj?.firstString("code") != null -> ServerToolKind.CODE_EXECUTION
        else -> ServerToolKind.GENERIC
    }
}

private fun buildServerToolTitle(
    kind: ServerToolKind,
    rawToolName: String,
    inputObj: JsonObject?,
    outputObj: JsonObject?,
): String {
    return when (kind) {
        ServerToolKind.CODE_EXECUTION -> {
            val lang = inputObj?.firstString("language", "lang")?.lowercase(Locale.US)
            if (lang != null) "执行代码 ($lang)" else "执行代码"
        }

        ServerToolKind.COMMAND_EXECUTION -> {
            val cmd = inputObj?.firstString("command", "cmd")
                ?.lineSequence()
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.take(48)
            if (cmd != null) "执行命令: $cmd" else "执行命令"
        }

        ServerToolKind.FILE_CHANGE -> {
            val changes = (outputObj?.get("changes") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val path = inputObj?.firstString("path", "file_path", "filePath", "target_file")
                ?: changes.firstOrNull()?.firstString("path")
            val fileName = path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            val lower = rawToolName.lowercase(Locale.US)
            when {
                changes.size > 1 -> "修改 ${changes.size} 个文件"
                lower.contains("read") || lower.contains("view") ->
                    if (fileName != null) "读取文件: $fileName" else "读取文件"
                lower.contains("write") || lower.contains("create") ->
                    if (fileName != null) "写入文件: $fileName" else "写入文件"
                else -> if (fileName != null) "编辑文件: $fileName" else "修改文件"
            }
        }

        ServerToolKind.WEB_SEARCH -> {
            val queries = (inputObj?.get("queries") as? JsonArray)
                ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull?.takeIf(String::isNotBlank) }
                .orEmpty()
            val single = inputObj?.firstString("query", "q")
            val preview = queries.firstOrNull() ?: single
            if (preview != null) "搜索: ${preview.take(40)}" else "网页搜索"
        }

        ServerToolKind.URL_CONTEXT -> {
            val urls = (inputObj?.get("urls") as? JsonArray)
                ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull?.takeIf(String::isNotBlank) }
                .orEmpty()
            val single = inputObj?.firstString("url")
            val preview = urls.firstOrNull() ?: single
            if (preview != null) "读取网页: ${preview.take(40)}" else "读取网页"
        }

        ServerToolKind.MCP_TOOL -> {
            val server = inputObj?.firstString("server", "serverName")
            val tool = inputObj?.firstString("tool", "toolName", "name")
            when {
                server != null && tool != null -> "MCP: $server/$tool"
                tool != null -> "MCP: $tool"
                else -> "调用 MCP 工具"
            }
        }

        ServerToolKind.GENERIC -> "调用工具 $rawToolName"
    }
}

private val CODE_LIKE_KEYS = listOf("code", "command", "cmd", "script", "diff", "patch", "sql", "content", "new_text", "old_text")
private val INPUT_META_KEYS = setOf("language", "lang", "cwd", "workdir", "working_directory", "workingDirectory", "path", "file_path", "filePath", "target_file")

private fun buildInputBlocks(kind: ServerToolKind, inputEl: JsonElement?): List<ServerToolBlock> {
    if (inputEl == null || inputEl is JsonNull) return emptyList()
    if (inputEl is JsonPrimitive) {
        val text = inputEl.contentOrNull.orEmpty()
        if (text.isBlank()) return emptyList()
        return listOf(ServerToolBlock.Text(label = null, text = text))
    }
    if (inputEl is JsonArray) {
        return listOf(ServerToolBlock.Json(label = null, prettyJson = JsonInstantPretty.encodeToString(inputEl)))
    }
    val obj = inputEl as? JsonObject ?: return emptyList()
    if (obj.isEmpty()) return emptyList()

    val blocks = mutableListOf<ServerToolBlock>()
    val consumedKeys = mutableSetOf<String>()

    when (kind) {
        ServerToolKind.COMMAND_EXECUTION -> {
            val cmd = obj.firstString("command", "cmd")
            if (cmd != null) {
                consumedKeys += listOf("command", "cmd", "cwd", "workdir", "working_directory", "workingDirectory")
                obj.firstString("description")?.let {
                    consumedKeys += "description"
                    blocks += ServerToolBlock.Text(label = "说明", text = it)
                }
                blocks += ServerToolBlock.Code(label = "命令", code = cmd, language = "bash")
            }
        }

        ServerToolKind.CODE_EXECUTION -> {
            val code = obj.firstString("code", "script", "source")
            if (code != null) {
                val lang = inferCodeLanguage(languageHint = obj.firstString("language", "lang"))
                consumedKeys += listOf("code", "script", "source", "language", "lang")
                blocks += ServerToolBlock.Code(label = "代码 ($lang)", code = code, language = lang)
            }
        }

        ServerToolKind.WEB_SEARCH -> {
            val queries = (obj["queries"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull?.takeIf(String::isNotBlank) }
                .orEmpty()
            val single = obj.firstString("query", "q")
            val all = (queries + listOfNotNull(single)).distinct()
            if (all.isNotEmpty()) {
                consumedKeys += listOf("queries", "query", "q")
                blocks += ServerToolBlock.Chips(label = "搜索关键词", items = all)
            }
        }

        ServerToolKind.URL_CONTEXT -> {
            val urls = (obj["urls"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull?.takeIf(String::isNotBlank) }
                .orEmpty()
            val single = obj.firstString("url")
            val all = (urls + listOfNotNull(single)).distinct()
            if (all.isNotEmpty()) {
                consumedKeys += listOf("urls", "url")
                blocks += ServerToolBlock.Chips(label = "目标链接", items = all)
            }
        }

        ServerToolKind.FILE_CHANGE -> {
            val path = obj.firstString("path", "file_path", "filePath", "target_file")
            val lang = inferCodeLanguage(pathHint = path)
            path?.let {
                consumedKeys += listOf("path", "file_path", "filePath", "target_file")
                blocks += ServerToolBlock.KeyValues(label = null, pairs = listOf("文件路径" to it))
            }
            obj.firstString("diff", "patch")?.let {
                consumedKeys += listOf("diff", "patch")
                blocks += ServerToolBlock.Code(label = "变更 Diff", code = it, language = "diff")
            }
            obj.firstString("content")?.let {
                consumedKeys += "content"
                blocks += ServerToolBlock.Code(label = "文件内容", code = it, language = lang)
            }
            obj.firstString("old_text")?.let {
                consumedKeys += "old_text"
                blocks += ServerToolBlock.Code(label = "原内容", code = it, language = lang)
            }
            obj.firstString("new_text")?.let {
                consumedKeys += "new_text"
                blocks += ServerToolBlock.Code(label = "新内容", code = it, language = lang)
            }
        }

        else -> Unit
    }

    // Extract any remaining code-like fields cleanly as code blocks
    val pathHint = obj.firstString("path", "file_path", "filePath")
    val langHint = obj.firstString("language", "lang")
    for (key in CODE_LIKE_KEYS) {
        if (key in consumedKeys) continue
        val text = obj[key]?.jsonPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
        consumedKeys += key
        val lang = when (key) {
            "command", "cmd" -> "bash"
            "diff", "patch" -> "diff"
            "sql" -> "sql"
            else -> inferCodeLanguage(languageHint = langHint, pathHint = pathHint)
        }
        blocks += ServerToolBlock.Code(label = key, code = text, language = lang)
    }

    val remaining = obj.filterKeys { it !in consumedKeys && it !in INPUT_META_KEYS }
    if (remaining.isNotEmpty()) {
        val scalarPairs = mutableListOf<Pair<String, String>>()
        val complexEntries = mutableMapOf<String, JsonElement>()
        remaining.forEach { (k, v) ->
            val unwrapped = unwrapJsonElement(v)
            if (unwrapped is JsonPrimitive) {
                scalarPairs += k to (unwrapped.contentOrNull ?: unwrapped.toString())
            } else if (unwrapped != null && unwrapped !is JsonNull) {
                complexEntries[k] = unwrapped
            }
        }
        if (scalarPairs.isNotEmpty()) {
            blocks += ServerToolBlock.KeyValues(label = "参数", pairs = scalarPairs)
        }
        if (complexEntries.isNotEmpty()) {
            blocks += ServerToolBlock.Json(
                label = if (blocks.isEmpty()) null else "其他参数",
                prettyJson = JsonInstantPretty.encodeToString(JsonObject(complexEntries)),
            )
        }
    }

    return blocks
}

private val OUTPUT_META_KEYS = setOf("exitCode", "exit_code", "returnCode", "return_code", "durationMs", "duration_ms", "elapsedMs", "elapsed_ms", "outcome", "status")

private fun buildOutputBlocks(
    kind: ServerToolKind,
    inputObj: JsonObject?,
    outputEl: JsonElement?,
): List<ServerToolBlock> {
    if (outputEl == null || outputEl is JsonNull) return emptyList()
    if (outputEl is JsonPrimitive) {
        val raw = outputEl.contentOrNull.orEmpty()
        if (raw.isBlank()) return emptyList()
        val parsed = parseJsonTextOrNull(raw)
        if (parsed != null) {
            return buildOutputBlocks(kind, inputObj, parsed)
        }
        return listOf(ServerToolBlock.Code(label = null, code = raw, language = "text"))
    }
    if (outputEl is JsonArray) {
        val searchResults = extractSearchResults(outputEl)
        if (searchResults.isNotEmpty()) {
            return listOf(ServerToolBlock.SearchResults(searchResults))
        }
        return listOf(ServerToolBlock.Json(label = null, prettyJson = JsonInstantPretty.encodeToString(outputEl)))
    }

    val obj = outputEl as? JsonObject ?: return emptyList()
    if (obj.isEmpty()) return emptyList()

    val blocks = mutableListOf<ServerToolBlock>()
    val consumedKeys = mutableSetOf<String>()
    consumedKeys += OUTPUT_META_KEYS

    // 1. Codex fileChange: changes array
    val changes = (obj["changes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    if (changes.isNotEmpty()) {
        consumedKeys += "changes"
        changes.forEach { change ->
            val path = change.firstString("path")
            val changeKind = change.firstString("kind", "type")
            val diff = change.firstString("diff")
            if (!diff.isNullOrBlank()) {
                blocks += ServerToolBlock.Diff(path = path, kind = changeKind, diff = diff)
            } else if (!path.isNullOrBlank()) {
                blocks += ServerToolBlock.KeyValues(label = null, pairs = listOfNotNull(
                    "文件" to path,
                    changeKind?.let { "操作" to it },
                ))
            }
        }
    }

    // 2. Search results (Antigravity google_search / Codex webSearch)
    for (key in listOf("results", "items", "groundingChunks", "search_results")) {
        val arr = obj[key] as? JsonArray ?: continue
        val parsedResults = extractSearchResults(arr)
        if (parsedResults.isNotEmpty()) {
            consumedKeys += key
            blocks += ServerToolBlock.SearchResults(parsedResults)
        }
    }

    // 3. URL context results (Antigravity url_context)
    if (kind == ServerToolKind.URL_CONTEXT) {
        val urlArr = obj["urls"] as? JsonArray ?: obj["url_metadata"] as? JsonArray
        if (urlArr != null) {
            val urlItems = urlArr.mapNotNull { item ->
                val itemObj = item as? JsonObject ?: return@mapNotNull null
                val url = itemObj.firstString("url", "retrieved_url") ?: return@mapNotNull null
                ServerToolUrlItem(
                    url = url,
                    status = itemObj.firstString("url_retrieval_status", "status"),
                    content = itemObj.firstString("content", "text", "snippet"),
                )
            }
            if (urlItems.isNotEmpty()) {
                consumedKeys += listOf("urls", "url_metadata")
                blocks += ServerToolBlock.UrlItems(urlItems)
            }
        }
    }

    // 4. Primary execution output / stdout / aggregatedOutput / result / content
    val pathHint = inputObj?.firstString("path", "file_path", "filePath")
    val outputLang = if (kind == ServerToolKind.FILE_CHANGE && pathHint != null) {
        inferCodeLanguage(pathHint = pathHint)
    } else {
        "text"
    }

    for (key in listOf("aggregatedOutput", "stdout", "output", "result", "content", "text")) {
        val rawVal = obj[key] ?: continue
        val unwrapped = unwrapJsonElement(rawVal) ?: continue
        consumedKeys += key
        when (unwrapped) {
            is JsonPrimitive -> {
                val text = unwrapped.contentOrNull.orEmpty()
                if (text.isNotBlank()) {
                    val label = when (key) {
                        "stdout" -> "标准输出"
                        "aggregatedOutput", "output" -> "输出"
                        "result" -> "结果"
                        else -> null
                    }
                    blocks += ServerToolBlock.Code(label = label, code = text, language = outputLang)
                }
            }
            is JsonObject, is JsonArray -> {
                blocks += ServerToolBlock.Json(
                    label = if (key == "result") "结果" else key,
                    prettyJson = JsonInstantPretty.encodeToString(unwrapped),
                )
            }
            else -> Unit
        }
    }

    // 5. Error / stderr
    for (key in listOf("stderr", "error", "errorMessage", "message")) {
        val text = obj.firstString(key) ?: continue
        consumedKeys += key
        val label = if (key == "stderr") "错误输出 (stderr)" else "错误信息"
        blocks += ServerToolBlock.Text(label = label, text = text, isError = true)
    }

    // 6. Remaining fields as pretty JSON or key-values
    val remaining = obj.filterKeys { it !in consumedKeys }
    if (remaining.isNotEmpty()) {
        val pretty = JsonInstantPretty.encodeToString(JsonObject(remaining))
        blocks += ServerToolBlock.Json(
            label = if (blocks.isEmpty()) null else "其他信息",
            prettyJson = pretty,
        )
    }

    return blocks
}

private fun extractSearchResults(arr: JsonArray): List<ServerToolSearchResult> {
    return arr.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val web = obj["web"] as? JsonObject
        val target = web ?: obj
        val url = target.firstString("url", "uri", "link") ?: return@mapNotNull null
        val title = target.firstString("title", "name") ?: url
        val snippet = target.firstString("snippet", "content", "description", "text").orEmpty()
        ServerToolSearchResult(title = title, url = url, snippet = snippet)
    }
}
