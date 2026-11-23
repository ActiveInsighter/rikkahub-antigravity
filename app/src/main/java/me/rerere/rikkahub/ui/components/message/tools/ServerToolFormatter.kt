package me.rerere.rikkahub.ui.components.message.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.http.jsonArrayOrNull
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

enum class ServerToolCategory {
    COMMAND_EXECUTION,
    CODE_EXECUTION,
    FILE_READ,
    FILE_ADD,
    FILE_EDIT,
    FILE_DELETE,
    SEARCH_FILES,
    WEB_SEARCH,
    URL_FETCH,
    MCP_CALL,
    BUILTIN_TOOL,
    GENERIC,
}

data class CodeSnippet(
    val code: String,
    val language: String,
)

sealed interface ToolDetailSection {
    data class Metadata(val items: List<Pair<String, String>>) : ToolDetailSection
    data class Code(val label: String? = null, val code: String, val language: String) : ToolDetailSection
    data class Diff(val path: String? = null, val diff: String) : ToolDetailSection
    data class Text(val label: String? = null, val text: String, val isError: Boolean = false) : ToolDetailSection
    data class Chips(val label: String? = null, val items: List<String>) : ToolDetailSection
}

data class FormattedServerTool(
    val category: ServerToolCategory,
    val title: String,
    val subtitle: String? = null,
    val summaryCode: CodeSnippet? = null,
    val statusText: String? = null,
    val stepDurationText: String? = null,
    val isError: Boolean = false,
    val inputSections: List<ToolDetailSection> = emptyList(),
    val outputSections: List<ToolDetailSection> = emptyList(),
)

/**
 * 将 Claude Code CLI / Codex CLI (App-Server) / Antigravity CLI / Antigravity API (Interactions)
 * 等服务端工具的原始 JSON 入参与出参转换为结构化展示模型，避免直接向用户展示难以阅读的原始 JSON。
 */
fun formatServerTool(tool: UIMessagePart.ServerTool): FormattedServerTool {
    val providerType = tool.metadata?.get("providerType")?.jsonPrimitiveOrNull?.contentOrNull
    val normalizedTool = normalizeToolKey(providerType?.takeIf { it.isNotBlank() } ?: tool.toolName)
    val input = unwrapJsonElement(tool.input)
    val output = unwrapJsonElement(tool.output)
    val isFailed = tool.status == ServerToolStatus.FAILED
    val isLoading = tool.status == ServerToolStatus.IN_PROGRESS

    return formatToolPayload(
        rawToolName = tool.toolName,
        normalizedKey = normalizedTool,
        input = input,
        output = output,
        metadata = tool.metadata,
        isFailed = isFailed,
        isLoading = isLoading,
    )
}

/**
 * 将 [ToolUIContext]（可用于未注册的普通工具或由 ServerTool 转换而来的上下文）转换为结构化展示模型。
 */
fun formatToolUIContext(context: ToolUIContext): FormattedServerTool {
    val providerType = context.tool.metadata?.get("providerType")?.jsonPrimitiveOrNull?.contentOrNull
    val normalizedKey = normalizeToolKey(providerType?.takeIf { it.isNotBlank() } ?: context.tool.toolName)
    val input = unwrapJsonElement(context.arguments)
    val rawOutputText = context.tool.output
        .filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }
    val output = unwrapJsonElement(
        context.content?.takeUnless { it is JsonObject && it.isEmpty() && rawOutputText.isNotBlank() }
            ?: rawOutputText.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) }
    )
    val agentStatus = context.tool.metadata?.get("agentStatus")?.jsonPrimitiveOrNull?.contentOrNull
    val isFailed = agentStatus in setOf("failed", "canceled") ||
        (output as? JsonObject)?.bool("is_error") == true

    return formatToolPayload(
        rawToolName = context.tool.toolName,
        normalizedKey = normalizedKey,
        input = input,
        output = output,
        metadata = context.tool.metadata,
        isFailed = isFailed,
        isLoading = context.loading,
    )
}

private fun formatToolPayload(
    rawToolName: String,
    normalizedKey: String,
    input: JsonElement?,
    output: JsonElement?,
    metadata: JsonObject?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val inputObj = input as? JsonObject
    val outputObj = output as? JsonObject
    val rawDurationMs = extractDurationMs(outputObj, inputObj, metadata)
    val stepDurationText = formatStepDuration(rawDurationMs)

    return when {
        isMcpTool(rawToolName, normalizedKey, inputObj) ->
            formatMcpTool(rawToolName, inputObj, input, outputObj, output, rawDurationMs, stepDurationText, isFailed, isLoading)

        normalizedKey in COMMAND_TOOLS ||
            (inputObj?.firstString("command", "cmd", "command_line", "bash_command", "CommandLine") != null &&
                inputObj.firstString("code") == null) ->
            formatCommandTool(rawToolName, normalizedKey, inputObj, input, outputObj, output, rawDurationMs, stepDurationText, isFailed, isLoading)

        normalizedKey in CODE_EXEC_TOOLS || inputObj?.firstString("code", "source") != null ->
            formatCodeExecutionTool(rawToolName, inputObj, input, outputObj, output, rawDurationMs, stepDurationText, isFailed, isLoading)

        normalizedKey in FILE_READ_TOOLS ->
            formatFileReadTool(rawToolName, inputObj, outputObj, output, rawDurationMs, stepDurationText, isFailed, isLoading)

        normalizedKey in FILE_DELETE_TOOLS ->
            formatFileDeleteTool(rawToolName, inputObj, outputObj, output, rawDurationMs, stepDurationText, isFailed, isLoading)

        normalizedKey in FILE_WRITE_TOOLS ->
            formatFileWriteTool(rawToolName, inputObj, outputObj, output, rawDurationMs, stepDurationText, isFailed, isLoading)

        normalizedKey in FILE_EDIT_TOOLS ||
            outputObj?.get("changes") is JsonArray ||
            inputObj?.get("changes") is JsonArray ||
            inputObj?.get("edits") is JsonArray ||
            inputObj?.get("ReplacementChunks") is JsonArray ||
            inputObj?.firstString("diff", "patch") != null ||
            (inputObj?.firstString("old_str", "old_string", "old_text", "TargetContent") != null &&
                inputObj.firstString("new_str", "new_string", "new_text", "ReplacementContent") != null) ->
            formatFileEditTool(rawToolName, inputObj, outputObj, output, rawDurationMs, stepDurationText, isFailed, isLoading)

        normalizedKey in SEARCH_FILES_TOOLS ->
            formatSearchFilesTool(rawToolName, inputObj, outputObj, output, rawDurationMs, stepDurationText, isFailed, isLoading)

        normalizedKey in WEB_SEARCH_TOOLS ->
            formatWebSearchTool(rawToolName, inputObj, output, rawDurationMs, stepDurationText, isFailed, isLoading)

        normalizedKey in URL_FETCH_TOOLS ->
            formatUrlFetchTool(rawToolName, inputObj, outputObj, output, rawDurationMs, stepDurationText, isFailed, isLoading)

        normalizedKey in BUILTIN_TOOLS ->
            formatBuiltinTool(rawToolName, normalizedKey, inputObj, input, outputObj, output, rawDurationMs, stepDurationText, isFailed, isLoading)

        else ->
            formatGenericTool(rawToolName, input, output, rawDurationMs, stepDurationText, isFailed, isLoading)
    }
}

