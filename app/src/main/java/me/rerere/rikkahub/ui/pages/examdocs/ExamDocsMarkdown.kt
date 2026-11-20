package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.components.richtext.RichMarkdownRenderer
import me.rerere.rikkahub.ui.pages.examdocs.components.DocAccordion
import me.rerere.rikkahub.ui.pages.examdocs.components.DocCallout
import me.rerere.rikkahub.ui.pages.examdocs.components.DocCard
import me.rerere.rikkahub.ui.pages.examdocs.components.DocCards
import me.rerere.rikkahub.ui.pages.examdocs.components.DocQuestionCard
import me.rerere.rikkahub.ui.pages.examdocs.components.DocSteps
import me.rerere.rikkahub.ui.pages.examdocs.components.DocTabs
import me.rerere.rikkahub.ui.pages.examdocs.components.DocWordCards

data class ExamDocsRenderContext(
    val registry: ExamDocsComponentRegistry,
    val renderMarkdown: RichMarkdownRenderer,
    val onLinkClick: ((String) -> Unit)? = null,
)

typealias ExamDocsCustomBlockRenderer = @Composable (
    block: ExamDocsSegment.CustomBlock,
    context: ExamDocsRenderContext,
) -> Unit

class ExamDocsComponentRegistry(
    private val onLinkClick: ((String) -> Unit)? = null,
    private val renderers: Map<String, ExamDocsCustomBlockRenderer> = defaultRenderers,
) {
    fun get(type: String): ExamDocsCustomBlockRenderer? = renderers[type.lowercase()]

    fun extend(type: String, renderer: ExamDocsCustomBlockRenderer): ExamDocsComponentRegistry =
        ExamDocsComponentRegistry(onLinkClick, renderers + (type.lowercase() to renderer))

    fun createContext(renderMarkdown: RichMarkdownRenderer): ExamDocsRenderContext =
        ExamDocsRenderContext(this, renderMarkdown, onLinkClick)

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
        is ExamDocsSegment.Markdown -> context.renderMarkdown(block.document, Modifier.fillMaxWidth())
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
