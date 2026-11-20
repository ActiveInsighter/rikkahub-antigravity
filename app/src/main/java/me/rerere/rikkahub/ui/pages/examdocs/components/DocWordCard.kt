package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment

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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Controls bar: count, study mode toggle, search
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        displayed.forEachIndexed { index, card ->
            WordCardItem(
                card = card,
                index = index + 1,
                studyMode = studyMode,
            )
        }

        if (displayed.size < filtered.size) {
            val remaining = filtered.size - displayed.size
            OutlinedButton(
                onClick = { visibleCount += PAGE_SIZE },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("加载更多（还有 $remaining 个单词）")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordCardItem(
    card: WordCardDto,
    index: Int,
    studyMode: Boolean,
) {
    var revealed by rememberSaveable(card.word, studyMode) { mutableStateOf(!studyMode) }
    var showSenses by rememberSaveable(card.word) { mutableStateOf(false) }
    val showMeaning = !studyMode || revealed

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                if (studyMode && !revealed) {
                    revealed = true
                } else if (card.senses.isNotEmpty()) {
                    showSenses = !showSenses
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = card.word,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                card.phonetic?.takeIf { it.isNotBlank() }?.let { phonetic ->
                    Text(
                        text = phonetic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                card.label?.takeIf { it.isNotBlank() }?.let { label ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(
                    text = index.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            if (showMeaning) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    card.meanings.forEach { meaning ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            meaning.pos?.takeIf { it.isNotBlank() }?.let { pos ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (meaning.key) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                ) {
                                    Text(
                                        text = pos,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (meaning.key) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = meaning.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (meaning.key) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                if (card.senses.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSenses = !showSenses }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (showSenses) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (showSenses) "收起例句与考频" else "查看例句与考频 (${card.senses.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                AnimatedVisibility(visible = showSenses && card.senses.isNotEmpty()) {
                    val total = remember(card.senses) { card.senses.sumOf { it.count.coerceAtLeast(0) } }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        card.senses.forEach { sense ->
                            val pct = sense.percentage ?: if (total > 0) (sense.count * 100 / total) else 0
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = sense.gloss,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "${sense.count}次 ($pct%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                )
                                sense.examples.forEach { ex ->
                                    Column(
                                        modifier = Modifier.padding(top = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = ex.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
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
            } else {
                FilledTonalButton(
                    onClick = { revealed = true },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("点击查看释义")
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