private fun formatCommandTool(
    rawToolName: String,
    normalizedKey: String,
    inputObj: JsonObject?,
    rawInput: JsonElement?,
    outputObj: JsonObject?,
    rawOutput: JsonElement?,
    durationMs: String?,
    stepDurationText: String?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val command = inputObj?.firstString("command", "cmd", "command_line", "bash_command", "CommandLine", "script", "input")
    val cwd = inputObj?.firstString("cwd", "workdir", "working_directory", "directory", "Cwd")
    val description = inputObj?.firstString("description", "explanation", "purpose")
    val cmdLang = if (normalizedKey == "powershell") "powershell" else "bash"

    val exitCode = outputObj?.firstString("exitCode", "exit_code", "return_code", "returncode", "status_code")
    val outcome = outputObj?.firstString("outcome", "status")
    val stdout = outputObj?.firstString("aggregatedOutput", "aggregated_output", "stdout", "output", "result")
        ?: (rawOutput as? JsonPrimitive)?.contentOrNull
    val stderr = outputObj?.firstString("stderr", "error", "error_message")

    // 三层渐进式读文件识别：1. commandActions/locations -> 2. Bash 读命令启发式 -> 3. 输出内容启发式 + bash 兜底
    val readTargetPath = extractReadTargetFromActions(inputObj, outputObj)
        ?: extractReadPathFromBashCommand(command)
    val isReadFileCommand = !readTargetPath.isNullOrBlank()

    val errorState = isFailed ||
        (exitCode != null && exitCode != "0") ||
        outcome?.contains("FAIL", ignoreCase = true) == true ||
        outcome?.contains("ERROR", ignoreCase = true) == true

    val statusText = buildStatusSummary(
        isLoading = isLoading,
        isError = errorState,
        exitCode = exitCode,
        durationMs = durationMs,
        outcome = outcome,
    )

    val inputSections = buildList {
        val meta = buildList {
            readTargetPath?.let { add("目标文件" to it) }
            cwd?.let { add("工作目录" to it) }
            description?.let { add("说明" to it) }
        }
        if (meta.isNotEmpty()) add(ToolDetailSection.Metadata(meta))
        if (!command.isNullOrBlank()) {
            add(ToolDetailSection.Code(label = "命令", code = command, language = cmdLang))
        } else if (rawInput != null) {
            addAll(extractGenericSections(rawInput, defaultLanguage = cmdLang))
        }
        if (inputObj != null) {
            addAll(
                extractRemainingObject(
                    inputObj,
                    excludedKeys = setOf(
                        "command", "cmd", "command_line", "bash_command", "CommandLine", "script", "input",
                        "cwd", "workdir", "working_directory", "directory", "Cwd",
                        "description", "explanation", "purpose",
                    ),
                    defaultLanguage = cmdLang,
                )
            )
        }
    }

    val outputSections = buildList {
        val meta = buildList {
            exitCode?.let { add("退出码" to it) }
            durationMs?.let { add("耗时" to "${it} ms") }
            outcome?.takeIf { exitCode == null }?.let { add("状态" to formatOutcomeLabel(it)) }
        }
        if (meta.isNotEmpty()) add(ToolDetailSection.Metadata(meta))
        if (!stdout.isNullOrBlank()) {
            val detectionSample = if (isReadFileCommand) stripLineNumbersForDetection(stdout) else stdout
            val outLang = inferCodeLanguage(
                explicitLang = null,
                path = readTargetPath,
                code = detectionSample,
                defaultLanguage = "bash",
            )
            add(
                ToolDetailSection.Code(
                    label = readTargetPath ?: "输出",
                    code = stdout,
                    language = outLang,
                )
            )
        }
        if (!stderr.isNullOrBlank()) {
            add(ToolDetailSection.Text(label = "错误输出", text = stderr, isError = true))
        }
        if (outputObj != null) {
            addAll(
                extractRemainingObject(
                    outputObj,
                    excludedKeys = setOf(
                        "exitCode", "exit_code", "return_code", "returncode", "status_code",
                        "durationMs", "duration_ms", "elapsed_ms", "execution_time_ms", "outcome", "status",
                        "aggregatedOutput", "aggregated_output", "stdout", "output", "result",
                        "stderr", "error", "error_message",
                    ),
                    defaultLanguage = "bash",
                )
            )
        } else if (rawOutput != null && stdout == null) {
            addAll(extractGenericSections(rawOutput, defaultLanguage = "bash"))
        }
    }

    val firstLine = command?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
    val readFileName = readTargetPath?.fileNameOfPath()
    val category = if (isReadFileCommand) ServerToolCategory.FILE_READ else ServerToolCategory.COMMAND_EXECUTION
    val title = when {
        readFileName != null -> "读取 $readFileName"
        description != null && description.isNotBlank() && description.length <= 48 -> description
        else -> "执行命令"
    }

    return FormattedServerTool(
        category = category,
        title = title,
        subtitle = firstLine ?: cwd ?: rawToolName,
        summaryCode = firstLine?.let { CodeSnippet(it, cmdLang) },
        statusText = statusText,
        stepDurationText = stepDurationText,
        isError = errorState,
        inputSections = inputSections,
        outputSections = outputSections,
    )
}

private fun formatCodeExecutionTool(
    rawToolName: String,
    inputObj: JsonObject?,
    rawInput: JsonElement?,
    outputObj: JsonObject?,
    rawOutput: JsonElement?,
    durationMs: String?,
    stepDurationText: String?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val code = inputObj?.firstString("code", "source", "script", "program")
    val explicitLang = inputObj?.firstString("language", "lang")
    val path = inputObj?.firstString("path", "file_path", "filename")
    val detectedLang = inferCodeLanguage(
        explicitLang = explicitLang,
        path = path,
        code = code,
        defaultLanguage = "python",
    )

    val exitCode = outputObj?.firstString("exitCode", "exit_code", "return_code")
    val outcome = outputObj?.firstString("outcome", "status")
    val stdout = outputObj?.firstString("output", "stdout", "aggregatedOutput", "result")
        ?: (rawOutput as? JsonPrimitive)?.contentOrNull
    val stderr = outputObj?.firstString("stderr", "error", "error_message")

    val errorState = isFailed ||
        (exitCode != null && exitCode != "0") ||
        outcome?.contains("FAIL", ignoreCase = true) == true ||
        outcome?.contains("ERROR", ignoreCase = true) == true

    val statusText = buildStatusSummary(
        isLoading = isLoading,
        isError = errorState,
        exitCode = exitCode,
        durationMs = durationMs,
        outcome = outcome,
    )

    val inputSections = buildList {
        val meta = buildList {
            if (detectedLang != "plaintext") add("语言" to detectedLang)
            path?.let { add("文件" to it) }
        }
        if (meta.isNotEmpty()) add(ToolDetailSection.Metadata(meta))
        if (!code.isNullOrBlank()) {
            add(ToolDetailSection.Code(label = "代码", code = code, language = detectedLang))
        } else if (rawInput != null) {
            addAll(extractGenericSections(rawInput))
        }
        if (inputObj != null) {
            addAll(
                extractRemainingObject(
                    inputObj,
                    excludedKeys = setOf("code", "source", "script", "program", "language", "lang", "path", "file_path", "filename"),
                )
            )
        }
    }

    val outputSections = buildList {
        val meta = buildList {
            exitCode?.let { add("退出码" to it) }
            durationMs?.let { add("耗时" to "${it} ms") }
            outcome?.takeIf { exitCode == null }?.let { add("状态" to formatOutcomeLabel(it)) }
        }
        if (meta.isNotEmpty()) add(ToolDetailSection.Metadata(meta))
        if (!stdout.isNullOrBlank()) {
            val outLang = inferCodeLanguage(explicitLang = null, path = null, code = stdout, defaultLanguage = "bash")
            add(ToolDetailSection.Code(label = "输出", code = stdout, language = outLang))
        }
        if (!stderr.isNullOrBlank()) {
            add(ToolDetailSection.Text(label = "错误输出", text = stderr, isError = true))
        }
        if (outputObj != null) {
            addAll(
                extractRemainingObject(
                    outputObj,
                    excludedKeys = setOf(
                        "exitCode", "exit_code", "return_code", "durationMs", "duration_ms", "elapsed_ms", "execution_time_ms",
                        "outcome", "status", "output", "stdout", "aggregatedOutput", "result",
                        "stderr", "error", "error_message",
                    ),
                )
            )
        } else if (rawOutput != null && stdout == null) {
            addAll(extractGenericSections(rawOutput))
        }
    }

    val previewLines = code?.lines()?.take(3)?.joinToString("\n")?.takeIf { it.isNotBlank() }

    return FormattedServerTool(
        category = ServerToolCategory.CODE_EXECUTION,
        title = "执行代码 ($detectedLang)",
        subtitle = code?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim(),
        summaryCode = previewLines?.let { CodeSnippet(it, detectedLang) },
        statusText = statusText,
        stepDurationText = stepDurationText,
        isError = errorState,
        inputSections = inputSections,
        outputSections = outputSections,
    )
}

