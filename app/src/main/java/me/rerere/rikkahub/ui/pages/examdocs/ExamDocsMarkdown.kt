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
import me.rerere.rikkahub.ui.pages.examdocs.components.ExamDocsAccordionBlock
import me.rerere.rikkahub.ui.pages.examdocs.components.ExamDocsCalloutCard
import me.rerere.rikkahub.ui.pages.examdocs.components.ExamDocsCardItem
import me.rerere.rikkahub.ui.pages.examdocs.components.ExamDocsCardsBlock
import me.rerere.rikkahub.ui.pages.examdocs.components.ExamDocsQuestionBlock
import me.rerere.rikkahub.ui.pages.examdocs.components.ExamDocsStepsBlock
import me.rerere.rikkahub.ui.pages.examdocs.components.ExamDocsTabsBlock
import me.rerere.rikkahub.ui.pages.examdocs.components.ExamDocsWordCardsBlock

internal data class ExamDocsBlockRenderContext(
    val renderSegment: @Composable (ExamDocsSegment) -> Unit,
    val onLinkClick: (String) -> Unit,
)

internal typealias ExamDocsCustomBlockRenderer = @Composable (
    block: ExamDocsSegment.CustomBlock,
    context: ExamDocsBlockRenderContext,
    content: @Composable () -> Unit,
) -> Unit

class ExamDocsComponentRegistry(
    private val renderers: Map<String, ExamDocsCustomBlockRenderer> = defaultRenderers,
) {
    internal fun get(type: String): ExamDocsCustomBlockRenderer? = renderers[type.lowercase()]

    internal fun extend(type: String, renderer: ExamDocsCustomBlockRenderer): ExamDocsComponentRegistry =
        ExamDocsComponentRegistry(renderers + (type.lowercase() to renderer))

    private companion object {
        val callout: ExamDocsCustomBlockRenderer = { block, _, content ->
            ExamDocsCalloutCard(block, content)
        }
        val accordion: ExamDocsCustomBlockRenderer = { block, _, content ->
            ExamDocsAccordionBlock(block, content = content)
        }
        val steps: ExamDocsCustomBlockRenderer = { block, context, _ ->
            ExamDocsStepsBlock(block, context.renderSegment)
        }
        val cards: ExamDocsCustomBlockRenderer = { block, context, _ ->
            ExamDocsCardsBlock(block, context.renderSegment, context.onLinkClick)
        }
        val card: ExamDocsCustomBlockRenderer = { block, context, _ ->
            ExamDocsCardItem(block, context.renderSegment, context.onLinkClick, Modifier.fillMaxWidth())
        }
        val tabs: ExamDocsCustomBlockRenderer = { block, context, _ ->
            ExamDocsTabsBlock(block, context.renderSegment)
        }
        val question: ExamDocsCustomBlockRenderer = { block, context, _ ->
            ExamDocsQuestionBlock(block, context.renderSegment)
        }
        val wordCards: ExamDocsCustomBlockRenderer = { block, _, _ ->
            ExamDocsWordCardsBlock(block)
        }
        val files: ExamDocsCustomBlockRenderer = { block, _, content ->
            ExamDocsCalloutCard(block.copy(type = "note", title = block.title ?: "目录结构"), content)
        }

        val defaultRenderers = mapOf(
            "note" to callout,
            "tip" to callout,
            "idea" to callout,
            "success" to callout,
            "info" to callout,
            "warning" to callout,
            "warn" to callout,
            "danger" to callout,
            "error" to callout,
            "callout" to callout,
            "solution" to accordion,
            "accordion" to accordion,
            "details" to accordion,
            "steps" to steps,
            "cards" to cards,
            "card" to card,
            "tabs" to tabs,
            "question" to question,
            "wordcards" to wordCards,
            "files" to files,
        )
    }
}

@Composable
internal fun ExamDocsMarkdownBlock(
    block: ExamDocsSegment,
    registry: ExamDocsComponentRegistry,
    renderMarkdown: RichMarkdownRenderer,
    onLinkClick: (String) -> Unit = {},
) {
    when (block) {
        is ExamDocsSegment.Markdown -> renderMarkdown(block.document, Modifier.fillMaxWidth())
        is ExamDocsSegment.CustomBlock -> {
            val renderChild: @Composable (ExamDocsSegment) -> Unit = { child ->
                ExamDocsMarkdownBlock(child, registry, renderMarkdown, onLinkClick)
            }
            val content: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    block.blocks.forEach { renderChild(it) }
                }
            }
            val renderer = registry.get(block.type)
            if (renderer != null) {
                renderer(block, ExamDocsBlockRenderContext(renderChild, onLinkClick), content)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    block.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    content()
                }
            }
        }
    }
}
