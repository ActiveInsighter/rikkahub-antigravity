package me.rerere.rikkahub.ui.pages.examdocs

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.components.ai.ChatTopBarCapsule
import me.rerere.rikkahub.ui.pages.examdocs.components.GradeResult
import me.rerere.rikkahub.ui.pages.examdocs.components.savedQuestionGrade
import java.util.Locale

internal data class DocsDockQuestion(val block: ExamDocsSegment.CustomBlock, val blockIndex: Int, val label: String, val ordinal: Int)
internal data class DocsQuestionJump(val block: ExamDocsSegment.CustomBlock, val sequence: Int)
internal val LocalDocsQuestionJump = staticCompositionLocalOf<DocsQuestionJump?> { null }

internal fun containsDockQuestion(segment: ExamDocsSegment, target: ExamDocsSegment.CustomBlock): Boolean =
    segment === target || (segment is ExamDocsSegment.CustomBlock && segment.blocks.any { containsDockQuestion(it, target) })

internal fun indexDockQuestions(blocks: List<ExamDocsSegment>): List<DocsDockQuestion> = buildList {
    fun visit(block: ExamDocsSegment, index: Int) {
        if (block !is ExamDocsSegment.CustomBlock) return
        if (block.type == "question") {
            val ordinal = size + 1
            add(DocsDockQuestion(block, index, block.attrs["number"]?.takeIf { it.isNotBlank() } ?: ordinal.toString(), ordinal))
            return
        }
        block.blocks.forEach { visit(it, index) }
    }
    blocks.forEachIndexed { index, block -> visit(block, index) }
}

private enum class DockStatus(val label: String) {
    Idle("未答"), Draft("未提交"), Correct("正确"), Partial("部分正确"), Incorrect("错误"), Recorded("已记录")
}
private data class DockEntry(val question: DocsDockQuestion, val status: DockStatus)
private data class DockMark(val question: DocsDockQuestion?, val millis: Long)

@Composable
private fun DockStatus.tint(): Color = when (this) {
    DockStatus.Idle -> MaterialTheme.colorScheme.outlineVariant
    DockStatus.Draft -> MaterialTheme.colorScheme.outline
    DockStatus.Correct -> if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) Color(0xFF83D6A1) else Color(0xFF27834B)
    DockStatus.Partial -> if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) Color(0xFFF2CC79) else Color(0xFFB17916)
    DockStatus.Incorrect -> MaterialTheme.colorScheme.error
    DockStatus.Recorded -> MaterialTheme.colorScheme.primary
}