private fun formatFileReadTool(
    rawToolName: String,
    inputObj: JsonObject?,
    outputObj: JsonObject?,
    rawOutput: JsonElement?,
    durationMs: String?,
    stepDurationText: String?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val path = inputObj?.firstString("path", "file_path", "filename", "target_file", "file", "AbsolutePath")
    val startLine = inputObj?.firstString("start_line", "startLine", "StartLine", "offset", "line_start")
    val endLine = inputObj?.firstString("end_line", "endLine", "EndLine", "limit", "line_end")

    val content = outputObj?.firstString("content", "text", "file_content", "data", "output")
        ?: (rawOutput as? JsonPrimitive)?.contentOrNull
    val explicitLang = outputObj?.firstString("language", "lang") ?: inputObj?.firstString("language", "lang")
    val errorMsg = outputObj?.firstString("error", "error_message")
    val lang = inferCodeLanguage(
        explicitLang = explicitLang,
        path = path,
        code = stripLineNumbersForDetection(content),
        defaultLanguage = "bash",
    )

    val errorState = isFailed || !errorMsg.isNullOrBlank()
    val lineRange = listOfNotNull(startLine, endLine).takeIf { it.isNotEmpty() }?.joinToString(" - ")

    val inputSections = buildList {
        val meta = buildList {
            path?.let { add("路径" to it) }
            lineRange?.let { add("行范围" to it) }
        }
        if (meta.isNotEmpty()) add(ToolDetailSection.Metadata(meta))
        if (inputObj != null) {
            addAll(
                extractRemainingObject(
                    inputObj,
                    excludedKeys = setOf(
                        "path", "file_path", "filename", "target_file", "file", "AbsolutePath",
                        "start_line", "startLine", "StartLine", "offset", "line_start",
                        "end_line", "endLine", "EndLine", "limit", "line_end", "language", "lang",
                    ),
                )
            )
        }
    }

    val outputSections = buildList {
        if (!errorMsg.isNullOrBlank()) {
            add(ToolDetailSection.Text(label = "错误", text = errorMsg, isError = true))
        }
        if (!content.isNullOrBlank()) {
            add(ToolDetailSection.Code(label = path ?: "文件内容", code = content, language = lang))
        } else if (rawOutput != null && errorMsg == null) {
            addAll(extractGenericSections(rawOutput))
        }
    }

    val fileName = path?.fileNameOfPath()
    return FormattedServerTool(
        category = ServerToolCategory.FILE_READ,
        title = if (fileName != null) "读取 $fileName" else "读取文件 ($rawToolName)",
        subtitle = path?.let { if (lineRange != null) "$it ($lineRange)" else it },
        statusText = buildStatusSummary(isLoading = isLoading, isError = errorState, durationMs = durationMs),
        stepDurationText = stepDurationText,
        isError = errorState,
        inputSections = inputSections,
        outputSections = outputSections,
    )
}

private fun formatFileDeleteTool(
    rawToolName: String,
    inputObj: JsonObject?,
    outputObj: JsonObject?,
    rawOutput: JsonElement?,
    durationMs: String?,
    stepDurationText: String?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val path = inputObj?.firstString("path", "file_path", "filename", "target_file", "file", "TargetFile", "AbsolutePath")
    val errorMsg = outputObj?.firstString("error", "error_message")
    val message = outputObj?.firstString("message", "status", "result")
        ?: (rawOutput as? JsonPrimitive)?.contentOrNull
    val errorState = isFailed || !errorMsg.isNullOrBlank()

    val inputSections = buildList {
        path?.let { add(ToolDetailSection.Metadata(listOf("路径" to it))) }
        if (inputObj != null) {
            addAll(
                extractRemainingObject(
                    inputObj,
                    excludedKeys = setOf("path", "file_path", "filename", "target_file", "file", "TargetFile", "AbsolutePath"),
                )
            )
        }
    }

    val outputSections = buildList {
        if (!errorMsg.isNullOrBlank()) add(ToolDetailSection.Text(label = "错误", text = errorMsg, isError = true))
        if (!message.isNullOrBlank()) add(ToolDetailSection.Text(label = "结果", text = message))
    }

    val fileName = path?.fileNameOfPath()
    return FormattedServerTool(
        category = ServerToolCategory.FILE_DELETE,
        title = if (fileName != null) "删除 $fileName" else "删除文件 ($rawToolName)",
        subtitle = path,
        statusText = buildStatusSummary(isLoading = isLoading, isError = errorState, durationMs = durationMs),
        stepDurationText = stepDurationText,
        isError = errorState,
        inputSections = inputSections,
        outputSections = outputSections,
    )
}

private fun formatFileWriteTool(
    rawToolName: String,
    inputObj: JsonObject?,
    outputObj: JsonObject?,
    rawOutput: JsonElement?,
    durationMs: String?,
    stepDurationText: String?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val path = inputObj?.firstString("path", "file_path", "filename", "target_file", "file", "TargetFile")
    val content = inputObj?.firstString("content", "text", "code", "data", "CodeContent")
    val explicitLang = inputObj?.firstString("language", "lang")
    val lang = inferCodeLanguage(explicitLang = explicitLang, path = path, code = content, defaultLanguage = "bash")

    val errorMsg = outputObj?.firstString("error", "error_message")
    val message = outputObj?.firstString("message", "status", "result")
        ?: (rawOutput as? JsonPrimitive)?.contentOrNull
    val errorState = isFailed || !errorMsg.isNullOrBlank()

    val inputSections = buildList {
        path?.let { add(ToolDetailSection.Metadata(listOf("路径" to it))) }
        if (!content.isNullOrBlank()) {
            add(ToolDetailSection.Code(label = path ?: "写入内容", code = content, language = lang))
        }
    }

    val outputSections = buildList {
        if (!errorMsg.isNullOrBlank()) add(ToolDetailSection.Text(label = "错误", text = errorMsg, isError = true))
        if (!message.isNullOrBlank()) add(ToolDetailSection.Text(label = "结果", text = message))
        if (outputObj != null) {
            addAll(extractRemainingObject(outputObj, setOf("error", "error_message", "message", "status", "result")))
        }
    }

    val fileName = path?.fileNameOfPath()
    val isCreate = normalizeToolKey(rawToolName) in setOf("create_file", "write_to_file", "write")
    return FormattedServerTool(
        category = ServerToolCategory.FILE_ADD,
        title = when {
            fileName != null && isCreate -> "新建 $fileName"
            fileName != null -> "写入 $fileName"
            else -> "写入文件 ($rawToolName)"
        },
        subtitle = path,
        summaryCode = content?.lines()?.take(3)?.joinToString("\n")?.takeIf { it.isNotBlank() }?.let { CodeSnippet(it, lang) },
        statusText = buildStatusSummary(isLoading = isLoading, isError = errorState, durationMs = durationMs),
        stepDurationText = stepDurationText,
        isError = errorState,
        inputSections = inputSections,
        outputSections = outputSections,
    )
}

