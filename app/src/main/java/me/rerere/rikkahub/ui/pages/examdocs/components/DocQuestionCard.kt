package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownInline
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.QuillWrite01
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.components.ui.cardGroupItemShape

private enum class QuestionKind(val label: String, val icon: ImageVector) {
    Single("单选题", HugeIcons.CheckmarkCircle02),
    Multiple("多选题", HugeIcons.CheckList),
    Boolean("判断题", HugeIcons.Tick02),
    Blank("填空题", HugeIcons.Edit01),
    Essay("解答题", HugeIcons.QuillWrite01),
}

private enum class SelfGradeOption(val letter: String, val label: String, val result: GradeResult) {
    Correct("A", "正确", GradeResult.Correct),
    Partial("B", "部分正确", GradeResult.Partial),
    Incorrect("C", "错误", GradeResult.Incorrect),
}

private enum class GradeResult(val icon: ImageVector) {
    Correct(HugeIcons.CheckmarkCircle02),
    Partial(HugeIcons.Alert02),
    Incorrect(HugeIcons.Alert01),
    Recorded(HugeIcons.Tick01),
}

private enum class ChoiceFeedback(val icon: ImageVector? = null, val label: String? = null) {
    Idle,
    Selected,
    Correct(HugeIcons.Tick01, "选对"),
    Incorrect(HugeIcons.Cancel01, "选错"),
    Missed(HugeIcons.CheckmarkCircle02, "漏选"),
    Answer(HugeIcons.CheckmarkCircle02, "正确答案"),
}

private data class QuestionResult(val grade: GradeResult, val label: String)

private enum class QuestionTagKind { Type, Source, Point }

private data class QuestionTag(val label: String, val group: String?, val kind: QuestionTagKind)

private data class ParsedQuestionTags(
    val type: List<QuestionTag>,
    val source: List<QuestionTag>,
    val point: List<QuestionTag>,
)

private val typeGroups = setOf("题型", "难度", "标记", "特点")
private val typeKeywords = setOf("易错", "重点", "难点", "难题", "压轴", "高频", "必做", "经典", "基础", "拔高")
private val sourceGroups = setOf("来源", "出处", "试卷", "年份", "科目", "章节")
private val pointGroups = setOf("考点", "知识点", "考察点")
private val tagGroupRegex = Regex("""^([^:：]{1,12})[:：]\s*(.+)$""")
private val paperSuffixRegex = Regex("""^(王道·卷[一二三四五六七八]|竟成·模拟[一二三四五六七八])-(?:Q)?\d+$""")

private fun parseQuestionTags(raw: String?): ParsedQuestionTags {
    if (raw.isNullOrBlank()) return ParsedQuestionTags(emptyList(), emptyList(), emptyList())
    val seen = mutableSetOf<String>()
    val type = mutableListOf<QuestionTag>()
    val source = mutableListOf<QuestionTag>()
    val point = mutableListOf<QuestionTag>()
    raw.split(',', '，', '、', '；').map(String::trim).filter(String::isNotEmpty).forEach { item ->
        val match = tagGroupRegex.matchEntire(item)
        val group = match?.groupValues?.get(1)?.trim()
        val label = (match?.groupValues?.get(2) ?: item).trim()
        if (label.isEmpty()) return@forEach
        val kind = when {
            group != null && group in pointGroups -> QuestionTagKind.Point
            group != null && group in typeGroups -> QuestionTagKind.Type
            group != null && group in sourceGroups -> QuestionTagKind.Source
            group == null && label in typeKeywords -> QuestionTagKind.Type
            else -> QuestionTagKind.Source
        }
        if (!seen.add("${kind.name}:$label")) return@forEach
        val tag = QuestionTag(label, group, kind)
        when (kind) {
            QuestionTagKind.Type -> if (type.size < 12) type.add(tag)
            QuestionTagKind.Source -> if (source.size < 12) source.add(tag)
            QuestionTagKind.Point -> if (point.size < 12) point.add(tag)
        }
    }
    return ParsedQuestionTags(type, source, point)
}

