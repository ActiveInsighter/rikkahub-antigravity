package me.rerere.rikkahub.data.workflow

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

enum class DslOutlineKind(val label: String) {
    TASK("Task"),
    EVENT("Event"),
}

data class DslOutlineItem(
    val kind: DslOutlineKind,
    val title: String,
    val line: Int,
    val offset: Int,
)

data class DslDiagnostic(
    val line: Int,
    val message: String,
    val isError: Boolean = true,
)

data class DslInspection(
    val diagnostics: List<DslDiagnostic>,
    val outline: List<DslOutlineItem>,
    val variables: List<String>,
    val taskCount: Int,
    val eventCount: Int,
) {
    val errorCount: Int get() = diagnostics.count { it.isError }
    val hasErrors: Boolean get() = errorCount > 0
}

enum class DslLoopTemplate(val label: String, val description: String) {
    RANGE("数字循环", "i 依次取 1、2、3，两端都包含"),
    LIST("列表循环", "逐项取值，%index% 是第几项"),
    OUTLINE("章节 × 小节", "外层循环章，内层循环本章小节"),
}

data class StartPagePreset(
    val label: String,
    val url: String,
)

val DEFAULT_START_PAGE_PRESETS = listOf(
    StartPagePreset("默认起始页变量", "<%start_url%>"),
    StartPagePreset("ChatGPT", "<https://chatgpt.com/>"),
    StartPagePreset("Gemini", "<https://gemini.google.com/app>"),
    StartPagePreset("Claude", "<https://claude.ai/new>"),
    StartPagePreset("DeepSeek", "<https://chat.deepseek.com/>"),
    StartPagePreset("Kimi", "<https://www.kimi.com/>"),
    StartPagePreset("Perplexity", "<https://www.perplexity.ai/>"),
)

sealed interface DslInsertResult {
    data class Success(val value: TextFieldValue) : DslInsertResult
    data class Failure(val message: String) : DslInsertResult
}

data class DslHighlightColors(
    val directive: Color,
    val variable: Color,
    val tag: Color,
    val brace: Color,
    val fence: Color,
)

class DslSyntaxVisualTransformation(
    private val colors: DslHighlightColors,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(highlightDsl(text.text, colors), OffsetMapping.Identity)
    }
}

fun highlightDsl(source: String, colors: DslHighlightColors): AnnotatedString = buildAnnotatedString {
    append(source)
    if (source.isEmpty()) return@buildAnnotatedString

    val directiveStyle = SpanStyle(color = colors.directive, fontWeight = FontWeight.SemiBold)
    val variableStyle = SpanStyle(color = colors.variable, fontWeight = FontWeight.SemiBold)
    val tagStyle = SpanStyle(color = colors.tag, fontWeight = FontWeight.Medium)
    val braceStyle = SpanStyle(color = colors.brace, fontWeight = FontWeight.Bold)
    val fenceStyle = SpanStyle(color = colors.fence)

    val directiveRegex = Regex(
        """(^|\n)[ \t]*(@(?:run|mode|maxConcurrency|task|event|act|let|var|for|end|executor)\b[^\n{]*?)(?=\s*\{|\s*$)""",
        RegexOption.IGNORE_CASE,
    )
    for (match in directiveRegex.findAll(source)) {
        val group = match.groups[2] ?: continue
        val text = group.value
        val eqIdx = text.indexOf('=')
        val highlightEnd = if (eqIdx >= 0) group.range.first + eqIdx + 1 else {
            val spaceIdx = text.indexOfFirst { it == ' ' || it == '\t' }
            if (spaceIdx > 0) group.range.first + spaceIdx else group.range.last + 1
        }
        addStyle(directiveStyle, group.range.first, highlightEnd)
    }

    val varRegex = Regex("""%[A-Za-z_][A-Za-z0-9_]*%""")
    for (match in varRegex.findAll(source)) {
        addStyle(variableStyle, match.range.first, match.range.last + 1)
    }

    val tagRegex = Regex("""<(?:sleep\(\s*\d+(?:\.\d+)?\s*\)|https?://[^>\n]+|%[A-Za-z_][A-Za-z0-9_]*%)>""")
    for (match in tagRegex.findAll(source)) {
        addStyle(tagStyle, match.range.first, match.range.last + 1)
    }

    val fenceRegex = Regex("""(^|\n)[ \t]*(?:```|~~~)[^\n]*""")
    for (match in fenceRegex.findAll(source)) {
        addStyle(fenceStyle, match.range.first, match.range.last + 1)
    }

    for (i in source.indices) {
        val ch = source[i]
        if (ch == '{' || ch == '}') {
            addStyle(braceStyle, i, i + 1)
        }
    }
}

