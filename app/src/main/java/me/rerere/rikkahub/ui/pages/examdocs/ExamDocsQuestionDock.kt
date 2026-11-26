package me.rerere.rikkahub.ui.pages.examdocs

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Pause
import me.rerere.hugeicons.stroke.Pin
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.examdocs.DocsSavedAnswer
import me.rerere.rikkahub.data.examdocs.ExamDocsDocument
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.components.nav.topBarMenuHaze
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.roundToInt

internal enum class DocQuestionStatus(val label: String) {
    Idle("未答"),
    Draft("作答中"),
    Correct("正确"),
    Partial("部分"),
    Incorrect("错误"),
    Recorded("已答"),
    ;

    val isAnswered: Boolean get() = this != Idle && this != Draft
}

internal data class PageQuestionItem(
    val id: String,
    val label: String,
    val blockIndex: Int,
    val status: DocQuestionStatus,
)

internal data class StopwatchMark(
    val atMs: Long,
    val questionId: String?,
    val label: String?,
)

@Stable
internal class DockStopwatchState {
    var sinceMs by mutableStateOf<Long?>(null)
        private set
    var bankedMs by mutableLongStateOf(0L)
        private set
    var marks by mutableStateOf<List<StopwatchMark>>(emptyList())
        private set

    val running: Boolean get() = sinceMs != null
    val started: Boolean get() = sinceMs != null || bankedMs > 0L

    fun elapsedMs(now: Long = SystemClock.elapsedRealtime()): Long =
        bankedMs + (sinceMs?.let { now - it } ?: 0L)

    fun start() {
        if (sinceMs == null) {
            sinceMs = SystemClock.elapsedRealtime()
        }
    }

    fun pause() {
        val startedAt = sinceMs ?: return
        bankedMs += SystemClock.elapsedRealtime() - startedAt
        sinceMs = null
    }

    fun mark(questionId: String?, label: String?) {
        marks = marks + StopwatchMark(elapsedMs(), questionId, label)
    }

    fun reset() {
        sinceMs = null
        bankedMs = 0L
        marks = emptyList()
    }
}

