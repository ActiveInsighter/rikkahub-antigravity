package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import me.rerere.rikkahub.ui.theme.extendColors

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
    val base: Color,
    val ink: Color,
    val tint: Color,
    val line: Color,
    val soft: Color,
)

@Composable
private fun rememberExamTone(result: GradeResult): ExamTone {
    val ext = MaterialTheme.extendColors
    val dark = LocalDarkMode.current
    return when (result) {
        GradeResult.Correct -> ExamTone(
            base = ext.green6,
            ink = if (dark) ext.green9 else ext.green8,
            tint = ext.green1,
            line = ext.green3,
            soft = ext.green2,
        )
        GradeResult.Partial -> ExamTone(
            base = ext.orange6,
            ink = if (dark) ext.orange9 else ext.orange8,
            tint = ext.orange1,
            line = ext.orange3,
            soft = ext.orange2,
        )
        GradeResult.Incorrect -> ExamTone(
            base = ext.red6,
            ink = if (dark) ext.red9 else ext.red8,
            tint = ext.red1,
            line = ext.red3,
            soft = ext.red2,
        )
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
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部栏：仅渲染 序号、题型、分值、来源
            QuestionHeader(
                number = number,
                kind = kind,
                points = points,
                sourceName = sourceName,
            )

            // 中间内容区
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        color = tone?.ink ?: scheme.onSurfaceVariant,
                                    )
                                }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 底部栏：左侧结果/提交/自评，右侧仅图标的 重做 & 查看解答
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        kind == QuestionKind.Essay -> {
                            val pickedGrade = SelfGradeOption.entries.firstOrNull { it.letter == selfGradeLetter }
                            if (pickedGrade != null) {
                                SelfGradeResultPill(pickedGrade)
                            } else {
                                SelfGradeRow(
                                    selectedLetter = selfGradeLetter,
                                    onSelect = { selfGradeLetter = it },
                                )
                            }
                        }
                        submitted -> {
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { submitted = true },
                                    enabled = canSubmit,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp),
                                ) {
                                    Text("提交答案", fontSize = 13.sp)
                                }
                                val hintText = if (multiple) {
                                    if (selection.isEmpty()) "可多选" else "已选 ${selection.size} 项"
                                } else {
                                    "填完后提交"
                                }
                                Text(hintText, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (hasAttempt) {
                        IconButton(
                            onClick = {
                                selection = arrayListOf()
                                blanks = ArrayList(List(blankCount) { "" })
                                submitted = false
                                selfGradeLetter = null
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Refresh03,
                                contentDescription = "重做",
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (solution != null) {
                        FilledTonalIconButton(
                            onClick = { showSolution = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = scheme.surfaceContainerHigh,
                                contentColor = scheme.onSurface,
                            ),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Book02,
                                contentDescription = "查看解答",
                                modifier = Modifier.size(18.dp),
                            )
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
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    IconButton(onClick = { showSolution = false }, modifier = Modifier.size(36.dp)) {
                        Icon(HugeIcons.Cancel01, "关闭解答", modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))

                if (kind != QuestionKind.Essay && (correct.isNotEmpty() || blankKeys.any { !it.answer.isNullOrBlank() })) {
                    val green = rememberExamTone(GradeResult.Correct)
                    val answerLabel = when {
                        kind == QuestionKind.Boolean -> if (correct.contains("A")) "正确" else "错误"
                        correct.isNotEmpty() -> correct.sorted().joinToString("")
                        else -> blankKeys.mapNotNull { it.answer?.takeIf(String::isNotBlank) }.joinToString("；")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("参考答案", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = green.tint,
                            border = BorderStroke(1.dp, green.line),
                        ) {
                            Text(
                                text = answerLabel,
                                color = green.ink,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = if (correct.isNotEmpty() && kind != QuestionKind.Boolean) FontFamily.Serif else FontFamily.Default,
                                fontStyle = if (correct.isNotEmpty() && kind != QuestionKind.Boolean) FontStyle.Italic else FontStyle.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
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
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("考点与标签", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            extraTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, scheme.outlineVariant),
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
                shape = RoundedCornerShape(8.dp),
                color = scheme.onSurface.copy(alpha = 0.07f),
                contentColor = scheme.onSurface.copy(alpha = 0.88f),
                modifier = Modifier.defaultMinSize(minWidth = 28.dp, minHeight = 26.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) {
                    Text(
                        text = number,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.height(26.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = kind.icon,
                    contentDescription = null,
                    tint = scheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = kind.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface.copy(alpha = 0.78f),
                )
                if (points != null) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .width(1.dp)
                            .height(12.dp)
                            .background(scheme.outlineVariant),
                    )
                    Text(
                        text = "$points 分",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (sourceName != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.height(26.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Book02,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = sourceName,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface.copy(alpha = 0.78f),
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
    val green = rememberExamTone(GradeResult.Correct)
    val red = rememberExamTone(GradeResult.Incorrect)
    val tone = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Answer -> green
        ChoiceFeedback.Incorrect -> red
        else -> null
    }
    val isDashed = feedback == ChoiceFeedback.Answer
    val bgColor = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Incorrect -> tone!!.tint
        ChoiceFeedback.Selected -> scheme.onSurface.copy(alpha = 0.045f)
        else -> Color.Transparent
    }
    val borderColor = when {
        tone != null -> tone.line
        feedback == ChoiceFeedback.Selected -> scheme.onSurface.copy(alpha = 0.8f)
        feedback == ChoiceFeedback.Dimmed -> scheme.outlineVariant.copy(alpha = 0.45f)
        else -> scheme.outlineVariant
    }
    val shape = RoundedCornerShape(14.dp)

    Box(modifier = modifier.height(48.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(shape)
                .background(bgColor)
                .then(
                    if (isDashed) {
                        Modifier.drawBehind {
                            drawRoundRect(
                                color = borderColor,
                                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                                style = Stroke(
                                    width = 1.2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                                ),
                            )
                        }
                    } else {
                        Modifier.border(
                            width = if (feedback == ChoiceFeedback.Selected || tone != null) 1.2.dp else 1.dp,
                            color = borderColor,
                            shape = shape,
                        )
                    },
                )
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .alpha(if (feedback == ChoiceFeedback.Dimmed) 0.5f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = tone?.soft ?: scheme.onSurface.copy(alpha = 0.06f),
                    contentColor = tone?.ink ?: scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    text = text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = tone?.ink ?: scheme.onSurface,
                )
            }
        }
        if (feedback == ChoiceFeedback.Answer && tone != null) {
            Surface(
                shape = CircleShape,
                color = tone.soft,
                border = BorderStroke(1.dp, tone.line),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = (-8).dp),
            ) {
                Text(
                    text = "正确答案",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tone.ink,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
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
    val green = rememberExamTone(GradeResult.Correct)
    val red = rememberExamTone(GradeResult.Incorrect)
    val amber = rememberExamTone(GradeResult.Partial)

    val tone = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Answer -> green
        ChoiceFeedback.Incorrect -> red
        ChoiceFeedback.Missed -> amber
        else -> null
    }
    val isDashed = feedback == ChoiceFeedback.Missed || feedback == ChoiceFeedback.Answer
    val bgColor = when (feedback) {
        ChoiceFeedback.Correct, ChoiceFeedback.Incorrect -> tone!!.tint
        ChoiceFeedback.Selected -> scheme.onSurface.copy(alpha = 0.045f)
        else -> Color.Transparent
    }
    val borderColor = when {
        tone != null -> tone.line
        feedback == ChoiceFeedback.Selected -> scheme.onSurface.copy(alpha = 0.8f)
        feedback == ChoiceFeedback.Dimmed -> scheme.outlineVariant.copy(alpha = 0.45f)
        else -> scheme.outlineVariant
    }
    val markerBg = when {
        tone != null -> tone.soft
        feedback == ChoiceFeedback.Selected -> scheme.onSurface
        else -> scheme.onSurface.copy(alpha = 0.06f)
    }
    val markerInk = when {
        tone != null -> tone.ink
        feedback == ChoiceFeedback.Selected -> scheme.surface
        else -> scheme.onSurface.copy(alpha = 0.82f)
    }

    val shape = RoundedCornerShape(14.dp)
    val interaction = if (multiple) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onSelect() }
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
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
                                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                                style = Stroke(
                                    width = 1.2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                                ),
                            )
                        }
                    } else {
                        Modifier.border(
                            width = if (feedback == ChoiceFeedback.Selected || tone != null) 1.2.dp else 1.dp,
                            color = borderColor,
                            shape = shape,
                        )
                    },
                )
                .then(interaction)
                .alpha(if (feedback == ChoiceFeedback.Dimmed) 0.5f else 1f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(modifier = Modifier.size(28.dp)) {
                    Surface(
                        shape = if (multiple) RoundedCornerShape(8.dp) else CircleShape,
                        color = markerBg,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = option.label,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontSize = 17.sp,
                                lineHeight = 18.sp,
                                color = markerInk,
                            )
                        }
                    }
                    if ((feedback == ChoiceFeedback.Correct || feedback == ChoiceFeedback.Incorrect) && tone != null) {
                        val badgeScale by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                            label = "verdictScale",
                        )
                        Surface(
                            shape = CircleShape,
                            color = tone.base,
                            border = BorderStroke(1.5.dp, tone.tint),
                            modifier = Modifier
                                .size(15.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = 3.dp, y = 3.dp)
                                .scale(badgeScale),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (feedback == ChoiceFeedback.Correct) HugeIcons.Tick01 else HugeIcons.Cancel01,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(9.dp),
                                )
                            }
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(top = 3.dp)) {
                    if (option.text != null) {
                        Text(option.text, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        renderChildren(option.blocks)
                    }
                }
            }
        }

        val noteText = when (feedback) {
            ChoiceFeedback.Missed -> "漏选"
            ChoiceFeedback.Answer -> "正确答案"
            else -> null
        }
        if (noteText != null && tone != null) {
            Surface(
                shape = CircleShape,
                color = tone.soft,
                border = BorderStroke(1.dp, tone.line),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = (-8).dp),
            ) {
                Text(
                    text = noteText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tone.ink,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
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
    val green = rememberExamTone(GradeResult.Correct)

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
            color = tone.tint,
            border = BorderStroke(1.dp, tone.line),
        ) {
            Row(
                modifier = Modifier.height(32.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(icon, contentDescription = null, tint = tone.base, modifier = Modifier.size(15.dp))
                Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tone.ink)
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
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("正确答案", fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = green.tint,
                    border = BorderStroke(1.dp, green.line),
                ) {
                    Text(
                        text = answerLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = if (correct.isNotEmpty() && kind != QuestionKind.Boolean) FontFamily.Serif else FontFamily.Default,
                        fontStyle = if (correct.isNotEmpty() && kind != QuestionKind.Boolean) FontStyle.Italic else FontStyle.Normal,
                        color = green.ink,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
        Text("自评", fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
        SelfGradeOption.entries.forEach { option ->
            val tone = rememberExamTone(option.result)
            val pressed = selectedLetter == option.letter
            val icon = when (option.result) {
                GradeResult.Correct -> HugeIcons.CheckmarkCircle02
                GradeResult.Partial -> HugeIcons.Alert02
                GradeResult.Incorrect -> HugeIcons.Alert01
            }
            Surface(
                shape = CircleShape,
                color = if (pressed) tone.tint else Color.Transparent,
                border = BorderStroke(1.dp, if (pressed) tone.base else scheme.outlineVariant),
                modifier = Modifier
                    .height(30.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(option.letter) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = tone.base, modifier = Modifier.size(14.dp))
                    Text(
                        text = option.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (pressed) tone.ink else scheme.onSurface.copy(alpha = 0.8f),
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
        color = tone.tint,
        border = BorderStroke(1.dp, tone.line),
    ) {
        Row(
            modifier = Modifier.height(32.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tone.base, modifier = Modifier.size(15.dp))
            Text("自评：${option.label}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tone.ink)
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