private data class BlockFrame(
    val kind: String,
    val line: Int,
    val openOffset: Int,
)

fun inspectWorkflowDsl(source: String): DslInspection {
    val diagnostics = mutableListOf<DslDiagnostic>()
    val outline = mutableListOf<DslOutlineItem>()
    val variables = linkedSetOf("index")

    if (source.trim().isEmpty()) {
        diagnostics.add(DslDiagnostic(1, "工作流定义不能为空。"))
        return DslInspection(diagnostics, outline, variables.toList(), 0, 0)
    }

    val lines = source.split('\n')
    val stack = mutableListOf<BlockFrame>()
    var inFence = false
    var fenceLine = 1
    var taskCount = 0
    var eventCount = 0
    var currentOffset = 0

    val taskHeaderRegex = Regex("""^\s*@task(?:\s+([^{]*?))?\s*\{?\s*$""", RegexOption.IGNORE_CASE)
    val eventHeaderRegex = Regex("""^\s*@event(?:\s+([^{]*?))?\s*\{?\s*$""", RegexOption.IGNORE_CASE)
    val forHeaderRegex = Regex("""^\s*@for\s+([A-Za-z_][A-Za-z0-9_]*(?:\s*,\s*[A-Za-z_][A-Za-z0-9_]*)*)\s+in\b""", RegexOption.IGNORE_CASE)
    val letRegex = Regex("""^\s*@(?:let|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=""", RegexOption.IGNORE_CASE)

    lines.forEachIndexed { idx, rawLine ->
        val lineNum = idx + 1
        val line = rawLine.trimEnd('\r')
        val trimmed = line.trim()

        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            if (!inFence) fenceLine = lineNum
            inFence = !inFence
            currentOffset += rawLine.length + 1
            return@forEachIndexed
        }

        if (!inFence) {
            letRegex.find(trimmed)?.let { m ->
                variables.add(m.groupValues[1])
            }
            forHeaderRegex.find(trimmed)?.let { m ->
                m.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                    variables.add(it)
                }
            }

            if (trimmed.startsWith("@task", ignoreCase = true) && !trimmed.startsWith("@task=", ignoreCase = true)) {
                taskCount++
                val title = taskHeaderRegex.matchEntire(trimmed)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    .ifEmpty { "Task $taskCount" }
                outline.add(DslOutlineItem(DslOutlineKind.TASK, title, lineNum, currentOffset))
            } else if (trimmed.startsWith("@event", ignoreCase = true) && !trimmed.startsWith("@event=", ignoreCase = true)) {
                eventCount++
                val title = eventHeaderRegex.matchEntire(trimmed)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    .ifEmpty { "Event $eventCount" }
                outline.add(DslOutlineItem(DslOutlineKind.EVENT, title, lineNum, currentOffset))
            }

            // Track braces on this line
            for (ch in line) {
                if (ch == '{') {
                    val kind = when {
                        trimmed.startsWith("@task", ignoreCase = true) -> "task"
                        trimmed.startsWith("@event", ignoreCase = true) -> "event"
                        trimmed.startsWith("@act", ignoreCase = true) -> "act"
                        trimmed.startsWith("@for", ignoreCase = true) -> "for"
                        else -> "block"
                    }
                    stack.add(BlockFrame(kind, lineNum, currentOffset))
                    val forDepth = stack.count { it.kind == "for" }
                    if (kind == "for" && forDepth > 3) {
                        diagnostics.add(DslDiagnostic(lineNum, "循环最多支持嵌套 3 层。"))
                    }
                } else if (ch == '}') {
                    if (stack.isEmpty()) {
                        diagnostics.add(DslDiagnostic(lineNum, "多余的闭合大括号 `}`。"))
                    } else {
                        stack.removeAt(stack.lastIndex)
                    }
                }
            }
        }
        currentOffset += rawLine.length + 1
    }

    if (inFence) {
        diagnostics.add(DslDiagnostic(fenceLine, "代码块围栏 ``` 未闭合。"))
    }
    for (unclosed in stack) {
        diagnostics.add(DslDiagnostic(unclosed.line, "第 ${unclosed.line} 行的 `{` 未闭合。"))
    }
    if (taskCount == 0) {
        diagnostics.add(DslDiagnostic(1, "Run 至少需要一个 `@task` 块。"))
    } else if (eventCount == 0) {
        diagnostics.add(DslDiagnostic(1, "Task 至少需要一个 `@event` 块。"))
    }

    return DslInspection(
        diagnostics = diagnostics,
        outline = outline,
        variables = variables.toList(),
        taskCount = taskCount,
        eventCount = eventCount,
    )
}

