package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.plainText
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment

private val QUESTION_TYPE_LABELS = mapOf(
    "single" to "单选题",
    "multiple" to "多选题",
    "boolean" to "判断题",
    "blank" to "填空题",
    "essay" to "解答题",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocQuestionCard(
    block: ExamDocsSegment.CustomBlock,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val questionId = block.attrs["id"]
    val questionType = block.attrs["type"]?.lowercase() ?: "essay"
    val number = block.attrs["number"]
    val points = block.attrs["points"]
    val booleanAnswer = block.attrs["answer"]?.lowercase()?.toBooleanStrictOrNull()
    val tags = remember(block.attrs["tags"]) {
        block.attrs["tags"]?.split('；', '、', ',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
    }

    val options = remember(block.blocks) {
        block.blocks.filterIsInstance<ExamDocsSegment.CustomBlock>().filter { it.type == "option" }
    }
    val solutionBlock = remember(block.blocks) {
        block.blocks.filterIsInstance<ExamDocsSegment.CustomBlock>().firstOrNull { it.type == "solution" }
    }
    val stemBlocks = remember(block.blocks) {
        block.blocks.filterNot {
            it is ExamDocsSegment.CustomBlock && (it.type == "option" || it.type == "solution")
        }
    }
    val blankCount = remember(stemBlocks) {
        if (questionType != "blank") 0
        else stemBlocks.sumOf { countBlankPlaceholders(it) }.coerceAtLeast(1)
    }

    val stateKey = questionId ?: "${questionType}_${number ?: block.hashCode()}"
    var selectedOptions by rememberSaveable(stateKey) { mutableStateOf(emptySet<String>()) }
    var selectedBoolean by rememberSaveable(stateKey) { mutableStateOf<Boolean?>(null) }
    var blankInputs by rememberSaveable(stateKey) { mutableStateOf(List(blankCount) { "" }) }
    var submitted by rememberSaveable(stateKey) { mutableStateOf(false) }
    var showSolution by rememberSaveable(stateKey) { mutableStateOf(false) }

    val correctLabels = remember(options) {
        options.mapIndexedNotNull { idx, opt ->
            val label = opt.attrs["label"] ?: ('A' + idx).toString()
            if (opt.attrs["correct"] == "true") label else null
        }.toSet()
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: Question badge, type, points, tags
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = buildString {
                            append("题目")
                            if (!number.isNullOrBlank()) append(" $number")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = QUESTION_TYPE_LABELS[questionType] ?: questionType,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (!points.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = "${points}分",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Stem
            if (stemBlocks.isNotEmpty()) {
                renderChildren(stemBlocks)
            }

            // Interactive body by question type
            when {
                (questionType == "single" || questionType == "multiple") && options.isNotEmpty() -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEachIndexed { index, option ->
                            val label = option.attrs["label"] ?: ('A' + index).toString()
                            val isSelected = label in selectedOptions
                            val isCorrect = label in correctLabels
                            QuestionOptionItem(
                                label = label,
                                isSelected = isSelected,
                                isCorrect = isCorrect,
                                submitted = submitted,
                                onClick = {
                                    if (!submitted) {
                                        selectedOptions = if (questionType == "single") {
                                            setOf(label)
                                        } else {
                                            if (isSelected) selectedOptions - label else selectedOptions + label
                                        }
                                    }
                                },
                                content = { renderChildren(option.blocks) },
                            )
                        }
                    }
                }
                questionType == "boolean" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf(true to "正确", false to "错误").forEach { (value, label) ->
                            val isSelected = selectedBoolean == value
                            val isCorrectChoice = booleanAnswer == value
                            val containerColor = when {
                                submitted && isCorrectChoice -> MaterialTheme.colorScheme.primaryContainer
                                submitted && isSelected && !isCorrectChoice -> MaterialTheme.colorScheme.errorContainer
                                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                            Surface(
                                onClick = {
                                    if (!submitted) {
                                        selectedBoolean = value
                                        submitted = true
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = containerColor,
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (value) HugeIcons.Tick01 else HugeIcons.Cancel01,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                questionType == "blank" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        blankInputs.forEachIndexed { idx, value ->
                            OutlinedTextField(
                                value = value,
                                onValueChange = { updated ->
                                    blankInputs = blankInputs.toMutableList().also { it[idx] = updated }
                                },
                                label = { Text("第 ${idx + 1} 空") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // Action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (questionType == "single" || questionType == "multiple" || questionType == "blank") {
                    if (!submitted) {
                        Button(
                            onClick = {
                                submitted = true
                                showSolution = true
                            },
                            enabled = when (questionType) {
                                "single", "multiple" -> selectedOptions.isNotEmpty()
                                "blank" -> blankInputs.any { it.isNotBlank() }
                                else -> true
                            },
                        ) {
                            Text("核对答案")
                        }
                    } else {
                        OutlinedButton(onClick = {
                            submitted = false
                            selectedOptions = emptySet()
                            selectedBoolean = null
                        }) {
                            Text("重做")
                        }
                    }
                } else if (questionType == "boolean" && submitted) {
                    OutlinedButton(onClick = {
                        submitted = false
                        selectedBoolean = null
                    }) {
                        Text("重做")
                    }
                }

                Spacer(Modifier.weight(1f))

                if (solutionBlock != null) {
                    FilledTonalButton(onClick = { showSolution = !showSolution }) {
                        Text(if (showSolution) "收起解析" else "查看解析")
                    }
                }
            }

            if (submitted && (questionType == "single" || questionType == "multiple") && correctLabels.isNotEmpty()) {
                val isExact = selectedOptions == correctLabels
                val isPartial = !isExact && selectedOptions.isNotEmpty() && selectedOptions.all { it in correctLabels }
                val statusText = when {
                    isExact -> "回答正确"
                    isPartial -> "漏选（正确答案：${correctLabels.sorted().joinToString("")}）"
                    else -> "回答有误（正确答案：${correctLabels.sorted().joinToString("")}）"
                }
                val statusColor = if (isExact) {
                    MaterialTheme.colorScheme.primary
                } else if (isPartial) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.error
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                )
            }

            AnimatedVisibility(visible = showSolution && solutionBlock != null) {
                solutionBlock?.let { sol ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = sol.title ?: "参考答案与解析",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            renderChildren(sol.blocks)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionOptionItem(
    label: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    submitted: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = when {
        submitted && isCorrect -> scheme.primaryContainer.copy(alpha = 0.65f)
        submitted && isSelected && !isCorrect -> scheme.errorContainer.copy(alpha = 0.65f)
        isSelected -> scheme.secondaryContainer.copy(alpha = 0.7f)
        else -> scheme.surface
    }
    val borderColor = when {
        submitted && isCorrect -> scheme.primary
        submitted && isSelected && !isCorrect -> scheme.error
        isSelected -> scheme.primary
        else -> scheme.outlineVariant.copy(alpha = 0.45f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected || (submitted && isCorrect)) 1.4.dp else 0.8.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            submitted && isCorrect -> scheme.primary
                            submitted && isSelected && !isCorrect -> scheme.error
                            isSelected -> scheme.primary
                            else -> scheme.surfaceContainerHigh
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected || (submitted && isCorrect)) {
                        scheme.onPrimary
                    } else {
                        scheme.onSurfaceVariant
                    },
                )
            }
            Box(Modifier.weight(1f)) {
                content()
            }
            if (submitted && isCorrect) {
                Icon(
                    imageVector = HugeIcons.CheckmarkCircle02,
                    contentDescription = "正确",
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun countBlankPlaceholders(segment: ExamDocsSegment): Int = when (segment) {
    is ExamDocsSegment.Markdown -> segment.document.blocks.sumOf { block ->
        when (block) {
            is MarkdownBlock.Paragraph -> "____".toRegex().findAll(block.content.plainText()).count()
            else -> 0
        }
    }
    is ExamDocsSegment.CustomBlock -> segment.blocks.sumOf(::countBlankPlaceholders)
}
