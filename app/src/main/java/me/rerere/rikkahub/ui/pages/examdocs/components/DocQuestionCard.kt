package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownInline
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.theme.LocalDarkMode

private enum class QuestionKind(val label: String) {
    Single("单选题"), Multiple("多选题"), Boolean("判断题"), Blank("填空题"), Essay("解答题"),
}

@Serializable
private data class BlankKey(
    val answer: String? = null,
    val aliases: List<String> = emptyList(),
    val kind: String? = null,
) {
    fun matches(input: String): Boolean? = answer?.let {
        val normalized = normalize(input)
        normalize(it) == normalized || aliases.any { alias -> normalize(alias) == normalized }
    }

    private fun normalize(value: String): String = value.trim().lowercase()
        .replace(Regex("""\s+"""), if (kind == "math") "" else " ")
}

private data class AnswerChoice(
    val label: String,
    val blocks: List<ExamDocsSegment> = emptyList(),
    val text: String? = null,
    val correct: Boolean = false,
)

private enum class ChoiceState { Idle, Selected, Correct, Incorrect }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocQuestionCard(
    block: ExamDocsSegment.CustomBlock,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = remember(block.attrs["type"]) {
        QuestionKind.entries.firstOrNull { it.name.equals(block.attrs["type"], ignoreCase = true) }
            ?: QuestionKind.Essay
    }
    val options = remember(block, kind) {
        if (kind == QuestionKind.Boolean) {
            val answer = block.attrs["answer"]?.lowercase()?.toBooleanStrictOrNull()
            listOf(
                AnswerChoice("A", text = "正确", correct = answer == true),
                AnswerChoice("B", text = "错误", correct = answer == false),
            )
        } else {
            block.blocks.filterIsInstance<ExamDocsSegment.CustomBlock>()
                .filter { it.type == "option" }
                .mapIndexed { index, option ->
                    AnswerChoice(
                        option.attrs["label"] ?: ('A' + index).toString(),
                        option.blocks,
                        correct = option.attrs["correct"] == "true",
                    )
                }
        }
    }
    val solution = remember(block) {
        block.blocks.filterIsInstance<ExamDocsSegment.CustomBlock>().firstOrNull { it.type == "solution" }
    }
    val stem = remember(block) {
        block.blocks.filterNot {
            it is ExamDocsSegment.CustomBlock && (it.type == "option" || it.type == "solution")
        }
    }
    val blankKeys = remember(block.attrs["blanks"]) {
        block.attrs["blanks"]?.let { runCatching { Json.decodeFromString<List<BlankKey>>(it) }.getOrNull() }.orEmpty()
    }
    val blankCount = remember(kind, stem, blankKeys) {
        if (kind == QuestionKind.Blank) maxOf(1, blankKeys.size, stem.sumOf(::countBlankPlaceholders)) else 0
    }
    val id = block.attrs["id"]
    val correct = remember(options) { options.filter(AnswerChoice::correct).map(AnswerChoice::label).toSet() }
    var selection by rememberSaveable(id) { mutableStateOf(arrayListOf<String>()) }
    var blanks by rememberSaveable(id, blankCount) { mutableStateOf(ArrayList(List(blankCount) { "" })) }
    var draft by rememberSaveable(id) { mutableStateOf("") }
    var submitted by rememberSaveable(id) { mutableStateOf(false) }
    var showSolution by rememberSaveable(id) { mutableStateOf(false) }
    val multiple = kind == QuestionKind.Multiple
    val canSubmit = if (blankCount > 0) blanks.all(String::isNotBlank) else selection.isNotEmpty()
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            QuestionHeader(block = block, kind = kind)

            if (stem.isNotEmpty()) {
                renderChildren(stem)
            }

            if (options.isNotEmpty()) {
                Column(
                    modifier = if (multiple) Modifier else Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { option ->
                        val selected = option.label in selection
                        val state = when {
                            submitted && option.correct -> ChoiceState.Correct
                            submitted && correct.isNotEmpty() && selected -> ChoiceState.Incorrect
                            selected -> ChoiceState.Selected
                            else -> ChoiceState.Idle
                        }
                        QuestionChoice(
                            option = option,
                            state = state,
                            selected = selected,
                            multiple = multiple,
                            enabled = !submitted,
                            onSelect = {
                                selection = when {
                                    !multiple -> arrayListOf(option.label)
                                    selected -> ArrayList(selection - option.label)
                                    else -> ArrayList(selection + option.label)
                                }
                            },
                            renderChildren = renderChildren,
                        )
                    }
                }
            }

            if (blankCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    blanks.forEachIndexed { index, value ->
                        val blankKey = blankKeys.getOrNull(index)
                        val match = if (submitted) blankKey?.matches(value) else null
                        OutlinedTextField(
                            value = value,
                            onValueChange = { updated -> blanks = ArrayList(blanks).apply { set(index, updated) } },
                            label = { Text("第 ${index + 1} 空") },
                            readOnly = submitted,
                            isError = match == false,
                            trailingIcon = if (match != null) {
                                {
                                    Icon(
                                        imageVector = if (match) HugeIcons.Tick01 else HugeIcons.Cancel01,
                                        contentDescription = null,
                                        tint = if (match) answerSuccessColor() else scheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else null,
                            supportingText = if (match != null) {
                                {
                                    val hint = if (match) {
                                        "与参考答案一致"
                                    } else {
                                        blankKey?.answer?.let { "参考答案：$it" } ?: "请对照参考答案"
                                    }
                                    Text(hint)
                                }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (kind == QuestionKind.Essay) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("作答草稿") },
                    placeholder = { Text("先独立完成，再对照参考解析自评") },
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(
                visible = submitted,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                QuestionFeedback(
                    selection = selection,
                    correct = correct,
                    blankCount = blankCount,
                    blankKeys = blankKeys,
                    blanks = blanks,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (submitted) {
                    TextButton(
                        onClick = {
                            selection = arrayListOf()
                            blanks = ArrayList(List(blankCount) { "" })
                            submitted = false
                        },
                        shape = CircleShape,
                    ) {
                        Text("重做")
                    }
                } else if (options.isNotEmpty() || blankCount > 0) {
                    Button(
                        onClick = { submitted = true },
                        enabled = canSubmit,
                        shape = CircleShape,
                    ) {
                        Text("核对答案")
                    }
                }
                if (solution != null) {
                    FilledTonalButton(
                        onClick = { showSolution = !showSolution },
                        shape = CircleShape,
                    ) {
                        Text(if (showSolution) "收起解析" else "查看解析")
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            imageVector = if (showSolution) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showSolution && solution != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                if (solution != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                        Surface(
                            color = scheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = solution.title ?: "参考答案与解析",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.primary,
                                )
                                renderChildren(solution.blocks)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionHeader(block: ExamDocsSegment.CustomBlock, kind: QuestionKind) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            block.attrs["number"]?.let { number ->
                Surface(
                    shape = CircleShape,
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ) {
                    Text(
                        text = "第 $number 题",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
            ) {
                Text(
                    text = kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            block.attrs["points"]?.let { points ->
                Surface(
                    shape = CircleShape,
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurfaceVariant,
                ) {
                    Text(
                        text = "$points 分",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        val tags = block.attrs["tags"].orEmpty()
            .split('；', ',')
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = scheme.surfaceContainerHigh.copy(alpha = 0.7f),
                        contentColor = scheme.onSurfaceVariant,
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionChoice(
    option: AnswerChoice,
    state: ChoiceState,
    selected: Boolean,
    multiple: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val success = answerSuccessColor()
    val targetAccent = when (state) {
        ChoiceState.Correct -> success
        ChoiceState.Incorrect -> scheme.error
        ChoiceState.Selected -> scheme.primary
        ChoiceState.Idle -> scheme.onSurfaceVariant
    }
    val targetContainer = when (state) {
        ChoiceState.Correct -> success.copy(alpha = 0.08f)
        ChoiceState.Incorrect -> scheme.error.copy(alpha = 0.08f)
        ChoiceState.Selected -> scheme.primaryContainer.copy(alpha = 0.35f)
        ChoiceState.Idle -> scheme.surface
    }
    val targetBorder = when (state) {
        ChoiceState.Idle -> scheme.outlineVariant.copy(alpha = 0.6f)
        else -> targetAccent
    }
    val accent by animateColorAsState(targetAccent, label = "ChoiceAccent")
    val containerColor by animateColorAsState(targetContainer, label = "ChoiceContainer")
    val borderColor by animateColorAsState(targetBorder, label = "ChoiceBorder")

    val shape = RoundedCornerShape(16.dp)
    val badgeShape = if (multiple) RoundedCornerShape(9.dp) else CircleShape
    val interaction = if (multiple) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onSelect() }
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape).then(interaction),
        shape = shape,
        color = containerColor,
        border = BorderStroke(if (state == ChoiceState.Idle) 1.dp else 1.5.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = badgeShape,
                color = if (state == ChoiceState.Idle) scheme.surfaceContainerHigh else accent.copy(alpha = 0.16f),
                modifier = Modifier.size(30.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(top = 3.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (option.text != null) {
                    Text(option.text, style = MaterialTheme.typography.bodyMedium)
                } else {
                    renderChildren(option.blocks)
                }
                if (state == ChoiceState.Correct || state == ChoiceState.Incorrect) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (state == ChoiceState.Correct) HugeIcons.Tick01 else HugeIcons.Cancel01,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (state == ChoiceState.Correct) "正确选项" else "所选项有误",
                            color = accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionFeedback(
    selection: List<String>,
    correct: Set<String>,
    blankCount: Int,
    blankKeys: List<BlankKey>,
    blanks: List<String>,
) {
    val scheme = MaterialTheme.colorScheme
    val matched = if (blankCount > 0) blanks.mapIndexed { index, input -> blankKeys.getOrNull(index)?.matches(input) } else emptyList()
    val exact = if (blankCount > 0) matched.all { it == true } else correct.isNotEmpty() && selection.toSet() == correct
    val answerText = if (correct.isNotEmpty()) " · 参考答案：${correct.sorted().joinToString("")}" else ""
    val text = when {
        exact -> "回答正确"
        blankCount > 0 && matched.any { it != null } -> "${matched.count { it == true }}/$blankCount 空与参考答案一致"
        correct.isEmpty() -> "已记录作答，可对照解析自查"
        selection.all { it in correct } -> "还有正确选项未选$answerText"
        else -> "答案有误$answerText"
    }
    val accent = when {
        exact -> answerSuccessColor()
        correct.isEmpty() -> scheme.primary
        else -> scheme.error
    }
    Surface(
        color = accent.copy(alpha = 0.1f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (exact) HugeIcons.Tick01 else HugeIcons.Cancel01,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun answerSuccessColor() = if (LocalDarkMode.current) Color(0xFF7ED99A) else Color(0xFF1F6E3D)

private fun countBlankPlaceholders(segment: ExamDocsSegment): Int = when (segment) {
    is ExamDocsSegment.Markdown -> segment.document.blocks.sumOf { block ->
        if (block is MarkdownBlock.Paragraph) {
            block.content.count { part -> part is MarkdownInline.Code && part.code == "____" }
        } else 0
    }
    is ExamDocsSegment.CustomBlock -> segment.blocks.sumOf(::countBlankPlaceholders)
}