fun formatWorkflowDsl(source: String): String {
    val normalized = source.replace("\r\n", "\n").replace("\r", "\n")
    val lines = normalized.split('\n')
    val out = mutableListOf<String>()
    var depth = 0
    var inFence = false
    var listDepth: Int? = null
    val indentUnit = "  "

    val fenceRegex = Regex("""^(?:```|~~~)""")
    val forHeaderRegex = Regex("""^@for\b""", RegexOption.IGNORE_CASE)

    for (raw in lines) {
        val trimmed = raw.trim()

        if (fenceRegex.containsMatchIn(trimmed)) {
            out.add(indentUnit.repeat(depth) + trimmed)
            inFence = !inFence
            continue
        }

        if (inFence) {
            out.add(raw)
            continue
        }

        if (trimmed.isEmpty()) {
            out.add("")
            continue
        }

        if (listDepth != null) {
            if (!trimmed.startsWith("]") && !trimmed.contains("{")) {
                out.add(indentUnit.repeat(listDepth + 1) + trimmed)
                continue
            }
            listDepth = null
        }

        val leadingCloses = trimmed.takeWhile { it == '}' }.length
        val opens = trimmed.count { it == '{' }
        val closes = trimmed.count { it == '}' }
        val lineDepth = (depth - leadingCloses).coerceAtLeast(0)

        out.add(indentUnit.repeat(lineDepth) + trimmed)
        depth = (depth + opens - closes).coerceAtLeast(0)
        if (forHeaderRegex.containsMatchIn(trimmed) && !trimmed.contains("{")) {
            listDepth = lineDepth
        }
    }

    return out.joinToString("\n")
}

private fun lineStartOffset(source: String, offset: Int): Int {
    val clamped = offset.coerceIn(0, source.length)
    val prevNewline = source.lastIndexOf('\n', (clamped - 1).coerceAtLeast(0))
    return if (prevNewline < 0 || clamped == 0) 0 else prevNewline + 1
}

private fun lineEndOffset(source: String, offset: Int): Int {
    val clamped = offset.coerceIn(0, source.length)
    val nextNewline = source.indexOf('\n', clamped)
    return if (nextNewline < 0) source.length else nextNewline + 1
}

