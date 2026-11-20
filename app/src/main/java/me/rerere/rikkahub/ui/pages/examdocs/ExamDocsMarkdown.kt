package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.data.examdocs.ExamDocsRepository
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.SelectableContent

@Immutable
data class DocHeading(
    val level: Int,
    val title: String,
    val lineIndex: Int,
    val segmentIndex: Int = 0,
)

@Immutable
data class ParsedExamDocsContent(
    val processedMarkdown: String,
    val segments: List<ExamDocsSegment>,
    val headings: List<DocHeading>,
)

sealed interface ExamDocsSegment {
    data class Markdown(val content: String) : ExamDocsSegment

    data class CustomBlock(
        val type: String,
        val title: String?,
        val body: String,
    ) : ExamDocsSegment
}

typealias ExamDocsCustomBlockRenderer = @Composable (
    block: ExamDocsSegment.CustomBlock,
    renderMarkdown: @Composable (String) -> Unit,
) -> Unit

class ExamDocsComponentRegistry(
    private val renderers: Map<String, ExamDocsCustomBlockRenderer> = defaultRenderers(),
) {
    fun get(type: String): ExamDocsCustomBlockRenderer? = renderers[type.lowercase()]

    fun extend(
        type: String,
        renderer: ExamDocsCustomBlockRenderer,
    ): ExamDocsComponentRegistry = ExamDocsComponentRegistry(
        renderers = renderers + (type.lowercase() to renderer),
    )

    companion object {
        fun defaultRenderers(): Map<String, ExamDocsCustomBlockRenderer> {
            val calloutRenderer: ExamDocsCustomBlockRenderer = { block, renderMarkdown ->
                ExamDocsCalloutCard(
                    type = block.type,
                    title = block.title,
                    body = block.body,
                    renderMarkdown = renderMarkdown,
                )
            }
            val questionRenderer: ExamDocsCustomBlockRenderer = { block, renderMarkdown ->
                ExamDocsQuestionCard(
                    title = block.title,
                    body = block.body,
                    renderMarkdown = renderMarkdown,
                )
            }
            val solutionRenderer: ExamDocsCustomBlockRenderer = { block, renderMarkdown ->
                ExamDocsCollapsibleCard(
                    title = block.title ?: "查看答案与解析",
                    body = block.body,
                    isSolution = true,
                    renderMarkdown = renderMarkdown,
                )
            }
            val accordionRenderer: ExamDocsCustomBlockRenderer = { block, renderMarkdown ->
                ExamDocsCollapsibleCard(
                    title = block.title ?: "展开详情",
                    body = block.body,
                    isSolution = false,
                    renderMarkdown = renderMarkdown,
                )
            }
            return mapOf(
                "note" to calloutRenderer,
                "tip" to calloutRenderer,
                "info" to calloutRenderer,
                "warning" to calloutRenderer,
                "danger" to calloutRenderer,
                "callout" to calloutRenderer,
                "card" to calloutRenderer,
                "tab" to calloutRenderer,
                "question" to questionRenderer,
                "solution" to solutionRenderer,
                "accordion" to accordionRenderer,
            )
        }
    }
}

private val LEADING_PATH_HEADER_REGEX = Regex("^#\\s+.+?\\s+\\(/docs[^)]*\\)\\s*$")
private val HEADING_REGEX = Regex("^(#{1,4})\\s+(.+?)\\s*$")
private val CONTAINER_OPEN_REGEX = Regex("^:::\\s*([A-Za-z0-9_-]+)(?:\\s+(.*))?$")

fun preprocessExamDocsMarkdown(raw: String): String {
    val normalized = raw.replace("\r\n", "\n")
    val lines = normalized.lines()
    if (lines.isEmpty()) return normalized

    // exam-docs llms.mdx exports prepend "# Title (/docs/path)" before the document's "# Title".
    val firstLine = lines.first().trim()
    return if (LEADING_PATH_HEADER_REGEX.matches(firstLine)) {
        lines.drop(1).dropWhile { it.isBlank() }.joinToString("\n")
    } else {
        normalized
    }
}

fun extractDocHeadings(markdown: String): List<DocHeading> {
    val result = mutableListOf<DocHeading>()
    var inCodeFence = false
    markdown.lines().forEachIndexed { index, rawLine ->
        val trimmed = rawLine.trim()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inCodeFence = !inCodeFence
            return@forEachIndexed
        }
        if (inCodeFence) return@forEachIndexed
        val match = HEADING_REGEX.matchEntire(trimmed) ?: return@forEachIndexed
        val level = match.groupValues[1].length
        val title = match.groupValues[2]
            .replace(Regex("\\s+#+\\s*$"), "")
            .trim()
        if (title.isNotEmpty()) {
            result.add(DocHeading(level = level, title = title, lineIndex = index))
        }
    }
    return result
}

