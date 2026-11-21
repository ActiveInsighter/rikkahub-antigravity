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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.theme.extendColors

@Composable
internal fun DocSteps(
    block: ExamDocsSegment.CustomBlock,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCardSteps = block.type == "cardsteps"
    fun isStep(segment: ExamDocsSegment): Boolean =
        segment is ExamDocsSegment.CustomBlock && (segment.type == "step" || segment.type == "cardstep")

    val stepCount = block.blocks.count(::isStep)
    Column(modifier = modifier.fillMaxWidth()) {
        var stepIndex = 0
        block.blocks.forEach { segment ->
            if (segment !is ExamDocsSegment.CustomBlock || !isStep(segment)) {
                Column(Modifier.padding(bottom = 12.dp)) { renderChildren(listOf(segment)) }
            } else {
                stepIndex++
                if (isCardSteps) {
                    DocCardStepItem(
                        numberLabel = stepIndex.toString().padStart(2, '0'),
                        step = segment,
                        isLast = stepIndex == stepCount,
                        renderChildren = renderChildren,
                    )
                } else {
                    DocTimelineStepItem(
                        number = stepIndex,
                        step = segment,
                        isFirst = stepIndex == 1,
                        isLast = stepIndex == stepCount,
                        renderChildren = renderChildren,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocTimelineStepItem(
    number: Int,
    step: ExamDocsSegment.CustomBlock,
    isFirst: Boolean,
    isLast: Boolean,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
) {
    val extendColors = MaterialTheme.extendColors
    val lineColor = extendColors.gray3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val center = if (layoutDirection == LayoutDirection.Ltr) 15.dp.toPx() else size.width - 15.dp.toPx()
                drawLine(
                    color = lineColor,
                    start = Offset(center, if (isFirst) 15.dp.toPx() else 0f),
                    end = Offset(center, if (isLast) 15.dp.toPx() else size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = extendColors.gray2,
            contentColor = extendColors.gray9,
            border = BorderStroke(1.dp, extendColors.gray3),
            modifier = Modifier.size(30.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 3.dp, bottom = if (isLast) 0.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            step.title?.let { title ->
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
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
    val iconName = step.attrs["icon"]
    val icon = resolveToneIcon(step.attrs["tone"], iconName)
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
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = toneColor, modifier = Modifier.size(18.dp))
                } else if (!iconName.isNullOrBlank()) {
                    Text(iconName, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            )
        }
    }
}