private fun currentLineIndent(source: String, offset: Int): String {
    val start = lineStartOffset(source, offset)
    val sb = StringBuilder()
    var i = start
    while (i < source.length && (source[i] == ' ' || source[i] == '\t')) {
        sb.append(source[i])
        i++
    }
    return sb.toString()
}

private fun replaceRange(
    value: TextFieldValue,
    from: Int,
    to: Int,
    insert: String,
    cursorDelta: Int,
    selectionLength: Int = 0,
): DslInsertResult.Success {
    val safeFrom = from.coerceIn(0, value.text.length)
    val safeTo = to.coerceIn(safeFrom, value.text.length)
    val nextText = buildString {
        append(value.text, 0, safeFrom)
        append(insert)
        append(value.text, safeTo, value.text.length)
    }
    val cursorStart = (safeFrom + cursorDelta).coerceIn(0, nextText.length)
    val cursorEnd = (cursorStart + selectionLength).coerceIn(cursorStart, nextText.length)
    return DslInsertResult.Success(
        TextFieldValue(
            text = nextText,
            selection = TextRange(cursorStart, cursorEnd),
        )
    )
}

fun insertTextAtCursor(
    value: TextFieldValue,
    insert: String,
    cursorDelta: Int = insert.length,
    selectionLength: Int = 0,
): DslInsertResult.Success {
    val from = value.selection.min.coerceIn(0, value.text.length)
    val to = value.selection.max.coerceIn(from, value.text.length)
    return replaceRange(value, from, to, insert, cursorDelta, selectionLength)
}

fun insertTaskSnippet(value: TextFieldValue): DslInsertResult {
    val source = value.text
    val sep = when {
        source.isEmpty() -> ""
        source.endsWith("\n\n") -> ""
        source.endsWith("\n") -> "\n"
        else -> "\n\n"
    }
    val snippet = "$sep@task Task {\n  @mode=serial\n\n}\n"
    val titleOffset = sep.length + "@task ".length
    return replaceRange(
        value = value,
        from = source.length,
        to = source.length,
        insert = snippet,
        cursorDelta = titleOffset,
        selectionLength = "Task".length,
    )
}

fun insertEventSnippet(value: TextFieldValue): DslInsertResult {
    val source = value.text
    val pos = value.selection.min.coerceIn(0, source.length)
    val indent = currentLineIndent(source, pos)
    val lineStart = lineStartOffset(source, pos)
    val prefix = source.substring(lineStart, pos)
    val insertAt = if (prefix.isBlank()) pos else lineEndOffset(source, pos)
    val sep = if (insertAt > 0 && source[insertAt - 1] != '\n') "\n" else ""
    val blockIndent = if (prefix.isBlank() && indent.isNotEmpty()) indent else "  "
    val snippet = "$sep${blockIndent}@event Event {\n$blockIndent  {\n$blockIndent    \n$blockIndent  }\n$blockIndent}\n"
    val titleOffset = sep.length + blockIndent.length + "@event ".length
    return replaceRange(value, insertAt, insertAt, snippet, titleOffset, "Event".length)
}

fun insertActSnippet(value: TextFieldValue): DslInsertResult {
    val source = value.text
    val pos = value.selection.min.coerceIn(0, source.length)
    val indent = currentLineIndent(source, pos).ifEmpty { "    " }
    val lineStart = lineStartOffset(source, pos)
    val prefix = source.substring(lineStart, pos)
    val insertAt = if (prefix.isBlank()) pos else lineEndOffset(source, pos)
    val sep = if (insertAt > 0 && source[insertAt - 1] != '\n') "\n" else ""
    val snippet = "$sep${indent}@act {\n$indent  @event=\n$indent  {\n$indent    \n$indent  }\n$indent}\n"
    val cursorOffset = sep.length + indent.length + "@act {\n$indent  @event=".length
    return replaceRange(value, insertAt, insertAt, snippet, cursorOffset, 0)
}