private fun formatFileEditTool(
    rawToolName: String,
    inputObj: JsonObject?,
    outputObj: JsonObject?,
    rawOutput: JsonElement?,
    durationMs: String?,
    stepDurationText: String?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val path = inputObj?.firstString("path", "file_path", "filename", "target_file", "file", "TargetFile", "notebook_path")
    val inputDiff = inputObj?.firstString("diff", "patch")
    val oldStr = inputObj?.firstString("old_str", "old_string", "old_text", "search", "TargetContent")
    val newStr = inputObj?.firstString("new_str", "new_string", "new_text", "replace", "ReplacementContent", "new_source")

    // 支持 Claude MultiEdit (edits) 与 Antigravity multi_replace_file_content (ReplacementChunks)
    val multiEdits = (inputObj?.get("edits") as? JsonArray ?: inputObj?.get("ReplacementChunks") as? JsonArray)
        .orEmpty()
        .mapNotNull { it as? JsonObject }
        .mapNotNull { chunk ->
            val oldChunk = chunk.firstString("old_string", "old_str", "TargetContent")
            val newChunk = chunk.firstString("new_string", "new_str", "ReplacementContent")
            if (oldChunk != null && newChunk != null) {
                generateUnifiedDiff(oldChunk, newChunk, path ?: "file")
            } else {
                null
            }
        }

    val computedDiff = inputDiff ?: if (oldStr != null && newStr != null) {
        generateUnifiedDiff(oldStr, newStr, path ?: "file")
    } else if (multiEdits.isNotEmpty()) {
        multiEdits.joinToString("\n")
    } else {
        null
    }

    val outputDiff = outputObj?.firstString("diff", "patch")
    val changes = (outputObj?.get("changes") as? JsonArray ?: inputObj?.get("changes") as? JsonArray)
        .orEmpty()
        .mapNotNull { it as? JsonObject }

    // 识别 Codex fileChange / apply_patch 的增/删/改类型
    val changeKinds = changes.mapNotNull { extractChangeKind(it["kind"]) }
    val patchKinds = detectPatchChangeKinds(computedDiff ?: outputDiff)
    val resolvedCategory = when {
        changeKinds.isNotEmpty() && changeKinds.all { it == "add" } -> ServerToolCategory.FILE_ADD
        changeKinds.isNotEmpty() && changeKinds.all { it == "delete" } -> ServerToolCategory.FILE_DELETE
        patchKinds.isNotEmpty() && patchKinds.all { it == "add" } -> ServerToolCategory.FILE_ADD
        patchKinds.isNotEmpty() && patchKinds.all { it == "delete" } -> ServerToolCategory.FILE_DELETE
        else -> ServerToolCategory.FILE_EDIT
    }

    val errorMsg = outputObj?.firstString("error", "error_message")
    val message = outputObj?.firstString("message", "status", "result")
        ?: (rawOutput as? JsonPrimitive)?.contentOrNull
    val errorState = isFailed || !errorMsg.isNullOrBlank()

    val inputSections = buildList {
        path?.let { add(ToolDetailSection.Metadata(listOf("路径" to it))) }
        if (!computedDiff.isNullOrBlank()) {
            add(ToolDetailSection.Diff(path = path, diff = computedDiff))
        } else {
            val lang = inferCodeLanguage(null, path, newStr ?: oldStr, "bash")
            if (!oldStr.isNullOrBlank()) add(ToolDetailSection.Code(label = "原内容", code = oldStr, language = lang))
            if (!newStr.isNullOrBlank()) add(ToolDetailSection.Code(label = "新内容", code = newStr, language = lang))
        }
    }

    val outputSections = buildList {
        if (!errorMsg.isNullOrBlank()) add(ToolDetailSection.Text(label = "错误", text = errorMsg, isError = true))
        if (changes.isNotEmpty()) {
            changes.forEach { file ->
                val filePath = file.firstString("path", "file_path")
                val diff = file.firstString("diff", "patch")
                if (!diff.isNullOrBlank()) {
                    add(ToolDetailSection.Diff(path = filePath, diff = diff))
                } else if (!filePath.isNullOrBlank()) {
                    add(ToolDetailSection.Text(text = filePath))
                }
            }
        } else if (!outputDiff.isNullOrBlank()) {
            add(ToolDetailSection.Diff(path = path, diff = outputDiff))
        }
        if (!message.isNullOrBlank() && outputDiff == null) {
            add(ToolDetailSection.Text(label = "结果", text = message))
        }
    }

    val firstChangedPath = path ?: changes.firstOrNull()?.firstString("path", "file_path")
    val fileName = firstChangedPath?.fileNameOfPath()
    val title = when (resolvedCategory) {
        ServerToolCategory.FILE_ADD -> if (fileName != null) "新建 $fileName" else "新建文件 ($rawToolName)"
        ServerToolCategory.FILE_DELETE -> if (fileName != null) "删除 $fileName" else "删除文件 ($rawToolName)"
        else -> if (fileName != null) "编辑 $fileName" else "编辑文件 ($rawToolName)"
    }
    return FormattedServerTool(
        category = resolvedCategory,
        title = title,
        subtitle = firstChangedPath,
        statusText = buildStatusSummary(isLoading = isLoading, isError = errorState, durationMs = durationMs),
        stepDurationText = stepDurationText,
        isError = errorState,
        inputSections = inputSections,
        outputSections = outputSections,
    )
}

private fun formatSearchFilesTool(
    rawToolName: String,
    inputObj: JsonObject?,
    outputObj: JsonObject?,
    rawOutput: JsonElement?,
    durationMs: String?,
    stepDurationText: String?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val pattern = inputObj?.firstString("pattern", "query", "regex", "glob", "search", "Query", "Pattern")
    val path = inputObj?.firstString("path", "directory", "dir", "search_path", "DirectoryPath", "SearchPath", "SearchDirectory")
    val isListDir = normalizeToolKey(rawToolName) in setOf("list_dir", "list_directory", "ls")

    val inputSections = buildList {
        val meta = buildList {
            pattern?.let { add("关键词" to it) }
            path?.let { add("路径" to it) }
        }
        if (meta.isNotEmpty()) add(ToolDetailSection.Metadata(meta))
    }

    val outputSections = buildList {
        val textOutput = outputObj?.firstString("output", "content", "result")
            ?: (rawOutput as? JsonPrimitive)?.contentOrNull
        val items = outputObj?.get("matches")?.jsonArrayOrNull
            ?: outputObj?.get("files")?.jsonArrayOrNull
            ?: outputObj?.get("entries")?.jsonArrayOrNull
            ?: outputObj?.get("results")?.jsonArrayOrNull
            ?: (rawOutput as? JsonArray)

        if (items != null && items.isNotEmpty()) {
            val formattedList = items.joinToString("\n") { el ->
                when (el) {
                    is JsonPrimitive -> el.contentOrNull ?: el.toString()
                    is JsonObject -> {
                        val file = el.firstString("path", "file", "filename", "name")
                        val line = el.firstString("line", "line_number")
                        val text = el.firstString("text", "content", "line_content", "match")
                        listOfNotNull(
                            listOfNotNull(file, line).joinToString(":").takeIf { it.isNotBlank() },
                            text?.trim(),
                        ).joinToString("  ")
                    }
                    else -> el.toString()
                }
            }
            add(ToolDetailSection.Code(label = "匹配结果 (${items.size})", code = formattedList, language = "bash"))
        } else if (!textOutput.isNullOrBlank()) {
            add(ToolDetailSection.Code(label = "结果", code = textOutput, language = "bash"))
        } else if (rawOutput != null) {
            addAll(extractGenericSections(rawOutput))
        }
    }

    val title = when {
        isListDir && path != null -> "列出目录 ${path.substringAfterLast('/').ifBlank { path }}"
        isListDir -> "列出目录"
        pattern != null -> "搜索: $pattern"
        else -> "查找文件 ($rawToolName)"
    }

    return FormattedServerTool(
        category = ServerToolCategory.SEARCH_FILES,
        title = title,
        subtitle = path ?: pattern,
        statusText = buildStatusSummary(isLoading = isLoading, isError = isFailed, durationMs = durationMs),
        stepDurationText = stepDurationText,
        isError = isFailed,
        inputSections = inputSections,
        outputSections = outputSections,
    )
}

