package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Refresh03
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
    val blankCount = remember(stemBlocks, questionType) {
        if (questionType != "blank") 0
        else stemBlocks.sumOf { countBlankPlaceholders(it) }.coerceAtLeast(1)
    }

    val stateKey = questionId ?: "${questionType}_${number ?: block.hashCode()}"
    var selectedOptions by rememberSaveable(stateKey) { mutableStateOf(emptySet<String>()) }
    var selectedBoolean by rememberSaveable(stateKey) { mutableStateOf<Boolean?>(null) }
    var blankInputs by rememberSaveable(stateKey) { mutableStateOf(List(blankCount) { "" }) }
    var essayDraft by rememberSaveable(stateKey) { mutableStateOf("") }
    var submitted by rememberSaveable(stateKey) { mutableStateOf(false) }
    var showSolution by rememberSaveable(stateKey) { mutableStateOf(false) }

    val correctLabels = remember(options) {
        options.mapIndexedNotNull { idx, opt ->
            val label = opt.attrs["label"] ?: ('A' + idx).toString()
            if (opt.attrs["correct"] == "true") label else null
        }.toSet()
    }

    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: Question number, type, points, tags
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = scheme.primaryContainer,
                ) {
                    Text(
                        text = if (!number.isNullOrBlank()) "题目 $number" else "巩固练习",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = scheme.secondaryContainer,
                ) {
                    Text(
                        text = QUESTION_TYPE_LABELS[questionType] ?: questionType,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (!points.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = scheme.tertiaryContainer,
                    ) {
                        Text(
                            text = "${points}分",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = scheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Stem
            if (stemBlocks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    renderChildren(stemBlocks)
                }
            }

            // Interactive Body
            when {
                (questionType == "single" || questionType == "multiple") && options.isNotEmpty() -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEachIndexed { index, option ->
                            val label = option.attrs["label"] ?: ('A' + index).toString()
                            val isSelected = label in selectedOptions
                            val isCorrect = label in correctLabels
                            QuestionOptionItem(
                                label = label,
                                isMultiple = questionType == "multiple",
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
                            val targetContainer = when {
                                submitted && isCorrectChoice -> scheme.primaryContainer
                                submitted && isSelected && !isCorrectChoice -> scheme.errorContainer
                                isSelected -> scheme.secondaryContainer
                                else -> scheme.surface
                            }
                            val targetBorder = when {
                                submitted && isCorrectChoice -> scheme.primary
                                submitted && isSelected && !isCorrectChoice -> scheme.error
                                isSelected -> scheme.primary
                                else -> scheme.outlineVariant.copy(alpha = 0.5f)
                            }
                            val containerColor by animateColorAsState(targetContainer, label = "boolBg")
                            val borderColor by animateColorAsState(targetBorder, label = "boolBorder")

                            Surface(
                                onClick = {
                                    if (!submitted) {
                                        selectedBoolean = value
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = containerColor,
                                border = BorderStroke(
                                    if (isSelected || (submitted && isCorrectChoice)) 1.5.dp else 0.8.dp,
                                    borderColor,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (value) HugeIcons.Tick01 else HugeIcons.Cancel01,
                                        contentDescription = null,
                                        tint = when {
                                            submitted && isCorrectChoice -> scheme.primary
                                            submitted && isSelected && !isCorrectChoice -> scheme.error
                                            isSelected -> scheme.primary
                                            else -> scheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            submitted && isCorrectChoice -> scheme.onPrimaryContainer
                                            submitted && isSelected && !isCorrectChoice -> scheme.onErrorContainer
                                            isSelected -> scheme.onSecondaryContainer
                                            else -> scheme.onSurface
                                        },
                                    )
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
                                    if (!submitted) {
                                        blankInputs = blankInputs.toMutableList().also { it[idx] = updated }
                                    }
                                },
                                readOnly = submitted,
                                label = { Text("第 ${idx + 1} 空") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                questionType == "essay" -> {
                    OutlinedTextField(
                        value = essayDraft,
                        onValueChange = { essayDraft = it },
                        label = { Text("我的作答 / 解题思路（可选）") },
                        placeholder = { Text("先独立思考或记录要点，再对照参考解析自评。") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val canSubmit = when (questionType) {
                    "single", "multiple" -> selectedOptions.isNotEmpty()
                    "boolean" -> selectedBoolean != null
                    "blank" -> blankInputs.any { it.isNotBlank() }
                    else -> true
                }
                if (!submitted) {
                    Button(
                        onClick = {
                            submitted = true
                            showSolution = true
                        },
                        enabled = canSubmit,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (questionType == "essay") "查看参考解析" else "提交答案")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            submitted = false
                            showSolution = false
                            selectedOptions = emptySet()
                            selectedBoolean = null
                            blankInputs = List(blankCount) { "" }
                            essayDraft = ""
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(HugeIcons.Refresh03, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("重新作答")
                    }
                }

                Spacer(Modifier.weight(1f))

                if (solutionBlock != null) {
                    FilledTonalButton(
                        onClick = { showSolution = !showSolution },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (showSolution) "收起解析" else "查看解析")
                    }
                }
            }

            // Verdict Banner
            if (submitted) {
                QuestionVerdictBanner(
                    questionType = questionType,
                    selectedOptions = selectedOptions,
                    correctLabels = correctLabels,
                    selectedBoolean = selectedBoolean,
                    booleanAnswer = booleanAnswer,
                )
            }

            // Solution Panel
            AnimatedVisibility(
                visible = showSolution && solutionBlock != null,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                solutionBlock?.let { sol ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.surfaceContainer,
                        border = BorderStroke(0.6.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Idea,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = sol.title ?: "参考答案与解析",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.primary,
                                )
                            }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                            renderChildren(sol.blocks)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionVerdictBanner(
    questionType: String,
    selectedOptions: Set<String>,
    correctLabels: Set<String>,
    selectedBoolean: Boolean?,
    booleanAnswer: Boolean?,
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg, icon, text) = when {
        (questionType == "single" || questionType == "multiple") && correctLabels.isNotEmpty() -> {
            val isExact = selectedOptions == correctLabels
            val isPartial = !isExact && selectedOptions.isNotEmpty() && selectedOptions.all { it in correctLabels }
            when {
                isExact -> Quadruple(
                    scheme.primaryContainer.copy(alpha = 0.7f),
                    scheme.onPrimaryContainer,
                    HugeIcons.CheckmarkCircle02,
                    "回答正确 · 答案：${correctLabels.sorted().joinToString("")}",
                )
                isPartial -> Quadruple(
                    scheme.tertiaryContainer.copy(alpha = 0.7f),
                    scheme.onTertiaryContainer,
                    HugeIcons.AlertCircle,
                    "漏选 · 正确答案：${correctLabels.sorted().joinToString("")}",
                )
                else -> Quadruple(
                    scheme.errorContainer.copy(alpha = 0.7f),
                    scheme.onErrorContainer,
                    HugeIcons.Cancel01,
                    "回答有误 · 正确答案：${correctLabels.sorted().joinToString("")}",
                )
            }
        }
        questionType == "boolean" && booleanAnswer != null -> {
            val isCorrect = selectedBoolean == booleanAnswer
            val answerText = if (booleanAnswer) "正确" else "错误"
            if (isCorrect) {
                Quadruple(
                    scheme.primaryContainer.copy(alpha = 0.7f),
                    scheme.onPrimaryContainer,
                    HugeIcons.CheckmarkCircle02,
                    "回答正确 · 答案：$answerText",
                )
            } else {
                Quadruple(
                    scheme.errorContainer.copy(alpha = 0.7f),
                    scheme.onErrorContainer,
                    HugeIcons.Cancel01,
                    "回答有误 · 正确答案：$answerText",
                )
            }
        }
        else -> Quadruple(
            scheme.secondaryContainer.copy(alpha = 0.6f),
            scheme.onSecondaryContainer,
            HugeIcons.Idea,
            "已显示参考解析，请对照自评。",
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = fg,
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun QuestionOptionItem(
    label: String,
    isMultiple: Boolean,
    isSelected: Boolean,
    isCorrect: Boolean,
    submitted: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val targetContainer = when {
        submitted && isCorrect -> scheme.primaryContainer.copy(alpha = 0.65f)
        submitted && isSelected && !isCorrect -> scheme.errorContainer.copy(alpha = 0.65f)
        isSelected -> scheme.secondaryContainer.copy(alpha = 0.75f)
        else -> scheme.surface
    }
    val targetBorder = when {
        submitted && isCorrect -> scheme.primary
        submitted && isSelected && !isCorrect -> scheme.error
        isSelected -> scheme.primary
        else -> scheme.outlineVariant.copy(alpha = 0.45f)
    }
    val containerColor by animateColorAsState(targetContainer, label = "optionBg")
    val borderColor by animateColorAsState(targetBorder, label = "optionBorder")
    val badgeShape = if (isMultiple) RoundedCornerShape(8.dp) else CircleShape

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected || (submitted && isCorrect)) 1.5.dp else 0.8.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(badgeShape)
                    .background(
                        when {
                            submitted && isCorrect -> scheme.primary
                            submitted && isSelected && !isCorrect -> scheme.error
                            isSelected -> scheme.primary
                            else -> scheme.surfaceContainerHigh
                        },
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
            } else if (submitted && isSelected && !isCorrect) {
                Icon(
                    imageVector = HugeIcons.Cancel01,
                    contentDescription = "错误",
                    tint = scheme.error,
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