fun parseExamDocsSegments(markdown: String): List<ExamDocsSegment> {
    val lines = markdown.lines()
    val segments = mutableListOf<ExamDocsSegment>()
    val mdBuffer = StringBuilder()

    fun flushMarkdown() {
        val text = mdBuffer.toString().trim('\n')
        if (text.isNotBlank()) {
            segments.add(ExamDocsSegment.Markdown(text))
        }
        mdBuffer.clear()
    }

    var i = 0
    var inCodeFence = false
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inCodeFence = !inCodeFence
            mdBuffer.append(line).append('\n')
            i++
            continue
        }

        if (!inCodeFence) {
            val openMatch = CONTAINER_OPEN_REGEX.matchEntire(trimmed)
            if (openMatch != null) {
                val type = openMatch.groupValues[1]
                val title = openMatch.groupValues[2].trim().takeIf { it.isNotEmpty() }
                val bodyLines = mutableListOf<String>()
                var j = i + 1
                var closed = false
                while (j < lines.size) {
                    if (lines[j].trim() == ":::") {
                        closed = true
                        break
                    }
                    bodyLines.add(lines[j])
                    j++
                }
                if (closed) {
                    flushMarkdown()
                    segments.add(
                        ExamDocsSegment.CustomBlock(
                            type = type,
                            title = title,
                            body = bodyLines.joinToString("\n").trim(),
                        )
                    )
                    i = j + 1
                    continue
                }
            }
        }

        mdBuffer.append(line).append('\n')
        i++
    }
    flushMarkdown()
    return segments
}

/**
 * Parse and chunk an ExamDocs/StudyDocs markdown document for lazy, block-level
 * rendering in a LazyColumn, recording exact segment indices for every heading.
 */
fun parseExamDocsDocument(raw: String): ParsedExamDocsContent {
    val processed = preprocessExamDocsMarkdown(raw)
    val rawSegments = parseExamDocsSegments(processed)
    val chunkedSegments = mutableListOf<ExamDocsSegment>()
    val headings = mutableListOf<DocHeading>()
    var globalLineIndex = 0

    for (segment in rawSegments) {
        when (segment) {
            is ExamDocsSegment.CustomBlock -> {
                chunkedSegments.add(segment)
                globalLineIndex += segment.body.lines().size + 2
            }

            is ExamDocsSegment.Markdown -> {
                val lines = segment.content.lines()
                val chunkBuffer = StringBuilder()
                var chunkLineCount = 0
                var inCodeFence = false
                var inMathBlock = false

                fun flushChunk() {
                    val text = chunkBuffer.toString().trim('\n')
                    if (text.isNotBlank()) {
                        chunkedSegments.add(ExamDocsSegment.Markdown(text))
                    }
                    chunkBuffer.clear()
                    chunkLineCount = 0
                }

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                        inCodeFence = !inCodeFence
                    } else if (!inCodeFence && trimmed == "$$") {
                        inMathBlock = !inMathBlock
                    }

                    val isTopLevelBoundary = !inCodeFence && !inMathBlock
                    val headingMatch = if (isTopLevelBoundary) HEADING_REGEX.matchEntire(trimmed) else null

                    if (headingMatch != null) {
                        if (chunkBuffer.isNotEmpty()) {
                            flushChunk()
                        }
                        val level = headingMatch.groupValues[1].length
                        val title = headingMatch.groupValues[2]
                            .replace(Regex("\\s+#+\\s*$"), "")
                            .trim()
                        if (title.isNotEmpty()) {
                            headings.add(
                                DocHeading(
                                    level = level,
                                    title = title,
                                    lineIndex = globalLineIndex,
                                    segmentIndex = chunkedSegments.size,
                                )
                            )
                        }
                    } else if (isTopLevelBoundary && trimmed.isEmpty() && chunkLineCount >= 18) {
                        // Split large sections on blank lines so LazyColumn only composes visible chunks.
                        flushChunk()
                        globalLineIndex++
                        continue
                    }

                    chunkBuffer.append(line).append('\n')
                    chunkLineCount++
                    globalLineIndex++
                }
                flushChunk()
            }
        }
    }

    return ParsedExamDocsContent(
        processedMarkdown = processed,
        segments = chunkedSegments,
        headings = headings,
    )
}

fun resolveExamDocsUrl(baseUrl: String, currentPageUrl: String?, rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return trimmed
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:")) {
        return trimmed
    }
    val normalizedBase = ExamDocsRepository.normalizeBaseUrl(baseUrl)
    if (trimmed.startsWith("//")) {
        return "https:$trimmed"
    }
    if (trimmed.startsWith("/")) {
        return "$normalizedBase$trimmed"
    }
    val parentDir = currentPageUrl
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?.substringBeforeLast('/', missingDelimiterValue = "/docs")
        ?.ifBlank { "/docs" }
        ?: "/docs"
    return "$normalizedBase$parentDir/$trimmed"
}

