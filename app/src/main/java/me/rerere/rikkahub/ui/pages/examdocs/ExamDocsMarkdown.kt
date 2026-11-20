package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.bettertext.markdown.MarkdownBlock.Details
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.MaterialMarkdownDefaults

typealias ExamDocsCustomBlockRenderer = @Composable (
    block: ExamDocsSegment.CustomBlock,
    content: @Composable () -> Unit,
) -> Unit

class ExamDocsComponentRegistry(
    private val renderers: Map<String, ExamDocsCustomBlockRenderer> = defaultRenderers,
) {
    fun get(type: String): ExamDocsCustomBlockRenderer? = renderers[type.lowercase()]

    fun extend(type: String, renderer: ExamDocsCustomBlockRenderer): ExamDocsComponentRegistry =
        ExamDocsComponentRegistry(renderers + (type.lowercase() to renderer))

    private companion object {
        val callout: ExamDocsCustomBlockRenderer = { block, content -> ExamDocsCalloutCard(block, content) }
        val solution: ExamDocsCustomBlockRenderer = { block, content ->
            MaterialMarkdownDefaults.Details(
                block = Details(summary = emptyList(), children = emptyList()),
                summary = {
                    Text(block.title ?: "参考答案与解析", style = MaterialTheme.typography.titleSmall)
                },
                content = content,
            )
        }
        val defaultRenderers = mapOf(
            "note" to callout,
            "tip" to callout,
            "info" to callout,
            "warning" to callout,
            "danger" to callout,
            "callout" to callout,
            "solution" to solution,
        )
    }
}

@Composable
internal fun ExamDocsMarkdownBlock(
    block: ExamDocsSegment,
    registry: ExamDocsComponentRegistry,
    onLinkClick: (String) -> Unit,
    imageUrlResolver: (String) -> String,
) {
    when (block) {
        is ExamDocsSegment.Markdown -> MarkdownBlock(
            document = block.document,
            modifier = Modifier.fillMaxWidth(),
            onLinkClick = onLinkClick,
            imageUrlResolver = imageUrlResolver,
        )
        is ExamDocsSegment.CustomBlock -> {
            val content: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    block.blocks.forEach {
                        ExamDocsMarkdownBlock(it, registry, onLinkClick, imageUrlResolver)
                    }
                }
            }
            val renderer = registry.get(block.type)
            if (renderer != null) {
                renderer(block, content)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    block.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    content()
                }
            }
        }
    }
}

@Composable
private fun ExamDocsCalloutCard(block: ExamDocsSegment.CustomBlock, content: @Composable () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val (color, label) = when (block.type) {
        "tip" -> colorScheme.secondaryContainer to "提示"
        "warning" -> colorScheme.tertiaryContainer to "注意"
        "danger" -> colorScheme.errorContainer to "警告"
        "note" -> colorScheme.surfaceContainerHigh to "笔记"
        else -> colorScheme.primaryContainer to "说明"
    }
    Surface(
        color = color,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(block.title ?: label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