private fun formatWebSearchTool(
    rawToolName: String,
    inputObj: JsonObject?,
    rawOutput: JsonElement?,
    durationMs: String?,
    stepDurationText: String?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val queries = buildList {
        inputObj?.get("queries")?.jsonArrayOrNull?.forEach { el ->
            el.jsonPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotBlank() }?.let(::add)
        }
        inputObj?.firstString("query", "q")?.let { if (it !in this) add(it) }
    }

    val inputSections = buildList {
        if (queries.isNotEmpty()) add(ToolDetailSection.Chips(label = "搜索关键词", items = queries))
    }

    val outputSections = buildList {
        val results = when (rawOutput) {
            is JsonArray -> rawOutput
            is JsonObject -> rawOutput["results"]?.jsonArrayOrNull ?: rawOutput["items"]?.jsonArrayOrNull
            else -> null
        }
        if (results != null) {
            val formattedItems = results.mapNotNull { el ->
                val obj = el.jsonObjectOrNull ?: return@mapNotNull el.jsonPrimitiveOrNull?.contentOrNull
                val title = obj.firstString("title", "name")
                val url = obj.firstString("url", "link")
                val snippet = obj.firstString("snippet", "text", "description")
                listOfNotNull(title, url, snippet).takeIf { it.isNotEmpty() }?.joinToString("\n")
            }
            if (formattedItems.isNotEmpty()) {
                add(ToolDetailSection.Text(label = "搜索结果", text = formattedItems.joinToString("\n\n")))
            } else {
                val filtered = results.filter { el ->
                    val obj = el.jsonObjectOrNull
                    obj == null || !obj.containsKey("search_suggestions") || obj.size > 1
                }
                if (filtered.isNotEmpty()) {
                    add(ToolDetailSection.Text(label = "搜索结果", text = filtered.joinToString("\n") { it.toString() }))
                }
            }
        } else if (rawOutput != null) {
            addAll(extractGenericSections(rawOutput))
        }
    }

    return FormattedServerTool(
        category = ServerToolCategory.WEB_SEARCH,
        title = if (queries.isNotEmpty()) "搜索: ${queries.first()}" else "网页搜索 ($rawToolName)",
        subtitle = queries.takeIf { it.size > 1 }?.joinToString(" · "),
        statusText = buildStatusSummary(isLoading = isLoading, isError = isFailed, durationMs = durationMs),
        stepDurationText = stepDurationText,
        isError = isFailed,
        inputSections = inputSections,
        outputSections = outputSections,
    )
}

private fun formatUrlFetchTool(
    rawToolName: String,
    inputObj: JsonObject?,
    outputObj: JsonObject?,
    rawOutput: JsonElement?,
    durationMs: String?,
    stepDurationText: String?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val urls = buildList {
        inputObj?.get("urls")?.jsonArrayOrNull?.forEach { el ->
            el.jsonPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotBlank() }?.let(::add)
        }
        inputObj?.firstString("url", "uri", "Url")?.let { if (it !in this) add(it) }
    }

    val inputSections = buildList {
        if (urls.isNotEmpty()) add(ToolDetailSection.Chips(label = "目标 URL", items = urls))
    }

    val content = outputObj?.firstString("content", "text", "markdown", "body")
        ?: (rawOutput as? JsonPrimitive)?.contentOrNull
    val title = outputObj?.firstString("title")
    val errorMsg = outputObj?.firstString("error", "error_message")
    val errorState = isFailed || !errorMsg.isNullOrBlank()

    val outputSections = buildList {
        title?.let { add(ToolDetailSection.Metadata(listOf("标题" to it))) }
        if (!errorMsg.isNullOrBlank()) add(ToolDetailSection.Text(label = "错误", text = errorMsg, isError = true))
        if (!content.isNullOrBlank()) {
            val lang = inferCodeLanguage(null, null, content, "markdown")
            add(ToolDetailSection.Code(label = "页面内容", code = content, language = lang))
        } else if (rawOutput != null && errorMsg == null) {
            addAll(extractGenericSections(rawOutput))
        }
    }

    return FormattedServerTool(
        category = ServerToolCategory.URL_FETCH,
        title = title?.take(48) ?: "读取网页 ($rawToolName)",
        subtitle = urls.firstOrNull(),
        statusText = buildStatusSummary(isLoading = isLoading, isError = errorState, durationMs = durationMs),
        stepDurationText = stepDurationText,
        isError = errorState,
        inputSections = inputSections,
        outputSections = outputSections,
    )
}

private fun formatMcpTool(
    rawToolName: String,
    inputObj: JsonObject?,
    rawInput: JsonElement?,
    outputObj: JsonObject?,
    rawOutput: JsonElement?,
    durationMs: String?,
    stepDurationText: String?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val (parsedServer, parsedTool) = parseMcpNames(rawToolName)
    val serverName = inputObj?.firstString("server", "server_name", "mcp_server") ?: parsedServer
    val toolName = inputObj?.firstString("tool", "tool_name", "name") ?: parsedTool ?: rawToolName

    val argsElement = inputObj?.get("arguments")?.let(::unwrapJsonElement)
        ?: inputObj?.get("params")?.let(::unwrapJsonElement)
        ?: inputObj?.get("input")?.let(::unwrapJsonElement)

    val inputSections = buildList {
        val meta = buildList {
            serverName?.let { add("MCP 服务" to it) }
            add("工具" to toolName)
        }
        add(ToolDetailSection.Metadata(meta))
        if (argsElement != null) {
            addAll(extractGenericSections(argsElement))
        } else if (inputObj != null) {
            addAll(
                extractRemainingObject(
                    inputObj,
                    excludedKeys = setOf("server", "server_name", "mcp_server", "tool", "tool_name", "name"),
                )
            )
        } else if (rawInput != null) {
            addAll(extractGenericSections(rawInput))
        }
    }

    val errorMsg = outputObj?.firstString("error", "error_message")
    val errorState = isFailed || !errorMsg.isNullOrBlank() || outputObj?.bool("isError") == true

    val resultElement = outputObj?.get("result")?.let(::unwrapJsonElement)
        ?: outputObj?.get("content")?.let(::unwrapJsonElement)
        ?: outputObj?.get("output")?.let(::unwrapJsonElement)

    val outputSections = buildList {
        if (!errorMsg.isNullOrBlank()) {
            add(ToolDetailSection.Text(label = "错误", text = errorMsg, isError = true))
        }
        val mcpText = extractMcpContentText(resultElement ?: rawOutput)
        if (!mcpText.isNullOrBlank()) {
            val lang = inferCodeLanguage(null, null, mcpText, "bash")
            add(ToolDetailSection.Code(label = "返回结果", code = mcpText, language = lang))
        } else if (resultElement != null) {
            addAll(extractGenericSections(resultElement))
        } else if (rawOutput != null && errorMsg == null) {
            addAll(extractGenericSections(rawOutput))
        }
    }

    val title = if (!serverName.isNullOrBlank()) "MCP: $serverName / $toolName" else "MCP: $toolName"
    return FormattedServerTool(
        category = ServerToolCategory.MCP_CALL,
        title = title,
        subtitle = serverName ?: rawToolName,
        statusText = buildStatusSummary(isLoading = isLoading, isError = errorState, durationMs = durationMs),
        stepDurationText = stepDurationText,
        isError = errorState,
        inputSections = inputSections,
        outputSections = outputSections,
    )
}

