package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    // ArrayList is supported by the SavedStateRegistry, including after lazy items leave the viewport.
    var selection by rememberSaveable(id) { mutableStateOf(arrayListOf<String>()) }
    var blanks by rememberSaveable(id, blankCount) { mutableStateOf(ArrayList(List(blankCount) { "" })) }
    var draft by rememberSaveable(id) { mutableStateOf("") }
    var submitted by rememberSaveable(id) { mutableStateOf(false) }
    var showSolution by rememberSaveable(id) { mutableStateOf(false) }
    val multiple = kind == QuestionKind.Multiple
    val canSubmit = if (blankCount > 0) blanks.all(String::isNotBlank) else selection.isNotEmpty()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            QuestionHeader(block, kind)
            renderChildren(stem)

            if (options.isNotEmpty()) {
                Column(
                    modifier = if (multiple) Modifier else Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
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
            blanks.forEachIndexed { index, value ->
                val match = if (submitted) blankKeys.getOrNull(index)?.matches(value) else null
                OutlinedTextField(
                    value = value,
                    onValueChange = { updated -> blanks = ArrayList(blanks).apply { set(index, updated) } },
                    label = { Text("第 ${index + 1} 空") },
                    readOnly = submitted,
                    isError = match == false,
                    supportingText = if (match != null) {
                        { Text(if (match) "与参考答案一致" else "请对照参考答案，也可能是等价写法") }
                    } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (kind == QuestionKind.Essay) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("作答草稿") },
                    minLines = 2,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (submitted) {
                QuestionFeedback(selection, correct, blankCount, blankKeys, blanks)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (submitted) {
                    TextButton(onClick = {
                        selection = arrayListOf()
                        blanks = ArrayList(List(blankCount) { "" })
                        submitted = false
                    }) { Text("重做") }
                } else if (options.isNotEmpty() || blankCount > 0) {
                    Button(onClick = { submitted = true }, enabled = canSubmit) { Text("核对答案") }
                }
                if (solution != null) {
                    FilledTonalButton(onClick = { showSolution = true }) { Text("查看解析") }
                }
            }
        }
    }

    if (showSolution && solution != null) {
        ModalBottomSheet(
            onDismissRequest = { showSolution = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(solution.title ?: "参考答案与解析", style = MaterialTheme.typography.headlineSmall)
                block.attrs["number"]?.let {
                    Text("第 ${it} 题 · ${kind.label}", style = MaterialTheme.typography.labelLarge)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                renderChildren(solution.blocks)
            }
        }
    }
}

@Composable
private fun QuestionHeader(block: ExamDocsSegment.CustomBlock, kind: QuestionKind) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                block.attrs["number"]?.let { "第 ${it} 题" } ?: kind.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (block.attrs["number"] != null) {
                Text(kind.label, style = MaterialTheme.typography.labelLarge, color = scheme.primary, modifier = Modifier.padding(top = 3.dp))
            }
            block.attrs["points"]?.let {
                Text("$it 分", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
            }
        }
        val tags = block.attrs["tags"].orEmpty().split('；').map(String::trim).filter(String::isNotEmpty)
        if (tags.isNotEmpty()) {
            Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
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
    val accent = when (state) {
        ChoiceState.Correct -> success
        ChoiceState.Incorrect -> scheme.error
        ChoiceState.Selected -> scheme.primary
        ChoiceState.Idle -> scheme.onSurfaceVariant
    }
    val tint = when (state) {
        ChoiceState.Correct -> success.copy(alpha = 0.12f)
        ChoiceState.Incorrect -> scheme.error.copy(alpha = 0.12f)
        ChoiceState.Selected -> scheme.primary.copy(alpha = 0.12f)
        ChoiceState.Idle -> Color.Transparent
    }
    val shape = MaterialTheme.shapes.large
    val interaction = if (multiple) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onSelect() }
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape).then(interaction),
        shape = shape,
        color = scheme.surface,
        border = BorderStroke(1.dp, if (state == ChoiceState.Idle) scheme.outlineVariant else accent),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(shape = if (multiple) MaterialTheme.shapes.small else CircleShape, color = tint,
                modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(option.label, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f).padding(vertical = 3.dp)) {
                if (option.text != null) Text(option.text) else renderChildren(option.blocks)
                if (state == ChoiceState.Correct || state == ChoiceState.Incorrect) {
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(if (state == ChoiceState.Correct) HugeIcons.Tick01 else HugeIcons.Cancel01,
                            contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                        Text(if (state == ChoiceState.Correct) "正确选项" else "所选项有误",
                            color = accent, style = MaterialTheme.typography.labelSmall)
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
    val text = when {
        exact -> "回答正确"
        blankCount > 0 && matched.any { it != null } -> "${matched.count { it == true }}/$blankCount 空与参考答案一致"
        correct.isEmpty() -> "已记录作答，可对照解析自查"
        selection.all { it in correct } -> "还有正确选项未选"
        else -> "答案有误"
    }
    val accent = if (exact) answerSuccessColor() else scheme.onSurfaceVariant
    Surface(color = accent.copy(alpha = 0.1f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(text, color = accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun answerSuccessColor() = if (LocalDarkMode.current) Color(0xFF9DD6AB) else Color(0xFF28613C)

private fun countBlankPlaceholders(segment: ExamDocsSegment): Int = when (segment) {
    is ExamDocsSegment.Markdown -> segment.document.blocks.sumOf { block ->
        if (block is MarkdownBlock.Paragraph) {
            block.content.count { part -> part is MarkdownInline.Code && part.code == "____" }
        } else 0
    }
    is ExamDocsSegment.CustomBlock -> segment.blocks.sumOf(::countBlankPlaceholders)
}
