package me.rerere.rikkahub.ui.pages.examdocs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.SelectableContent
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.LocalChatFontFamily
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily

/**
 * 可扩展的原生文档块模型。
 *
 * 默认所有内容走 RikkaHub AI 消息同款的 [MarkdownBlock] 渲染；
 * 后续可通过扩展 [ExamDocBlock] 子类与 [ExamDocComponentRegistry] 来插入原生自定义组件
 * （例如题卡、解析折叠块、Callout、Tabs、交互式选项等）。
 */
sealed interface ExamDocBlock {
    data class Markdown(val content: String) : ExamDocBlock

    data class Custom(
        val type: String,
        val payload: String,
        val metadata: Map<String, String> = emptyMap(),
    ) : ExamDocBlock
}

/**
 * 自定义组件注册表，便于后续按块类型扩展原生 Compose 组件。
 */
data class ExamDocComponentRegistry(
    val customRenderers: Map<String, @Composable (ExamDocBlock.Custom) -> Unit> = emptyMap(),
    val blockParser: (String) -> List<ExamDocBlock> = ::defaultExamDocBlockParser,
)

private val FENCED_CUSTOM_BLOCK_REGEX = Regex(
    pattern = """^:::([a-zA-Z0-9_-]+)(?:\s+([^\n]*))?\n([\s\S]*?)^:::\s*$""",
    options = setOf(RegexOption.MULTILINE),
)

/**
 * 默认解析器：识别 `:::type ... :::` 自定义容器语法，其余正文合并为 [ExamDocBlock.Markdown]。
 * 未匹配到自定义容器时整篇直接作为一个 Markdown 块，保证最佳的跨段落选择与排版连续性。
 */
fun defaultExamDocBlockParser(markdown: String): List<ExamDocBlock> {
    if (markdown.isBlank()) return emptyList()
    val matches = FENCED_CUSTOM_BLOCK_REGEX.findAll(markdown).toList()
    if (matches.isEmpty()) {
        return listOf(ExamDocBlock.Markdown(markdown))
    }

    val blocks = mutableListOf<ExamDocBlock>()
    var cursor = 0
    for (match in matches) {
        if (match.range.first > cursor) {
            val preceding = markdown.substring(cursor, match.range.first).trim()
            if (preceding.isNotEmpty()) {
                blocks += ExamDocBlock.Markdown(preceding)
            }
        }
        val type = match.groupValues[1].lowercase()
        val title = match.groupValues[2].trim()
        val body = match.groupValues[3].trim()
        blocks += ExamDocBlock.Custom(
            type = type,
            payload = body,
            metadata = if (title.isNotEmpty()) mapOf("title" to title) else emptyMap(),
        )
        cursor = match.range.last + 1
    }
    if (cursor < markdown.length) {
        val trailing = markdown.substring(cursor).trim()
        if (trailing.isNotEmpty()) {
            blocks += ExamDocBlock.Markdown(trailing)
        }
    }
    return blocks
}

/**
 * 复用 RikkaHub AI 消息的 Markdown 渲染能力（BetterText + LaTeX + 代码高亮 + 表格 + Mermaid + 长按选词），
 * 并结合 [ExamDocComponentRegistry] 支持自定义原生组件插槽。
 */
@Composable
fun ExamDocsMarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    registry: ExamDocComponentRegistry = remember { DefaultExamDocComponentRegistry },
    onClickCitation: (String) -> Unit = {},
) {
    val displaySetting = LocalSettings.current.displaySetting
    val chatFontFamily = LocalChatFontFamily.current ?: rememberChatFontFamily(displaySetting)
    val baseStyle = LocalTextStyle.current
    val textStyle = remember(baseStyle, displaySetting.fontSizeRatio, chatFontFamily) {
        baseStyle.copy(
            fontSize = baseStyle.fontSize * displaySetting.fontSizeRatio,
            lineHeight = baseStyle.lineHeight * displaySetting.fontSizeRatio,
            fontFamily = chatFontFamily,
        )
    }

    val blocks = remember(markdown, registry) {
        registry.blockParser(markdown)
    }

    CompositionLocalProvider(LocalTextStyle provides textStyle) {
        SelectableContent {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                blocks.forEach { block ->
                    when (block) {
                        is ExamDocBlock.Markdown -> {
                            MarkdownBlock(
                                content = block.content,
                                streaming = false,
                                onClickCitation = onClickCitation,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        is ExamDocBlock.Custom -> {
                            val renderer = registry.customRenderers[block.type]
                            if (renderer != null) {
                                renderer(block)
                            } else {
                                DefaultExamCalloutCard(block = block)
                            }
                        }
                    }
                }
            }
        }
    }
}

val DefaultExamDocComponentRegistry = ExamDocComponentRegistry(
    customRenderers = mapOf(
        "note" to { DefaultExamCalloutCard(it) },
        "tip" to { DefaultExamCalloutCard(it) },
        "warning" to { DefaultExamCalloutCard(it) },
        "info" to { DefaultExamCalloutCard(it) },
        "callout" to { DefaultExamCalloutCard(it) },
    ),
)

@Composable
private fun DefaultExamCalloutCard(
    block: ExamDocBlock.Custom,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (block.type) {
        "warning" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        "tip" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    }
    val title = block.metadata["title"]?.takeIf { it.isNotBlank() } ?: block.type.uppercase()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (block.payload.isNotBlank()) {
                MarkdownBlock(
                    content = block.payload,
                    streaming = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
