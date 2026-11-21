package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Eye
import me.rerere.hugeicons.stroke.Fullscreen
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Translate
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment
import me.rerere.rikkahub.ui.theme.GoogleSansFlex
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.sin

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
    var mode by rememberSaveable { mutableStateOf(WordStudyMode.Default) }
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
            Text(
                "${filtered.size} 个词条",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            WordStudyModeTabs(
                selected = mode,
                onSelect = { mode = it },
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
                    mode = mode,
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
private fun WordStudyModeTabs(
    selected: WordStudyMode,
    onSelect: (WordStudyMode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val entries = WordStudyMode.entries
    val selectedIndex = entries.indexOf(selected).coerceAtLeast(0)
    Surface(
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .width(192.dp)
                .height(34.dp)
                .padding(3.dp),
        ) {
            val tabWidth = maxWidth / entries.size
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                label = "wordModeIndicator",
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(scheme.surface),
            )
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                entries.forEach { mode ->
                    val active = mode == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(mode) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
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
    var wordRevealed by rememberSaveable(mode, word.word) { mutableStateOf(false) }
    var meaningRevealed by rememberSaveable(mode, word.word) { mutableStateOf(false) }
    val hideHeadword = mode == WordStudyMode.Dictation && !wordRevealed
    val hideMeanings = mode == WordStudyMode.Translation && !meaningRevealed

    Surface(
        color = tone.surface,
        contentColor = tone.ink,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, tone.ink.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (hideHeadword) {
                        MaskedRevealChip(
                            label = "点击显示单词",
                            tone = tone,
                            onClick = { wordRevealed = true },
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalArrangement = Arrangement.Center,
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = word.word,
                                fontFamily = GoogleSansFlex.Title.Emphasized.Large,
                                fontSize = 21.sp,
                                lineHeight = 25.sp,
                                fontWeight = FontWeight.Bold,
                                color = tone.ink,
                            )
                            word.phonetic?.takeIf(String::isNotBlank)?.let { phonetic ->
                                Text(
                                    text = phonetic,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 13.5.sp,
                                    lineHeight = 18.sp,
                                    color = lerp(tone.ink, tone.surface, 0.36f),
                                )
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    word.label?.takeIf(String::isNotBlank)?.let { label ->
                        WordImportanceBadge(label = label, tone = tone)
                    }
                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Fullscreen,
                            contentDescription = "查看 ${word.word} 的详细释义和例句",
                            tint = tone.ink.copy(alpha = 0.78f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (hideMeanings) {
                Box(modifier = Modifier.padding(top = 10.dp)) {
                    MaskedRevealChip(
                        label = "点击显示释义",
                        tone = tone,
                        onClick = { meaningRevealed = true },
                    )
                }
            } else if (word.meanings.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 10.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    word.meanings.forEach { WordMeaningRow(it, tone, showGrammar = true) }
                }
            }
        }
    }
}

@Composable
private fun MaskedRevealChip(
    label: String,
    tone: WordPalette,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = lerp(tone.surface, tone.accent, 0.14f),
        contentColor = lerp(tone.accent, tone.ink, 0.68f),
        border = BorderStroke(1.dp, tone.accent.copy(alpha = 0.25f)),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(HugeIcons.Eye, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WordImportanceBadge(label: String, tone: WordPalette) {
    Surface(
        shape = CircleShape,
        color = lerp(tone.surface, tone.accent, 0.16f),
        contentColor = lerp(tone.accent, tone.ink, 0.74f),
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WordMeaningRow(meaning: WordMeaning, tone: WordPalette, showGrammar: Boolean) {
    val meaningInk = lerp(tone.ink, tone.surface, 0.1f)
    val keyInk = lerp(tone.accent, tone.ink, 0.56f)
    val phraseInk = lerp(tone.accent, tone.ink, 0.54f)
    val posBg = lerp(tone.surface, tone.accent, if (meaning.key) 0.20f else 0.13f)
    val posInk = lerp(tone.accent, tone.ink, 0.62f)
    val keyUnderlineColor = tone.accent.copy(alpha = 0.62f)
    val phraseHighlightColor = tone.accent.copy(alpha = 0.20f)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        val badge = meaning.pos?.takeIf(String::isNotBlank) ?: "短语".takeIf { meaning.phrase }
        if (!badge.isNullOrBlank()) {
            Surface(
                shape = CircleShape,
                color = posBg,
                contentColor = posInk,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .defaultMinSize(minWidth = 42.dp),
            ) {
                Text(
                    text = badge,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
        val textColor = when {
            meaning.key -> keyInk
            meaning.phrase -> phraseInk
            else -> meaningInk
        }
        val annotated = buildAnnotatedString {
            if (showGrammar && !meaning.grammar.isNullOrBlank()) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        color = phraseInk,
                    ),
                ) {
                    append(meaning.grammar)
                    append(" ")
                }
            }
            withStyle(
                SpanStyle(
                    fontWeight = if (meaning.key || meaning.phrase) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor,
                ),
            ) {
                append(meaning.text)
            }
        }
        Text(
            text = annotated,
            fontSize = 14.5.sp,
            lineHeight = 22.sp,
            onTextLayout = { textLayout = it },
            modifier = Modifier
                .weight(1f)
                .drawBehind {
                    val layout = textLayout ?: return@drawBehind
                    if (meaning.phrase) {
                        for (line in 0 until layout.lineCount) {
                            val left = layout.getLineLeft(line)
                            val right = layout.getLineRight(line)
                            val top = layout.getLineTop(line)
                            val bottom = layout.getLineBottom(line)
                            val bandTop = top + (bottom - top) * 0.58f
                            drawRoundRect(
                                color = phraseHighlightColor,
                                topLeft = Offset(left - 2.dp.toPx(), bandTop),
                                size = Size((right - left) + 4.dp.toPx(), bottom - bandTop),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            )
                        }
                    }
                    if (meaning.key) {
                        val strokePx = 1.8.dp.toPx()
                        val ampPx = 1.3.dp.toPx()
                        val waveLenPx = 9.dp.toPx()
                        for (line in 0 until layout.lineCount) {
                            val left = layout.getLineLeft(line)
                            val right = layout.getLineRight(line)
                            if (right <= left) continue
                            val baseY = layout.getLineBottom(line) - 1.5.dp.toPx()
                            val path = Path().apply {
                                moveTo(left, baseY)
                                var x = left
                                val step = 2.dp.toPx()
                                while (x < right) {
                                    val nextX = (x + step).coerceAtMost(right)
                                    val phase = ((nextX - left) / waveLenPx) * (2f * Math.PI.toFloat())
                                    lineTo(nextX, baseY + sin(phase) * ampPx)
                                    x = nextX
                                }
                            }
                            drawPath(
                                path = path,
                                color = keyUnderlineColor,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                            )
                        }
                    }
                },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                            text = "$position / $totalCount",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    word.label?.takeIf(String::isNotBlank)?.let { label ->
                        WordImportanceBadge(label = label, tone = tone)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(HugeIcons.Cancel01, "关闭词卡", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Surface(
                color = tone.surface,
                contentColor = tone.ink,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, tone.ink.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.Center,
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = word.word,
                            fontFamily = GoogleSansFlex.Headline.Emphasized.Small,
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = tone.ink,
                        )
                        word.phonetic?.takeIf(String::isNotBlank)?.let { phonetic ->
                            Text(
                                text = phonetic,
                                fontFamily = FontFamily.Serif,
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                color = lerp(tone.ink, tone.surface, 0.36f),
                            )
                        }
                    }
                    if (word.meanings.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            word.meanings.forEach { WordMeaningRow(it, tone, showGrammar = true) }
                        }
                    }
                }
            }

            if (word.senses.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("真题语境与例句", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "点击例句查看译文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    Surface(
        color = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(sense.gloss, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (total > 0) {
                    Text("${sense.count} 次 · $share%", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { share / 100f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = tone.accent,
                    trackColor = scheme.surfaceContainerHighest,
                )
            }
            if (sense.examples.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sense.examples.forEach { example ->
                        WordExampleItem(example = example, tone = tone)
                    }
                }
            } else {
                Text("暂无例句", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WordExampleItem(example: WordExample, tone: WordPalette) {
    val scheme = MaterialTheme.colorScheme
    var showTranslation by rememberSaveable(example.text) { mutableStateOf(false) }
    val hasTranslation = example.translation.isNotBlank()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (showTranslation) scheme.surfaceContainer else scheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (showTranslation) tone.accent.copy(alpha = 0.45f) else scheme.outlineVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = hasTranslation) { showTranslation = !showTranslation },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = example.text,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (hasTranslation) {
                    Surface(
                        shape = CircleShape,
                        color = if (showTranslation) tone.accent.copy(alpha = 0.18f) else scheme.surfaceContainerHigh,
                        contentColor = if (showTranslation) tone.ink else scheme.onSurfaceVariant,
                    ) {
                        Icon(
                            imageVector = HugeIcons.Translate,
                            contentDescription = if (showTranslation) "隐藏译文" else "查看译文",
                            modifier = Modifier.padding(5.dp).size(13.dp),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showTranslation && hasTranslation,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))
                    Text(
                        text = example.translation,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = scheme.onSurfaceVariant,
                    )
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