private fun formatBuiltinTool(
    rawToolName: String,
    normalizedKey: String,
    inputObj: JsonObject?,
    rawInput: JsonElement?,
    outputObj: JsonObject?,
    rawOutput: JsonElement?,
    durationMs: String?,
    stepDurationText: String?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val description = inputObj?.firstString("description", "prompt", "skill", "command_id", "CommandId")
    val title = when (normalizedKey) {
        "agent", "task" -> description?.takeIf { it.length <= 36 }?.let { "子代理: $it" } ?: "调用子代理"
        "todowrite", "todo_write", "update_plan" -> "更新任务计划"
        "todoread", "todo_read" -> "读取任务计划"
        "enterplanmode", "enter_plan_mode" -> "进入规划模式"
        "exitplanmode", "exit_plan_mode" -> "提交规划方案"
        "askuserquestion", "ask_user_question" -> "向用户提问"
        "skill" -> description?.let { "加载技能: $it" } ?: "加载技能"
        "slashcommand", "slash_command" -> "执行斜杠命令"
        "command_status" -> "查看命令状态"
        "send_command_input" -> "发送命令输入"
        "bashoutput", "bash_output" -> "读取后台输出"
        "killshell", "kill_shell" -> "终止后台终端"
        "view_image", "imageview" -> "查看图片"
        else -> "内置工具: $rawToolName"
    }

    val inputSections = if (rawInput != null) extractGenericSections(rawInput) else emptyList()
    val outputSections = if (rawOutput != null) extractGenericSections(rawOutput) else emptyList()
    val firstMeta = inputSections.filterIsInstance<ToolDetailSection.Metadata>().firstOrNull()?.items?.firstOrNull()

    return FormattedServerTool(
        category = ServerToolCategory.BUILTIN_TOOL,
        title = title,
        subtitle = description ?: firstMeta?.let { "${it.first}: ${it.second}" },
        statusText = buildStatusSummary(isLoading = isLoading, isError = isFailed, durationMs = durationMs),
        stepDurationText = stepDurationText,
        isError = isFailed,
        inputSections = inputSections,
        outputSections = outputSections,
    )
}

private fun formatGenericTool(
    rawToolName: String,
    input: JsonElement?,
    output: JsonElement?,
    durationMs: String?,
    stepDurationText: String?,
    isFailed: Boolean,
    isLoading: Boolean,
): FormattedServerTool {
    val inputSections = if (input != null) extractGenericSections(input) else emptyList()
    val outputSections = if (output != null) extractGenericSections(output) else emptyList()
    val firstCode = inputSections.filterIsInstance<ToolDetailSection.Code>().firstOrNull()
        ?: outputSections.filterIsInstance<ToolDetailSection.Code>().firstOrNull()
    val firstMeta = inputSections.filterIsInstance<ToolDetailSection.Metadata>().firstOrNull()?.items?.firstOrNull()

    return FormattedServerTool(
        category = ServerToolCategory.GENERIC,
        title = rawToolName.ifBlank { "工具调用" },
        subtitle = firstMeta?.let { "${it.first}: ${it.second}" }
            ?: firstCode?.code?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim(),
        statusText = buildStatusSummary(isLoading = isLoading, isError = isFailed, durationMs = durationMs),
        stepDurationText = stepDurationText,
        isError = isFailed,
        inputSections = inputSections,
        outputSections = outputSections,
    )
}

private fun extractGenericSections(
    element: JsonElement,
    defaultLanguage: String = "bash",
): List<ToolDetailSection> {
    return when (element) {
        is JsonNull -> emptyList()
        is JsonPrimitive -> {
            val text = element.contentOrNull.orEmpty()
            if (text.isBlank()) emptyList()
            else {
                val lang = inferCodeLanguage(null, null, text, defaultLanguage)
                if (lang != "plaintext" || text.contains('\n')) {
                    listOf(ToolDetailSection.Code(code = text, language = lang))
                } else {
                    listOf(ToolDetailSection.Text(text = text))
                }
            }
        }
        is JsonObject -> extractRemainingObject(element, excludedKeys = emptySet(), defaultLanguage = defaultLanguage)
        is JsonArray -> {
            if (element.isEmpty()) emptyList()
            else if (element.all { it is JsonPrimitive }) {
                listOf(ToolDetailSection.Chips(items = element.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull }))
            } else {
                listOf(ToolDetailSection.Code(code = JsonInstantPretty.encodeToString(element), language = "json"))
            }
        }
    }
}

private fun extractRemainingObject(
    obj: JsonObject,
    excludedKeys: Set<String>,
    defaultLanguage: String = "bash",
): List<ToolDetailSection> {
    val remaining = obj.filterKeys { it !in excludedKeys }
    if (remaining.isEmpty()) return emptyList()

    val metadata = mutableListOf<Pair<String, String>>()
    val sections = mutableListOf<ToolDetailSection>()
    val nested = mutableMapOf<String, JsonElement>()

    for ((key, value) in remaining) {
        when (value) {
            is JsonNull -> Unit
            is JsonPrimitive -> {
                val content = value.contentOrNull ?: value.toString()
                if (content.isBlank()) continue
                if (content.contains('\n') || key.lowercase() in CODE_LIKE_KEYS || content.length > 120) {
                    val pathHint = obj.firstString("path", "file_path", "filename", "TargetFile")
                    val lang = when {
                        key.lowercase() in setOf("diff", "patch") -> "diff"
                        key.lowercase() in setOf("command", "cmd", "script") -> "bash"
                        key.lowercase() == "sql" -> "sql"
                        else -> inferCodeLanguage(null, pathHint, content, defaultLanguage)
                    }
                    sections += ToolDetailSection.Code(label = key, code = content, language = lang)
                } else {
                    metadata += key to content
                }
            }
            is JsonArray -> {
                if (value.isNotEmpty() && value.all { it is JsonPrimitive }) {
                    sections += ToolDetailSection.Chips(
                        label = key,
                        items = value.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull },
                    )
                } else if (value.isNotEmpty()) {
                    nested[key] = value
                }
            }
            is JsonObject -> if (value.isNotEmpty()) nested[key] = value
        }
    }

    return buildList {
        if (metadata.isNotEmpty()) add(ToolDetailSection.Metadata(metadata))
        addAll(sections)
        if (nested.isNotEmpty()) {
            add(ToolDetailSection.Code(code = JsonInstantPretty.encodeToString(JsonObject(nested)), language = "json"))
        }
    }
}

private fun extractReadTargetFromActions(inputObj: JsonObject?, outputObj: JsonObject?): String? {
    val actions = (inputObj?.get("commandActions") as? JsonArray)
        ?: (outputObj?.get("commandActions") as? JsonArray)
        ?: (inputObj?.get("locations") as? JsonArray)
        ?: return null
    for (el in actions) {
        val obj = el as? JsonObject ?: continue
        val type = obj.firstString("type", "action", "kind")?.lowercase()
        if (type == null || type == "read" || type == "read_file") {
            val path = obj.firstString("path", "file_path", "file")
            if (!path.isNullOrBlank()) return path
        }
    }
    return null
}

/**
 * 轻量级 Bash 读文件命令启发式提取：
 * 支持 `cat src/app.ts`、`head -n 50 src/app.ts`、`sed -n '1,80p' src/app.ts`、`nl -ba src/app.ts`、
 * `cat src/app.ts | head -n 50` 等常见只读命令，避免包含重定向写入的命令。
 */
