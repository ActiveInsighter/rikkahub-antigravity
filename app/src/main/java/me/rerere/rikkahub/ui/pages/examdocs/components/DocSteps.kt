package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.theme.extendColors

private val StepNodeSize = 28.dp
private val StepContentInset = 40.dp

@Composable
internal fun DocSteps(
    block: ExamDocsSegment.CustomBlock,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCardSteps = block.type == "cardsteps"
    val hasSteps = block.blocks.any { it is ExamDocsSegment.CustomBlock && it.isStep() }
    val colors = MaterialTheme.extendColors
    Column(
        modifier = modifier.fillMaxWidth().drawBehind {
            if (hasSteps) {
                val inset = StepNodeSize.toPx() / 2f
                val x = if (layoutDirection == LayoutDirection.Ltr) inset else size.width - inset
                drawLine(colors.gray3, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
        },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        var stepNumber = 0
        for (segment in block.blocks) {
            if (segment is ExamDocsSegment.CustomBlock && segment.isStep()) {
                stepNumber++
                Row(horizontalArrangement = Arrangement.spacedBy(StepContentInset - StepNodeSize)) {
                    Surface(
                        modifier = Modifier.size(StepNodeSize),
                        shape = CircleShape,
                        color = colors.gray2,
                        contentColor = colors.gray9,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isCardSteps) stepNumber.toString().padStart(2, '0') else stepNumber.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (isCardSteps) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            StepContent(segment, renderChildren, Modifier.padding(14.dp), showIcon = true)
                        }
                    } else {
                        StepContent(segment, renderChildren, Modifier.weight(1f).padding(top = 3.dp))
                    }
                }
            } else {
                Box(Modifier.padding(start = if (hasSteps) StepContentInset else 0.dp)) {
                    renderChildren(listOf(segment))
                }
            }
        }
    }
}

private fun ExamDocsSegment.CustomBlock.isStep(): Boolean = type == "step" || type == "cardstep"

@Composable
private fun StepContent(
    step: ExamDocsSegment.CustomBlock,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
    modifier: Modifier = Modifier,
    showIcon: Boolean = false,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            step.title?.let { title ->
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (step.blocks.isNotEmpty()) {
                renderChildren(step.blocks)
            }
        }
        if (showIcon) {
            val iconName = step.attrs["icon"]
            val icon = resolveToneIcon(step.attrs["tone"], iconName)
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = resolveToneColor(step.attrs["tone"]), modifier = Modifier.size(18.dp))
            } else if (!iconName.isNullOrBlank()) {
                Text(iconName, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
