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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import me.rerere.hugeicons.stroke.View
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

// Study Docs 7-tone vocabulary palette.
private val palettes = listOf(
    WordPalette(Color(0xFFE7F0E4), Color(0xFF5D7B5F), Color(0xFF283A2C)),
    WordPalette(Color(0xFFF2E8DD), Color(0xFF8C6B52), Color(0xFF423228)),
    WordPalette(Color(0xFFF3E4E8), Color(0xFF8F6170), Color(0xFF452C34)),
    WordPalette(Color(0xFFE2EEF3), Color(0xFF537888), Color(0xFF243B45)),
    WordPalette(Color(0xFFEBE5F1), Color(0xFF715F88), Color(0xFF352C44)),
    WordPalette(Color(0xFFF3EBDA), Color(0xFF886C42), Color(0xFF433622)),
    WordPalette(Color(0xFFE4ECD7), Color(0xFF6A8045), Color(0xFF313F21)),
)

private val senseColors = listOf(
    Color(0xFF4F7A5A),
    Color(0xFF537888),
    Color(0xFF8C6B52),
    Color(0xFF715F88),
    Color(0xFF8F6170),
    Color(0xFF886C42),
)

private val wordCardJson = Json { ignoreUnknownKeys = true }
private const val PAGE_SIZE = 20

