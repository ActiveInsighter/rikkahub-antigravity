package me.rerere.rikkahub.ui.pages.examdocs

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
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
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.richtext.rememberRichMarkdownRenderer
import me.rerere.rikkahub.ui.context.LocalSettings

private const val PREFETCH_GROUP = 8

@Composable
internal fun rememberExamDocsRenderer(
    document: ExamDocsDocument,
    listState: LazyListState,
    onLinkClick: (String) -> Unit,
    imageUrlResolver: (String) -> String,
): RichMarkdownRenderer {
    val context = LocalContext.current.applicationContext
    val cache = remember(document, context) { DocMathLayouts(context) }
    val painter = rememberLatexPainter()
    val displayMath: @Composable (String) -> Unit = remember(cache) {
        { formula ->
            val layout = remember(formula, cache) { cache.get(formula, display = true) }
            val style = LocalTextStyle.current
            LatexBlock(
                layout,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                fontSize = style.fontSize.takeOrElse { MaterialTheme.typography.bodyLarge.fontSize } * 1.15f,
                scale = 1f,
                color = style.color.takeOrElse { LocalContentColor.current },
            )
        }
    }
    val inlineMath: (String) -> BetterInline = remember(cache, painter) {
        { formula -> LatexInline(cache.get(formula, display = false), painter) }
    }

    if (LocalSettings.current.displaySetting.enableLatexRendering) {
        LaunchedEffect(cache, listState) {
            snapshotFlow { listState.firstVisibleItemIndex / PREFETCH_GROUP }.collectLatest { group ->
                withContext(Dispatchers.Default) {
                    val start = ((group - 1).coerceAtLeast(0) * PREFETCH_GROUP)
                    val end = ((group + 3) * PREFETCH_GROUP).coerceAtMost(document.blocks.size)
                    for (index in start until end) {
                        ensureActive()
                        val segment = document.blocks[index] as? ExamDocsSegment.Markdown ?: continue
                        when (val block = segment.document.blocks.singleOrNull()) {
                            is MarkdownBlock.Paragraph -> cache.prefetch(block.content)
                            is MarkdownBlock.Heading -> cache.prefetch(block.content)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
    val imageContent: @Composable (MarkdownInline.Image, String?, String) -> Unit = remember(onLinkClick) {
        { image, link, url ->
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
    return rememberRichMarkdownRenderer(
        onLinkClick = onLinkClick,
        imageUrlResolver = imageUrlResolver,
        displayMath = displayMath,
        inlineMath = inlineMath,
        imageContent = imageContent,
    )
}

private class DocMathLayouts(private val context: Context) {
    private val inline = LruCache<String, LatexLayout>(192)
    private val blocks = LruCache<String, LatexLayout>(64)
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
