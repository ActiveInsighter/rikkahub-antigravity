package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import me.rerere.rikkahub.ui.theme.GoogleSansFlex
import me.rerere.rikkahub.ui.theme.LocalDarkMode

private enum class WordStudyMode(val label: String) {
    Default("默认"),
    Dictation("默写"),
    Translation("翻译"),
}

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
    var studyMode by rememberSaveable { mutableStateOf(WordStudyMode.Default) }
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

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${filtered.size} 个词条",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            WordModeSegmentedTabs(
                selected = studyMode,
                onSelect = { studyMode = it },
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
                WordCardFace(
                    word = words[index],
                    tone = wordPalette(index),
                    mode = studyMode,
                    onExpand = { activeWord = index },
                )
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
            position = activePosition + 1,
            totalCount = filtered.size,
            onPrevious = previous?.let { { activeWord = it } },
            onNext = next?.let { { activeWord = it } },
            onDismiss = { activeWord = -1 },
        )
    }
}

@Composable
private fun WordModeSegmentedTabs(
    selected: WordStudyMode,
    onSelect: (WordStudyMode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val modes = WordStudyMode.entries
    val selectedIndex = modes.indexOf(selected).coerceAtLeast(0)

    Surface(
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(3.dp)
                .width(174.dp)
                .height(30.dp),
        ) {
            val segmentWidth = maxWidth / modes.size
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                label = "word_mode_indicator",
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(scheme.surface),
            )
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                modes.forEach { mode ->
                    val isActive = mode == selected
                    val textColor by animateColorAsState(
                        targetValue = if (isActive) scheme.onSurface else scheme.onSurfaceVariant,
                        label = "word_mode_text",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable { onSelect(mode) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                            color = textColor,
                        )
                    }
                }
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordCardFace(
    word: WordCard,
    tone: WordPalette,
    mode: WordStudyMode,
    onExpand: () -> Unit,
) {
    var revealed by rememberSaveable(mode, word.word) { mutableStateOf(false) }
    val hideHeadword = mode == WordStudyMode.Dictation && !revealed
    val hideMeanings = mode == WordStudyMode.Translation && !revealed
    val dark = LocalDarkMode.current

    Surface(
        color = tone.surface,
        contentColor = tone.ink,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, tone.ink.copy(alpha = if (dark) 0.14f else 0.08f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 10.dp, bottom = 15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (hideHeadword) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = lerp(tone.surface, tone.accent, 0.14f),
                            border = BorderStroke(1.dp, tone.accent.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { revealed = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    HugeIcons.View,
                                    contentDescription = "显示单词",
                                    tint = tone.ink.copy(alpha = 0.7f),
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    "点击显示单词",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = tone.ink.copy(alpha = 0.78f),
                                )
                            }
                        }
                    } else {
                        WordHeadwordRow(word = word, tone = tone, large = false)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    word.label?.takeIf(String::isNotBlank)?.let { label ->
                        WordPriorityPill(label = label, tone = tone)
                    }
                    IconButton(onClick = onExpand, modifier = Modifier.size(36.dp)) {
                        Icon(
                            HugeIcons.Fullscreen,
                            contentDescription = "查看 ${word.word} 的详细释义和例句",
                            tint = tone.ink.copy(alpha = 0.8f),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }

            if (hideMeanings) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = lerp(tone.surface, tone.accent, 0.14f),
                    border = BorderStroke(1.dp, tone.accent.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { revealed = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            HugeIcons.View,
                            contentDescription = "显示释义",
                            tint = tone.ink.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            "点击显示释义",
                            style = MaterialTheme.typography.labelLarge,
                            color = tone.ink.copy(alpha = 0.78f),
                        )
                    }
                }
            } else if (word.meanings.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    word.meanings.forEach { meaning ->
                        WordMeaningRow(meaning = meaning, tone = tone, showGrammar = true)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordHeadwordRow(
    word: WordCard,
    tone: WordPalette,
    large: Boolean,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = word.word,
            fontFamily = GoogleSansFlex.Title.Emphasized.Large,
            fontSize = if (large) 26.sp else 21.sp,
            lineHeight = if (large) 31.sp else 26.sp,
            fontWeight = FontWeight.Bold,
            color = tone.ink,
        )
        word.phonetic?.takeIf(String::isNotBlank)?.let { phonetic ->
            Text(
                text = phonetic,
                fontFamily = FontFamily.Serif,
                fontSize = if (large) 15.sp else 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = tone.ink.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun WordPriorityPill(label: String, tone: WordPalette) {
    val dark = LocalDarkMode.current
    val bg = lerp(tone.surface, tone.accent, if (dark) 0.28f else 0.18f)
    val fg = lerp(tone.accent, tone.ink, if (dark) 0.28f else 0.42f)
    Surface(
        shape = CircleShape,
        color = bg,
        contentColor = fg,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WordMeaningRow(
    meaning: WordMeaning,
    tone: WordPalette,
    showGrammar: Boolean,
) {
    val dark = LocalDarkMode.current
    val badge = meaning.pos?.takeIf(String::isNotBlank) ?: "短语".takeIf { meaning.phrase }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (!badge.isNullOrBlank()) {
            val posBg = lerp(tone.surface, tone.accent, if (meaning.key) (if (dark) 0.30f else 0.22f) else (if (dark) 0.22f else 0.14f))
            val posFg = lerp(tone.accent, tone.ink, if (dark) 0.30f else 0.48f)
            Surface(
                shape = CircleShape,
                color = posBg,
                contentColor = posFg,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .defaultMinSize(minWidth = 42.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(
                        text = badge,
                        fontFamily = if (meaning.pos != null) FontFamily.Serif else FontFamily.Default,
                        fontStyle = if (meaning.pos != null) FontStyle.Italic else FontStyle.Normal,
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (showGrammar) {
                meaning.grammar?.takeIf(String::isNotBlank)?.let { grammar ->
                    Text(
                        text = grammar,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = lerp(tone.accent, tone.ink, 0.45f),
                    )
                }
            }
            val underlineColor = tone.accent.copy(alpha = if (dark) 0.52f else 0.42f)
            val keyBgColor = lerp(tone.surface, tone.accent, if (dark) 0.24f else 0.16f)
            val phraseMarkColor = tone.accent.copy(alpha = if (dark) 0.28f else 0.18f)
            Text(
                text = meaning.text,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                fontWeight = if (meaning.key || meaning.phrase) FontWeight.SemiBold else FontWeight.Normal,
                color = if (meaning.key) tone.ink else tone.ink.copy(alpha = 0.90f),
                modifier = Modifier.drawBehind {
                    if (meaning.key) {
                        drawRoundRect(
                            color = keyBgColor,
                            topLeft = Offset(-3.dp.toPx(), 1.dp.toPx()),
                            size = Size(size.width + 6.dp.toPx(), size.height - 2.dp.toPx()),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                        )
                        val y = size.height - 1.5.dp.toPx()
                        val path = Path().apply {
                            moveTo(0f, y)
                            val waveWidth = 8.dp.toPx()
                            var x = 0f
                            var up = true
                            while (x < size.width) {
                                val nextX = (x + waveWidth).coerceAtMost(size.width)
                                val midX = (x + nextX) / 2f
                                val cy = if (up) y - 1.6.dp.toPx() else y + 1.6.dp.toPx()
                                quadraticTo(midX, cy, nextX, y)
                                x = nextX
                                up = !up
                            }
                        }
                        drawPath(
                            path = path,
                            color = underlineColor,
                            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
                        )
                    } else if (meaning.phrase) {
                        drawRoundRect(
                            color = phraseMarkColor,
                            topLeft = Offset(0f, size.height * 0.62f),
                            size = Size(size.width, size.height * 0.34f),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun WordDetails(
    word: WordCard,
    tone: WordPalette,
    position: Int,
    totalCount: Int,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(word) { scrollState.scrollTo(0) }
    val dark = LocalDarkMode.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top navigation toolbar (matches StudyCardHeader)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (onPrevious != null || onNext != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            IconButton(
                                onClick = { onPrevious?.invoke() },
                                enabled = onPrevious != null,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(HugeIcons.ArrowLeft01, "上一个单词", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                "$position / $totalCount",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            IconButton(
                                onClick = { onNext?.invoke() },
                                enabled = onNext != null,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(HugeIcons.ArrowRight01, "下一个单词", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                word.label?.takeIf(String::isNotBlank)?.let { label ->
                    WordPriorityPill(label = label, tone = tone)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(HugeIcons.Cancel01, "关闭词卡", modifier = Modifier.size(18.dp))
                }
            }

            // Hero card with headword, phonetic, and complete meanings
            Surface(
                color = tone.surface,
                contentColor = tone.ink,
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, tone.ink.copy(alpha = if (dark) 0.14f else 0.08f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WordHeadwordRow(word = word, tone = tone, large = true, modifier = Modifier.fillMaxWidth())
                    if (word.meanings.isNotEmpty()) {
                        HorizontalDivider(color = tone.ink.copy(alpha = 0.10f))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            word.meanings.forEach { meaning ->
                                WordMeaningRow(meaning = meaning, tone = tone, showGrammar = true)
                            }
                        }
                    }
                }
            }

            // Senses & interactive examples
            if (word.senses.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("真题语境", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "点击例句查看译文",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val total = word.senses.sumOf { it.count.coerceAtLeast(0) }
                word.senses.forEachIndexed { index, sense ->
                    WordSenseDetails(
                        sense = sense,
                        order = index,
                        total = total,
                        tone = tone,
                    )
                }
            }
        }
    }
}

@Composable
private fun WordSenseDetails(
    sense: WordSense,
    order: Int,
    total: Int,
    tone: WordPalette,
) {
    val scheme = MaterialTheme.colorScheme
    val share = (sense.percentage ?: if (total > 0) sense.count * 100 / total else 0).coerceIn(0, 100)
    val orderLabel = (order + 1).toString().padStart(2, '0')
    val senseAccent = lerp(tone.accent, tone.surface, (order * 0.16f).coerceAtMost(0.45f))

    Surface(
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Sense header bar with proportional fill behind it
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.onSurface.copy(alpha = 0.035f)),
            ) {
                if (total > 0 && share > 0) {
                    Box(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(share / 100f)
                                .fillMaxHeight()
                                .background(senseAccent.copy(alpha = 0.16f)),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(senseAccent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = orderLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = sense.gloss,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (total > 0) {
                        Text(
                            text = "${sense.count} 次 · $share%",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (sense.examples.isEmpty()) {
                Text(
                    "暂无例句",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sense.examples.forEach { example ->
                        SenseExampleItem(example = example, accent = tone.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun SenseExampleItem(
    example: WordExample,
    accent: Color,
) {
    val scheme = MaterialTheme.colorScheme
    var showTranslation by rememberSaveable(example.text) { mutableStateOf(false) }
    val hasTranslation = example.translation.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (hasTranslation) Modifier.clickable { showTranslation = !showTranslation }
                else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 9.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.6f)),
            )
            Text(
                text = example.text,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = scheme.onSurface,
                textDecoration = if (hasTranslation && !showTranslation) TextDecoration.Underline else TextDecoration.None,
                modifier = Modifier.weight(1f),
            )
        }
        AnimatedVisibility(
            visible = showTranslation && hasTranslation,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 15.dp),
            ) {
                Text(
                    text = example.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
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
