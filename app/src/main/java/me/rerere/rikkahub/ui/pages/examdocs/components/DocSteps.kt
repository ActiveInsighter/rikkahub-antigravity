package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment

@Composable
internal fun DocSteps(
    block: ExamDocsSegment.CustomBlock,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCardSteps = block.type == "cardsteps"
    val stepItems = block.blocks.filterIsInstance<ExamDocsSegment.CustomBlock>()
        .filter { it.type == "step" || it.type == "cardstep" }

    if (stepItems.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            renderChildren(block.blocks)
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isCardSteps) 10.dp else 0.dp),
    ) {
        stepItems.forEachIndexed { index, step ->
            val numberLabel = (index + 1).toString().padStart(2, '0')
            val isLast = index == stepItems.lastIndex
            if (isCardSteps) {
                DocCardStepItem(
                    numberLabel = numberLabel,
                    step = step,
                    isLast = isLast,
                    renderChildren = renderChildren,
                )
            } else {
                DocTimelineStepItem(
                    number = index + 1,
                    step = step,
                    isLast = isLast,
                    renderChildren = renderChildren,
                )
            }
        }
    }
}

@Composable
private fun DocTimelineStepItem(
    number: Int,
    step: ExamDocsSegment.CustomBlock,
    isLast: Boolean,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (!isLast) {
                    val center = if (layoutDirection == LayoutDirection.Ltr) {
                        13.dp.toPx()
                    } else {
                        size.width - 13.dp.toPx()
                    }
                    drawLine(lineColor, Offset(center, 26.dp.toPx()), Offset(center, size.height), 1.5.dp.toPx())
                }
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(26.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            step.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (step.blocks.isNotEmpty()) {
                renderChildren(step.blocks)
            }
        }
    }
}

@Composable
private fun DocCardStepItem(
    numberLabel: String,
    step: ExamDocsSegment.CustomBlock,
    isLast: Boolean,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
) {
    val toneColor = resolveToneColor(step.attrs["tone"])
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = toneColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = numberLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = toneColor,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    step.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (step.blocks.isNotEmpty()) {
                        renderChildren(step.blocks)
                    }
                }
            }
        }
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            )
        }
    }
}
