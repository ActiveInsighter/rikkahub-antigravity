package me.rerere.rikkahub.ui.pages.examdocs

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.examdocs.ExamDocsDocument
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.components.ai.ChatTopBarCapsule
import me.rerere.rikkahub.ui.pages.examdocs.components.dockQuestionStatus
import java.util.Locale

internal class DocsQuestionJump(val block: ExamDocsSegment.CustomBlock)
internal val LocalDocsQuestionJump = staticCompositionLocalOf<MutableState<DocsQuestionJump?>?> { null }

internal fun ExamDocsSegment.containsDockQuestion(target: ExamDocsSegment.CustomBlock): Boolean =
    this === target || (this is ExamDocsSegment.CustomBlock && blocks.any { it.containsDockQuestion(target) })

internal enum class DocsDockStatus(val label: String, val graded: Boolean = false) {
    Idle("未答"), Draft("未提交"), Correct("正确", true), Partial("部分正确", true),
    Incorrect("错误", true), Recorded("已记录", true),
}

internal data class DocsDockItem(
    val block: ExamDocsSegment.CustomBlock,
    val blockIndex: Int,
    val label: String,
    val ordinal: Int,
)

internal fun dockItems(document: ExamDocsDocument): List<DocsDockItem> = buildList {
    fun visit(segment: ExamDocsSegment, blockIndex: Int) {
        if (segment !is ExamDocsSegment.CustomBlock) return
        if (segment.type == "question") {
            val ordinal = size + 1
            add(DocsDockItem(segment, blockIndex, segment.attrs["number"]?.takeIf(String::isNotBlank) ?: ordinal.toString(), ordinal))
        }
        segment.blocks.forEach { visit(it, blockIndex) }
    }
    document.blocks.forEachIndexed { index, segment -> visit(segment, index) }
}

private data class DockMark(val item: DocsDockItem, val elapsed: Long)

private fun duration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ExamDocsQuestionDock(
    document: ExamDocsDocument,
    hazeState: HazeState,
    displaySetting: DisplaySetting,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onJump: (DocsDockItem) -> Unit,
) {
    val reading = LocalDocsReadingProgress.current
    val questions = remember(document) { dockItems(document) }
    if (questions.isEmpty()) return
    val states = remember(questions, reading?.identities, reading?.snapshot) {
        questions.map { item ->
            val identity = reading?.identities?.get(item.block)
            dockQuestionStatus(item.block, identity?.let { reading?.snapshot?.answers?.get(it.answerId) })
        }
    }
    val answered = states.count { it.graded }
    val correct = states.count { it == DocsDockStatus.Correct }
    val graded = states.count { it.graded && it != DocsDockStatus.Recorded }
    val scheme = MaterialTheme.colorScheme
    val colors = mapOf(
        DocsDockStatus.Idle to scheme.outlineVariant,
        DocsDockStatus.Draft to scheme.outline,
        DocsDockStatus.Correct to Color(0xFF389467),
        DocsDockStatus.Partial to Color(0xFFD29A38),
        DocsDockStatus.Incorrect to scheme.error,
        DocsDockStatus.Recorded to scheme.primary,
    )
    var open by remember(document) { mutableStateOf(false) }
    var since by remember(document) { mutableStateOf<Long?>(null) }
    var banked by remember(document) { mutableLongStateOf(0L) }
    var marks by remember(document) { mutableStateOf(emptyList<DockMark>()) }
    fun elapsed() = banked + (since?.let { SystemClock.elapsedRealtime() - it } ?: 0L)
    val current = questions.lastOrNull { it.blockIndex <= listState.firstVisibleItemIndex } ?: questions.first()
    ChatTopBarCapsule(
        hazeState = hazeState,
        displaySetting = displaySetting,
        showBackground = true,
        modifier = modifier.shadow(4.dp, CircleShape),
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).clickable { open = true }
                .semantics { contentDescription = "答题卡，已答 $answered/${questions.size}" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val stroke = 3.dp.toPx()
                val step = 360f / states.size
                val gap = if (states.size == 1) 0f else minOf(3f, step * 0.35f)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                states.forEachIndexed { index, status ->
                    drawArc(
                        color = colors.getValue(status),
                        startAngle = -90f + index * step + gap / 2f,
                        sweepAngle = step - gap,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = arcSize,
                        style = Stroke(stroke),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$answered/${questions.size}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (since != null) Text("计时中", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
            }
        }
    }
    if (open) {
        var time by remember { mutableLongStateOf(elapsed()) }
        LaunchedEffect(since, banked) {
            time = elapsed()
            while (since != null) {
                delay(250)
                time = elapsed()
            }
        }
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LazyColumn(
                Modifier.fillMaxWidth().fillMaxHeight(0.78f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("答题卡", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("已答 $answered/${questions.size}" + if (graded > 0) " · 正确率 ${(correct * 100f / graded).toInt()}%" else "",
                            style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(duration(time), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                            Text(if (since != null) "计时中" else if (banked > 0L) "已暂停" else "点击开始计时",
                                style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = {
                                    if (since != null) { banked = elapsed(); since = null }
                                    else since = SystemClock.elapsedRealtime()
                                }) { Text(if (since != null) "暂停" else "开始") }
                                TextButton(onClick = { marks = marks + DockMark(current, elapsed()) },
                                    enabled = since != null || banked > 0L) { Text("标记") }
                                TextButton(onClick = { since = null; banked = 0L; marks = emptyList() },
                                    enabled = since != null || banked > 0L) { Text("重置") }
                            }
                        }
                    }
                }
                if (marks.isNotEmpty()) {
                    item { Text("计时标记", style = MaterialTheme.typography.titleSmall) }
                    items(marks.size) { index ->
                        val mark = marks[index]
                        Surface(onClick = { open = false; onJump(mark.item) },
                            shape = RoundedCornerShape(12.dp), color = scheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("第 ${mark.item.label} 题")
                                Text("+${duration(mark.elapsed - (marks.getOrNull(index - 1)?.elapsed ?: 0L))} · ${duration(mark.elapsed)}",
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DocsDockStatus.entries.forEach { status ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(Modifier.size(9.dp), shape = CircleShape, color = colors.getValue(status)) {}
                                Text("${status.label} ${states.count { it == status }}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                // Chunking keeps large question banks lazy.
                items(questions.indices.chunked(5)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { index ->
                            val item = questions[index]
                            val status = states[index]
                            val tint = colors.getValue(status)
                            Surface(
                                onClick = { open = false; onJump(item) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                    contentDescription = "第 ${item.label} 题，${status.label}"
                                },
                                shape = RoundedCornerShape(13.dp),
                                color = if (status == DocsDockStatus.Idle) scheme.surfaceContainerLow else tint.copy(alpha = 0.13f),
                                border = BorderStroke(if (item == current) 2.dp else 1.dp, tint),
                            ) {
                                Column(Modifier.padding(vertical = 8.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(item.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    Text(status.label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                        color = if (status == DocsDockStatus.Idle) scheme.onSurfaceVariant else tint)
                                }
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