internal fun extractReadPathFromBashCommand(command: String?): String? {
    val raw = command?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (raw.contains('\n') || raw.contains('>') || raw.contains("<<")) return null

    // 剥离前置 cd <dir> &&
    val afterCd = raw.replace(Regex("""^cd\s+(?:"[^"]+"|'[^']+'|\S+)\s*&&\s*"""), "")
    // 对管道取第一段，若存在非只读分号/&&串联则放弃
    val firstSegment = afterCd.split('|').first().trim()
    if (firstSegment.contains("&&") || firstSegment.contains(";")) return null

    val tokens = tokenizeShellWords(firstSegment)
    if (tokens.isEmpty()) return null

    val cmd = tokens.first().substringAfterLast('/')
    if (cmd !in setOf("cat", "head", "tail", "sed", "nl", "bat", "less", "more")) return null

    var i = 1
    var sawSedQuietFlag = false
    var skippedSedScript = false
    val candidates = mutableListOf<String>()

    while (i < tokens.size) {
        val tok = tokens[i]
        when {
            tok == "--" -> {
                candidates.addAll(tokens.drop(i + 1))
                break
            }
            tok.startsWith("-") -> {
                if (cmd == "sed" && (tok == "-n" || tok.contains('n'))) {
                    sawSedQuietFlag = true
                }
                // 跳过带参数的选项如 -n 50, -c 100, -e '1,5p'
                if (tok in setOf("-n", "-c", "-e", "-f", "-b", "-w", "-s") && i + 1 < tokens.size) {
                    if (tok == "-e") skippedSedScript = true
                    i += 2
                    continue
                }
            }
            cmd == "sed" && !skippedSedScript && Regex("""^['"]?\d+(?:,\d+)?p['"]?$""").matches(tok) -> {
                skippedSedScript = true
            }
            else -> candidates += tok
        }
        i++
    }

    if (cmd == "sed" && !sawSedQuietFlag && !skippedSedScript) return null
    val rawPath = candidates.singleOrNull() ?: return null
    val cleaned = rawPath.trim('"', '\'').replace(Regex(""":\d+(?:-\d+)?$"""), "")
    if (cleaned.isBlank() || cleaned.startsWith("-")) return null
    return cleaned
}

private fun tokenizeShellWords(command: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    for (ch in command) {
        when {
            quote != null -> {
                if (ch == quote) quote = null else current.append(ch)
            }
            ch == '"' || ch == '\'' -> quote = ch
            ch.isWhitespace() -> {
                if (current.isNotEmpty()) {
                    result += current.toString()
                    current.clear()
                }
            }
            else -> current.append(ch)
        }
    }
    if (current.isNotEmpty()) result += current.toString()
    return result
}

private fun stripLineNumbersForDetection(text: String?): String? {
    val raw = text ?: return null
    val stripped = raw.lineSequence()
        .take(30)
        .joinToString("\n") { line ->
            line.replace(Regex("""^\s*\d+[\t|:→]\s?"""), "")
        }
    return stripped.takeIf { it.isNotBlank() }
}

private fun extractMcpContentText(element: JsonElement?): String? {
    return when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> {
            val contentArray = element["content"] as? JsonArray
            if (contentArray != null) {
                extractMcpContentText(contentArray)
            } else {
                element.firstString("text", "output", "message")
            }
        }
        is JsonArray -> {
            val texts = element.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull
                    is JsonObject -> item.firstString("text", "content")
                    else -> null
                }
            }
            texts.takeIf { it.isNotEmpty() }?.joinToString("\n")
        }
        else -> null
    }
}

private fun parseMcpNames(rawToolName: String): Pair<String?, String?> {
    val trimmed = rawToolName.trim()
    if (trimmed.startsWith("mcp__", ignoreCase = true)) {
        val rest = trimmed.substring(5)
        val sepIndex = rest.indexOf("__")
        return if (sepIndex > 0) {
            rest.substring(0, sepIndex) to rest.substring(sepIndex + 2).ifBlank { null }
        } else {
            null to rest.ifBlank { null }
        }
    }
    if (trimmed.startsWith("mcp:", ignoreCase = true) || trimmed.startsWith("mcp/", ignoreCase = true)) {
        val rest = trimmed.substring(4)
        val parts = rest.split(':', '/', limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else null to rest.ifBlank { null }
    }
    return null to null
}

private fun isMcpTool(rawToolName: String, normalizedKey: String, inputObj: JsonObject?): Boolean {
    if (normalizedKey in MCP_TOOLS) return true
    val lower = rawToolName.trim().lowercase()
    if (lower.startsWith("mcp__") || lower.startsWith("mcp:") || lower.startsWith("mcp/")) return true
    if (inputObj != null &&
        inputObj.firstString("server", "server_name", "mcp_server") != null &&
        inputObj.firstString("tool", "tool_name") != null
    ) {
        return true
    }
    return false
}

private fun extractChangeKind(kindEl: JsonElement?): String? = when (kindEl) {
    is JsonPrimitive -> kindEl.contentOrNull?.lowercase()
    is JsonObject -> kindEl.firstString("type", "kind")?.lowercase()
    else -> null
}

private fun detectPatchChangeKinds(diff: String?): List<String> {
    if (diff.isNullOrBlank()) return emptyList()
    val kinds = mutableListOf<String>()
    for (line in diff.lineSequence()) {
        when {
            line.startsWith("*** Add File:") -> kinds += "add"
            line.startsWith("*** Delete File:") -> kinds += "delete"
            line.startsWith("*** Update File:") -> kinds += "update"
        }
    }
    return kinds
}

/**
 * 若元素是包含 JSON 对象/数组的字符串原语（Antigravity 部分工具输出会序列化为 JSON 字符串），则自动解包。
 */
internal fun unwrapJsonElement(element: JsonElement?): JsonElement? {
    if (element == null || element is JsonNull) return null
    if (element is JsonPrimitive && element.isString) {
        val raw = element.content.trim()
        if ((raw.startsWith("{") && raw.endsWith("}")) || (raw.startsWith("[") && raw.endsWith("]"))) {
            runCatching { JsonInstant.parseToJsonElement(raw) }.getOrNull()?.let { return it }
        }
    }
    return element
}

/**
 * 推断代码高亮语言，支持显式语言名、文件扩展名（含 .ts / .tsx / .java / .kt / .kts）以及代码内容启发式识别。
 */
fun inferCodeLanguage(
    explicitLang: String? = null,
    path: String? = null,
    code: String? = null,
    defaultLanguage: String = "plaintext",
): String {
    normalizeLanguageName(explicitLang)?.let { return it }
    languageFromPath(path)?.let { return it }
    detectLanguageFromCode(code)?.let { return it }
    return defaultLanguage
}

private fun normalizeLanguageName(lang: String?): String? {
    val clean = lang?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    return when (clean) {
        "kt", "kts", "kotlin" -> "kotlin"
        "java" -> "java"
        "py", "python", "python3" -> "python"
        "sh", "bash", "zsh", "shell" -> "bash"
        "js", "javascript", "mjs", "cjs" -> "javascript"
        "ts", "typescript", "tsx" -> "typescript"
        "c" -> "c"
        "cpp", "c++", "cc", "cxx" -> "cpp"
        "cs", "csharp", "c#" -> "csharp"
        "go", "golang" -> "go"
        "rs", "rust" -> "rust"
        "rb", "ruby" -> "ruby"
        "php" -> "php"
        "swift" -> "swift"
        "dart" -> "dart"
        "sql" -> "sql"
        "json" -> "json"
        "xml" -> "xml"
        "html", "htm" -> "html"
        "css" -> "css"
        "yaml", "yml" -> "yaml"
        "md", "markdown" -> "markdown"
        "diff", "patch" -> "diff"
        "dockerfile", "docker" -> "dockerfile"
        "powershell", "ps1" -> "powershell"
        "lua" -> "lua"
        "ini", "toml" -> "ini"
        "properties" -> "properties"
        "plaintext", "text", "txt" -> "plaintext"
        else -> clean
    }
}

private fun languageFromPath(path: String?): String? {
    val fileName = path?.fileNameOfPath() ?: return null
    if (fileName.equals("Dockerfile", ignoreCase = true)) return "dockerfile"
    if (fileName.equals("CMakeLists.txt", ignoreCase = true)) return "cmake"
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext.isBlank() || ext == fileName.lowercase()) return null
    return when (ext) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "js", "mjs", "cjs", "jsx" -> "javascript"
        "ts", "tsx", "mts", "cts" -> "typescript"
        "py" -> "python"
        "rb" -> "ruby"
        "go" -> "go"
        "rs" -> "rust"
        "c", "h" -> "c"
        "cpp", "cc", "cxx", "hpp", "hxx" -> "cpp"
        "cs" -> "csharp"
        "swift" -> "swift"
        "dart" -> "dart"
        "php" -> "php"
        "sh", "bash", "zsh" -> "bash"
        "ps1" -> "powershell"
        "json" -> "json"
        "xml" -> "xml"
        "html", "htm" -> "html"
        "css", "scss" -> "css"
        "yaml", "yml" -> "yaml"
        "toml", "ini", "cfg" -> "ini"
        "properties" -> "properties"
        "md", "markdown" -> "markdown"
        "sql" -> "sql"
        "diff", "patch" -> "diff"
        "lua" -> "lua"
        "gradle" -> "kotlin"
        else -> null
    }
}

