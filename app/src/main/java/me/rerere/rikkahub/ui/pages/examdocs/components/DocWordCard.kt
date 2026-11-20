package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Fullscreen
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.theme.LocalDarkMode

@Serializable
internal data class WordMeaningDto(
    val pos: String? = null,
    val text: String = "",
    val key: Boolean = false,
    val grammar: String? = null,
    val phrase: Boolean = false,
)

@Serializable
internal data class WordExampleDto(
    val text: String = "",
    val translation: String = "",
)

@Serializable
internal data class WordSenseDto(
    val gloss: String = "",
    val count: Int = 0,
    val examples: List<WordExampleDto> = emptyList(),
    val percentage: Int? = null,
)

@Serializable
internal data class WordCardDto(
    val word: String = "",
    val phonetic: String? = null,
    val label: String? = null,
    val meanings: List<WordMeaningDto> = emptyList(),
    val senses: List<WordSenseDto> = emptyList(),
)

private data class WordCardTone(
    val surface: Color,
    val accent: Color,
    val ink: Color,
)

private val lightTones = listOf(
    WordCardTone(Color(0xFFDFE9DC), Color(0xFF6D866E), Color(0xFF34473A)), // 1: Sage
    WordCardTone(Color(0xFFEADFD4), Color(0xFF8A6F59), Color(0xFF4A3B31)), // 2: Warm Sand
    WordCardTone(Color(0xFFEADBDF), Color(0xFF8D6875), Color(0xFF4B373E)), // 3: Dusty Rose
    WordCardTone(Color(0xFFD7E5EB), Color(0xFF607F8D), Color(0xFF304751)), // 4: Slate Blue
    WordCardTone(Color(0xFFE1DBE8), Color(0xFF756686), Color(0xFF40374C)), // 5: Lavender
    WordCardTone(Color(0xFFEBE3D2), Color(0xFF87704D), Color(0xFF493E30)), // 6: Warm Amber
    WordCardTone(Color(0xFFDAE3C9), Color(0xFF71844F), Color(0xFF3D4B30)), // 7: Olive Moss
)

private val darkTones = listOf(
    WordCardTone(Color(0xFF1E261F), Color(0xFF8FB390), Color(0xFFE4EFE5)), // 1: Sage
    WordCardTone(Color(0xFF27201B), Color(0xFFC0A087), Color(0xFFF2E8DF)), // 2: Warm Sand
    WordCardTone(Color(0xFF271E21), Color(0xFFC297A5), Color(0xFFF2E4E8)), // 3: Dusty Rose
    WordCardTone(Color(0xFF1B2428), Color(0xFF8AB0C1), Color(0xFFE2EEF3)), // 4: Slate Blue
    WordCardTone(Color(0xFF221E28), Color(0xFFA897BC), Color(0xFFECE7F2)), // 5: Lavender
    WordCardTone(Color(0xFF26221A), Color(0xFFBDA279), Color(0xFFF2ECE0)), // 6: Warm Amber
    WordCardTone(Color(0xFF202519), Color(0xFF9FB678), Color(0xFFE8F0DA)), // 7: Olive Moss
)

@Composable
private fun wordCardTone(index: Int): WordCardTone {
    val palette = if (LocalDarkMode.current) darkTones else lightTones
    return palette[index.mod(palette.size)]
}

private val wordCardJson = Json { ignoreUnknownKeys = true }
private const val PAGE_SIZE = 20