private fun formatSourcePaperName(sources: List<QuestionTag>): String? {
    val filtered = sources.filter { it.group != "科目" && it.group != "章节" }
    if (filtered.isEmpty()) return null
    val first = filtered[0].label
    val second = filtered.getOrNull(1)?.label.orEmpty()
    if (first == "王道8套卷" && Regex("""^第\d+套$""").matches(second)) {
        return first + second
    }
    return paperSuffixRegex.replace(first, "$1")
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
    val parsedTags = remember(block.attrs["tags"]) { parseQuestionTags(block.attrs["tags"]) }
    val sourceName = remember(parsedTags) { formatSourcePaperName(parsedTags.source) }
    val number = block.attrs["number"]?.takeIf(String::isNotBlank)
    val points = block.attrs["points"]?.takeIf(String::isNotBlank)

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
                        label = option.attrs["label"] ?: ('A' + index).toString(),
                        blocks = option.blocks,
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
    var selfGradeLetter by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var showSolution by rememberSaveable(id) { mutableStateOf(false) }

    val multiple = kind == QuestionKind.Multiple
    val autoSubmitChoice = kind == QuestionKind.Single || kind == QuestionKind.Boolean
    val canSubmit = if (blankCount > 0) blanks.all(String::isNotBlank) else selection.isNotEmpty()
    val pickedGrade = SelfGradeOption.entries.firstOrNull { it.letter == selfGradeLetter }
    val focusManager = LocalFocusManager.current
    val blankMatches = remember(submitted, blankKeys, blanks) {
        if (submitted) blanks.mapIndexed { index, value -> blankKeys.getOrNull(index)?.matches(value) }
        else emptyList()
    }
    val result = when {
        pickedGrade != null -> QuestionResult(pickedGrade.result, "自评：${pickedGrade.label}")
        submitted -> gradeAnswer(selection, correct, blankMatches)
        else -> null
    }
    val referenceAnswer = remember(kind, correct, blankKeys) {
        when {
            kind == QuestionKind.Boolean && correct.isNotEmpty() -> if ("A" in correct) "正确" else "错误"
            correct.isNotEmpty() -> correct.sorted().joinToString("、")
            else -> blankKeys.mapNotNull { it.answer?.takeIf(String::isNotBlank) }
                .joinToString("；").takeIf(String::isNotEmpty)
        }
    }
    val hasActions = result != null || multiple || blankCount > 0 || solution != null

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            QuestionHeader(number, kind, points, sourceName)
            renderChildren(stem)

            if (options.isNotEmpty()) {
                Column(
                    modifier = if (multiple) Modifier else Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    options.forEachIndexed { index, option ->
                        val selected = option.label in selection
                        QuestionChoice(
                            option = option,
                            shape = cardGroupItemShape(index, options.size),
                            feedback = resolveOptionFeedback(
                                selected, option.correct, submitted, correct.isNotEmpty(), multiple,
                            ),
                            selected = selected,
                            multiple = multiple,
                            enabled = !submitted,
                            onSelect = {
                                when {
                                    autoSubmitChoice -> {
                                        selection = arrayListOf(option.label)
                                        submitted = true
                                    }
                                    selected -> selection = ArrayList(selection - option.label)
                                    else -> selection = ArrayList(selection + option.label)
                                }
                            },
                            renderChildren = renderChildren,
                        )
                    }
                }
            }

            if (blankCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    blanks.forEachIndexed { index, value ->
                        val match = blankMatches.getOrNull(index)
                        TextField(
                            value = value,
                            onValueChange = { updated -> blanks = ArrayList(blanks).apply { set(index, updated) } },
                            label = { Text(if (blankCount > 1) "第 ${index + 1} 空" else "填写答案") },
                            readOnly = submitted,
                            supportingText = when (match) {
                                true -> { { Text("与参考答案一致") } }
                                false -> { { Text("请核对参考答案，等价写法可能需要自行判断") } }
                                null -> null
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (kind == QuestionKind.Essay) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("作答草稿（可选）") },
                    minLines = 2,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (pickedGrade == null) {
                    SelfGradeChoices(selfGradeLetter) {
                        focusManager.clearFocus()
                        selfGradeLetter = it
                    }
                }
            }

            val motion = MaterialTheme.motionScheme
            AnimatedVisibility(
                visible = result != null,
                enter = expandVertically(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                // Keep the previous content in place while the result animates out on retry.
                var displayedResult by remember { mutableStateOf(result) }
                SideEffect {
                    if (result != null) displayedResult = result
                }
                val visibleResult = result ?: displayedResult
                if (visibleResult != null) {
                    QuestionResultPanel(
                        result = visibleResult,
                        supportingText = referenceAnswer?.takeIf {
                            visibleResult.grade == GradeResult.Partial || visibleResult.grade == GradeResult.Incorrect
                        }?.let { "参考答案  $it" },
                    )
                }
            }

            if (hasActions) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!submitted && (multiple || blankCount > 0)) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                submitted = true
                            },
                            enabled = canSubmit,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text("提交答案")
                        }
                    }
                    if (solution != null) {
                        FilledTonalButton(
                            onClick = {
                                focusManager.clearFocus()
                                showSolution = true
                            },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        ) {
                            Icon(HugeIcons.Book02, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("查看解答")
                        }
                    }
                    if (result != null) {
                        TextButton(
                            onClick = {
                                selection = arrayListOf()
                                blanks = ArrayList(List(blankCount) { "" })
                                submitted = false
                                selfGradeLetter = null
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(HugeIcons.Refresh03, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(if (kind == QuestionKind.Essay) "重新自评" else "重做")
                        }
                    }
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = solution.title ?: if (kind == QuestionKind.Essay) "参考解答" else "解析",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showSolution = false }, shapes = IconButtonDefaults.shapes()) {
                        Icon(HugeIcons.Cancel01, "关闭解答")
                    }
                }
                QuestionHeader(number, kind, points, sourceName)
                if (kind != QuestionKind.Essay && referenceAnswer != null) {
                    QuestionResultPanel(QuestionResult(GradeResult.Correct, "参考答案"), referenceAnswer)
                }

                renderChildren(solution.blocks)

                if (kind == QuestionKind.Essay) {
                    SelfGradeChoices(selfGradeLetter) { selfGradeLetter = it }
                }
                val extraTags = parsedTags.point + parsedTags.type
                if (extraTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("考点与标签", style = MaterialTheme.typography.titleSmallEmphasized)
                        Text(
                            text = extraTags.joinToString("、") { it.label },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionHeader(number: String?, kind: QuestionKind, points: String?, sourceName: String?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(kind.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Column {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (number != null) Text(number, style = MaterialTheme.typography.titleSmallEmphasized)
                Text(kind.label, style = MaterialTheme.typography.titleSmallEmphasized)
                if (points != null) {
                    Text(
                        text = "$points 分",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (sourceName != null) {
                Text(sourceName, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

private fun resolveOptionFeedback(
    selected: Boolean,
    isCorrect: Boolean,
    submitted: Boolean,
    hasKey: Boolean,
    multiple: Boolean,
): ChoiceFeedback = when {
    !submitted || !hasKey -> if (selected) ChoiceFeedback.Selected else ChoiceFeedback.Idle
    selected && isCorrect -> ChoiceFeedback.Correct
    selected -> ChoiceFeedback.Incorrect
    isCorrect -> if (multiple) ChoiceFeedback.Missed else ChoiceFeedback.Answer
    else -> ChoiceFeedback.Idle
}

@Composable
private fun QuestionChoice(
    option: AnswerChoice,
    shape: Shape,
    feedback: ChoiceFeedback,
    selected: Boolean,
    multiple: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val (container, content) = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Answer, ChoiceFeedback.Missed ->
            scheme.primaryContainer to scheme.onPrimaryContainer
        ChoiceFeedback.Selected -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ChoiceFeedback.Incorrect -> scheme.surfaceContainerHighest to scheme.onSurface
        ChoiceFeedback.Idle -> scheme.surfaceContainerHigh to scheme.onSurface
    }
    val containerColor by animateColorAsState(container, motion.fastEffectsSpec(), label = "option container")
    val interaction = if (multiple) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onSelect() }
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape).then(interaction),
        shape = shape,
        color = containerColor,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (option.text == null) {
                Text(option.label, style = MaterialTheme.typography.labelLargeEmphasized)
            }
            Column(Modifier.weight(1f)) {
                if (option.text != null) {
                    Text(option.text, style = MaterialTheme.typography.bodyLarge)
                } else {
                    renderChildren(option.blocks)
                }
                if (feedback == ChoiceFeedback.Missed || feedback == ChoiceFeedback.Answer) {
                    Text(checkNotNull(feedback.label), style = MaterialTheme.typography.labelMedium)
                }
            }

            Crossfade(
                targetState = feedback.takeIf { it.icon != null },
                animationSpec = motion.fastEffectsSpec(),
                label = "option verdict",
            ) { verdict ->
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    when {
                        verdict?.icon != null -> Icon(
                            imageVector = verdict.icon,
                            contentDescription = verdict.label.takeIf {
                                verdict == ChoiceFeedback.Correct || verdict == ChoiceFeedback.Incorrect
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        multiple -> Checkbox(checked = selected, onCheckedChange = null)
                        else -> RadioButton(selected = selected, onClick = null)
                    }
                }
            }
        }
    }
}

private fun gradeAnswer(
    selection: List<String>,
    correct: Set<String>,
    blankMatches: List<Boolean?>,
): QuestionResult {
    if (blankMatches.isNotEmpty()) {
        val matches = blankMatches.count { it == true }
        val checked = blankMatches.count { it != null }
        return when {
            checked == 0 -> QuestionResult(GradeResult.Recorded, "已记录作答")
            matches == blankMatches.size -> QuestionResult(GradeResult.Correct, "回答正确")
            else -> QuestionResult(
                grade = if (matches > 0) GradeResult.Partial else GradeResult.Incorrect,
                label = if (checked == blankMatches.size) "$matches/$checked 空正确"
                else "$matches/$checked 空正确，其余请自行核对",
            )
        }
    }
    return when {
        correct.isEmpty() -> QuestionResult(GradeResult.Recorded, "已记录作答")
        selection.toSet() == correct -> QuestionResult(GradeResult.Correct, "回答正确")
        selection.all { it in correct } -> QuestionResult(GradeResult.Partial, "部分正确，有漏选")
        else -> QuestionResult(GradeResult.Incorrect, "回答错误")
    }
}

@Composable
private fun QuestionResultPanel(result: QuestionResult, supportingText: String?) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (result.grade) {
        GradeResult.Correct -> scheme.primaryContainer to scheme.onPrimaryContainer
        GradeResult.Partial -> scheme.secondaryContainer to scheme.onSecondaryContainer
        GradeResult.Incorrect, GradeResult.Recorded -> scheme.surfaceContainerHighest to scheme.onSurface
    }
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(result.grade.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(result.label, style = MaterialTheme.typography.titleSmallEmphasized)
                if (supportingText != null) {
                    Text(supportingText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelfGradeChoices(selectedLetter: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("自评", style = MaterialTheme.typography.titleSmallEmphasized)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelfGradeOption.entries.forEach { option ->
                FilterChip(
                    selected = selectedLetter == option.letter,
                    onClick = { onSelect(option.letter) },
                    label = { Text(option.label) },
                    leadingIcon = {
                        Icon(option.result.icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                    },
                )
            }
        }
    }
}

private fun countBlankPlaceholders(segment: ExamDocsSegment): Int = when (segment) {
    is ExamDocsSegment.Markdown -> segment.document.blocks.sumOf { block ->
        if (block is MarkdownBlock.Paragraph) {
            block.content.count { part -> part is MarkdownInline.Code && part.code == "____" }
        } else 0
    }
    is ExamDocsSegment.CustomBlock -> segment.blocks.sumOf(::countBlankPlaceholders)
}