private fun detectLanguageFromCode(code: String?): String? {
    val sample = code?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val firstLine = sample.lineSequence().firstOrNull()?.trim().orEmpty()
    if (firstLine.startsWith("#!/bin/bash") || firstLine.startsWith("#!/bin/sh") || firstLine.startsWith("#!/usr/bin/env bash")) {
        return "bash"
    }
    if (firstLine.startsWith("#!/usr/bin/env python") || firstLine.startsWith("#!/usr/bin/python")) {
        return "python"
    }
    if (sample.startsWith("diff --git ") || (sample.contains("\n--- ") && sample.contains("\n+++ ") && sample.contains("\n@@ "))) {
        return "diff"
    }
    if ((sample.startsWith("{") && sample.endsWith("}")) || (sample.startsWith("[") && sample.endsWith("]"))) {
        if (runCatching { JsonInstant.parseToJsonElement(sample) }.isSuccess) return "json"
    }
    if (
        Regex("""\b(data\s+class|sealed\s+class|sealed\s+interface|companion\s+object|fun\s+\w+\s*\()""").containsMatchIn(sample) ||
        sample.contains("import kotlinx.") ||
        sample.contains("import androidx.") ||
        (sample.contains("val ") && sample.contains("fun "))
    ) {
        return "kotlin"
    }
    if (
        Regex("""\b(public\s+class|private\s+class|protected\s+class|public\s+static\s+void\s+main|System\.out\.print)""").containsMatchIn(sample) ||
        sample.contains("import java.") ||
        sample.contains("import javax.") ||
        Regex("""^package\s+[\w.]+;""", RegexOption.MULTILINE).containsMatchIn(sample)
    ) {
        return "java"
    }
    if (
        Regex("""\b(interface\s+\w+\s*\{|type\s+\w+\s*=|export\s+(?:async\s+)?function|import\s+.*\s+from\s+['"])""").containsMatchIn(sample)
    ) {
        return "typescript"
    }
    return null
}

private fun extractDurationMs(
    outputObj: JsonObject?,
    inputObj: JsonObject?,
    metadata: JsonObject?,
): String? =
    outputObj?.firstString("durationMs", "duration_ms", "elapsed_ms", "execution_time_ms")
        ?: inputObj?.firstString("durationMs", "duration_ms", "elapsed_ms")
        ?: metadata?.firstString("durationMs", "duration_ms", "elapsed_ms")

internal fun formatStepDuration(durationMsRaw: String?): String? {
    val ms = durationMsRaw?.trim()?.toDoubleOrNull()?.roundToLong() ?: return null
    if (ms <= 0L) return null
    return if (ms < 1000L) {
        "${ms}ms"
    } else {
        ms.milliseconds.toString(DurationUnit.SECONDS, 1)
    }
}

private fun buildStatusSummary(
    isLoading: Boolean,
    isError: Boolean,
    exitCode: String? = null,
    durationMs: String? = null,
    outcome: String? = null,
): String? {
    if (isLoading) return "执行中"
    val parts = buildList {
        if (exitCode != null) {
            add("退出码 $exitCode")
        } else if (outcome != null) {
            add(formatOutcomeLabel(outcome))
        } else if (isError) {
            add("失败")
        }
        durationMs?.let { add("${it} ms") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun formatOutcomeLabel(outcome: String): String = when (outcome.uppercase()) {
    "OUTCOME_OK", "OK", "SUCCESS", "COMPLETED" -> "成功"
    "OUTCOME_FAILED", "FAILED", "ERROR" -> "失败"
    "OUTCOME_DEADLINE_EXCEEDED", "TIMEOUT" -> "超时"
    else -> outcome
}

private fun String.fileNameOfPath(): String =
    substringAfterLast('/').substringAfterLast('\\')

private fun normalizeToolKey(name: String): String = name.trim().lowercase()

private fun JsonObject.firstString(vararg keys: String): String? {
    for (key in keys) {
        val value = this[key]?.jsonPrimitiveOrNull?.contentOrNull
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun JsonObject.bool(key: String): Boolean? =
    this[key]?.jsonPrimitiveOrNull?.booleanOrNull

private val COMMAND_TOOLS = setOf(
    "commandexecution", "command_execution", "bash", "powershell", "shell", "sh",
    "run_command", "execute_command", "terminal", "local_shell", "exec_command",
)

private val CODE_EXEC_TOOLS = setOf(
    "code_execution", "execute_code", "run_code", "python",
)

private val FILE_READ_TOOLS = setOf(
    "read_file", "file_read", "view_file", "view_file_outline", "view_code_item",
    "cat", "read", "get_file", "open_file",
)

private val FILE_WRITE_TOOLS = setOf(
    "write_file", "file_write", "create_file", "write", "save_file", "write_to_file",
)

private val FILE_DELETE_TOOLS = setOf(
    "delete_file", "remove_file", "file_delete", "unlink", "rm", "delete",
)

private val FILE_EDIT_TOOLS = setOf(
    "edit_file", "file_edit", "edit", "multiedit", "multi_edit",
    "replace_file_content", "multi_replace_file_content",
    "str_replace", "str_replace_editor", "notebook_edit", "notebookedit",
    "patch", "apply_patch", "file_change", "filechange", "update_file",
)

private val SEARCH_FILES_TOOLS = setOf(
    "grep", "grep_search", "search_files", "ripgrep", "rg", "find_files",
    "glob", "list_dir", "list_directory", "ls", "find_by_name", "code_search", "file_search",
)

private val WEB_SEARCH_TOOLS = setOf(
    "google_search", "web_search", "search_web", "websearch",
)

private val URL_FETCH_TOOLS = setOf(
    "url_context", "web_fetch", "webfetch", "fetch_url", "browse", "read_url", "read_url_content",
)

private val MCP_TOOLS = setOf(
    "mcptoolcall", "mcp_tool_call", "mcp_server", "mcp_tool_use", "mcp_tool_result",
)

private val BUILTIN_TOOLS = setOf(
    "agent", "task", "todowrite", "todo_write", "todoread", "todo_read",
    "update_plan", "enterplanmode", "enter_plan_mode", "exitplanmode", "exit_plan_mode",
    "askuserquestion", "ask_user_question", "skill", "slashcommand", "slash_command",
    "command_status", "send_command_input", "bashoutput", "bash_output", "killshell", "kill_shell",
    "view_image", "imageview", "collabtoolcall", "collab_tool_call",
)

private val CODE_LIKE_KEYS = setOf(
    "code", "command", "cmd", "script", "content", "output", "stdout", "stderr",
    "diff", "patch", "text", "sql", "query", "source", "program",
)