@OptIn(ExperimentalLayoutApi::class)
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
            word.word.contains(term, ignoreCase = true) ||
                word.meanings.any { it.text.contains(term, ignoreCase = true) }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = "${filtered.size} 个词条",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = studyMode,
                onClick = { studyMode = !studyMode },
                leadingIcon = {
                    Icon(
                        HugeIcons.View,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
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
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        for (index in filtered.take(visibleCount)) {
            key(index) {
                WordCardFace(
                    word = words[index],
                    tone = wordPalette(index),
                    studyMode = studyMode,
                    onExpand = { activeWord = index },
                )
            }
        }

        if (filtered.isEmpty()) {
            Text(
                "没有匹配的词条",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (filtered.size > visibleCount) {
            TextButton(
                onClick = { visibleCount += PAGE_SIZE },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("加载更多（剩余 ${filtered.size - visibleCount} 个）")
            }
        }
    }

    if (activeWord in words.indices) {
        WordDetailsDialog(
            words = words,
            index = activeWord,
            onNavigate = { next -> activeWord = next.coerceIn(0, words.lastIndex) },
            onDismiss = { activeWord = -1 },
        )
    }
}

@Composable
private fun wordPalette(index: Int): WordPalette {
    val tone = palettes[index % palettes.size]
    if (!LocalDarkMode.current) return tone
    return WordPalette(
        surface = lerp(Color(0xFF1F2220), tone.accent, 0.24f),
        accent = lerp(tone.accent, tone.surface, 0.65f),
        ink = lerp(Color(0xFFF0F1EE), tone.surface, 0.3f),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordCardFace(
    word: WordCard,
    tone: WordPalette,
    studyMode: Boolean,
    onExpand: () -> Unit,
) {
    var revealed by rememberSaveable(studyMode, word.word) { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    val badgeBg = lerp(tone.surface, tone.accent, 0.16f)
    val badgeText = lerp(tone.accent, tone.ink, 0.4f)

    Surface(
        color = tone.surface,
        contentColor = tone.ink,
        shape = shape,
        border = BorderStroke(1.dp, tone.ink.copy(alpha = 0.09f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable {
                if (studyMode && !revealed) {
                    revealed = true
                } else {
                    onExpand()
                }
            },
    ) {
        Column(
            modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f).padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = tone.ink,
                        modifier = Modifier.alignByBaseline(),
                    )
                    word.phonetic?.takeIf(String::isNotBlank)?.let { phonetic ->
                        Text(
                            text = phonetic,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tone.ink.copy(alpha = 0.66f),
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    word.label?.takeIf(String::isNotBlank)?.let { label ->
                        Surface(
                            shape = CircleShape,
                            color = badgeBg,
                            contentColor = badgeText,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                    }
                    Surface(
                        onClick = onExpand,
                        shape = CircleShape,
                        color = badgeBg,
                        contentColor = tone.ink,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = HugeIcons.Fullscreen,
                                contentDescription = "查看 ${word.word} 的详细释义和例句",
                                tint = tone.ink,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = !studyMode || revealed) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    word.meanings.forEach { meaning ->
                        WordMeaningRow(meaning = meaning, tone = tone, showGrammar = false)
                    }
                }
            }

            if (studyMode && !revealed) {
                Surface(
                    onClick = { revealed = true },
                    shape = RoundedCornerShape(12.dp),
                    color = tone.ink.copy(alpha = 0.06f),
                ) {
                    Text(
                        text = "点击显示释义",
                        style = MaterialTheme.typography.labelLarge,
                        color = tone.ink.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WordMeaningRow(
    meaning: WordMeaning,
    tone: WordPalette,
    showGrammar: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val badge = meaning.pos ?: "短语".takeIf { meaning.phrase }
        if (!badge.isNullOrBlank()) {
            val bg = lerp(tone.surface, tone.accent, if (meaning.key) 0.24f else 0.14f)
            val fg = lerp(tone.accent, tone.ink, 0.45f)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = bg,
                contentColor = fg,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = meaning.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (meaning.key) FontWeight.SemiBold else FontWeight.Normal,
                color = tone.ink.copy(alpha = 0.92f),
            )
            if (showGrammar) {
                meaning.grammar?.takeIf(String::isNotBlank)?.let { grammar ->
                    Text(
                        text = grammar,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = tone.ink.copy(alpha = 0.68f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordDetailsDialog(
    words: List<WordCard>,
    index: Int,
    onNavigate: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val word = words[index]
    val tone = wordPalette(index)
    val scheme = MaterialTheme.colorScheme
    val total = remember(word) { word.senses.sumOf { it.count.coerceAtLeast(0) } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(28.dp),
            color = scheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top navigation bar inside modal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tone.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (words.size > 1) {
                        IconButton(
                            onClick = { onNavigate(index - 1) },
                            enabled = index > 0,
                        ) {
                            Icon(HugeIcons.ArrowLeft01, "上一个单词", tint = tone.ink)
                        }
                        Text(
                            text = "${index + 1} / ${words.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = tone.ink.copy(alpha = 0.78f),
                        )
                        IconButton(
                            onClick = { onNavigate(index + 1) },
                            enabled = index < words.lastIndex,
                        ) {
                            Icon(HugeIcons.ArrowRight01, "下一个单词", tint = tone.ink)
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    word.label?.takeIf(String::isNotBlank)?.let { label ->
                        Surface(
                            shape = CircleShape,
                            color = lerp(tone.surface, tone.accent, 0.18f),
                            contentColor = lerp(tone.accent, tone.ink, 0.4f),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(HugeIcons.Cancel01, "关闭词卡", tint = tone.ink)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Toned Hero section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tone.surface)
                            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = word.word,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = tone.ink,
                                modifier = Modifier.alignByBaseline(),
                            )
                            word.phonetic?.takeIf(String::isNotBlank)?.let { phonetic ->
                                Text(
                                    text = phonetic,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tone.ink.copy(alpha = 0.68f),
                                    modifier = Modifier.alignByBaseline(),
                                )
                            }
                        }
                        if (word.meanings.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                word.meanings.forEach { meaning ->
                                    WordMeaningRow(meaning = meaning, tone = tone, showGrammar = true)
                                }
                            }
                        }
                    }

                    // Senses & examples
                    if (word.senses.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "真题语境与例句",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (total > 0) {
                                    Text(
                                        text = "共 $total 次",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }

                            word.senses.forEachIndexed { senseIndex, sense ->
                                WordSenseDetails(
                                    sense = sense,
                                    total = total,
                                    accent = senseColors[senseIndex % senseColors.size],
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordSenseDetails(sense: WordSense, total: Int, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    val share = (sense.percentage ?: if (total > 0) sense.count * 100 / total else 0).coerceIn(0, 100)
    Surface(
        color = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Text(
                    text = sense.gloss,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (total > 0) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.14f),
                        contentColor = accent,
                    ) {
                        Text(
                            text = "${sense.count} 次 · $share%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (total > 0) {
                LinearProgressIndicator(
                    progress = { share / 100f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = accent,
                    trackColor = scheme.surfaceContainerHighest,
                )
            }

            if (sense.examples.isEmpty()) {
                Text(
                    text = "暂无例句",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                sense.examples.forEachIndexed { idx, example ->
                    if (idx > 0) {
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = example.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                        )
                        if (example.translation.isNotBlank()) {
                            Text(
                                text = example.translation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
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