private fun duration(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ExamDocsQuestionDock(
    questions: List<DocsDockQuestion>,
    listState: LazyListState,
    hazeState: HazeState,
    displaySetting: DisplaySetting,
    modifier: Modifier = Modifier,
    onJump: (DocsDockQuestion) -> Unit,
) {
    val progress = LocalDocsReadingProgress.current
    val entries = remember(questions, progress?.snapshot, progress?.identities) {
        questions.map { question ->
            val saved = progress?.identities?.get(question.block)?.let { progress?.snapshot?.answers?.get(it.answerId) }
            val answer = saved?.state
            val status = when {
                answer == null -> DockStatus.Idle
                !answer.submitted -> if (answer.selected.isNotEmpty() || answer.values.any { it.isNotBlank() } || !saved?.draft.isNullOrBlank()) DockStatus.Draft else DockStatus.Idle
                else -> when (savedQuestionGrade(question.block, answer)) {
                    GradeResult.Correct -> DockStatus.Correct
                    GradeResult.Partial, GradeResult.Missed -> DockStatus.Partial
                    GradeResult.Incorrect -> DockStatus.Incorrect
                    else -> DockStatus.Recorded
                }
            }
            DockEntry(question, status)
        }
    }
    val answered = entries.count { it.status != DockStatus.Idle && it.status != DockStatus.Draft }
    val graded = entries.count { it.status in listOf(DockStatus.Correct, DockStatus.Partial, DockStatus.Incorrect) }
    val colors = entries.map { it.status.tint() }
    var open by remember(questions) { mutableStateOf(false) }
    var since by remember(questions) { mutableStateOf<Long?>(null) }
    var banked by remember(questions) { mutableLongStateOf(0L) }
    var marks by remember(questions) { mutableStateOf<List<DockMark>>(emptyList()) }
    fun elapsed() = banked + (since?.let { SystemClock.elapsedRealtime() - it } ?: 0L)
    val currentQuestion by remember(questions, listState) {
        derivedStateOf { questions.lastOrNull { it.blockIndex <= listState.firstVisibleItemIndex } ?: questions.firstOrNull() }
    }
    ChatTopBarCapsule(hazeState, displaySetting, showBackground = true, modifier = modifier) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).clickable { open = true }
                .semantics { contentDescription = "答题卡，已答 ${answered}/${questions.size} 题" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val stroke = 3.dp.toPx()
                val diameter = size.minDimension - stroke
                val step = 360f / colors.size.coerceAtLeast(1)
                val gap = if (colors.size == 1) 0f else minOf(4f, step * 0.30f)
                colors.forEachIndexed { index, color ->
                    drawArc(color, -90f + step * index + gap / 2, step - gap, false,
                        topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2),
                        size = Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Butt))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$answered/${questions.size}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(if (since != null) "计时中" else "已答", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.82f).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("答题卡", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("已答 $answered/${questions.size}" + if (graded > 0) " · 正确率 ${entries.count { it.status == DockStatus.Correct } * 100 / graded}%" else "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DockClock(since, banked)
                            Button(onClick = {
                                if (since == null) since = SystemClock.elapsedRealtime() else { banked = elapsed(); since = null }
                            }) { Text(if (since != null) "暂停" else if (banked > 0) "继续" else "开始") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { marks = marks + DockMark(currentQuestion, elapsed()) }, enabled = since != null || banked > 0) { Text("标记当前题") }
                            TextButton(onClick = { since = null; banked = 0; marks = emptyList() }, enabled = since != null || banked > 0) { Text("重置") }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    DockStatus.entries.forEach { status ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(shape = CircleShape, color = status.tint(), modifier = Modifier.size(8.dp)) {}
                            Text("${status.label} ${entries.count { it.status == status }}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                LazyVerticalGrid(columns = GridCells.Adaptive(54.dp), modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (marks.isNotEmpty()) {
                        items(marks.size, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { index ->
                            val mark = marks[index]
                            Surface(onClick = { mark.question?.let { open = false; onJump(it) } },
                                enabled = mark.question != null, shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(mark.question?.let { "第 ${it.label} 题" } ?: "标记 ${index + 1}")
                                    Text("+${duration(mark.millis - (marks.getOrNull(index - 1)?.millis ?: 0L))} · ${duration(mark.millis)}")
                                }
                            }
                        }
                    }
                    items(entries, key = { it.question.ordinal }) { entry ->
                        val tint = entry.status.tint()
                        Surface(onClick = { open = false; onJump(entry.question) },
                            shape = MaterialTheme.shapes.medium,
                            color = tint.copy(alpha = if (entry.status == DockStatus.Idle) 0.07f else 0.13f),
                            border = BorderStroke(if (entry.question == currentQuestion) 2.dp else 1.dp, tint),
                            modifier = Modifier.height(48.dp).semantics { contentDescription = "第 ${entry.question.label} 题，${entry.status.label}" }) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(entry.question.label, color = if (entry.status == DockStatus.Idle) MaterialTheme.colorScheme.onSurfaceVariant else tint,
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockClock(since: Long?, banked: Long) {
    var now by remember(since) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(since) {
        if (since != null) while (true) { now = SystemClock.elapsedRealtime(); delay(250) }
    }
    Column {
        Text(duration(banked + (since?.let { now - it } ?: 0L)), style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold)
        Text(if (since != null) "计时中" else if (banked > 0) "已暂停" else "本页计时", style = MaterialTheme.typography.labelSmall)
    }
}
