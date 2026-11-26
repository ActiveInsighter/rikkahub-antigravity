package me.rerere.rikkahub.ui.pages.examdocs

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.core.net.toUri
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import me.rerere.bettertext.latex.LatexBlock
import me.rerere.bettertext.latex.LatexInline
import me.rerere.bettertext.latex.LatexLayout
import me.rerere.bettertext.latex.LatexSettings
import me.rerere.bettertext.latex.rememberLatexPainter
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownInline
import me.rerere.bettertext.text.BetterInline
import me.rerere.rikkahub.data.examdocs.ExamDocsDocument
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.components.richtext.RichMarkdownRenderer
import me.rerere.rikkahub.ui.components.richtext.SvgArtwork
import me.rerere.rikkahub.ui.components.richtext.SvgPreviewDialog
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.richtext.buildSvgImageRequest
import me.rerere.rikkahub.ui.components.richtext.rememberRichMarkdownRenderer
import me.rerere.rikkahub.ui.context.LocalSettings
import java.util.Collections

private const val PREFETCH_GROUP = 8

@Composable
internal fun rememberDocMathLayouts(document: ExamDocsDocument): DocMathLayouts {
    val context = LocalContext.current.applicationContext
    return remember(document, context) { DocMathLayouts(context) }
}

@Composable
internal fun rememberExamDocsRenderer(
    document: ExamDocsDocument,
    listState: LazyListState,
    onLinkClick: (String) -> Unit,
    imageUrlResolver: (String) -> String,
    cache: DocMathLayouts = rememberDocMathLayouts(document),
): RichMarkdownRenderer {
    val context = LocalContext.current.applicationContext
    val painter = rememberLatexPainter()
    val displayMath: @Composable (String) -> Unit = remember(cache) {
        { formula ->
            val compact = LocalCompactMathBlock.current
            val layout = remember(formula, cache) { cache.get(formula, display = true) }
            val style = LocalTextStyle.current
            val fontSize = style.fontSize.takeOrElse { MaterialTheme.typography.bodyLarge.fontSize }
            val color = style.color.takeOrElse { LocalContentColor.current }
            if (compact) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    LatexBlock(
                        layout,
                        modifier = Modifier.padding(vertical = 2.dp),
                        fontSize = fontSize,
                        scale = 1.08f,
                        color = color,
                    )
                }
            } else {
                LatexBlock(
                    layout,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    fontSize = fontSize,
                    scale = 1.15f,
                    color = color,
                )
            }
        }
    }
    val inlineMath: (String) -> BetterInline = remember(cache, painter) {
        { formula -> LatexInline(cache.get(formula, display = false), painter) }
    }

    val renderLatex = LocalSettings.current.displaySetting.enableLatexRendering
    val prefetchedUrls = remember(document) { Collections.synchronizedSet(mutableSetOf<String>()) }
    LaunchedEffect(cache, listState, renderLatex, imageUrlResolver) {
        val imageLoader = context.imageLoader
        snapshotFlow { listState.firstVisibleItemIndex / PREFETCH_GROUP }.collectLatest { group ->
            withContext(Dispatchers.Default) {
                val start = ((group - 1).coerceAtLeast(0) * PREFETCH_GROUP)
                val end = ((group + 3) * PREFETCH_GROUP).coerceAtMost(document.blocks.size)
                for (index in start until end) {
                    ensureActive()
                    val segment = document.blocks[index]
                    if (renderLatex) {
                        cache.prefetchSegment(segment)
                    }
                    prefetchImages(segment, imageUrlResolver, prefetchedUrls) { resolvedUrl ->
                        val request = if (isSvgUrl(resolvedUrl)) {
                            buildSvgImageRequest(context, resolvedUrl)
                        } else {
                            ImageRequest.Builder(context).data(resolvedUrl).build()
                        }
                        imageLoader.enqueue(request)
                    }
                }
            }
        }
    }
    val imageContent: @Composable (MarkdownInline.Image, String?, String) -> Unit = remember(onLinkClick) {
        { image, link, url ->
            if (isSvgUrl(url)) {
                var expanded by remember(url) { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SvgArtwork(
                        model = url,
                        description = image.alt,
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .heightIn(max = 520.dp)
                            .clickable {
                                if (link == null || link == image.url) expanded = true else onLinkClick(link)
                            },
                    )
                }
                if (expanded) {
                    SvgPreviewDialog(url, onDismiss = { expanded = false }, description = image.alt, framed = false)
                }
            } else {
                val colors = MaterialTheme.colorScheme
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = colors.surfaceContainerLow,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ZoomableAsyncImage(
                            model = url,
                            contentDescription = image.alt,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 480.dp),
                        )
                        if (!image.alt.isNullOrBlank()) {
                            Text(
                                text = image.alt.orEmpty(),
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (link != null && link != image.url) {
                            TextButton(onClick = { onLinkClick(link) }) { Text("打开图片链接") }
                        }
                    }
                }
            }
        }
    }
    return rememberRichMarkdownRenderer(
        onLinkClick = onLinkClick,
        imageUrlResolver = imageUrlResolver,
        displayMath = displayMath,
        inlineMath = inlineMath,
        imageContent = imageContent,
        svgPreviewByDefault = true,
        showCodeBlockContainer = false,
    )
}