fun insertLoopSnippet(value: TextFieldValue, template: DslLoopTemplate): DslInsertResult {
    val source = value.text
    val pos = value.selection.min.coerceIn(0, source.length)
    val indent = currentLineIndent(source, pos).ifEmpty { "  " }
    val lineStart = lineStartOffset(source, pos)
    val prefix = source.substring(lineStart, pos)
    val insertAt = if (prefix.isBlank()) pos else lineEndOffset(source, pos)
    val sep = if (insertAt > 0 && source[insertAt - 1] != '\n') "\n" else ""

    val (lines, selectToken) = when (template) {
        DslLoopTemplate.RANGE -> listOf(
            "@for i in range(1, 3) {",
            "  ",
            "}",
        ) to "i"
        DslLoopTemplate.LIST -> listOf(
            "@for item in [甲, 乙, 丙] {",
            "  ",
            "}",
        ) to "甲, 乙, 丙"
        DslLoopTemplate.OUTLINE -> listOf(
            "@for ch, title, secs in [",
            "  (1, 第一章, [小节一, 小节二])",
            "  (2, 第二章, [小节一])",
            "] {",
            "  @for sec in %secs% {",
            "    { 写第%ch%章《%title%》第%index%节：%sec% }",
            "  }",
            "}",
        ) to "1, 第一章, [小节一, 小节二]"
    }

    val body = lines.joinToString("\n") { "$indent$it" }
    val snippet = "$sep$body\n"
    val selectIdx = snippet.indexOf(selectToken)
    return replaceRange(
        value = value,
        from = insertAt,
        to = insertAt,
        insert = snippet,
        cursorDelta = if (selectIdx >= 0) selectIdx else snippet.length,
        selectionLength = if (selectIdx >= 0) selectToken.length else 0,
    )
}

fun insertVariableDeclSnippet(value: TextFieldValue): DslInsertResult {
    val source = value.text
    val pos = value.selection.min.coerceIn(0, source.length)
    val indent = currentLineIndent(source, pos).ifEmpty { "  " }
    val lineStart = lineStartOffset(source, pos)
    val prefix = source.substring(lineStart, pos)
    val insertAt = if (prefix.isBlank()) pos else lineEndOffset(source, pos)
    val sep = if (insertAt > 0 && source[insertAt - 1] != '\n') "\n" else ""
    val snippet = "$sep${indent}@let name=\n"
    val nameOffset = sep.length + indent.length + "@let ".length
    return replaceRange(value, insertAt, insertAt, snippet, nameOffset, "name".length)
}

fun insertMessageBlockSnippet(value: TextFieldValue): DslInsertResult {
    val source = value.text
    val pos = value.selection.min.coerceIn(0, source.length)
    val indent = currentLineIndent(source, pos)
    val snippet = "{\n$indent  \n$indent}"
    val cursorDelta = "{\n$indent  ".length
    return insertTextAtCursor(value, snippet, cursorDelta, 0)
}

fun insertWaitSnippet(value: TextFieldValue, seconds: Int = 3): DslInsertResult =
    insertTextAtCursor(value, "<sleep($seconds)>", "<sleep(".length, seconds.toString().length)

fun insertLinkSnippet(value: TextFieldValue, preset: String = "<>"): DslInsertResult {
    return if (preset == "<>") {
        insertTextAtCursor(value, "<>", 1, 0)
    } else {
        insertTextAtCursor(value, preset, preset.length, 0)
    }
}

fun duplicateCurrentLine(value: TextFieldValue): DslInsertResult {
    val source = value.text
    if (source.isEmpty()) return DslInsertResult.Success(value)
    val pos = value.selection.min.coerceIn(0, source.length)
    val start = lineStartOffset(source, pos)
    val end = lineEndOffset(source, pos)
    val lineText = source.substring(start, end)
    val toInsert = if (lineText.endsWith("\n")) lineText else "\n$lineText"
    return replaceRange(value, end, end, toInsert, toInsert.length, 0)
}
