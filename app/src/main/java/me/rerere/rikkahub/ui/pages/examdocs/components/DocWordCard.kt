package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
private data class WordMeaning(
    val pos: String? = null,
    val text: String = "",
    val key: Boolean = false,
    val grammar: String? = null,
    val phrase: Boolean = false,
)

@Serializable
private data class WordExample(
    val text: String = "",
    val translation: String = "",
)

@Serializable
private data class WordSense(
    val gloss: String = "",
    val count: Int = 0,
    val examples: List<WordExample> = emptyList(),
    val percentage: Int? = null,
)

@Serializable
private data class WordCard(
    val word: String,
    val phonetic: String? = null,
    val label: String? = null,
    val meanings: List<WordMeaning> = emptyList(),
    val senses: List<WordSense> = emptyList(),
)

private data class WordPalette(val surface: Color, val accent: Color, val ink: Color)

// Study Docs vocabulary tones, used in the same order in the Android deck.
private val palettes = listOf(
    WordPalette(Color(0xFFDFE9DC), Color(0xFF6D866E), Color(0xFF34473A)),
    WordPalette(Color(0xFFEADFD4), Color(0xFF8A6F59), Color(0xFF4A3B31)),
    WordPalette(Color(0xFFEADBDF), Color(0xFF8D6875), Color(0xFF4B373E)),
    WordPalette(Color(0xFFD7E5EB), Color(0xFF607F8D), Color(0xFF304751)),
    WordPalette(Color(0xFFE1DBE8), Color(0xFF756686), Color(0xFF40374C)),
    WordPalette(Color(0xFFEBE3D2), Color(0xFF87704D), Color(0xFF493E30)),
    WordPalette(Color(0xFFDAE3C9), Color(0xFF71844F), Color(0xFF3D4B30)),
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
    var visibleCount by rememberSaveable { mutableIntStateOf(PAGE_SIZE) }
    var activeWord by rememberSaveable { mutableIntStateOf(-1) }

    val filtered = remember(words, query) {
        val term = query.trim()
        if (term.isEmpty()) words.indices.toList() else words.indices.filter { index ->
            val word = words[index]
            word.word.contains(term, ignoreCase = true) || word.meanings.any { it.text.contains(term, ignoreCase = true) }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${filtered.size} 个词条", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            FilterChip(
                selected = studyMode,
                onClick = { studyMode = !studyMode },
                label = { Text("背词模式") },
            )
        }
        if (words.size > 10) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; visibleCount = PAGE_SIZE },
                placeholder = { Text("搜索单词或释义") },
                leadingIcon = { Icon(HugeIcons.Search01, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        for (index in filtered.take(visibleCount)) {
            key(index) {
                WordCardFace(words[index], wordPalette(index), studyMode, onExpand = { activeWord = index })
            }
        }
        if (filtered.isEmpty()) {
            Text("没有匹配的词条", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (filtered.size > visibleCount) {
            TextButton(onClick = { visibleCount += PAGE_SIZE }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("加载更多（剩余 ${filtered.size - visibleCount} 个）")
            }
        }
    }
    val activePosition = filtered.indexOf(activeWord)
    if (activePosition >= 0) {
        val previous = filtered.getOrNull(activePosition - 1)
        val next = filtered.getOrNull(activePosition + 1)
        WordDetails(
            word = words[activeWord],
            tone = wordPalette(activeWord),
            onPrevious = previous?.let { { activeWord = it } },
            onNext = next?.let { { activeWord = it } },
            onDismiss = { activeWord = -1 },
        )
    }
}

@Composable
private fun wordPalette(index: Int): WordPalette {
    val tone = palettes[index % palettes.size]
    if (!LocalDarkMode.current) return tone
    return WordPalette(
        lerp(Color(0xFF202320), tone.accent, 0.25f),
        lerp(tone.accent, tone.surface, 0.7f),
        lerp(Color(0xFFEEEEED), tone.surface, 0.35f),
    )
}

@Composable
private fun WordCardFace(word: WordCard, tone: WordPalette, studyMode: Boolean, onExpand: () -> Unit) {
    var revealed by rememberSaveable(studyMode, word.word) { mutableStateOf(false) }
    Surface(
        color = tone.surface,
        contentColor = tone.ink,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 11.dp, end = 10.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WordHeading(word, titleLarge = false, modifier = Modifier.weight(1f).padding(top = 5.dp))
                IconButton(onClick = onExpand) {
                    Icon(HugeIcons.Fullscreen, "查看 ${word.word} 的详细释义和例句", tint = tone.ink)
                }
            }
            if (!studyMode || revealed) {
                Column(
                    Modifier.padding(top = 8.dp, end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    word.meanings.forEach { WordMeaningRow(it, tone, showGrammar = false) }
                }
            } else {
                TextButton(onClick = { revealed = true }) { Text("显示释义", color = tone.ink) }
            }
        }
    }
}

@Composable
private fun WordHeading(word: WordCard, titleLarge: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            word.word,
            style = if (titleLarge) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        word.phonetic?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        word.label?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun WordMeaningRow(meaning: WordMeaning, tone: WordPalette, showGrammar: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        val badge = meaning.pos ?: "短语".takeIf { meaning.phrase }
        if (!badge.isNullOrBlank()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = lerp(tone.surface, tone.accent, if (meaning.key) 0.25f else 0.14f),
                contentColor = tone.ink,
            ) {
                Text(badge, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                meaning.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (meaning.key) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (showGrammar) {
                meaning.grammar?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                }
            }
        }
    }
}

@Composable
private fun WordDetails(
    word: WordCard,
    tone: WordPalette,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(word) { scrollState.scrollTo(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(color = tone.surface, contentColor = tone.ink, shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(start = 16.dp, top = 10.dp, end = 12.dp, bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        WordHeading(word, titleLarge = true, modifier = Modifier.weight(1f).padding(top = 6.dp))
                        IconButton(onClick = onDismiss) { Icon(HugeIcons.Cancel01, "关闭词卡") }
                    }
                    if (onPrevious != null || onNext != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                                Icon(HugeIcons.ArrowLeft01, "上一个单词")
                            }
                            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                                Icon(HugeIcons.ArrowRight01, "下一个单词")
                            }
                        }
                    }
                    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        word.meanings.forEach { WordMeaningRow(it, tone, showGrammar = true) }
                    }
                }
            }
            if (word.senses.isNotEmpty()) {
                Text("用法与例句", style = MaterialTheme.typography.titleMedium)
                val total = word.senses.sumOf { it.count.coerceAtLeast(0) }
                word.senses.forEach { WordSenseDetails(it, total, tone) }
            }
        }
    }
}

@Composable
private fun WordSenseDetails(sense: WordSense, total: Int, tone: WordPalette) {
    val scheme = MaterialTheme.colorScheme
    val share = (sense.percentage ?: if (total > 0) sense.count * 100 / total else 0).coerceIn(0, 100)
    Surface(color = scheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(sense.gloss, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (total > 0) {
                    Text("${sense.count} 次 · $share%", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
            if (total > 0) {
                LinearProgressIndicator(progress = { share / 100f }, modifier = Modifier.fillMaxWidth(), color = tone.accent)
            }
            sense.examples.forEach { example ->
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.55f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(example.text, style = MaterialTheme.typography.bodyMedium)
                    if (example.translation.isNotBlank()) {
                        Text(example.translation, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun parseWordCards(block: ExamDocsSegment.CustomBlock): List<WordCard> {
    val code = block.blocks.asSequence()
        .filterIsInstance<ExamDocsSegment.Markdown>()
        .flatMap { it.document.blocks.asSequence() }
        .filterIsInstance<MarkdownBlock.CodeBlock>()
        .firstOrNull()?.code ?: return emptyList()
    return runCatching { wordCardJson.decodeFromString<List<WordCard>>(code) }.getOrDefault(emptyList())
}