@Composable
internal fun rememberExamDocsMathBlockRenderer(
    document: ExamDocsDocument,
    cache: DocMathLayouts = rememberDocMathLayouts(document),
): @Composable (formula: String, display: Boolean) -> Unit {
    return remember(cache) {
        { formula, display ->
            val compact = LocalCompactMathBlock.current
            val layout = remember(formula, display, cache) { cache.get(formula, display = display) }
            val style = LocalTextStyle.current
            val fontSize = style.fontSize.takeOrElse { MaterialTheme.typography.bodyLarge.fontSize }
            val color = style.color.takeOrElse { LocalContentColor.current }
            if (compact || !display) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    LatexBlock(
                        layout,
                        modifier = Modifier.padding(vertical = 2.dp),
                        fontSize = fontSize,
                        scale = if (compact) 1.08f else 1.1f,
                        color = color,
                    )
                }
            } else {
                LatexBlock(
                    layout,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    fontSize = fontSize,
                    scale = 1.15f,
                    color = color,
                )
            }
        }
    }
}

internal class DocMathLayouts(private val context: Context) {
    private val inline = LruCache<String, LatexLayout>(512)
    private val blocks = LruCache<String, LatexLayout>(192)
    private val inlineSettings = LatexSettings()
    private val blockSettings = LatexSettings(displayMode = true)

    fun get(formula: String, display: Boolean): LatexLayout {
        val cache = if (display) blocks else inline
        return cache.get(formula) ?: LatexLayout.create(
            formula,
            if (display) blockSettings else inlineSettings,
            context,
        ).also { cache.put(formula, it) }
    }

    fun prefetchSegment(segment: ExamDocsSegment) {
        when (segment) {
            is ExamDocsSegment.Markdown -> segment.document.blocks.forEach(::prefetchBlock)
            is ExamDocsSegment.CustomBlock -> segment.blocks.forEach(::prefetchSegment)
        }
    }

    private fun prefetchBlock(block: MarkdownBlock) {
        when (block) {
            is MarkdownBlock.Paragraph -> prefetch(block.content)
            is MarkdownBlock.Heading -> prefetch(block.content)
            is MarkdownBlock.Quote -> block.children.forEach(::prefetchBlock)
            is MarkdownBlock.ListBlock -> block.items.forEach { item ->
                item.children.forEach(::prefetchBlock)
            }
            is MarkdownBlock.Table -> {
                block.header.forEach { cell -> prefetch(cell.content) }
                block.rows.forEach { row -> row.forEach { cell -> prefetch(cell.content) } }
            }
            else -> Unit
        }
    }

    fun prefetch(parts: List<MarkdownInline>) {
        for (part in parts) {
            when (part) {
                is MarkdownInline.Math -> get(part.formula, part.display)
                is MarkdownInline.Styled -> prefetch(part.children)
                is MarkdownInline.Link -> prefetch(part.children)
                else -> Unit
            }
        }
    }
}

private fun isSvgUrl(url: String): Boolean =
    url.startsWith("data:image/svg+xml", ignoreCase = true) ||
        url.toUri().path?.endsWith(".svg", ignoreCase = true) == true

private fun prefetchImages(
    segment: ExamDocsSegment,
    imageUrlResolver: (String) -> String,
    seen: MutableSet<String>,
    onEnqueue: (String) -> Unit,
) {
    when (segment) {
        is ExamDocsSegment.Markdown -> segment.document.blocks.forEach { block ->
            prefetchBlockImages(block, imageUrlResolver, seen, onEnqueue)
        }
        is ExamDocsSegment.CustomBlock -> segment.blocks.forEach { child ->
            prefetchImages(child, imageUrlResolver, seen, onEnqueue)
        }
    }
}

private fun prefetchBlockImages(
    block: MarkdownBlock,
    imageUrlResolver: (String) -> String,
    seen: MutableSet<String>,
    onEnqueue: (String) -> Unit,
) {
    when (block) {
        is MarkdownBlock.Paragraph -> prefetchInlineImages(block.content, imageUrlResolver, seen, onEnqueue)
        is MarkdownBlock.Heading -> prefetchInlineImages(block.content, imageUrlResolver, seen, onEnqueue)
        is MarkdownBlock.Quote -> block.children.forEach { prefetchBlockImages(it, imageUrlResolver, seen, onEnqueue) }
        is MarkdownBlock.ListBlock -> block.items.forEach { item ->
            item.children.forEach { prefetchBlockImages(it, imageUrlResolver, seen, onEnqueue) }
        }
        is MarkdownBlock.Table -> {
            block.header.forEach { cell -> prefetchInlineImages(cell.content, imageUrlResolver, seen, onEnqueue) }
            block.rows.forEach { row -> row.forEach { cell -> prefetchInlineImages(cell.content, imageUrlResolver, seen, onEnqueue) } }
        }
        else -> Unit
    }
}

private fun prefetchInlineImages(
    inlines: List<MarkdownInline>,
    imageUrlResolver: (String) -> String,
    seen: MutableSet<String>,
    onEnqueue: (String) -> Unit,
) {
    for (inline in inlines) {
        when (inline) {
            is MarkdownInline.Image -> {
                val resolved = imageUrlResolver(inline.url)
                if (resolved.isNotBlank() && seen.add(resolved)) {
                    onEnqueue(resolved)
                }
            }
            is MarkdownInline.Styled -> prefetchInlineImages(inline.children, imageUrlResolver, seen, onEnqueue)
            is MarkdownInline.Link -> prefetchInlineImages(inline.children, imageUrlResolver, seen, onEnqueue)
            else -> Unit
        }
    }
}
