package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.plainText
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment

private data class StepItemData(
    val title: String?,
    val tone: String?,
    val segments: List<ExamDocsSegment>,
)

@Composable
internal fun ExamDocsStepsBlock(
    block: ExamDocsSegment.CustomBlock,
    renderSegment: @Composable (ExamDocsSegment) -> Unit,
) {
    val steps = remember(block.blocks) { collectSteps(block.blocks) }
    if (steps.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        steps.forEachIndexed { index, step ->
            val isLast = index == steps.lastIndex
            val palette = resolveTonePalette(step.tone)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (step.tone != null) palette.container else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(26.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (step.tone != null) palette.accent else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    if (!isLast) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .weight(1f)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (isLast) 4.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    step.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    step.segments.forEach { segment ->
                        renderSegment(segment)
                    }
                }
            }
        }
    }
}

private fun collectSteps(blocks: List<ExamDocsSegment>): List<StepItemData> {
    val explicitSteps = blocks.filterIsInstance<ExamDocsSegment.CustomBlock>().filter { it.type == "step" }
    if (explicitSteps.isNotEmpty() && explicitSteps.size == blocks.size) {
        return explicitSteps.map { step ->
            val meta = parseComponentMeta(step.title)
            StepItemData(
                title = meta.displayTitle,
                tone = meta.string("tone"),
                segments = step.blocks,
            )
        }
    }

    // Standard Fumadocs <Steps> uses ### Headings to delimit steps.
    val result = mutableListOf<StepItemData>()
    var currentTitle: String? = null
    var currentSegments = mutableListOf<ExamDocsSegment>()

    fun flush() {
        if (currentTitle != null || currentSegments.isNotEmpty()) {
            result.add(StepItemData(currentTitle, null, currentSegments))
            currentTitle = null
            currentSegments = mutableListOf()
        }
    }

    for (segment in blocks) {
        val heading = (segment as? ExamDocsSegment.Markdown)?.document?.blocks?.singleOrNull() as? MarkdownBlock.Heading
        if (heading != null) {
            flush()
            currentTitle = heading.content.plainText().trim().ifEmpty { null }
        } else if (segment is ExamDocsSegment.CustomBlock && segment.type == "step") {
            flush()
            val meta = parseComponentMeta(segment.title)
            result.add(StepItemData(meta.displayTitle, meta.string("tone"), segment.blocks))
        } else {
            currentSegments.add(segment)
        }
    }
    flush()
    return result
}