@Serializable
private data class DockBlankKey(
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

private fun evaluateDocQuestionStatus(
    block: ExamDocsSegment.CustomBlock,
    saved: DocsSavedAnswer?,
): DocQuestionStatus {
    if (saved == null) return DocQuestionStatus.Idle
    val answer = saved.state
    if (!answer.submitted) {
        val hasDraft = answer.selected.isNotEmpty() ||
            answer.values.any { it.isNotBlank() } ||
            saved.draft.isNotBlank()
        return if (hasDraft) DocQuestionStatus.Draft else DocQuestionStatus.Idle
    }

    val type = block.attrs["type"]
    if (type.equals("Essay", ignoreCase = true)) {
        return when (answer.selected.firstOrNull()) {
            "A" -> DocQuestionStatus.Correct
            "B" -> DocQuestionStatus.Partial
            "C" -> DocQuestionStatus.Incorrect
            else -> DocQuestionStatus.Recorded
        }
    }

    if (type.equals("Blank", ignoreCase = true)) {
        val blankKeys = block.attrs["blanks"]
            ?.let { runCatching { Json.decodeFromString<List<DockBlankKey>>(it) }.getOrNull() }
            .orEmpty()
        val blankCount = maxOf(1, blankKeys.size, answer.values.size)
        val matches = (0 until blankCount).map { index ->
            when {
                "blank:$index:correct" in answer.selected -> true
                "blank:$index:incorrect" in answer.selected -> false
                else -> blankKeys.getOrNull(index)?.matches(answer.values.getOrElse(index) { "" })
            }
        }
        val correctCount = matches.count { it == true }
        val checkedCount = matches.count { it != null }
        return when {
            checkedCount == 0 -> DocQuestionStatus.Recorded
            correctCount == matches.size -> DocQuestionStatus.Correct
            correctCount > 0 -> DocQuestionStatus.Partial
            else -> DocQuestionStatus.Incorrect
        }
    }

    val correct = if (type.equals("Boolean", ignoreCase = true)) {
        when (block.attrs["answer"]?.lowercase()?.toBooleanStrictOrNull()) {
            true -> setOf("A")
            false -> setOf("B")
            else -> emptySet()
        }
    } else {
        block.blocks.filterIsInstance<ExamDocsSegment.CustomBlock>()
            .filter { it.type == "option" }
            .mapIndexedNotNull { index, option ->
                if (option.attrs["correct"] == "true") {
                    option.attrs["label"] ?: ('A' + index).toString()
                } else {
                    null
                }
            }
            .toSet()
    }

    return when {
        correct.isEmpty() -> DocQuestionStatus.Recorded
        answer.selected.toSet() == correct -> DocQuestionStatus.Correct
        answer.selected.all { it in correct } -> DocQuestionStatus.Partial
        else -> DocQuestionStatus.Incorrect
    }
}

private fun collectPageQuestions(
    document: ExamDocsDocument,
    reading: DocsReadingProgress?,
): List<PageQuestionItem> {
    val items = mutableListOf<PageQuestionItem>()
    var ordinal = 0
    fun visit(segments: List<ExamDocsSegment>, topBlockIndex: Int) {
        for (segment in segments) {
            if (segment !is ExamDocsSegment.CustomBlock) continue
            if (segment.type == "question") {
                ordinal++
                val label = segment.attrs["number"]?.takeIf(String::isNotBlank) ?: ordinal.toString()
                val identity = reading?.identities?.get(segment)
                val keyId = identity?.answerId ?: "local:$ordinal"
                val saved = identity?.let { reading.snapshot?.answers?.get(it.answerId) }
                items.add(
                    PageQuestionItem(
                        id = keyId,
                        label = label,
                        blockIndex = topBlockIndex,
                        status = evaluateDocQuestionStatus(segment, saved),
                    ),
                )
            }
            visit(segment.blocks, topBlockIndex)
        }
    }
    document.blocks.forEachIndexed { index, segment ->
        visit(listOf(segment), index)
    }
    return items
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds / 60L) % 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun statusAccentColor(status: DocQuestionStatus): Color {
    val dark = LocalDarkMode.current
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        DocQuestionStatus.Idle -> scheme.outlineVariant.copy(alpha = 0.5f)
        DocQuestionStatus.Draft -> scheme.secondary.copy(alpha = 0.75f)
        DocQuestionStatus.Correct -> if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A)
        DocQuestionStatus.Partial -> if (dark) Color(0xFFFBBF24) else Color(0xFFD97706)
        DocQuestionStatus.Incorrect -> scheme.error
        DocQuestionStatus.Recorded -> scheme.primary
    }
}

