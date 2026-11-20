package me.rerere.rikkahub.data.examdocs

import androidx.compose.runtime.Immutable
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownDocument
import me.rerere.bettertext.markdown.parseMarkdown
import me.rerere.bettertext.markdown.plainText
import okhttp3.HttpUrl.Companion.toHttpUrl

@Immutable
data class ExamDocsDocument(
    val blocks: List<ExamDocsSegment>,
    val headings: List<DocHeading>,
    val sourceUrl: String,
    val sourceBytes: Int,
)

@Immutable
data class DocHeading(val level: Int, val title: String, val blockIndex: Int, val anchor: String)

@Immutable
sealed interface ExamDocsSegment {
    data class Markdown(val document: MarkdownDocument) : ExamDocsSegment
    data class CustomBlock(
        val type: String,
        val title: String?,
        val blocks: List<ExamDocsSegment>,
        val attrs: Map<String, String> = emptyMap(),
    ) : ExamDocsSegment
}

private val legacyHeader = Regex("""^#\s+.+?\s+\(/docs[^)]*\)\s*$""")
private val containerOpen = Regex("""^ {0,3}:::[ \t]*([A-Za-z0-9_-]+)(?:[ \t]+(.*))?$""")
private val containerClose = Regex("""^ {0,3}:::[ \t]*$""")
private val codeFence = Regex("""^ {0,3}(`{3,}|~{3,})(.*)$""")
private val anchorPunctuation = Regex("""[^\p{L}\p{M}\p{N}_\s-]""")
private val anchorWhitespace = Regex("""\s+""")
private val attrBlockRegex = Regex("""^\{([^}]*)\}(?:\s+(.*))?$""")
private val attrPairRegex = Regex("""([A-Za-z0-9_-]+)="((?:[^"\\]|\\.)*)"""")

private fun parseDirectiveHeader(rawRest: String?): Pair<Map<String, String>, String?> {
    val trimmed = rawRest?.trim().orEmpty()
    if (trimmed.isEmpty()) return emptyMap<String, String>() to null
    val blockMatch = attrBlockRegex.matchEntire(trimmed) ?: return emptyMap<String, String>() to trimmed
    val attrs = buildMap {
        for (match in attrPairRegex.findAll(blockMatch.groupValues[1])) {
            put(match.groupValues[1], match.groupValues[2].replace("\\\"", "\""))
        }
    }
    val title = blockMatch.groupValues[2].trim().ifEmpty { null }
    return attrs to title
}

fun prepareExamDocsDocument(raw: String, sourceUrl: String, legacy: Boolean): ExamDocsDocument {
    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').removePrefix("\uFEFF")
    val stripped = if (legacy && legacyHeader.matches(normalized.substringBefore('\n'))) {
        normalized.substringAfter('\n', "").trimStart('\n')
    } else {
        normalized
    }
    val markdown = normalizeExamDocsMarkdown(stripped)
    val blocks = parseExamDocsSegments(markdown)
    val anchors = mutableSetOf<String>()
    val headings = blocks.mapIndexedNotNull { index, segment ->
        val heading = (segment as? ExamDocsSegment.Markdown)?.document?.blocks?.singleOrNull() as? MarkdownBlock.Heading
            ?: return@mapIndexedNotNull null
        val title = heading.content.plainText().trim().ifEmpty { return@mapIndexedNotNull null }
        val base = title.lowercase().replace(anchorPunctuation, "").replace(anchorWhitespace, "-")
        var anchor = base
        var suffix = 1
        while (!anchors.add(anchor)) anchor = "$base-${suffix++}"
        DocHeading(heading.level, title, index, anchor)
    }
    return ExamDocsDocument(blocks, headings, sourceUrl, (raw.length * 2).coerceAtLeast(1))
}

internal fun parseExamDocsSegments(markdown: String): List<ExamDocsSegment> {
    val lines = markdown.lines()
    val matchingEnds = mutableMapOf<Int, Int>()
    val open = ArrayDeque<Int>()
    var fence: String? = null

    // Find balanced containers before parsing any Markdown. Incomplete containers
    // stay in the text and neither code fences nor their contents become directives.
    lines.forEachIndexed { index, line ->
        val match = codeFence.matchEntire(line)
        val marker = match?.groupValues?.get(1)
        val current = fence
        when {
            current != null -> {
                if (marker != null && marker.first() == current.first() &&
                    marker.length >= current.length && match?.groupValues?.get(2).isNullOrBlank()
                ) fence = null
            }
            marker != null -> fence = marker
            containerOpen.matches(line) -> open.addLast(index)
            containerClose.matches(line) && open.isNotEmpty() -> matchingEnds[open.removeLast()] = index
        }
    }

    fun parseRange(start: Int, end: Int, depth: Int): List<ExamDocsSegment> = buildList {
        fun addMarkdown(from: Int, until: Int) {
            if (from >= until) return
            val text = lines.subList(from, until).joinToString("\n")
            if (text.isBlank()) return
            for (block in parseMarkdown(text).blocks) {
                add(ExamDocsSegment.Markdown(MarkdownDocument("", listOf(block))))
            }
        }

        var from = start
        var index = start
        while (index < end && depth < 16) {
            val close = matchingEnds[index]
            if (close == null || close >= end) {
                index++
                continue
            }
            val directive = requireNotNull(containerOpen.matchEntire(lines[index]))
            val type = directive.groupValues[1].lowercase()
            val (attrs, title) = parseDirectiveHeader(directive.groupValues[2])
            val children = parseRange(index + 1, close, depth + 1)
            addMarkdown(from, index)
            add(ExamDocsSegment.CustomBlock(type, title, children, attrs))
            index = close + 1
            from = index
        }
        addMarkdown(from, end)
    }

    return parseRange(0, lines.size, 0)
}

data class ExamDocsLink(val path: String, val fragment: String?)

fun getExamDocsLink(baseUrl: String, currentPageUrl: String, rawUrl: String): ExamDocsLink? {
    val base = baseUrl.toHttpUrl()
    val target = base.resolve(resolveExamDocsUrl(baseUrl, currentPageUrl, rawUrl)) ?: return null
    if (target.scheme != base.scheme || target.host != base.host || target.port != base.port) return null
    val path = target.encodedPath.removePrefix(base.encodedPath.trimEnd('/'))
    val normalized = ExamDocsRepository.normalizeDocPath(path)
    if (normalized != "/docs" && !normalized.startsWith("/docs/")) return null
    return ExamDocsLink(normalized, target.fragment?.takeIf(String::isNotBlank))
}

fun resolveExamDocsUrl(baseUrl: String, currentPageUrl: String, rawUrl: String): String {
    val link = rawUrl.trim().removePrefix("<").removeSuffix(">").trim()
    val cleanBase = baseUrl.trimEnd('/')
    val base = "$cleanBase$currentPageUrl".toHttpUrl()
    if (link.startsWith('/') && !link.startsWith("//")) {
        return base.resolve("$cleanBase$link")?.toString() ?: link
    }
    return base.resolve(link)?.toString() ?: link
}

private val htmlImgTagRegex = Regex("""<(?:img|Image)\b([^>]*?)/?>""", RegexOption.IGNORE_CASE)
private val htmlAttrSrcRegex = Regex("""\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
private val htmlAttrAltRegex = Regex("""\balt\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
private val displayBracketMathRegex = Regex("""\\\[([\s\S]*?)\\\]""")
private val inlineParenMathRegex = Regex("""\\\(([\s\S]*?)\\\)""")

internal fun normalizeExamDocsMarkdown(markdown: String): String {
    val lines = markdown.lines()
    val out = StringBuilder(markdown.length + 32)
    var fence: String? = null
    lines.forEachIndexed { idx, line ->
        val match = codeFence.matchEntire(line)
        val marker = match?.groupValues?.get(1)
        val current = fence
        val transformed = when {
            current != null -> {
                if (marker != null && marker.first() == current.first() &&
                    marker.length >= current.length && match?.groupValues?.get(2).isNullOrBlank()
                ) {
                    fence = null
                }
                line
            }
            marker != null -> {
                fence = marker
                line
            }
            else -> normalizeOutsideInlineCode(line)
        }
        if (idx > 0) out.append('\n')
        out.append(transformed)
    }
    return out.toString()
}

private fun normalizeOutsideInlineCode(line: String): String {
    if (!line.contains('<') && !line.contains('\\')) return line
    val result = StringBuilder(line.length)
    var i = 0
    while (i < line.length) {
        if (line[i] == '`') {
            var ticks = 0
            while (i + ticks < line.length && line[i + ticks] == '`') ticks++
            val closeIdx = line.indexOf("`".repeat(ticks), i + ticks)
            if (closeIdx >= 0) {
                result.append(line, i, closeIdx + ticks)
                i = closeIdx + ticks
                continue
            }
        }
        val nextTick = line.indexOf('`', i).let { if (it < 0) line.length else it }
        var chunk = line.substring(i, nextTick)
        if (chunk.contains('<')) {
            chunk = htmlImgTagRegex.replace(chunk) { m ->
                val attrs = m.groupValues[1]
                val srcMatch = htmlAttrSrcRegex.find(attrs)
                val src = srcMatch?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim()
                if (src.isNullOrEmpty()) {
                    m.value
                } else {
                    val altMatch = htmlAttrAltRegex.find(attrs)
                    val alt = altMatch?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim().orEmpty()
                    "![$alt](<$src>)"
                }
            }
        }
        if (chunk.contains("\\(") || chunk.contains("\\[")) {
            chunk = displayBracketMathRegex.replace(chunk) { "\n$$\n${it.groupValues[1].trim()}\n$$\n" }
            chunk = inlineParenMathRegex.replace(chunk) { "$${it.groupValues[1].trim()}$" }
        }
        result.append(chunk)
        i = nextTick
    }
    return result.toString()
}
