package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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
    val renderItemIndex: Int = 0,
)

@Immutable
sealed interface ExamDocsSegment {
    data class Markdown(val content: String) : ExamDocsSegment

    data class CustomBlock(
        val type: String,
        val title: String?,
        val body: String,
    ) : ExamDocsSegment
}

@Immutable
sealed interface ExamDocsRenderItem {
    val id: String

    data class MarkdownChunk(
        override val id: String,
        val content: String,
        val startLine: Int,
        val endLine: Int,
    ) : ExamDocsRenderItem

    data class CustomBlock(
        override val id: String,
        val block: ExamDocsSegment.CustomBlock,
        val startLine: Int,
        val endLine: Int,
    ) : ExamDocsRenderItem
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
                    title = block.title ?: "答案与解析",
                    badge = "解析",
                    body = block.body,
                    defaultExpanded = false,
                    renderMarkdown = renderMarkdown,
                )
            }
            val accordionRenderer: ExamDocsCustomBlockRenderer = { block, renderMarkdown ->
                ExamDocsCollapsibleCard(
                    title = block.title ?: "展开详情",
                    badge = null,
                    body = block.body,
                    defaultExpanded = false,
                    renderMarkdown = renderMarkdown,
                )
            }
            val titledCardRenderer: ExamDocsCustomBlockRenderer = { block, renderMarkdown ->
                ExamDocsTitledCard(
                    type = block.type,
                    title = block.title,
                    body = block.body,
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
                "question" to questionRenderer,
                "solution" to solutionRenderer,
                "accordion" to accordionRenderer,
                "card" to titledCardRenderer,
                "step" to titledCardRenderer,
                "tab" to titledCardRenderer,
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

private data class PositionedSegment(
    val segment: ExamDocsSegment,
    val startLine: Int,
    val endLine: Int,
)

private fun parsePositionedSegments(markdown: String): List<PositionedSegment> {
    val lines = markdown.lines()
    val segments = mutableListOf<PositionedSegment>()
    val mdBuffer = StringBuilder()
    var mdStartLine = 0

    fun flushMarkdown(endLine: Int) {
        val text = mdBuffer.toString().trim('\n')
        if (text.isNotBlank()) {
            segments.add(
                PositionedSegment(
                    segment = ExamDocsSegment.Markdown(text),
                    startLine = mdStartLine,
                    endLine = endLine,
                )
            )
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
            if (mdBuffer.isEmpty()) mdStartLine = i
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
                var nestedDepth = 0
                var innerFence = false
                while (j < lines.size) {
                    val innerTrimmed = lines[j].trim()
                    if (innerTrimmed.startsWith("```") || innerTrimmed.startsWith("~~~")) {
                        innerFence = !innerFence
                    }
                    if (!innerFence) {
                        if (CONTAINER_OPEN_REGEX.matches(innerTrimmed)) {
                            nestedDepth++
                        } else if (innerTrimmed == ":::") {
                            if (nestedDepth == 0) {
                                closed = true
                                break
                            }
                            nestedDepth--
                        }
                    }
                    bodyLines.add(lines[j])
                    j++
                }
                if (closed) {
                    flushMarkdown((i - 1).coerceAtLeast(mdStartLine))
                    segments.add(
                        PositionedSegment(
                            segment = ExamDocsSegment.CustomBlock(
                                type = type,
                                title = title,
                                body = bodyLines.joinToString("\n").trim(),
                            ),
                            startLine = i,
                            endLine = j,
                        )
                    )
                    i = j + 1
                    mdStartLine = i
                    continue
                }
            }
        }

        if (mdBuffer.isEmpty()) mdStartLine = i
        mdBuffer.append(line).append('\n')
        i++
    }
    flushMarkdown((lines.size - 1).coerceAtLeast(mdStartLine))
    return segments
}

fun parseExamDocsSegments(markdown: String): List<ExamDocsSegment> =
    parsePositionedSegments(markdown).map { it.segment }

/**
 * Splits a preprocessed Markdown document into small, LazyColumn-friendly
 * [ExamDocsRenderItem] chunks (respecting code fences, math blocks, and
 * custom containers) and maps each [DocHeading] to its exact item index.
 */
fun buildExamDocsRenderPlan(
    processedMarkdown: String,
    maxChunkLines: Int = 28,
): Pair<List<ExamDocsRenderItem>, List<DocHeading>> {
    val rawHeadings = extractDocHeadings(processedMarkdown)
    val positioned = parsePositionedSegments(processedMarkdown)
    val items = mutableListOf<ExamDocsRenderItem>()
    var counter = 0

    for (pos in positioned) {
        when (val seg = pos.segment) {
            is ExamDocsSegment.CustomBlock -> {
                items.add(
                    ExamDocsRenderItem.CustomBlock(
                        id = "custom_${counter++}_${pos.startLine}",
                        block = seg,
                        startLine = pos.startLine,
                        endLine = pos.endLine,
                    )
                )
            }

            is ExamDocsSegment.Markdown -> {
                val lines = seg.content.lines()
                if (lines.size <= maxChunkLines) {
                    items.add(
                        ExamDocsRenderItem.MarkdownChunk(
                            id = "md_${counter++}_${pos.startLine}",
                            content = seg.content,
                            startLine = pos.startLine,
                            endLine = pos.endLine,
                        )
                    )
                } else {
                    var chunkStartOffset = 0
                    var inFence = false
                    var inMathBlock = false
                    for (idx in lines.indices) {
                        val trimmed = lines[idx].trim()
                        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                            inFence = !inFence
                        } else if (!inFence && trimmed == "$$") {
                            inMathBlock = !inMathBlock
                        }

                        val chunkLen = idx - chunkStartOffset + 1
                        val canSplitHere = !inFence && !inMathBlock && chunkLen >= 10 && (
                            (chunkLen >= maxChunkLines && trimmed.isEmpty()) ||
                                (idx + 1 < lines.size && HEADING_REGEX.matches(lines[idx + 1].trim()) && chunkLen >= 8)
                        )

                        if (canSplitHere) {
                            val chunkText = lines.subList(chunkStartOffset, idx + 1).joinToString("\n").trim('\n')
                            if (chunkText.isNotBlank()) {
                                items.add(
                                    ExamDocsRenderItem.MarkdownChunk(
                                        id = "md_${counter++}_${pos.startLine + chunkStartOffset}",
                                        content = chunkText,
                                        startLine = pos.startLine + chunkStartOffset,
                                        endLine = pos.startLine + idx,
                                    )
                                )
                            }
                            chunkStartOffset = idx + 1
                        }
                    }
                    if (chunkStartOffset < lines.size) {
                        val tail = lines.subList(chunkStartOffset, lines.size).joinToString("\n").trim('\n')
                        if (tail.isNotBlank()) {
                            items.add(
                                ExamDocsRenderItem.MarkdownChunk(
                                    id = "md_${counter++}_${pos.startLine + chunkStartOffset}",
                                    content = tail,
                                    startLine = pos.startLine + chunkStartOffset,
                                    endLine = pos.endLine,
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    val mappedHeadings = rawHeadings.map { heading ->
        val itemIdx = items.indexOfFirst { item ->
            when (item) {
                is ExamDocsRenderItem.MarkdownChunk ->
                    heading.lineIndex in item.startLine..item.endLine
                is ExamDocsRenderItem.CustomBlock ->
                    heading.lineIndex in item.startLine..item.endLine
            }
        }.coerceAtLeast(0)
        heading.copy(renderItemIndex = itemIdx)
    }

    return items to mappedHeadings
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
fun ExamDocsRenderItemView(
    item: ExamDocsRenderItem,
    onLinkClick: (String) -> Unit,
    imageUrlResolver: (String) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    registry: ExamDocsComponentRegistry = remember { ExamDocsComponentRegistry() },
) {
    when (item) {
        is ExamDocsRenderItem.MarkdownChunk -> {
            MarkdownBlock(
                content = item.content,
                modifier = modifier.fillMaxWidth(),
                style = style,
                onLinkClick = onLinkClick,
                imageUrlResolver = imageUrlResolver,
            )
        }

        is ExamDocsRenderItem.CustomBlock -> {
            val block = item.block
            val customRenderer = registry.get(block.type)
            if (customRenderer != null) {
                customRenderer(block) { nestedMarkdown ->
                    ExamDocsNestedBody(
                        markdown = nestedMarkdown,
                        style = style,
                        onLinkClick = onLinkClick,
                        imageUrlResolver = imageUrlResolver,
                        registry = registry,
                    )
                }
            } else {
                ExamDocsNestedBody(
                    markdown = block.body,
                    style = style,
                    onLinkClick = onLinkClick,
                    imageUrlResolver = imageUrlResolver,
                    registry = registry,
                )
            }
        }
    }
}

@Composable
private fun ExamDocsNestedBody(
    markdown: String,
    style: TextStyle,
    onLinkClick: (String) -> Unit,
    imageUrlResolver: (String) -> String,
    registry: ExamDocsComponentRegistry,
) {
    val nestedSegments = remember(markdown) { parseExamDocsSegments(markdown) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        nestedSegments.forEach { seg ->
            when (seg) {
                is ExamDocsSegment.Markdown -> {
                    MarkdownBlock(
                        content = seg.content,
                        modifier = Modifier.fillMaxWidth(),
                        style = style,
                        onLinkClick = onLinkClick,
                        imageUrlResolver = imageUrlResolver,
                    )
                }

                is ExamDocsSegment.CustomBlock -> {
                    val customRenderer = registry.get(seg.type)
                    if (customRenderer != null) {
                        customRenderer(seg) { body ->
                            MarkdownBlock(
                                content = body,
                                modifier = Modifier.fillMaxWidth(),
                                style = style,
                                onLinkClick = onLinkClick,
                                imageUrlResolver = imageUrlResolver,
                            )
                        }
                    } else {
                        MarkdownBlock(
                            content = seg.body,
                            modifier = Modifier.fillMaxWidth(),
                            style = style,
                            onLinkClick = onLinkClick,
                            imageUrlResolver = imageUrlResolver,
                        )
                    }
                }
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
    val processed = remember(content) { preprocessExamDocsMarkdown(content) }
    val (items, _) = remember(processed) { buildExamDocsRenderPlan(processed) }
    val handleLinkClick = rememberExamDocsLinkHandler(baseUrl, currentPageUrl, onNavigateDoc)
    val imageResolver: (String) -> String = remember(baseUrl, currentPageUrl) {
        { rawUrl -> resolveExamDocsUrl(baseUrl, currentPageUrl, rawUrl) }
    }

    SelectableContent {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { item ->
                ExamDocsRenderItemView(
                    item = item,
                    onLinkClick = handleLinkClick,
                    imageUrlResolver = imageResolver,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamDocsQuestionCard(
    title: String?,
    body: String,
    renderMarkdown: @Composable (String) -> Unit,
) {
    val badges = remember(title) {
        title?.split("|")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    }
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
            if (badges.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    badges.forEachIndexed { idx, label ->
                        Surface(
                            shape = CircleShape,
                            color = if (idx == 0) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (idx == 0) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
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
    badge: String?,
    body: String,
    defaultExpanded: Boolean,
    renderMarkdown: @Composable (String) -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(defaultExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "collapsible_chevron",
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                if (badge != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = if (expanded) title else "点击查看$title",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
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

@Composable
private fun ExamDocsTitledCard(
    type: String,
    title: String?,
    body: String,
    renderMarkdown: @Composable (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title ?: type.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (body.isNotBlank()) {
                renderMarkdown(body)
            }
        }
    }
}
