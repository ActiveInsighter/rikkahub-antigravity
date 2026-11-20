package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
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
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.theme.GoogleSansFlex
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.extendColors

private enum class QuestionKind(val label: String) {
    Single("单选题"),
    Multiple("多选题"),
    Boolean("判断题"),
    Blank("填空题"),
    Essay("解答题"),
}

private enum class QuestionTagKind { Type, Source, Point }

private data class QuestionTag(
    val label: String,
    val group: String?,
    val kind: QuestionTagKind,
)

private data class ParsedQuestionTags(
    val type: List<QuestionTag>,
    val source: List<QuestionTag>,
    val point: List<QuestionTag>,
)

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

private enum class ChoiceState { Idle, Selected, Correct, Incorrect, Missed, Answer, Dimmed }

private enum class ResultTone { Correct, Partial, Incorrect }

private data class ToneColors(
    val base: Color,
    val ink: Color,
    val tint: Color,
    val line: Color,
    val soft: Color,
)

@Composable
private fun rememberResultToneColors(tone: ResultTone): ToneColors {
    val ext = MaterialTheme.extendColors
    val dark = LocalDarkMode.current
    return when (tone) {
        ResultTone.Correct -> if (dark) {
            ToneColors(
                base = ext.green5,
                ink = ext.green3,
                tint = ext.green6.copy(alpha = 0.12f),
                line = ext.green5.copy(alpha = 0.48f),
                soft = ext.green6.copy(alpha = 0.24f),
            )
        } else {
            ToneColors(
                base = ext.green6,
                ink = ext.green8,
                tint = ext.green1.copy(alpha = 0.72f),
                line = ext.green4.copy(alpha = 0.75f),
                soft = ext.green2.copy(alpha = 0.65f),
            )
        }
        ResultTone.Partial -> if (dark) {
            ToneColors(
                base = ext.orange5,
                ink = ext.orange3,
                tint = ext.orange6.copy(alpha = 0.12f),
                line = ext.orange5.copy(alpha = 0.48f),
                soft = ext.orange6.copy(alpha = 0.24f),
            )
        } else {
            ToneColors(
                base = ext.orange6,
                ink = ext.orange8,
                tint = ext.orange1.copy(alpha = 0.80f),
                line = ext.orange4.copy(alpha = 0.75f),
                soft = ext.orange2.copy(alpha = 0.65f),
            )
        }
        ResultTone.Incorrect -> if (dark) {
            ToneColors(
                base = ext.red5,
                ink = ext.red3,
                tint = ext.red6.copy(alpha = 0.12f),
                line = ext.red5.copy(alpha = 0.48f),
                soft = ext.red6.copy(alpha = 0.24f),
            )
        } else {
            ToneColors(
                base = ext.red6,
                ink = ext.red8,
                tint = ext.red1.copy(alpha = 0.75f),
                line = ext.red4.copy(alpha = 0.75f),
                soft = ext.red2.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
internal fun DocQuestionCard(
    block: ExamDocsSegment.CustomBlock,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val kind = remember(block.attrs["type"]) {
        QuestionKind.entries.firstOrNull { it.name.equals(block.attrs["type"], ignoreCase = true) }
            ?: QuestionKind.Essay
    }
    val parsedTags = remember(block.attrs["tags"]) { parseQuestionTags(block.attrs["tags"]) }
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
    var selection by rememberSaveable(id) { mutableStateOf(arrayListOf<String>()) }
    var blanks by rememberSaveable(id, blankCount) { mutableStateOf(ArrayList(List(blankCount) { "" })) }
    var draft by rememberSaveable(id) { mutableStateOf("") }
    var selfGrade by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var submitted by rememberSaveable(id) { mutableStateOf(false) }
    var showSolution by rememberSaveable(id) { mutableStateOf(false) }
    val multiple = kind == QuestionKind.Multiple
    val autoGradeOnTap = kind == QuestionKind.Single || kind == QuestionKind.Boolean
    val canSubmit = if (blankCount > 0) blanks.any(String::isNotBlank) else selection.isNotEmpty()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Header: Number, Type + Points, Source (only these)
            QuestionHeader(block = block, kind = kind, sourceTags = parsedTags.source)

            // 2. Content area: Stem + Options / Boolean / Blanks / Essay draft
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                renderChildren(stem)

                if (kind == QuestionKind.Boolean && options.size == 2) {
                    BooleanChoiceRow(
                        options = options,
                        selection = selection,
                        correct = correct,
                        submitted = submitted,
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
                            val state = resolveChoiceState(
                                option = option,
                                selected = selected,
                                submitted = submitted,
                                multiple = multiple,
                                hasCorrectKey = correct.isNotEmpty(),
                            )
                            QuestionChoice(
                                option = option,
                                state = state,
                                selected = selected,
                                multiple = multiple,
                                enabled = !submitted,
                                onSelect = {
                                    if (autoGradeOnTap) {
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

                blanks.forEachIndexed { index, value ->
                    val match = if (submitted) blankKeys.getOrNull(index)?.matches(value) else null
                    OutlinedTextField(
                        value = value,
                        onValueChange = { updated -> blanks = ArrayList(blanks).apply { set(index, updated) } },
                        label = { Text(if (blankCount > 1) "第 ${index + 1} 空" else "填写答案") },
                        readOnly = submitted,
                        isError = match == false,
                        supportingText = if (match != null) {
                            {
                                val okColor = rememberResultToneColors(ResultTone.Correct).ink
                                val errColor = rememberResultToneColors(ResultTone.Incorrect).ink
                                Text(
                                    text = if (match) "与参考答案一致" else "请对照参考答案，也可能是等价写法",
                                    color = if (match) okColor else errColor,
                                )
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (kind == QuestionKind.Essay) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("作答草稿 / 思路记录") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 3. Footer: Left = status/result or submit/self-grade; Right = icon-only redo + view solution
            QuestionFooter(
                kind = kind,
                options = options,
                selection = selection,
                correct = correct,
                blankCount = blankCount,
                blankKeys = blankKeys,
                blanks = blanks,
                selfGrade = selfGrade,
                submitted = submitted,
                canSubmit = canSubmit,
                hasSolution = solution != null,
                onSubmit = { submitted = true },
                onSelfGrade = { grade ->
                    selfGrade = grade
                    submitted = true
                },
                onReset = {
                    selection = arrayListOf()
                    blanks = ArrayList(List(blankCount) { "" })
                    selfGrade = null
                    submitted = false
                },
                onOpenSolution = { showSolution = true },
            )
        }
    }

    if (showSolution && solution != null) {
        QuestionSolutionModal(
            block = block,
            kind = kind,
            parsedTags = parsedTags,
            options = options,
            blankKeys = blankKeys,
            solution = solution,
            renderChildren = renderChildren,
            onDismiss = { showSolution = false },
        )
    }
}

private fun resolveChoiceState(
    option: AnswerChoice,
    selected: Boolean,
    submitted: Boolean,
    multiple: Boolean,
    hasCorrectKey: Boolean,
): ChoiceState {
    if (!submitted) return if (selected) ChoiceState.Selected else ChoiceState.Idle
    if (!hasCorrectKey) return if (selected) ChoiceState.Selected else ChoiceState.Dimmed
    return when {
        selected && option.correct -> ChoiceState.Correct
        selected && !option.correct -> ChoiceState.Incorrect
        !selected && option.correct -> if (multiple) ChoiceState.Missed else ChoiceState.Answer
        else -> ChoiceState.Dimmed
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionHeader(
    block: ExamDocsSegment.CustomBlock,
    kind: QuestionKind,
    sourceTags: List<QuestionTag>,
) {
    val scheme = MaterialTheme.colorScheme
    val number = block.attrs["number"]?.trim()?.takeIf(String::isNotEmpty)
    val points = block.attrs["points"]?.trim()?.takeIf(String::isNotEmpty)
    val sourceText = remember(sourceTags) { formatSourceTrail(sourceTags) }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = scheme.onSurface.copy(alpha = 0.07f),
                contentColor = scheme.onSurface.copy(alpha = 0.88f),
                modifier = Modifier.defaultMinSize(minWidth = 28.dp, minHeight = 28.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
                    Text(
                        text = number,
                        fontFamily = GoogleSansFlex.Title.Emphasized.Small,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Type + Points chip
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = questionTypeIcon(kind),
                    contentDescription = null,
                    tint = scheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface.copy(alpha = 0.76f),
                )
                if (points != null) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(12.dp)
                            .background(scheme.outlineVariant),
                    )
                    Text(
                        text = "$points 分",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Source trail
        if (sourceText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Book02,
                    contentDescription = "来源",
                    tint = scheme.onSurface.copy(alpha = 0.50f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = sourceText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun questionTypeIcon(kind: QuestionKind): ImageVector = when (kind) {
    QuestionKind.Single -> HugeIcons.LeftToRightListBullet
    QuestionKind.Multiple -> HugeIcons.CheckmarkCircle02
    QuestionKind.Boolean -> HugeIcons.Tick01
    QuestionKind.Blank -> HugeIcons.Edit01
    QuestionKind.Essay -> HugeIcons.Edit03
}

@Composable
private fun BooleanChoiceRow(
    options: List<AnswerChoice>,
    selection: List<String>,
    correct: Set<String>,
    submitted: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            val selected = option.label in selection
            val state = resolveChoiceState(
                option = option,
                selected = selected,
                submitted = submitted,
                multiple = false,
                hasCorrectKey = correct.isNotEmpty(),
            )
            BooleanOptionTile(
                option = option,
                isTrueOption = option.label == "A",
                state = state,
                selected = selected,
                enabled = !submitted,
                onSelect = { onSelect(option.label) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BooleanOptionTile(
    option: AnswerChoice,
    isTrueOption: Boolean,
    state: ChoiceState,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val correctColors = rememberResultToneColors(ResultTone.Correct)
    val incorrectColors = rememberResultToneColors(ResultTone.Incorrect)

    val (bg, borderColor, markerBg, markerFg) = when (state) {
        ChoiceState.Correct -> listOf(correctColors.tint, correctColors.line, correctColors.soft, correctColors.ink)
        ChoiceState.Incorrect -> listOf(incorrectColors.tint, incorrectColors.line, incorrectColors.soft, incorrectColors.ink)
        ChoiceState.Answer -> listOf(Color.Transparent, correctColors.line, correctColors.soft, correctColors.ink)
        ChoiceState.Selected -> listOf(scheme.onSurface.copy(alpha = 0.045f), scheme.onSurface.copy(alpha = 0.8f), scheme.onSurface, scheme.surface)
        ChoiceState.Dimmed -> listOf(Color.Transparent, scheme.outlineVariant.copy(alpha = 0.5f), scheme.onSurface.copy(alpha = 0.06f), scheme.onSurfaceVariant)
        else -> listOf(Color.Transparent, scheme.outlineVariant, scheme.onSurface.copy(alpha = 0.06f), scheme.onSurfaceVariant)
    }

    val animatedBg by animateColorAsState(bg, label = "bool_bg")
    val shape = RoundedCornerShape(14.dp)
    val isDashed = state == ChoiceState.Answer

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(shape)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .then(
                    if (isDashed) Modifier.dashedBorder(1.2.dp, borderColor, 14.dp)
                    else Modifier
                ),
            shape = shape,
            color = animatedBg,
            border = if (isDashed) null else BorderStroke(1.dp, borderColor),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (state == ChoiceState.Dimmed) 0.5f else 1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(markerBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isTrueOption) HugeIcons.Tick01 else HugeIcons.Cancel01,
                            contentDescription = null,
                            tint = markerFg,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (state == ChoiceState.Correct || state == ChoiceState.Incorrect) {
                        VerdictMiniBadge(
                            isCorrect = state == ChoiceState.Correct,
                            baseColor = if (state == ChoiceState.Correct) correctColors.base else incorrectColors.base,
                            tintColor = animatedBg,
                            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
                        )
                    }
                }
                Text(
                    text = option.text ?: if (isTrueOption) "正确" else "错误",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (state == ChoiceState.Correct) correctColors.ink
                    else if (state == ChoiceState.Incorrect) incorrectColors.ink
                    else scheme.onSurface,
                )
            }
        }
        if (state == ChoiceState.Answer) {
            FloatingNoteBadge(
                text = "正确答案",
                colors = correctColors,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 10.dp)
                    .offset(y = (-9).dp),
            )
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
    val correctColors = rememberResultToneColors(ResultTone.Correct)
    val incorrectColors = rememberResultToneColors(ResultTone.Incorrect)
    val missedColors = rememberResultToneColors(ResultTone.Partial)

    val (bg, borderColor, markerBg, markerFg) = when (state) {
        ChoiceState.Correct -> listOf(correctColors.tint, correctColors.line, correctColors.soft, correctColors.ink)
        ChoiceState.Incorrect -> listOf(incorrectColors.tint, incorrectColors.line, incorrectColors.soft, incorrectColors.ink)
        ChoiceState.Missed -> listOf(Color.Transparent, missedColors.line, missedColors.soft, missedColors.ink)
        ChoiceState.Answer -> listOf(Color.Transparent, correctColors.line, correctColors.soft, correctColors.ink)
        ChoiceState.Selected -> listOf(scheme.onSurface.copy(alpha = 0.045f), scheme.onSurface.copy(alpha = 0.80f), scheme.onSurface, scheme.surface)
        ChoiceState.Dimmed -> listOf(Color.Transparent, scheme.outlineVariant.copy(alpha = 0.50f), scheme.onSurface.copy(alpha = 0.06f), scheme.onSurfaceVariant)
        ChoiceState.Idle -> listOf(Color.Transparent, scheme.outlineVariant, scheme.onSurface.copy(alpha = 0.06f), scheme.onSurface.copy(alpha = 0.80f))
    }

    val animatedBg by animateColorAsState(bg, label = "choice_bg")
    val shape = RoundedCornerShape(14.dp)
    val markerShape = if (multiple) RoundedCornerShape(8.dp) else CircleShape
    val isDashed = state == ChoiceState.Missed || state == ChoiceState.Answer
    val interaction = if (multiple) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onSelect() }
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(interaction)
                .then(
                    if (isDashed) Modifier.dashedBorder(1.2.dp, borderColor, 14.dp)
                    else Modifier
                ),
            shape = shape,
            color = animatedBg,
            border = if (isDashed) null else BorderStroke(if (state == ChoiceState.Selected) 1.5.dp else 1.dp, borderColor),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (state == ChoiceState.Dimmed) 0.5f else 1f)
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(markerShape)
                            .background(markerBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.label,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp,
                            lineHeight = 18.sp,
                            color = markerFg,
                        )
                    }
                    if (state == ChoiceState.Correct || state == ChoiceState.Incorrect) {
                        VerdictMiniBadge(
                            isCorrect = state == ChoiceState.Correct,
                            baseColor = if (state == ChoiceState.Correct) correctColors.base else incorrectColors.base,
                            tintColor = animatedBg,
                            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 3.dp),
                ) {
                    if (option.text != null) {
                        Text(option.text, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        renderChildren(option.blocks)
                    }
                }
            }
        }

        if (state == ChoiceState.Missed) {
            FloatingNoteBadge(
                text = "漏选",
                colors = missedColors,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp)
                    .offset(y = (-9).dp),
            )
        } else if (state == ChoiceState.Answer) {
            FloatingNoteBadge(
                text = "正确答案",
                colors = correctColors,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp)
                    .offset(y = (-9).dp),
            )
        }
    }
}

@Composable
private fun VerdictMiniBadge(
    isCorrect: Boolean,
    baseColor: Color,
    tintColor: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = baseColor,
            border = BorderStroke(1.5.dp, if (tintColor == Color.Transparent) MaterialTheme.colorScheme.surface else tintColor),
            modifier = Modifier.size(16.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isCorrect) HugeIcons.Tick01 else HugeIcons.Cancel01,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
private fun FloatingNoteBadge(
    text: String,
    colors: ToneColors,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = colors.soft,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Text(
            text = text,
            color = colors.ink,
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionFooter(
    kind: QuestionKind,
    options: List<AnswerChoice>,
    selection: List<String>,
    correct: Set<String>,
    blankCount: Int,
    blankKeys: List<BlankKey>,
    blanks: List<String>,
    selfGrade: String?,
    submitted: Boolean,
    canSubmit: Boolean,
    hasSolution: Boolean,
    onSubmit: () -> Unit,
    onSelfGrade: (String) -> Unit,
    onReset: () -> Unit,
    onOpenSolution: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Left: Result pill / Submit / Self-grade
        Box(modifier = Modifier.weight(1f)) {
            if (submitted) {
                GradedResultSummary(
                    kind = kind,
                    selection = selection,
                    correct = correct,
                    blankCount = blankCount,
                    blankKeys = blankKeys,
                    blanks = blanks,
                    selfGrade = selfGrade,
                )
            } else if (kind == QuestionKind.Essay) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "对照解答自评",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    SelfGradePills(onSelect = onSelfGrade)
                }
            } else if (kind == QuestionKind.Multiple || blankCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onSubmit,
                        enabled = canSubmit,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text("提交答案", style = MaterialTheme.typography.labelLarge)
                    }
                    if (kind == QuestionKind.Multiple) {
                        Text(
                            text = if (selection.isNotEmpty()) "已选 ${selection.size} 项" else "可多选",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Right: Icon-only Redo & View Solution buttons (save space)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (submitted) {
                OutlinedIconButton(
                    onClick = onReset,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Refresh03,
                        contentDescription = "重做",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (hasSolution) {
                FilledTonalIconButton(
                    onClick = onOpenSolution,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                    ),
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Book02,
                        contentDescription = "查看解答",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelfGradePills(onSelect: (String) -> Unit) {
    val grades = listOf(
        Triple("correct", "正确", ResultTone.Correct),
        Triple("partial", "部分正确", ResultTone.Partial),
        Triple("incorrect", "错误", ResultTone.Incorrect),
    )
    grades.forEach { (key, label, tone) ->
        val colors = rememberResultToneColors(tone)
        val icon = when (tone) {
            ResultTone.Correct -> HugeIcons.CheckmarkCircle02
            ResultTone.Partial -> HugeIcons.AlertCircle
            ResultTone.Incorrect -> HugeIcons.Cancel01
        }
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            onClick = { onSelect(key) },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(icon, contentDescription = null, tint = colors.base, modifier = Modifier.size(14.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradedResultSummary(
    kind: QuestionKind,
    selection: List<String>,
    correct: Set<String>,
    blankCount: Int,
    blankKeys: List<BlankKey>,
    blanks: List<String>,
    selfGrade: String?,
) {
    val scheme = MaterialTheme.colorScheme
    val matched = if (blankCount > 0) blanks.mapIndexed { index, input -> blankKeys.getOrNull(index)?.matches(input) } else emptyList()

    val (tone, text) = when {
        kind == QuestionKind.Essay -> when (selfGrade) {
            "correct" -> ResultTone.Correct to "自评正确"
            "partial" -> ResultTone.Partial to "自评部分正确"
            else -> ResultTone.Incorrect to "自评错误"
        }
        blankCount > 0 -> {
            val rightCount = matched.count { it == true }
            when {
                matched.all { it == true } -> ResultTone.Correct to "回答正确"
                rightCount > 0 -> ResultTone.Partial to "$rightCount/$blankCount 空与参考答案一致"
                else -> ResultTone.Incorrect to "请对照参考答案"
            }
        }
        correct.isEmpty() -> ResultTone.Partial to "已记录作答"
        selection.toSet() == correct -> ResultTone.Correct to "回答正确"
        kind == QuestionKind.Multiple && selection.isNotEmpty() && selection.all { it in correct } ->
            ResultTone.Partial to "漏选"
        else -> ResultTone.Incorrect to "回答错误"
    }

    val colors = rememberResultToneColors(tone)
    val icon = when (tone) {
        ResultTone.Correct -> HugeIcons.CheckmarkCircle02
        ResultTone.Partial -> HugeIcons.AlertCircle
        ResultTone.Incorrect -> HugeIcons.Cancel01
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = colors.tint,
            border = BorderStroke(1.dp, colors.line),
        ) {
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .padding(start = 10.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(icon, contentDescription = null, tint = colors.base, modifier = Modifier.size(15.dp))
                Text(text, color = colors.ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }

        if (tone != ResultTone.Correct && kind != QuestionKind.Essay) {
            val answerDisplay = when {
                kind == QuestionKind.Boolean -> if ("A" in correct) "正确" else if ("B" in correct) "错误" else null
                blankCount > 0 -> blankKeys.mapNotNull { it.answer }.takeIf { it.isNotEmpty() }?.joinToString("；")
                correct.isNotEmpty() -> correct.sorted().joinToString(" ")
                else -> null
            }
            if (answerDisplay != null) {
                val okColors = rememberResultToneColors(ResultTone.Correct)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("正确答案", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = okColors.tint,
                        border = BorderStroke(1.dp, okColors.line),
                    ) {
                        Text(
                            text = answerDisplay,
                            color = okColors.ink,
                            fontFamily = if (kind == QuestionKind.Single || kind == QuestionKind.Multiple) FontFamily.Serif else FontFamily.Default,
                            fontStyle = if (kind == QuestionKind.Single || kind == QuestionKind.Multiple) FontStyle.Italic else FontStyle.Normal,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionSolutionModal(
    block: ExamDocsSegment.CustomBlock,
    kind: QuestionKind,
    parsedTags: ParsedQuestionTags,
    options: List<AnswerChoice>,
    blankKeys: List<BlankKey>,
    solution: ExamDocsSegment.CustomBlock,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val number = block.attrs["number"]?.trim()?.takeIf(String::isNotEmpty)
    val points = block.attrs["points"]?.trim()?.takeIf(String::isNotEmpty)
    val sourceText = remember(parsedTags) { formatSourceTrail(parsedTags.source) }
    val bottomTags = remember(parsedTags) { parsedTags.point + parsedTags.type }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (number != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = scheme.onSurface.copy(alpha = 0.08f),
                        modifier = Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(
                                text = number,
                                fontFamily = GoogleSansFlex.Title.Emphasized.Small,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = solution.title ?: "参考答案与解析",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    val subtitle = buildList {
                        add(kind.label)
                        if (points != null) add("$points 分")
                        if (sourceText != null) add(sourceText)
                    }.joinToString(" · ")
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(HugeIcons.Cancel01, "关闭解析", modifier = Modifier.size(18.dp))
                }
            }

            // Reference answer summary for non-essay questions
            val refAnswer = remember(kind, options, blankKeys) {
                when (kind) {
                    QuestionKind.Boolean -> options.firstOrNull { it.correct }?.text
                    QuestionKind.Single, QuestionKind.Multiple -> options.filter { it.correct }.joinToString(" ") { it.label }.ifEmpty { null }
                    QuestionKind.Blank -> blankKeys.mapNotNull { it.answer }.joinToString("；").ifEmpty { null }
                    QuestionKind.Essay -> null
                }
            }
            if (refAnswer != null) {
                val okColors = rememberResultToneColors(ResultTone.Correct)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = okColors.tint,
                    border = BorderStroke(1.dp, okColors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("参考答案", style = MaterialTheme.typography.labelLarge, color = okColors.ink, fontWeight = FontWeight.SemiBold)
                        Text(refAnswer, style = MaterialTheme.typography.bodyMedium, color = okColors.ink, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.65f))
            renderChildren(solution.blocks)

            // Knowledge points & other trait tags at the bottom of the solution sheet
            if (bottomTags.isNotEmpty()) {
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.65f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "考点与标签",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        bottomTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, scheme.outlineVariant),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = HugeIcons.Idea,
                                        contentDescription = null,
                                        tint = scheme.onSurface.copy(alpha = 0.55f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = tag.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
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

private fun Modifier.dashedBorder(
    strokeWidth: androidx.compose.ui.unit.Dp,
    color: Color,
    cornerRadius: androidx.compose.ui.unit.Dp,
): Modifier = drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
    )
    drawRoundRect(
        color = color,
        style = stroke,
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
    )
}

private val TYPE_GROUPS = setOf("题型", "难度", "标记", "特点")
private val TYPE_KEYWORDS = setOf("易错", "重点", "难点", "难题", "压轴", "高频", "必做", "经典", "基础", "拔高")
private val SOURCE_GROUPS = setOf("来源", "出处", "试卷", "年份", "科目", "章节")
private val POINT_GROUPS = setOf("考点", "知识点", "考察点")
private val TAG_GROUP_REGEX = Regex("""^([^:：]{1,12})[:：]\s*(.+)$""")

private fun parseQuestionTags(raw: String?): ParsedQuestionTags {
    if (raw.isNullOrBlank()) return ParsedQuestionTags(emptyList(), emptyList(), emptyList())
    val seen = mutableSetOf<String>()
    val type = mutableListOf<QuestionTag>()
    val source = mutableListOf<QuestionTag>()
    val point = mutableListOf<QuestionTag>()

    for (item in raw.split(',', '，', '、', '；').map(String::trim).filter(String::isNotEmpty)) {
        val match = TAG_GROUP_REGEX.matchEntire(item)
        val group = match?.groupValues?.get(1)?.trim()
        val label = (match?.groupValues?.get(2) ?: item).trim()
        if (label.isEmpty()) continue
        val kind = when {
            group != null && group in POINT_GROUPS -> QuestionTagKind.Point
            group != null && group in TYPE_GROUPS -> QuestionTagKind.Type
            group != null && group in SOURCE_GROUPS -> QuestionTagKind.Source
            group == null && label in TYPE_KEYWORDS -> QuestionTagKind.Type
            else -> QuestionTagKind.Source
        }
        val key = "${kind.name}\u0000$label"
        if (!seen.add(key)) continue
        val tag = QuestionTag(label = label, group = group, kind = kind)
        when (kind) {
            QuestionTagKind.Type -> if (type.size < 12) type.add(tag)
            QuestionTagKind.Source -> if (source.size < 12) source.add(tag)
            QuestionTagKind.Point -> if (point.size < 12) point.add(tag)
        }
    }
    return ParsedQuestionTags(type, source, point)
}

private fun formatSourceTrail(tags: List<QuestionTag>): String? {
    val sources = tags.filter { it.group != "科目" && it.group != "章节" }
    if (sources.isEmpty()) return null
    val first = sources[0].label
    val second = sources.getOrNull(1)?.label
    if (first == "王道8套卷" && second != null && Regex("""^第\d+套$""").matches(second)) {
        return first + second
    }
    return first.replace(Regex("""^(王道·卷[一二三四五六七八]|竟成·模拟[一二三四五六七八])-(?:Q)?\d+$"""), "$1")
}

private fun countBlankPlaceholders(segment: ExamDocsSegment): Int = when (segment) {
    is ExamDocsSegment.Markdown -> segment.document.blocks.sumOf { block ->
        if (block is MarkdownBlock.Paragraph) {
            block.content.count { part -> part is MarkdownInline.Code && part.code == "____" }
        } else 0
    }
    is ExamDocsSegment.CustomBlock -> segment.blocks.sumOf(::countBlankPlaceholders)
}
