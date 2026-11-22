package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import me.rerere.hugeicons.stroke.Tag01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.theme.LocalDarkMode

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

private enum class GradeResult { Correct, Partial, Incorrect }

private enum class ChoiceFeedback {
    Idle,
    Selected,
    Correct,
    Incorrect,
    Missed,
    Answer,
    Dimmed,
}

private data class ExamTone(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val softBadge: Color,
    val border: Color,
)

@Composable
private fun rememberExamTone(result: GradeResult): ExamTone {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkMode.current
    return remember(result, scheme, dark) {
        when (result) {
            GradeResult.Correct -> {
                val base = if (dark) Color(0xFF75D1A2) else Color(0xFF287855)
                val accent = lerp(base, scheme.primary, 0.14f)
                ExamTone(
                    accent = accent,
                    onAccent = if (dark) Color(0xFF0A2E1D) else Color.White,
                    container = lerp(scheme.surfaceContainerHigh, accent, if (dark) 0.22f else 0.14f),
                    onContainer = lerp(scheme.onSurface, accent, if (dark) 0.55f else 0.65f),
                    softBadge = lerp(scheme.surfaceContainerHighest, accent, if (dark) 0.30f else 0.20f),
                    border = accent.copy(alpha = if (dark) 0.48f else 0.40f),
                )
            }
            GradeResult.Partial -> {
                val base = if (dark) Color(0xFFE0B76A) else Color(0xFF9A6515)
                val accent = lerp(base, scheme.tertiary, 0.15f)
                ExamTone(
                    accent = accent,
                    onAccent = if (dark) Color(0xFF332000) else Color.White,
                    container = lerp(scheme.surfaceContainerHigh, accent, if (dark) 0.20f else 0.13f),
                    onContainer = lerp(scheme.onSurface, accent, if (dark) 0.55f else 0.65f),
                    softBadge = lerp(scheme.surfaceContainerHighest, accent, if (dark) 0.28f else 0.19f),
                    border = accent.copy(alpha = if (dark) 0.48f else 0.40f),
                )
            }
            GradeResult.Incorrect -> {
                ExamTone(
                    accent = scheme.error,
                    onAccent = scheme.onError,
                    container = lerp(scheme.surfaceContainerHigh, scheme.errorContainer, if (dark) 0.55f else 0.68f),
                    onContainer = scheme.onErrorContainer,
                    softBadge = scheme.errorContainer,
                    border = scheme.error.copy(alpha = if (dark) 0.50f else 0.42f),
                )
            }
        }
    }
}

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
    val hasAttempt = submitted || selfGradeLetter != null
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)),
        color = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, scheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            QuestionHeader(
                number = number,
                kind = kind,
                points = points,
                sourceName = sourceName,
            )

            renderChildren(stem)

            if (kind == QuestionKind.Boolean && options.size == 2) {
                BooleanChoiceRow(
                    options = options,
                    selection = selection,
                    submitted = submitted,
                    hasKey = correct.isNotEmpty(),
                    onSelect = { label ->
                        selection = arrayListOf(label)
                        submitted = true
                    },
                )
            } else if (options.isNotEmpty()) {
                Column(
                    modifier = if (multiple) Modifier else Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { option ->
                        val selected = option.label in selection
                        val feedback = resolveOptionFeedback(
                            selected = selected,
                            isCorrect = option.correct,
                            submitted = submitted,
                            hasKey = correct.isNotEmpty(),
                            multiple = multiple,
                        )
                        QuestionChoice(
                            option = option,
                            feedback = feedback,
                            selected = selected,
                            multiple = multiple,
                            enabled = !submitted,
                            onSelect = {
                                if (autoSubmitChoice) {
                                    selection = arrayListOf(option.label)
                                    submitted = true
                                } else {
                                    selection = if (selected) {
                                        ArrayList(selection - option.label)
                                    } else {
                                        ArrayList(selection + option.label)
                                    }
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
                        val match = if (submitted) blankKeys.getOrNull(index)?.matches(value) else null
                        val tone = when (match) {
                            true -> rememberExamTone(GradeResult.Correct)
                            false -> rememberExamTone(GradeResult.Incorrect)
                            null -> null
                        }
                        OutlinedTextField(
                            value = value,
                            onValueChange = { updated -> blanks = ArrayList(blanks).apply { set(index, updated) } },
                            label = { Text(if (blankCount > 1) "第 ${index + 1} 空" else "填写答案") },
                            readOnly = submitted,
                            isError = match == false,
                            supportingText = if (match != null) {
                                {
                                    Text(
                                        text = if (match) "与参考答案一致" else "请对照参考答案，也可能是等价写法",
                                        color = tone?.onContainer ?: scheme.onSurfaceVariant,
                                    )
                                }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = scheme.surfaceContainerLowest,
                                focusedContainerColor = scheme.surfaceContainerLowest,
                                unfocusedBorderColor = scheme.outlineVariant.copy(alpha = 0.55f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (kind == QuestionKind.Essay) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("作答草稿（可选）") },
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = scheme.surfaceContainerLowest,
                        focusedContainerColor = scheme.surfaceContainerLowest,
                        unfocusedBorderColor = scheme.outlineVariant.copy(alpha = 0.55f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 底部交互栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (kind == QuestionKind.Essay) {
                        val pickedGrade = SelfGradeOption.entries.firstOrNull { it.letter == selfGradeLetter }
                        AnimatedContent(
                            targetState = pickedGrade,
                            transitionSpec = {
                                (fadeIn(tween(220)) + slideInVertically { it / 4 }) togetherWith
                                    (fadeOut(tween(160)) + slideOutVertically { -it / 4 }) using
                                    SizeTransform(clip = false)
                            },
                            label = "EssaySelfGrade",
                        ) { grade ->
                            if (grade != null) {
                                SelfGradeResultPill(grade)
                            } else {
                                SelfGradeRow(
                                    selectedLetter = selfGradeLetter,
                                    onSelect = { selfGradeLetter = it },
                                )
                            }
                        }
                    } else {
                        AnimatedContent(
                            targetState = submitted,
                            transitionSpec = {
                                (fadeIn(tween(240)) + slideInVertically { it / 3 }) togetherWith
                                    (fadeOut(tween(160)) + slideOutVertically { -it / 3 }) using
                                    SizeTransform(clip = false)
                            },
                            label = "QuestionBottomState",
                        ) { isSubmitted ->
                            when {
                                isSubmitted -> {
                                    QuestionFeedback(
                                        kind = kind,
                                        selection = selection,
                                        correct = correct,
                                        blankCount = blankCount,
                                        blankKeys = blankKeys,
                                        blanks = blanks,
                                    )
                                }
                                multiple || blankCount > 0 -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Button(
                                            onClick = { submitted = true },
                                            enabled = canSubmit,
                                            shape = CircleShape,
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                            modifier = Modifier.height(38.dp),
                                        ) {
                                            Icon(
                                                imageVector = HugeIcons.Tick02,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "提交答案",
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                        val hintText = if (multiple) {
                                            if (selection.isEmpty()) "可多选" else "已选 ${selection.size} 项"
                                        } else {
                                            "填完后提交"
                                        }
                                        Text(
                                            text = hintText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnimatedVisibility(
                        visible = hasAttempt,
                        enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 450f)) + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                selection = arrayListOf()
                                blanks = ArrayList(List(blankCount) { "" })
                                submitted = false
                                selfGradeLetter = null
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = scheme.surfaceContainerHigh,
                                contentColor = scheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Refresh03,
                                contentDescription = "重做",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (solution != null) {
                        FilledTonalButton(
                            onClick = { showSolution = true },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = scheme.secondaryContainer,
                                contentColor = scheme.onSecondaryContainer,
                            ),
                            modifier = Modifier
                                .height(38.dp)
                                .semantics(mergeDescendants = true) { contentDescription = "查看解答" },
                        ) {
                            Icon(
                                imageVector = HugeIcons.Book02,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("解析", style = MaterialTheme.typography.labelLarge)
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
            containerColor = scheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        QuestionHeader(
                            number = number,
                            kind = kind,
                            points = points,
                            sourceName = sourceName,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { showSolution = false },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = scheme.surfaceContainerHigh,
                            contentColor = scheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(HugeIcons.Cancel01, "关闭解答", modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))

                if (kind != QuestionKind.Essay && (correct.isNotEmpty() || blankKeys.any { !it.answer.isNullOrBlank() })) {
                    val correctTone = rememberExamTone(GradeResult.Correct)
                    val answerLabel = when {
                        kind == QuestionKind.Boolean -> if (correct.contains("A")) "正确" else "错误"
                        correct.isNotEmpty() -> correct.sorted().joinToString("")
                        else -> blankKeys.mapNotNull { it.answer?.takeIf(String::isNotBlank) }.joinToString("；")
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = correctTone.container,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.CheckmarkCircle02,
                                contentDescription = null,
                                tint = correctTone.accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "参考答案",
                                style = MaterialTheme.typography.labelLarge,
                                color = correctTone.onContainer.copy(alpha = 0.85f),
                            )
                            Text(
                                text = answerLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = correctTone.onContainer,
                            )
                        }
                    }
                }

                Text(
                    text = solution.title ?: if (kind == QuestionKind.Essay) "参考解答" else "解析",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                renderChildren(solution.blocks)

                if (kind == QuestionKind.Essay) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("自评", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        SelfGradeRow(
                            selectedLetter = selfGradeLetter,
                            onSelect = { selfGradeLetter = it },
                        )
                    }
                }

                val extraTags = parsedTags.point + parsedTags.type
                if (extraTags.isNotEmpty()) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("考点与标签", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            extraTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = scheme.surfaceContainerHigh,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Icon(
                                            imageVector = HugeIcons.Tag01,
                                            contentDescription = null,
                                            tint = scheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = tag.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = scheme.onSurface,
                                        )
                                    }
                                }
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
private fun QuestionHeader(
    number: String?,
    kind: QuestionKind,
    points: String?,
    sourceName: String?,
) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Surface(
                shape = if (number.length <= 2) CircleShape else RoundedCornerShape(10.dp),
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                modifier = Modifier.defaultMinSize(minWidth = 28.dp, minHeight = 28.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.height(28.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = kind.icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
                if (points != null) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$points 分",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (sourceName != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Book02,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
): ChoiceFeedback {
    if (!submitted) return if (selected) ChoiceFeedback.Selected else ChoiceFeedback.Idle
    if (!hasKey) return if (selected) ChoiceFeedback.Selected else ChoiceFeedback.Dimmed
    return when {
        selected && isCorrect -> ChoiceFeedback.Correct
        selected && !isCorrect -> ChoiceFeedback.Incorrect
        !selected && isCorrect -> if (multiple) ChoiceFeedback.Missed else ChoiceFeedback.Answer
        else -> ChoiceFeedback.Dimmed
    }
}

@Composable
private fun BooleanChoiceRow(
    options: List<AnswerChoice>,
    selection: List<String>,
    submitted: Boolean,
    hasKey: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            val selected = option.label in selection
            val feedback = resolveOptionFeedback(
                selected = selected,
                isCorrect = option.correct,
                submitted = submitted,
                hasKey = hasKey,
                multiple = false,
            )
            val isTrue = option.label == "A"
            BooleanTile(
                text = option.text ?: if (isTrue) "正确" else "错误",
                icon = if (isTrue) HugeIcons.Tick01 else HugeIcons.Cancel01,
                feedback = feedback,
                selected = selected,
                enabled = !submitted,
                onClick = { onSelect(option.label) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BooleanTile(
    text: String,
    icon: ImageVector,
    feedback: ChoiceFeedback,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val correctTone = rememberExamTone(GradeResult.Correct)
    val incorrectTone = rememberExamTone(GradeResult.Incorrect)
    val tone = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Answer -> correctTone
        ChoiceFeedback.Incorrect -> incorrectTone
        else -> null
    }
    val isDashed = feedback == ChoiceFeedback.Answer

    val targetBg = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Incorrect -> tone!!.container
        ChoiceFeedback.Answer -> tone!!.container.copy(alpha = 0.65f)
        ChoiceFeedback.Selected -> scheme.primaryContainer
        ChoiceFeedback.Dimmed -> scheme.surfaceContainer.copy(alpha = 0.55f)
        ChoiceFeedback.Missed, ChoiceFeedback.Idle -> scheme.surfaceContainer
    }
    val targetBorder = when {
        tone != null -> tone.border
        feedback == ChoiceFeedback.Selected -> scheme.primary.copy(alpha = 0.7f)
        else -> Color.Transparent
    }
    val targetBadgeBg = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Incorrect -> tone!!.accent
        ChoiceFeedback.Answer -> tone!!.softBadge
        ChoiceFeedback.Selected -> scheme.primary
        else -> scheme.surfaceContainerHighest
    }
    val targetBadgeIcon = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Incorrect -> tone!!.onAccent
        ChoiceFeedback.Answer -> tone!!.onContainer
        ChoiceFeedback.Selected -> scheme.onPrimary
        else -> scheme.onSurfaceVariant
    }

    val bgColor by animateColorAsState(targetBg, tween(220), label = "boolBg")
    val borderColor by animateColorAsState(targetBorder, tween(220), label = "boolBorder")
    val badgeBg by animateColorAsState(targetBadgeBg, tween(220), label = "boolBadgeBg")
    val badgeIcon by animateColorAsState(targetBadgeIcon, tween(220), label = "boolBadgeIcon")
    val contentAlpha by animateFloatAsState(if (feedback == ChoiceFeedback.Dimmed) 0.52f else 1f, tween(220), label = "boolAlpha")

    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(shape)
            .background(bgColor)
            .then(
                if (isDashed) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = borderColor,
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                            style = Stroke(
                                width = 1.4.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                            ),
                        )
                    }
                } else if (borderColor != Color.Transparent) {
                    Modifier.border(width = 1.4.dp, color = borderColor, shape = shape)
                } else {
                    Modifier
                },
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .alpha(contentAlpha)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = badgeIcon, modifier = Modifier.size(14.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tone?.onContainer ?: if (feedback == ChoiceFeedback.Selected) scheme.onPrimaryContainer else scheme.onSurface,
            )
            AnimatedVisibility(
                visible = feedback == ChoiceFeedback.Answer && tone != null,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                if (tone != null) {
                    Surface(
                        shape = CircleShape,
                        color = tone.softBadge,
                    ) {
                        Text(
                            text = "正解",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = tone.onContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
    feedback: ChoiceFeedback,
    selected: Boolean,
    multiple: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val correctTone = rememberExamTone(GradeResult.Correct)
    val incorrectTone = rememberExamTone(GradeResult.Incorrect)
    val partialTone = rememberExamTone(GradeResult.Partial)

    val tone = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Answer -> correctTone
        ChoiceFeedback.Incorrect -> incorrectTone
        ChoiceFeedback.Missed -> partialTone
        else -> null
    }
    val isDashed = feedback == ChoiceFeedback.Missed || feedback == ChoiceFeedback.Answer

    val targetBg = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Incorrect -> tone!!.container
        ChoiceFeedback.Missed, ChoiceFeedback.Answer -> tone!!.container.copy(alpha = 0.65f)
        ChoiceFeedback.Selected -> scheme.primaryContainer
        ChoiceFeedback.Dimmed -> scheme.surfaceContainer.copy(alpha = 0.55f)
        ChoiceFeedback.Idle -> scheme.surfaceContainer
    }
    val targetBorder = when {
        tone != null -> tone.border
        feedback == ChoiceFeedback.Selected -> scheme.primary.copy(alpha = 0.72f)
        else -> Color.Transparent
    }
    val targetMarkerBg = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Incorrect -> tone!!.accent
        ChoiceFeedback.Missed, ChoiceFeedback.Answer -> tone!!.softBadge
        ChoiceFeedback.Selected -> scheme.primary
        ChoiceFeedback.Dimmed -> scheme.surfaceContainerHigh
        ChoiceFeedback.Idle -> scheme.surfaceContainerHighest
    }
    val targetMarkerInk = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Incorrect -> tone!!.onAccent
        ChoiceFeedback.Missed, ChoiceFeedback.Answer -> tone!!.onContainer
        ChoiceFeedback.Selected -> scheme.onPrimary
        ChoiceFeedback.Dimmed -> scheme.onSurfaceVariant.copy(alpha = 0.7f)
        ChoiceFeedback.Idle -> scheme.onSurfaceVariant
    }

    val bgColor by animateColorAsState(targetBg, tween(220), label = "choiceBg")
    val borderColor by animateColorAsState(targetBorder, tween(220), label = "choiceBorder")
    val markerBg by animateColorAsState(targetMarkerBg, tween(220), label = "choiceMarkerBg")
    val markerInk by animateColorAsState(targetMarkerInk, tween(220), label = "choiceMarkerInk")
    val contentAlpha by animateFloatAsState(if (feedback == ChoiceFeedback.Dimmed) 0.55f else 1f, tween(220), label = "choiceAlpha")

    val shape = RoundedCornerShape(16.dp)
    val interaction = if (multiple) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onSelect() }
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .then(
                if (isDashed) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = borderColor,
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                            style = Stroke(
                                width = 1.4.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                            ),
                        )
                    }
                } else if (borderColor != Color.Transparent) {
                    Modifier.border(width = 1.4.dp, color = borderColor, shape = shape)
                } else {
                    Modifier
                },
            )
            .then(interaction)
            .alpha(contentAlpha),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(if (multiple) RoundedCornerShape(10.dp) else CircleShape)
                    .background(markerBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = markerInk,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                if (option.text != null) {
                    Text(option.text, style = MaterialTheme.typography.bodyLarge)
                } else {
                    renderChildren(option.blocks)
                }
            }

            AnimatedVisibility(
                visible = feedback in setOf(
                    ChoiceFeedback.Correct,
                    ChoiceFeedback.Incorrect,
                    ChoiceFeedback.Missed,
                    ChoiceFeedback.Answer,
                ),
                enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 450f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                when (feedback) {
                    ChoiceFeedback.Correct, ChoiceFeedback.Incorrect -> {
                        if (tone != null) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(tone.accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (feedback == ChoiceFeedback.Correct) HugeIcons.Tick01 else HugeIcons.Cancel01,
                                    contentDescription = null,
                                    tint = tone.onAccent,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                    ChoiceFeedback.Missed, ChoiceFeedback.Answer -> {
                        if (tone != null) {
                            Surface(
                                shape = CircleShape,
                                color = tone.softBadge,
                            ) {
                                Text(
                                    text = if (feedback == ChoiceFeedback.Missed) "漏选" else "正确答案",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tone.onContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionFeedback(
    kind: QuestionKind,
    selection: List<String>,
    correct: Set<String>,
    blankCount: Int,
    blankKeys: List<BlankKey>,
    blanks: List<String>,
) {
    val scheme = MaterialTheme.colorScheme
    val matched = if (blankCount > 0) blanks.mapIndexed { index, input -> blankKeys.getOrNull(index)?.matches(input) } else emptyList()
    val exact = if (blankCount > 0) matched.all { it == true } else correct.isNotEmpty() && selection.toSet() == correct
    val partial = !exact && (
        (blankCount > 0 && matched.any { it == true }) ||
            (correct.isNotEmpty() && selection.isNotEmpty() && selection.all { it in correct })
        )
    val result = when {
        exact -> GradeResult.Correct
        partial -> GradeResult.Partial
        else -> GradeResult.Incorrect
    }
    val tone = rememberExamTone(result)
    val correctTone = rememberExamTone(GradeResult.Correct)

    val text = when {
        exact -> "回答正确"
        blankCount > 0 && matched.any { it != null } -> "${matched.count { it == true }}/$blankCount 空正确"
        correct.isEmpty() -> "已记录作答"
        partial -> "漏选"
        else -> "回答错误"
    }
    val icon = when (result) {
        GradeResult.Correct -> HugeIcons.CheckmarkCircle02
        GradeResult.Partial -> HugeIcons.Alert02
        GradeResult.Incorrect -> HugeIcons.Alert01
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = tone.container,
        ) {
            Row(
                modifier = Modifier.height(34.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, contentDescription = null, tint = tone.accent, modifier = Modifier.size(16.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tone.onContainer,
                )
            }
        }
        if (!exact && (correct.isNotEmpty() || blankKeys.any { !it.answer.isNullOrBlank() })) {
            val answerLabel = when {
                kind == QuestionKind.Boolean -> if (correct.contains("A")) "正确" else "错误"
                correct.isNotEmpty() -> correct.sorted().joinToString("")
                else -> blankKeys.mapNotNull { it.answer?.takeIf(String::isNotBlank) }.joinToString("；")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "正确答案",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = correctTone.container,
                ) {
                    Text(
                        text = answerLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = correctTone.onContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelfGradeRow(
    selectedLetter: String?,
    onSelect: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text("自评", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        SelfGradeOption.entries.forEach { option ->
            val tone = rememberExamTone(option.result)
            val pressed = selectedLetter == option.letter
            val icon = when (option.result) {
                GradeResult.Correct -> HugeIcons.CheckmarkCircle02
                GradeResult.Partial -> HugeIcons.Alert02
                GradeResult.Incorrect -> HugeIcons.Alert01
            }
            val bg by animateColorAsState(
                targetValue = if (pressed) tone.container else scheme.surfaceContainerHigh,
                animationSpec = tween(180),
                label = "selfGradeBg",
            )
            Surface(
                shape = CircleShape,
                color = bg,
                border = if (pressed) BorderStroke(1.2.dp, tone.border) else null,
                modifier = Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(option.letter) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = tone.accent, modifier = Modifier.size(14.dp))
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (pressed) tone.onContainer else scheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelfGradeResultPill(option: SelfGradeOption) {
    val tone = rememberExamTone(option.result)
    val icon = when (option.result) {
        GradeResult.Correct -> HugeIcons.CheckmarkCircle02
        GradeResult.Partial -> HugeIcons.Alert02
        GradeResult.Incorrect -> HugeIcons.Alert01
    }
    Surface(
        shape = CircleShape,
        color = tone.container,
    ) {
        Row(
            modifier = Modifier.height(34.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tone.accent, modifier = Modifier.size(16.dp))
            Text(
                text = "自评：${option.label}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tone.onContainer,
            )
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
