package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownDocument
import me.rerere.bettertext.markdown.MarkdownInline
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment

@Immutable
private data class QuestionOption(
    val label: String,
    val isCorrect: Boolean,
    val blocks: List<ExamDocsSegment>,
)

@Immutable
private data class QuestionBlankSpec(
    val answer: String,
    val aliases: List<String>,
)

private enum class GradeStatus { Unsubmitted, Correct, Partial, Wrong }

private val blankTokenRegex = Regex("""\[\[blank:(\{.*?\})\]\]""")
private val questionJson = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExamDocsQuestionBlock(
    block: ExamDocsSegment.CustomBlock,
    renderSegment: @Composable (ExamDocsSegment) -> Unit,
) {
    val meta = remember(block.title) { parseComponentMeta(block.title) }
    val type = meta.string("type")?.lowercase() ?: "single"
    val number = meta.string("number")
    val tags = meta.stringList("tags")
    val points = meta.json?.get("points")?.jsonPrimitive?.content
    val booleanAnswer = meta.boolean("answer")

    val options = remember(block.blocks) {
        block.blocks.filterIsInstance<ExamDocsSegment.CustomBlock>()
            .filter { it.type == "option" }
            .mapIndexed { idx, opt ->
                val optMeta = parseComponentMeta(opt.title)
                QuestionOption(
                    label = optMeta.string("label") ?: ('A' + idx).toString(),
                    isCorrect = optMeta.boolean("correct") == true,
                    blocks = opt.blocks,
                )
            }
    }
    val solutions = remember(block.blocks) {
        block.blocks.filterIsInstance<ExamDocsSegment.CustomBlock>().filter { it.type == "solution" }
    }
    val stemSegments = remember(block.blocks) {
        block.blocks.filterNot {
            it is ExamDocsSegment.CustomBlock && (it.type == "option" || it.type == "solution")
        }
    }
    val blanks = remember(stemSegments) { extractBlanks(stemSegments) }
    val cleanedStemSegments = remember(stemSegments) { replaceBlankTokensInSegments(stemSegments) }

    var selectedLabels by remember(block) { mutableStateOf(setOf<String>()) }
    var selectedBool by remember(block) { mutableStateOf<Boolean?>(null) }
    val blankInputs = remember(block, blanks.size) {
        mutableStateListOf<String>().apply { repeat(blanks.size) { add("") } }
    }
    var essayInput by remember(block) { mutableStateOf("") }
    var submitted by remember(block) { mutableStateOf(false) }
    var showSolution by remember(block) { mutableStateOf(false) }

    val gradeStatus = remember(submitted, selectedLabels, selectedBool, blankInputs.toList(), options, booleanAnswer, blanks) {
        if (!submitted) {
            GradeStatus.Unsubmitted
        } else {
            when (type) {
                "single" -> {
                    val correctSet = options.filter { it.isCorrect }.map { it.label }.toSet()
                    if (selectedLabels == correctSet && correctSet.isNotEmpty()) GradeStatus.Correct else GradeStatus.Wrong
                }
                "multiple" -> {
                    val correctSet = options.filter { it.isCorrect }.map { it.label }.toSet()
                    when {
                        selectedLabels == correctSet && correctSet.isNotEmpty() -> GradeStatus.Correct
                        selectedLabels.isNotEmpty() && selectedLabels.all { it in correctSet } -> GradeStatus.Partial
                        else -> GradeStatus.Wrong
                    }
                }
                "boolean" -> {
                    if (selectedBool != null && selectedBool == booleanAnswer) GradeStatus.Correct else GradeStatus.Wrong
                }
                "blank" -> {
                    val allMatch = blanks.isNotEmpty() && blanks.indices.all { i ->
                        val user = blankInputs.getOrNull(i)?.trim().orEmpty()
                        val spec = blanks[i]
                        val accepted = (listOf(spec.answer) + spec.aliases).map { it.trim() }.filter { it.isNotEmpty() }
                        accepted.any { it.equals(user, ignoreCase = true) }
                    }
                    if (allMatch) GradeStatus.Correct else GradeStatus.Wrong
                }
                else -> GradeStatus.Unsubmitted
            }
        }
    }

    val typeLabel = when (type) {
        "single" -> "单选题"
        "multiple" -> "多选题"
        "boolean" -> "判断题"
        "blank" -> "填空题"
        "essay" -> "解答题"
        else -> "练习题"
    }

    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(shape = CircleShape, color = colors.primaryContainer) {
                    Text(
                        text = if (number != null) "第 $number 题 · $typeLabel" else typeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (points != null) {
                    Surface(shape = CircleShape, color = colors.secondaryContainer.copy(alpha = 0.7f)) {
                        Text(
                            text = "$points 分",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
                for (tag in tags) {
                    Surface(shape = CircleShape, color = colors.surfaceContainerHigh) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Stem
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cleanedStemSegments.forEach { renderSegment(it) }
            }

            // Interactive answer section
            when (type) {
                "single", "multiple" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            val isSelected = option.label in selectedLabels
                            val containerColor = when {
                                submitted && option.isCorrect -> colors.secondaryContainer.copy(alpha = 0.75f)
                                submitted && isSelected && !option.isCorrect -> colors.errorContainer.copy(alpha = 0.7f)
                                isSelected -> colors.primaryContainer.copy(alpha = 0.65f)
                                else -> colors.surfaceContainer
                            }
                            val borderColor = when {
                                submitted && option.isCorrect -> colors.secondary
                                submitted && isSelected && !option.isCorrect -> colors.error
                                isSelected -> colors.primary
                                else -> colors.outlineVariant.copy(alpha = 0.35f)
                            }
                            Surface(
                                onClick = {
                                    if (type == "single") {
                                        selectedLabels = setOf(option.label)
                                    } else {
                                        selectedLabels = if (isSelected) selectedLabels - option.label else selectedLabels + option.label
                                    }
                                    submitted = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = containerColor,
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    submitted && option.isCorrect -> colors.secondary
                                                    submitted && isSelected && !option.isCorrect -> colors.error
                                                    isSelected -> colors.primary
                                                    else -> colors.surfaceContainerHighest
                                                }
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = option.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                submitted && option.isCorrect -> colors.onSecondary
                                                submitted && isSelected && !option.isCorrect -> colors.onError
                                                isSelected -> colors.onPrimary
                                                else -> colors.onSurfaceVariant
                                            },
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        option.blocks.forEach { renderSegment(it) }
                                    }
                                }
                            }
                        }
                    }
                }
                "boolean" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf(true to "正确", false to "错误").forEach { (value, label) ->
                            val isSelected = selectedBool == value
                            val isRight = submitted && booleanAnswer == value
                            val isWrongPick = submitted && isSelected && booleanAnswer != value
                            Surface(
                                onClick = {
                                    selectedBool = value
                                    submitted = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    isRight -> colors.secondaryContainer.copy(alpha = 0.75f)
                                    isWrongPick -> colors.errorContainer.copy(alpha = 0.7f)
                                    isSelected -> colors.primaryContainer.copy(alpha = 0.65f)
                                    else -> colors.surfaceContainer
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isRight -> colors.secondary
                                        isWrongPick -> colors.error
                                        isSelected -> colors.primary
                                        else -> colors.outlineVariant.copy(alpha = 0.35f)
                                    },
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
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
                "blank" -> {
                    if (blanks.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            blanks.forEachIndexed { index, spec ->
                                val userText = blankInputs.getOrElse(index) { "" }
                                val matched = submitted && (listOf(spec.answer) + spec.aliases)
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .any { it.equals(userText.trim(), ignoreCase = true) }
                                OutlinedTextField(
                                    value = userText,
                                    onValueChange = {
                                        if (index < blankInputs.size) blankInputs[index] = it
                                        submitted = false
                                    },
                                    label = { Text("第 ${index + 1} 空") },
                                    singleLine = true,
                                    isError = submitted && !matched,
                                    supportingText = if (submitted && !matched && spec.answer.isNotBlank()) {
                                        { Text("参考答案：${spec.answer}") }
                                    } else null,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                "essay" -> {
                    OutlinedTextField(
                        value = essayInput,
                        onValueChange = { essayInput = it },
                        label = { Text("作答草稿 / 思路笔记（本地暂存）") },
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (type != "essay") {
                    val canSubmit = when (type) {
                        "single", "multiple" -> selectedLabels.isNotEmpty()
                        "boolean" -> selectedBool != null
                        "blank" -> blankInputs.any { it.isNotBlank() }
                        else -> false
                    }
                    Button(
                        onClick = {
                            submitted = true
                            if (solutions.isNotEmpty()) showSolution = true
                        },
                        enabled = canSubmit,
                        shape = CircleShape,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Text("提交答案")
                    }
                }
                if (solutions.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showSolution = !showSolution },
                        shape = CircleShape,
                    ) {
                        Icon(HugeIcons.Idea, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (showSolution) "收起解析" else "查看解析")
                    }
                }
                Spacer(Modifier.weight(1f))
                if (submitted && gradeStatus != GradeStatus.Unsubmitted) {
                    val (statusText, statusColor) = when (gradeStatus) {
                        GradeStatus.Correct -> "回答正确" to colors.secondary
                        GradeStatus.Partial -> "漏选" to colors.tertiary
                        GradeStatus.Wrong -> "回答有误" to colors.error
                        GradeStatus.Unsubmitted -> "" to colors.onSurface
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
                if (submitted) {
                    TextButton(
                        onClick = {
                            submitted = false
                            selectedLabels = emptySet()
                            selectedBool = null
                            for (i in blankInputs.indices) blankInputs[i] = ""
                        },
                    ) {
                        Text("重做")
                    }
                }
            }

            // Solution area
            AnimatedVisibility(visible = showSolution && solutions.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.25f))
                    solutions.forEach { sol ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.secondaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = sol.title ?: "参考答案与解析",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSecondaryContainer,
                                )
                                sol.blocks.forEach { renderSegment(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun extractBlanks(segments: List<ExamDocsSegment>): List<QuestionBlankSpec> {
    val result = mutableListOf<QuestionBlankSpec>()
    fun walkInlines(inlines: List<MarkdownInline>) {
        for (inline in inlines) {
            when (inline) {
                is MarkdownInline.Text -> {
                    blankTokenRegex.findAll(inline.text).forEach { match ->
                        val meta = parseComponentMeta(match.groupValues[1])
                        result.add(
                            QuestionBlankSpec(
                                answer = meta.string("answer").orEmpty(),
                                aliases = meta.stringList("aliases"),
                            )
                        )
                    }
                }
                is MarkdownInline.Styled -> walkInlines(inline.children)
                is MarkdownInline.Link -> walkInlines(inline.children)
                else -> Unit
            }
        }
    }
    fun walkBlocks(blocks: List<MarkdownBlock>) {
        for (b in blocks) {
            when (b) {
                is MarkdownBlock.Paragraph -> walkInlines(b.content)
                is MarkdownBlock.Heading -> walkInlines(b.content)
                is MarkdownBlock.BlockQuote -> walkBlocks(b.children)
                is MarkdownBlock.BulletList -> b.items.forEach { walkBlocks(it.children) }
                is MarkdownBlock.OrderedList -> b.items.forEach { walkBlocks(it.children) }
                else -> Unit
            }
        }
    }
    for (segment in segments) {
        when (segment) {
            is ExamDocsSegment.Markdown -> walkBlocks(segment.document.blocks)
            is ExamDocsSegment.CustomBlock -> result.addAll(extractBlanks(segment.blocks))
        }
    }
    return result
}

private fun replaceBlankTokensInSegments(segments: List<ExamDocsSegment>): List<ExamDocsSegment> {
    var counter = 0
    fun transformInlines(inlines: List<MarkdownInline>): List<MarkdownInline> = inlines.map { inline ->
        when (inline) {
            is MarkdownInline.Text -> {
                if (!inline.text.contains("[[blank:")) inline
                else inline.copy(
                    text = blankTokenRegex.replace(inline.text) {
                        counter += 1
                        " [____($counter)] "
                    }
                )
            }
            is MarkdownInline.Styled -> inline.copy(children = transformInlines(inline.children))
            is MarkdownInline.Link -> inline.copy(children = transformInlines(inline.children))
            else -> inline
        }
    }
    fun transformBlocks(blocks: List<MarkdownBlock>): List<MarkdownBlock> = blocks.map { b ->
        when (b) {
            is MarkdownBlock.Paragraph -> b.copy(content = transformInlines(b.content))
            is MarkdownBlock.Heading -> b.copy(content = transformInlines(b.content))
            is MarkdownBlock.BlockQuote -> b.copy(children = transformBlocks(b.children))
            is MarkdownBlock.BulletList -> b.copy(items = b.items.map { it.copy(children = transformBlocks(it.children)) })
            is MarkdownBlock.OrderedList -> b.copy(items = b.items.map { it.copy(children = transformBlocks(it.children)) })
            else -> b
        }
    }
    return segments.map { segment ->
        when (segment) {
            is ExamDocsSegment.Markdown -> ExamDocsSegment.Markdown(
                MarkdownDocument(segment.document.source, transformBlocks(segment.document.blocks))
            )
            is ExamDocsSegment.CustomBlock -> segment.copy(blocks = replaceBlankTokensInSegments(segment.blocks))
        }
    }
}
