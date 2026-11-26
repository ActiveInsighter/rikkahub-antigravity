package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownDocument
import me.rerere.bettertext.markdown.MarkdownInline
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.components.richtext.RichMarkdownRenderer
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.examdocs.components.DocAccordion
import me.rerere.rikkahub.ui.pages.examdocs.components.DocCallout
import me.rerere.rikkahub.ui.pages.examdocs.components.DocCard
import me.rerere.rikkahub.ui.pages.examdocs.components.DocCards
import me.rerere.rikkahub.ui.pages.examdocs.components.DocQuestionCard
import me.rerere.rikkahub.ui.pages.examdocs.components.DocSteps
import me.rerere.rikkahub.ui.pages.examdocs.components.DocTabs
import me.rerere.rikkahub.ui.pages.examdocs.components.DocWordCards

internal val LocalCompactMathBlock = compositionLocalOf { false }

data class ExamDocsRenderContext(
    val registry: ExamDocsComponentRegistry,
    val renderMarkdown: RichMarkdownRenderer,
    val onLinkClick: ((String) -> Unit)? = null,
    val renderMathBlock: (@Composable (formula: String, display: Boolean) -> Unit)? = null,
)

typealias ExamDocsCustomBlockRenderer = @Composable (
    block: ExamDocsSegment.CustomBlock,
    context: ExamDocsRenderContext,
) -> Unit

class ExamDocsComponentRegistry(
    private val onLinkClick: ((String) -> Unit)? = null,
    private val renderers: Map<String, ExamDocsCustomBlockRenderer> = defaultRenderers,
    private val renderMathBlock: (@Composable (formula: String, display: Boolean) -> Unit)? = null,
) {
    fun get(type: String): ExamDocsCustomBlockRenderer? = renderers[type.lowercase()]

    fun extend(type: String, renderer: ExamDocsCustomBlockRenderer): ExamDocsComponentRegistry =
        ExamDocsComponentRegistry(onLinkClick, renderers + (type.lowercase() to renderer), renderMathBlock)

    fun createContext(renderMarkdown: RichMarkdownRenderer): ExamDocsRenderContext =
        ExamDocsRenderContext(this, renderMarkdown, onLinkClick, renderMathBlock)

    private companion object {
        val callout: ExamDocsCustomBlockRenderer = { block, ctx ->
            DocCallout(block = block, content = { RenderSegmentList(block.blocks, ctx) })
        }
        val accordion: ExamDocsCustomBlockRenderer = { block, ctx ->
            DocAccordion(block = block, content = { RenderSegmentList(block.blocks, ctx) })
        }
        val cards: ExamDocsCustomBlockRenderer = { block, ctx ->
            DocCards(block = block, renderChild = { child -> ExamDocsMarkdownBlock(child, ctx) })
        }
        val card: ExamDocsCustomBlockRenderer = { block, ctx ->
            DocCard(
                block = block,
                content = { RenderSegmentList(block.blocks, ctx) },
                onLinkClick = ctx.onLinkClick,
            )
        }
        val steps: ExamDocsCustomBlockRenderer = { block, ctx ->
            DocSteps(block = block, renderChildren = { children -> RenderSegmentList(children, ctx) })
        }
        val tabs: ExamDocsCustomBlockRenderer = { block, ctx ->
            DocTabs(block = block, renderChildren = { children -> RenderSegmentList(children, ctx) })
        }
        val question: ExamDocsCustomBlockRenderer = { block, ctx ->
            DocQuestionCard(block = block, renderChildren = { children -> RenderSegmentList(children, ctx) })
        }
        val wordCards: ExamDocsCustomBlockRenderer = { block, _ ->
            DocWordCards(block = block)
        }

        val defaultRenderers = mapOf(
            "note" to callout,
            "tip" to callout,
            "idea" to callout,
            "success" to callout,
            "important" to callout,
            "info" to callout,
            "warning" to callout,
            "warn" to callout,
            "caution" to callout,
            "danger" to callout,
            "error" to callout,
            "callout" to callout,
            "solution" to accordion,
            "accordion" to accordion,
            "details" to accordion,
            "cards" to cards,
            "card" to card,
            "steps" to steps,
            "cardsteps" to steps,
            "tabs" to tabs,
            "codetabs" to tabs,
            "question" to question,
            "wordcards" to wordCards,
        )
    }
}