@Composable
internal fun DocWordCards(
    block: ExamDocsSegment.CustomBlock,
    modifier: Modifier = Modifier,
) {
    val words = remember(block) { parseWordCards(block) }
    if (words.isEmpty()) return

    var query by rememberSaveable { mutableStateOf("") }
    var studyMode by rememberSaveable { mutableStateOf(false) }
    var visibleCount by rememberSaveable(words.size) { mutableIntStateOf(PAGE_SIZE) }
    var detailIndex by remember { mutableStateOf<Int?>(null) }

    val filtered = remember(words, query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) words
        else words.filter { entry ->
            entry.word.contains(trimmed, ignoreCase = true) ||
                entry.meanings.any { it.text.contains(trimmed, ignoreCase = true) }
        }
    }
    val displayed = remember(filtered, visibleCount) {
        filtered.take(visibleCount)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "词卡 · ${filtered.size} 词",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = studyMode,
                onClick = { studyMode = !studyMode },
                label = { Text(if (studyMode) "背词模式" else "列表模式") },
            )
        }

        if (words.size > 10) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    visibleCount = PAGE_SIZE
                },
                placeholder = { Text("筛选单词或释义…") },
                leadingIcon = { Icon(HugeIcons.Search01, null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        displayed.forEachIndexed { index, card ->
            WordCardItem(
                card = card,
                tone = wordCardTone(index),
                studyMode = studyMode,
                onExpand = { detailIndex = index },
            )
        }

        if (displayed.size < filtered.size) {
            val remaining = filtered.size - displayed.size
            OutlinedButton(
                onClick = { visibleCount += PAGE_SIZE },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("加载更多（还有 $remaining 个单词）")
            }
        }
    }

    val activeIdx = detailIndex
    if (activeIdx != null && filtered.isNotEmpty()) {
        val safeIdx = activeIdx.coerceIn(0, filtered.lastIndex)
        WordCardDetailDialog(
            words = filtered,
            index = safeIdx,
            tone = wordCardTone(safeIdx),
            onNavigate = { detailIndex = it.coerceIn(0, filtered.lastIndex) },
            onDismiss = { detailIndex = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordCardItem(
    card: WordCardDto,
    tone: WordCardTone,
    studyMode: Boolean,
    onExpand: () -> Unit,
) {
    var revealed by rememberSaveable(card.word, studyMode) { mutableStateOf(!studyMode) }
    val showMeaning = !studyMode || revealed

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = tone.surface,
        border = BorderStroke(1.dp, tone.accent.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable {
                if (studyMode && !revealed) {
                    revealed = true
                } else {
                    onExpand()
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header: Word + Phonetic on left, optional Label + Expand button on right (NO sequence number)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = card.word,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = tone.ink,
                    )
                    card.phonetic?.takeIf { it.isNotBlank() }?.let { phonetic ->
                        Text(
                            text = phonetic,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tone.ink.copy(alpha = 0.68f),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    card.label?.takeIf { it.isNotBlank() }?.let { label ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tone.accent.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = tone.ink.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Surface(
                        onClick = onExpand,
                        shape = CircleShape,
                        color = tone.accent.copy(alpha = 0.14f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = HugeIcons.Fullscreen,
                                contentDescription = "展开详情",
                                tint = tone.ink.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            if (showMeaning) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.meanings.forEach { meaning ->
                        WordMeaningRow(meaning = meaning, tone = tone)
                    }
                }

                if (card.senses.isNotEmpty()) {
                    val topGloss = card.senses.firstOrNull()?.gloss
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(tone.ink.copy(alpha = 0.05f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(tone.accent),
                        )
                        Text(
                            text = if (!topGloss.isNullOrBlank()) {
                                "${card.senses.size} 个考频义项 · $topGloss"
                            } else {
                                "${card.senses.size} 个考频义项与例句"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = tone.ink.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "详情",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = tone.accent,
                        )
                    }
                }
            } else {
                Surface(
                    onClick = { revealed = true },
                    shape = RoundedCornerShape(12.dp),
                    color = tone.accent.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = "点击查看释义",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = tone.ink,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WordMeaningRow(
    meaning: WordMeaningDto,
    tone: WordCardTone,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        meaning.pos?.takeIf { it.isNotBlank() }?.let { pos ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (meaning.key) tone.accent.copy(alpha = 0.22f) else tone.ink.copy(alpha = 0.08f),
            ) {
                Text(
                    text = pos,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tone.ink.copy(alpha = if (meaning.key) 1f else 0.78f),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = meaning.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (meaning.key) FontWeight.SemiBold else FontWeight.Normal,
                    color = tone.ink.copy(alpha = 0.92f),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (meaning.key) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = tone.accent.copy(alpha = 0.18f),
                    ) {
                        Text(
                            text = "核心",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = tone.ink,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            meaning.grammar?.takeIf { it.isNotBlank() }?.let { grammar ->
                Text(
                    text = grammar,
                    style = MaterialTheme.typography.labelSmall,
                    color = tone.ink.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordCardDetailDialog(
    words: List<WordCardDto>,
    index: Int,
    tone: WordCardTone,
    onNavigate: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val card = words[index]
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val totalCount = remember(card.senses) { card.senses.sumOf { it.count.coerceAtLeast(0) } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, tone.accent.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = screenHeight * 0.84f),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tinted Hero Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tone.surface)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        card.label?.takeIf { it.isNotBlank() }?.let { label ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = tone.accent.copy(alpha = 0.18f),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tone.ink,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (words.size > 1) {
                            Text(
                                text = "${index + 1} / ${words.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = tone.ink.copy(alpha = 0.65f),
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Cancel01,
                                contentDescription = "关闭",
                                tint = tone.ink,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = card.word,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = tone.ink,
                        )
                        card.phonetic?.takeIf { it.isNotBlank() }?.let { phonetic ->
                            Text(
                                text = phonetic,
                                style = MaterialTheme.typography.titleMedium,
                                color = tone.ink.copy(alpha = 0.7f),
                            )
                        }
                    }

                    if (card.meanings.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            card.meanings.forEach { meaning ->
                                WordMeaningRow(meaning = meaning, tone = tone)
                            }
                        }
                    }
                }

                // Scrollable Senses & Examples
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (card.senses.isEmpty()) {
                        Text(
                            text = "暂无考频义项与例句数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "考频义项与真题例句",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        card.senses.forEachIndexed { senseIdx, sense ->
                            val pct = sense.percentage ?: if (totalCount > 0) {
                                (sense.count.coerceAtLeast(0) * 100 / totalCount).coerceIn(0, 100)
                            } else 0
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = tone.accent.copy(alpha = 0.16f),
                                        ) {
                                            Text(
                                                text = (senseIdx + 1).toString().padStart(2, '0'),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = tone.accent,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            )
                                        }
                                        Text(
                                            text = sense.gloss,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "${sense.count} 次 · $pct%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    LinearProgressIndicator(
                                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                                        color = tone.accent,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                    )

                                    if (sense.examples.isEmpty()) {
                                        Text(
                                            text = "暂无例句",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            sense.examples.forEach { ex ->
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        if (ex.text.isNotBlank()) {
                                                            Text(
                                                                text = ex.text,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontStyle = FontStyle.Italic,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                            )
                                                        }
                                                        if (ex.translation.isNotBlank()) {
                                                            Text(
                                                                text = ex.translation,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                }

                // Footer Navigation
                if (words.size > 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { onNavigate(index - 1) },
                            enabled = index > 0,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(HugeIcons.ArrowLeft01, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("上一个")
                        }
                        FilledTonalButton(
                            onClick = { onNavigate(index + 1) },
                            enabled = index < words.lastIndex,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("下一个")
                            Spacer(Modifier.size(4.dp))
                            Icon(HugeIcons.ArrowRight01, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun parseWordCards(block: ExamDocsSegment.CustomBlock): List<WordCardDto> {
    val jsonText = block.blocks.asSequence()
        .filterIsInstance<ExamDocsSegment.Markdown>()
        .flatMap { it.document.blocks.asSequence() }
        .filterIsInstance<MarkdownBlock.CodeBlock>()
        .firstOrNull()
        ?.code
        ?: return emptyList()
    return runCatching { wordCardJson.decodeFromString<List<WordCardDto>>(jsonText) }.getOrDefault(emptyList())
}
