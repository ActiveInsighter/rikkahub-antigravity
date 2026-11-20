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
private val attrPairPattern = """([A-Za-z0-9_-]+)="((?:[^"\\]|\\.)*)""""
private val attrPairRegex = Regex(attrPairPattern)
private val attrBlockRegex = Regex("""^\{((?:$attrPairPattern[ \t]*)*)\}(?:[ \t]+(.*))?$""")
private val escapedAttribute = Regex("""\\(["\\])""")
private val inlineCodeOrMath = Regex(
    """(\x60+)(?!\x60).*?(?<!\x60)\1(?!\x60)|(?<![\\$])\$(?!\$)((?:\\.|[^\\$])+)\$(?![$\d])""",
)
private val wideMathEnvironment = Regex("""\\begin\{(?:cases|[pbBvV]?matrix|array|aligned|gathered)\}""")
private val singleLineDisplayMath = Regex("""^([ \t]*(?:>[ \t]*)*)\$\$(.+?)\$\$[ \t]*$""")
private val htmlImgTag = Regex("""<img\b([^>]*?)/?>""", RegexOption.IGNORE_CASE)
private val htmlImgSrc = Regex("""\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
private val htmlImgAlt = Regex("""\balt\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
private val latexBracketDisplay = Regex("""\\\[(.+?)\\\]""")
private val latexParenInline = Regex("""\\\((.+?)\\\)""")

private fun normalizeMarkdownLine(line: String): String {
    if (line.startsWith('\t') || line.startsWith("    ")) return line

    var current = line
    if ("<img" in current.lowercase()) {
        current = htmlImgTag.replace(current) { match ->
            val attrs = match.groupValues[1]
            val src = htmlImgSrc.find(attrs)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim()
            if (src.isNullOrEmpty()) return@replace match.value
            val alt = htmlImgAlt.find(attrs)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim().orEmpty()
            "![$alt](<$src>)"
        }
    }

    if ("\\[" in current) {
        current = latexBracketDisplay.replace(current) { "\$\$" + it.groupValues[1].trim() + "\$\$" }
    }
    if ("\\(" in current) {
        current = latexParenInline.replace(current) { "\$" + it.groupValues[1].trim() + "\$" }
    }

    val displayMatch = singleLineDisplayMath.matchEntire(current)
    if (displayMatch != null) {
        val prefix = displayMatch.groupValues[1]
        val formula = displayMatch.groupValues[2].trim()
        if (formula.isNotEmpty()) {
            return "${prefix}\$\$${formula}\$\$"
        }
    }

    if ('$' !in current) return current
    return inlineCodeOrMath.replace(current) { match ->
        val formula = match.groups[2]?.value?.trim()
        if (formula.isNullOrEmpty()) return@replace match.value
        val standalone = current.trim() == match.value && wideMathEnvironment.containsMatchIn(formula)
        val delimiter = if (standalone) "\$\$" else "\$"
        delimiter + formula + delimiter
    }
}

private fun parseDirectiveHeader(rawRest: String?): Pair<Map<String, String>, String?> {
    val trimmed = rawRest?.trim().orEmpty()
    if (trimmed.isEmpty()) return emptyMap<String, String>() to null
    val blockMatch = attrBlockRegex.matchEntire(trimmed) ?: return emptyMap<String, String>() to trimmed
    val attrs = buildMap {
        for (match in attrPairRegex.findAll(blockMatch.groupValues[1])) {
            put(match.groupValues[1], escapedAttribute.replace(match.groupValues[2]) { it.groupValues[1] })
        }
    }
    val title = blockMatch.groupValues.last().trim().ifEmpty { null }
    return attrs to title
}

fun prepareExamDocsDocument(raw: String, sourceUrl: String, legacy: Boolean): ExamDocsDocument {
    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').removePrefix("\uFEFF")
    val markdown = if (legacy && legacyHeader.matches(normalized.substringBefore('\n'))) {
        normalized.substringAfter('\n', "").trimStart('\n')
    } else {
        normalized
    }
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
    val rawLines = markdown.lines()
    val lines = ArrayList<String>(rawLines.size)
    var fence: String? = null
    for (line in rawLines) {
        val match = codeFence.matchEntire(line)
        val marker = match?.groupValues?.get(1)
        val current = fence
        when {
            current != null -> {
                if (marker != null && marker.first() == current.first() &&
                    marker.length >= current.length && match?.groupValues?.get(2).isNullOrBlank()
                ) fence = null
                lines.add(line)
            }
            marker != null -> {
                fence = marker
                lines.add(line)
            }
            containerOpen.matches(line) || containerClose.matches(line) -> lines.add(line)
            else -> lines.add(normalizeMarkdownLine(line))
        }
    }

    val matchingEnds = mutableMapOf<Int, Int>()
    val open = ArrayDeque<Int>()
    fence = null

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
    val link = rawUrl.trim()
    val base = "$baseUrl$currentPageUrl".toHttpUrl()
    if (link.startsWith('/') && !link.startsWith("//")) {
        return base.resolve("$baseUrl$link")?.toString() ?: link
    }
    return base.resolve(link)?.toString() ?: link
}