@Composable
internal fun ExamDocsMarkdownBlock(
    block: ExamDocsSegment,
    registry: ExamDocsComponentRegistry,
    renderMarkdown: RichMarkdownRenderer,
) {
    ExamDocsMarkdownBlock(block, registry.createContext(renderMarkdown))
}

@Composable
private fun ExamDocsMarkdownBlock(
    block: ExamDocsSegment,
    context: ExamDocsRenderContext,
) {
    when (block) {
        is ExamDocsSegment.Markdown -> {
            val renderMathBlock = context.renderMathBlock
            val latexEnabled = LocalSettings.current.displaySetting.enableLatexRendering
            val compact = LocalCompactMathBlock.current
            val wideThreshold = if (compact) 28 else 44
            val hasSpecialMath = renderMathBlock != null && latexEnabled && block.document.blocks.any { mdBlock ->
                mdBlock is MarkdownBlock.Paragraph && mdBlock.containsScrollableMath(wideThreshold)
            }
            if (!hasSpecialMath || renderMathBlock == null) {
                context.renderMarkdown(block.document, Modifier.fillMaxWidth())
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.document.blocks.forEach { mdBlock ->
                        if (mdBlock is MarkdownBlock.Paragraph && mdBlock.containsScrollableMath(wideThreshold)) {
                            RenderParagraphWithScrollableMath(
                                paragraph = mdBlock,
                                wideThreshold = wideThreshold,
                                renderMarkdown = context.renderMarkdown,
                                renderMathBlock = renderMathBlock,
                            )
                        } else {
                            val singleDoc = remember(mdBlock) { MarkdownDocument("", listOf(mdBlock)) }
                            context.renderMarkdown(singleDoc, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        is ExamDocsSegment.CustomBlock -> {
            val renderer = context.registry.get(block.type)
            if (renderer != null) {
                renderer(block, context)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    block.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    RenderSegmentList(block.blocks, context)
                }
            }
        }
    }
}

private fun MarkdownBlock.Paragraph.isStandaloneMath(): MarkdownInline.Math? {
    var found: MarkdownInline.Math? = null
    for (inline in content) {
        when (inline) {
            is MarkdownInline.Text -> if (inline.text.isNotBlank()) return null
            is MarkdownInline.Math -> {
                if (found != null) return null
                found = inline
            }
            else -> return null
        }
    }
    return found
}

private fun MarkdownBlock.Paragraph.containsScrollableMath(wideThreshold: Int): Boolean {
    if (isStandaloneMath() != null) return true
    return content.any { inline ->
        inline is MarkdownInline.Math && (inline.display || inline.formula.length >= wideThreshold)
    }
}

@Composable
private fun RenderParagraphWithScrollableMath(
    paragraph: MarkdownBlock.Paragraph,
    wideThreshold: Int,
    renderMarkdown: RichMarkdownRenderer,
    renderMathBlock: @Composable (formula: String, display: Boolean) -> Unit,
) {
    val standalone = remember(paragraph) { paragraph.isStandaloneMath() }
    if (standalone != null) {
        renderMathBlock(standalone.formula, standalone.display)
        return
    }

    val parts = remember(paragraph, wideThreshold) {
        buildList<Any> {
            val buffer = mutableListOf<MarkdownInline>()
            fun flushText() {
                if (buffer.isNotEmpty() && buffer.any { it !is MarkdownInline.Text || it.text.isNotBlank() }) {
                    add(MarkdownDocument("", listOf(MarkdownBlock.Paragraph(buffer.toList()))))
                }
                buffer.clear()
            }
            for (inline in paragraph.content) {
                if (inline is MarkdownInline.Math && (inline.display || inline.formula.length >= wideThreshold)) {
                    flushText()
                    add(inline)
                } else {
                    buffer.add(inline)
                }
            }
            flushText()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEach { part ->
            when (part) {
                is MarkdownDocument -> renderMarkdown(part, Modifier.fillMaxWidth())
                is MarkdownInline.Math -> renderMathBlock(part.formula, part.display)
            }
        }
    }
}

@Composable
private fun RenderSegmentList(
    blocks: List<ExamDocsSegment>,
    context: ExamDocsRenderContext,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { child ->
            ExamDocsMarkdownBlock(child, context)
        }
    }
}
