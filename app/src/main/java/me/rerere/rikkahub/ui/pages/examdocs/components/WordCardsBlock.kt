package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Eye
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import kotlin.math.roundToInt

private const val PAGE_SIZE = 20

@Immutable
@Serializable
private data class WordMeaningDto(
    val pos: String? = null,
    val text: String,
    val key: Boolean = false,
    val grammar: String? = null,
    val phrase: Boolean = false,
)

@Immutable
@Serializable
private data class WordExampleDto(
    val text: String,
    val translation: String = "",
)

@Immutable
@Serializable
private data class WordSenseDto(
    val gloss: String,
    val count: Int = 0,
    val percentage: Int? = null,
    val examples: List<WordExampleDto> = emptyList(),
)

@Immutable
@Serializable
private data class WordCardDto(
    val word: String,
    val phonetic: String? = null,
    val label: String? = null,
    val meanings: List<WordMeaningDto> = emptyList(),
    val senses: List<WordSenseDto> = emptyList(),
)

private val wordCardsJson = Json { ignoreUnknownKeys = true }

@Composable
internal fun ExamDocsWordCardsBlock(
    block: ExamDocsSegment.CustomBlock,
) {
    val words = remember(block.blocks) { parseWordCards(block.blocks) }
    if (words.isEmpty()) return

    var query by rememberSaveable(block.title) { mutableStateOf("") }
    var memoryMode by rememberSaveable(block.title) { mutableStateOf(false) }
    var visibleCount by rememberSaveable(block.title, query) { mutableIntStateOf(PAGE_SIZE) }
    var activeWord by remember { mutableStateOf<WordCardDto?>(null) }

    val filtered = remember(words, query) {
        val q = query.trim()
        if (q.isEmpty()) words
        else words.filter { card ->
            card.word.contains(q, ignoreCase = true) ||
                card.meanings.any { it.text.contains(q, ignoreCase = true) } ||
                (card.label?.contains(q, ignoreCase = true) == true)
        }
    }
    val displayed = remember(filtered, visibleCount) { filtered.take(visibleCount) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = memoryMode,
                onClick = { memoryMode = !memoryMode },
                leadingIcon = {
                    Icon(HugeIcons.Eye, null, Modifier.size(16.dp))
                },
                label = { Text(if (memoryMode) "记忆模式：隐藏释义" else "记忆模式") },
            )
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = colors.surfaceContainerHigh) {
                Text(
                    text = "共 ${filtered.size} 词",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        if (words.size > 8) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索单词或中文释义") },
                leadingIcon = { Icon(HugeIcons.Search01, null, Modifier.size(18.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(HugeIcons.Cancel01, "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (displayed.isEmpty()) {
            Text(
                text = "未找到匹配的单词",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            displayed.forEachIndexed { index, item ->
                WordCardItem(
                    card = item,
                    toneIndex = index,
                    memoryMode = memoryMode,
                    onOpenDetail = { activeWord = item },
                )
            }
            if (displayed.size < filtered.size) {
                val remaining = filtered.size - displayed.size
                OutlinedButton(
                    onClick = { visibleCount += PAGE_SIZE },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("加载更多（还有 $remaining 张词卡）")
                }
            }
        }
    }

    activeWord?.let { selected ->
        WordCardDetailSheet(
            card = selected,
            onDismiss = { activeWord = null },
        )
    }
}

@Composable
private fun WordCardItem(
    card: WordCardDto,
    toneIndex: Int,
    memoryMode: Boolean,
    onOpenDetail: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accent = when (toneIndex % 4) {
        0 -> colors.primary
        1 -> colors.secondary
        2 -> colors.tertiary
        else -> colors.primary.copy(alpha = 0.8f)
    }
    var peekMeaning by remember(card.word, memoryMode) { mutableStateOf(false) }
    val showMeanings = !memoryMode || peekMeaning

    Surface(
        onClick = {
            if (memoryMode && !peekMeaning) {
                peekMeaning = true
            } else {
                onOpenDetail()
            }
        },
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.28f)),
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
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = card.word,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                card.phonetic?.takeIf { it.isNotBlank() }?.let { phonetic ->
                    Text(
                        text = phonetic,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                card.label?.takeIf { it.isNotBlank() }?.let { label ->
                    Surface(shape = CircleShape, color = colors.surfaceContainerHigh) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showMeanings) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    card.meanings.forEach { meaning ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            meaning.pos?.takeIf { it.isNotBlank() }?.let { pos ->
                                Text(
                                    text = pos,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent,
                                )
                            }
                            Text(
                                text = meaning.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (meaning.key) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (meaning.key) colors.onSurface else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (memoryMode && !peekMeaning) {
                Text(
                    text = "轻触查看释义，再次点击查看例句与词义分布",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                )
            } else if (card.senses.isNotEmpty()) {
                Text(
                    text = "${card.senses.size} 个常考词义 · 点击查看分布与例句",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordCardDetailSheet(
    card: WordCardDto,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val totalCount = remember(card.senses) { card.senses.sumOf { it.count.coerceAtLeast(0) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = card.word,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        card.phonetic?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    card.label?.takeIf { it.isNotBlank() }?.let { label ->
                        Surface(shape = CircleShape, color = colors.primaryContainer) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            if (card.meanings.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("核心释义", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            card.meanings.forEach { m ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    m.pos?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.labelMedium, color = colors.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text(
                                        text = m.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (m.key) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (card.senses.isNotEmpty()) {
                item {
                    Text("词义分布与真题例句", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                itemsIndexed(card.senses) { index, sense ->
                    val pct = sense.percentage ?: if (totalCount > 0) {
                        ((sense.count.toFloat() / totalCount) * 100f).roundToInt().coerceIn(0, 100)
                    } else 0
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${(index + 1).toString().padStart(2, '0')} · ${sense.gloss}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (pct > 0 || sense.count > 0) {
                                    Text(
                                        text = if (pct > 0) "$pct%" else "${sense.count} 次",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            if (pct > 0) {
                                LinearProgressIndicator(
                                    progress = { pct / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                )
                            }
                            sense.examples.forEach { example ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceContainer)
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(example.text, style = MaterialTheme.typography.bodyMedium)
                                    if (example.translation.isNotBlank()) {
                                        Text(
                                            text = example.translation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
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

private fun parseWordCards(blocks: List<ExamDocsSegment>): List<WordCardDto> {
    val codeBlock = blocks.asSequence()
        .filterIsInstance<ExamDocsSegment.Markdown>()
        .mapNotNull { it.document.blocks.singleOrNull() as? MarkdownBlock.CodeBlock }
        .firstOrNull() ?: return emptyList()
    return runCatching { wordCardsJson.decodeFromString<List<WordCardDto>>(codeBlock.code) }
        .getOrDefault(emptyList())
}