@Composable
internal fun ExamDocsQuestionDock(
    document: ExamDocsDocument,
    listState: LazyListState,
    hazeState: HazeState,
    displaySetting: DisplaySetting,
    modifier: Modifier = Modifier,
) {
    val reading = LocalDocsReadingProgress.current
    val items = remember(document, reading?.snapshot, reading?.identities) {
        collectPageQuestions(document, reading)
    }
    if (items.isEmpty()) return

    val stopwatch = remember(document.sourceUrl) { DockStopwatchState() }
    var showSheet by remember { mutableStateOf(false) }
    var currentQuestionId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val answeredCount = remember(items) { items.count { it.status.isAnswered } }
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        onClick = {
            val visibleIndex = listState.firstVisibleItemIndex
            currentQuestionId = items.lastOrNull { it.blockIndex <= visibleIndex }?.id ?: items.firstOrNull()?.id
            showSheet = true
        },
        shape = CircleShape,
        color = if (displaySetting.enableBlurEffect) Color.Transparent else containerColor,
        modifier = modifier
            .size(64.dp)
            .topBarMenuHaze(
                hazeState = hazeState,
                displaySetting = displaySetting,
                containerColor = containerColor,
                shape = CircleShape,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            DockStatusRing(
                items = items,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
            )
            AnimatedContent(
                targetState = stopwatch.started,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "dockBallContent",
            ) { timerStarted ->
                if (timerStarted) {
                    FloatingBallTimerContent(stopwatch)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$answeredCount",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "/${items.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            QuestionDockSheetContent(
                items = items,
                currentQuestionId = currentQuestionId,
                stopwatch = stopwatch,
                onMarkHere = {
                    val visibleIndex = listState.firstVisibleItemIndex
                    val target = items.lastOrNull { it.blockIndex <= visibleIndex } ?: items.firstOrNull()
                    stopwatch.mark(target?.id, target?.label)
                },
                onJumpToQuestion = { blockIndex ->
                    showSheet = false
                    scope.launch {
                        listState.animateScrollToItem(blockIndex)
                    }
                },
            )
        }
    }
}

@Composable
private fun DockStatusRing(
    items: List<PageQuestionItem>,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
    val segmentColors = items.map { statusAccentColor(it.status) }

    Canvas(modifier = modifier) {
        val strokePx = 3.2.dp.toPx()
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
        drawCircle(
            color = trackColor,
            style = Stroke(width = strokePx),
        )
        val count = items.size
        if (count == 0) return@Canvas
        val step = 360f / count
        val gap = if (count > 1) minOf(8f, step * 0.28f) else 0f
        val sweep = (step - gap).coerceAtLeast(1f)
        segmentColors.forEachIndexed { index, color ->
            val startAngle = -90f + index * step + gap / 2f
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = stroke,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionDockSheetContent(
    items: List<PageQuestionItem>,
    currentQuestionId: String?,
    stopwatch: DockStopwatchState,
    onMarkHere: () -> Unit,
    onJumpToQuestion: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val counts = remember(items) {
        items.groupingBy { it.status }.eachCount()
    }
    val correctCount = counts[DocQuestionStatus.Correct] ?: 0
    val partialCount = counts[DocQuestionStatus.Partial] ?: 0
    val incorrectCount = counts[DocQuestionStatus.Incorrect] ?: 0
    val recordedCount = counts[DocQuestionStatus.Recorded] ?: 0
    val idleCount = (counts[DocQuestionStatus.Idle] ?: 0) + (counts[DocQuestionStatus.Draft] ?: 0)
    val answeredCount = items.size - idleCount
    val gradedCount = correctCount + partialCount + incorrectCount
    val accuracy = if (gradedCount > 0) ((correctCount.toFloat() / gradedCount) * 100f).roundToInt() else null
    val progress = if (items.isNotEmpty()) answeredCount.toFloat() / items.size else 0f
    val markedIds = remember(stopwatch.marks) {
        stopwatch.marks.mapNotNull { it.questionId }.toSet()
    }
    val itemById = remember(items) { items.associateBy { it.id } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 计时器卡片
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.surfaceContainerLow,
            border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                stopwatch.running -> colors.primaryContainer
                                stopwatch.started -> colors.secondaryContainer
                                else -> colors.surfaceContainerHigh
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Clock01,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = when {
                                        stopwatch.running -> colors.onPrimaryContainer
                                        stopwatch.started -> colors.onSecondaryContainer
                                        else -> colors.onSurfaceVariant
                                    },
                                )
                                Text(
                                    text = when {
                                        stopwatch.running -> "计时中"
                                        stopwatch.started -> "已暂停"
                                        else -> "做题计时"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = when {
                                        stopwatch.running -> colors.onPrimaryContainer
                                        stopwatch.started -> colors.onSecondaryContainer
                                        else -> colors.onSurfaceVariant
                                    },
                                )
                            }
                        }
                        StopwatchClockText(stopwatch)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalIconButton(
                            onClick = stopwatch::reset,
                            enabled = stopwatch.started,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colors.surfaceContainerHigh,
                            ),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(HugeIcons.Refresh03, "重置计时", modifier = Modifier.size(18.dp))
                        }
                        FilledTonalIconButton(
                            onClick = onMarkHere,
                            enabled = stopwatch.started,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colors.surfaceContainerHigh,
                            ),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(HugeIcons.Pin, "标记当前题目与时间", modifier = Modifier.size(18.dp))
                        }
                        FilledIconButton(
                            onClick = { if (stopwatch.running) stopwatch.pause() else stopwatch.start() },
                            modifier = Modifier.size(46.dp),
                        ) {
                            Icon(
                                imageVector = if (stopwatch.running) HugeIcons.Pause else HugeIcons.Play,
                                contentDescription = if (stopwatch.running) "暂停计时" else "开始计时",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                if (stopwatch.marks.isNotEmpty()) {
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "分段标记 (${stopwatch.marks.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = colors.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        stopwatch.marks.forEachIndexed { index, mark ->
                            val targetBlockIndex = mark.questionId?.let { itemById[it]?.blockIndex }
                            val prevAt = stopwatch.marks.getOrNull(index - 1)?.atMs ?: 0L
                            val split = (mark.atMs - prevAt).coerceAtLeast(0L)
                            Surface(
                                onClick = { if (targetBlockIndex != null) onJumpToQuestion(targetBlockIndex) },
                                enabled = targetBlockIndex != null,
                                shape = RoundedCornerShape(12.dp),
                                color = colors.surfaceContainer.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(colors.primary.copy(alpha = 0.14f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.primary,
                                        )
                                    }
                                    Text(
                                        text = mark.label?.let { "第 $it 题" } ?: "标记 ${index + 1}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = colors.surfaceContainerHighest.copy(alpha = 0.7f),
                                    ) {
                                        Text(
                                            text = "+${formatDuration(split)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                            color = colors.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                    Text(
                                        text = formatDuration(mark.atMs),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 答题卡卡片
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.surfaceContainerLow,
            border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "答题卡",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            shape = CircleShape,
                            color = colors.surfaceContainerHigh,
                        ) {
                            Text(
                                text = "$answeredCount / ${items.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    if (accuracy != null) {
                        Surface(
                            shape = CircleShape,
                            color = colors.primaryContainer.copy(alpha = 0.75f),
                        ) {
                            Text(
                                text = "正确率 $accuracy%",
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // 进度条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(colors.primary),
                    )
                }

                // 图例胶囊
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LegendItem(DocQuestionStatus.Correct, correctCount)
                    LegendItem(DocQuestionStatus.Incorrect, incorrectCount)
                    if (partialCount > 0) {
                        LegendItem(DocQuestionStatus.Partial, partialCount)
                    }
                    if (recordedCount > 0) {
                        LegendItem(DocQuestionStatus.Recorded, recordedCount)
                    }
                    LegendItem(DocQuestionStatus.Idle, idleCount)
                }

                // 等宽自适应题号网格
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns = (maxWidth / 52.dp).toInt().coerceIn(5, 10)
                    val rows = remember(items, columns) { items.chunked(columns) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowItems.forEach { item ->
                                    val accent = statusAccentColor(item.status)
                                    val isCurrent = item.id == currentQuestionId
                                    val isMarked = item.id in markedIds
                                    val tileBg = when (item.status) {
                                        DocQuestionStatus.Idle -> colors.surfaceContainer
                                        else -> accent.copy(alpha = 0.14f)
                                    }
                                    val tileBorder = when {
                                        isCurrent -> BorderStroke(2.dp, colors.primary)
                                        item.status == DocQuestionStatus.Idle ->
                                            BorderStroke(0.8.dp, colors.outlineVariant.copy(alpha = 0.45f))
                                        else -> BorderStroke(1.dp, accent.copy(alpha = 0.55f))
                                    }
                                    val textColor = when (item.status) {
                                        DocQuestionStatus.Idle -> colors.onSurfaceVariant
                                        else -> accent
                                    }

                                    Surface(
                                        onClick = { onJumpToQuestion(item.blockIndex) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = tileBg,
                                        border = tileBorder,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                                color = textColor,
                                                maxLines = 1,
                                            )
                                            if (isMarked) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(5.dp)
                                                        .size(5.dp)
                                                        .background(colors.primary, CircleShape),
                                                )
                                            }
                                        }
                                    }
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberStopwatchElapsedMs(stopwatch: DockStopwatchState): Long {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(stopwatch.running, stopwatch.sinceMs, stopwatch.bankedMs) {
        if (!stopwatch.running) return@LaunchedEffect
        while (true) {
            val remainder = stopwatch.elapsedMs() % 1000L
            delay((1000L - remainder).coerceAtLeast(16L))
            tick++
        }
    }
    @Suppress("UNUSED_EXPRESSION")
    tick
    return stopwatch.elapsedMs()
}

@Composable
private fun FloatingBallTimerContent(stopwatch: DockStopwatchState) {
    val elapsedMs = rememberStopwatchElapsedMs(stopwatch)
    val text = formatDuration(elapsedMs)
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFeatureSettings = "tnum",
                fontSize = if (text.length > 5) 10.sp else 12.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = if (stopwatch.running) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
        )
        if (!stopwatch.running) {
            Text(
                text = "暂停",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StopwatchClockText(stopwatch: DockStopwatchState) {
    val text = formatDuration(rememberStopwatchElapsedMs(stopwatch))
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Bold,
        color = if (stopwatch.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun LegendItem(status: DocQuestionStatus, count: Int) {
    val color = statusAccentColor(status)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, CircleShape),
            )
            Text(
                text = "${status.label} $count",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