@Composable
fun rememberExamDocsLinkHandler(
    baseUrl: String,
    currentPageUrl: String?,
    onNavigateDoc: (String) -> Unit,
): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return remember(baseUrl, currentPageUrl, onNavigateDoc) {
        { rawUrl ->
            val trimmed = rawUrl.trim()
            val normalizedBase = ExamDocsRepository.normalizeBaseUrl(baseUrl)
            val docPath = when {
                trimmed.startsWith("/docs") -> trimmed.removeSuffix(".android.md").removeSuffix(".md")
                trimmed.startsWith("$normalizedBase/docs") ->
                    trimmed.removePrefix(normalizedBase).removeSuffix(".android.md").removeSuffix(".md")
                else -> null
            }
            if (docPath != null) {
                onNavigateDoc(ExamDocsRepository.normalizeDocPath(docPath))
            } else {
                val target = resolveExamDocsUrl(baseUrl, currentPageUrl, trimmed)
                runCatching { uriHandler.openUri(target) }
            }
        }
    }
}

@Composable
fun rememberExamDocsImageResolver(
    baseUrl: String,
    currentPageUrl: String?,
): (String) -> String {
    return remember(baseUrl, currentPageUrl) {
        { rawUrl -> resolveExamDocsUrl(baseUrl, currentPageUrl, rawUrl) }
    }
}

@Composable
fun ExamDocsSegmentItem(
    segment: ExamDocsSegment,
    onLinkClick: (String) -> Unit,
    imageResolver: (String) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    registry: ExamDocsComponentRegistry = remember { ExamDocsComponentRegistry() },
) {
    when (segment) {
        is ExamDocsSegment.Markdown -> {
            MarkdownBlock(
                content = segment.content,
                modifier = modifier.fillMaxWidth(),
                style = style,
                onLinkClick = onLinkClick,
                imageUrlResolver = imageResolver,
            )
        }

        is ExamDocsSegment.CustomBlock -> {
            val customRenderer = registry.get(segment.type)
            if (customRenderer != null) {
                customRenderer(segment) { nestedMarkdown ->
                    MarkdownBlock(
                        content = nestedMarkdown,
                        modifier = Modifier.fillMaxWidth(),
                        style = style,
                        onLinkClick = onLinkClick,
                        imageUrlResolver = imageResolver,
                    )
                }
            } else {
                MarkdownBlock(
                    content = segment.body,
                    modifier = modifier.fillMaxWidth(),
                    style = style,
                    onLinkClick = onLinkClick,
                    imageUrlResolver = imageResolver,
                )
            }
        }
    }
}

@Composable
fun ExamDocsMarkdown(
    content: String,
    baseUrl: String,
    currentPageUrl: String?,
    onNavigateDoc: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    registry: ExamDocsComponentRegistry = remember { ExamDocsComponentRegistry() },
) {
    val parsed = remember(content) { parseExamDocsDocument(content) }
    val handleLinkClick = rememberExamDocsLinkHandler(baseUrl, currentPageUrl, onNavigateDoc)
    val imageResolver = rememberExamDocsImageResolver(baseUrl, currentPageUrl)

    SelectableContent {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            parsed.segments.forEach { segment ->
                ExamDocsSegmentItem(
                    segment = segment,
                    onLinkClick = handleLinkClick,
                    imageResolver = imageResolver,
                    style = style,
                    registry = registry,
                )
            }
        }
    }
}

@Composable
private fun ExamDocsCalloutCard(
    type: String,
    title: String?,
    body: String,
    renderMarkdown: @Composable (String) -> Unit,
) {
    val isWarning = type.equals("warning", ignoreCase = true) || type.equals("danger", ignoreCase = true)
    val containerColor = if (isWarning) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    }
    val borderColor = if (isWarning) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    }
    val titleColor = if (isWarning) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title ?: type.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
            if (body.isNotBlank()) {
                renderMarkdown(body)
            }
        }
    }
}

@Composable
private fun ExamDocsQuestionCard(
    title: String?,
    body: String,
    renderMarkdown: @Composable (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!title.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            if (body.isNotBlank()) {
                renderMarkdown(body)
            }
        }
    }
}

@Composable
private fun ExamDocsCollapsibleCard(
    title: String,
    body: String,
    isSolution: Boolean,
    renderMarkdown: @Composable (String) -> Unit,
) {
    var expanded by rememberSaveable(title, body.hashCode()) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "collapsible_chevron",
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSolution) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (isSolution) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation),
                    tint = if (isSolution) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSolution) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded && body.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                ) {
                    renderMarkdown(body)
                }
            }
        }
    }
}
