package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment

@Composable
internal fun ExamDocsTabsBlock(
    block: ExamDocsSegment.CustomBlock,
    renderSegment: @Composable (ExamDocsSegment) -> Unit,
) {
    val tabs = remember(block.blocks) {
        block.blocks.filterIsInstance<ExamDocsSegment.CustomBlock>().filter { it.type == "tab" }
    }
    if (tabs.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            block.blocks.forEach { renderSegment(it) }
        }
        return
    }

    var selectedIndex by rememberSaveable(block.title, tabs.size) { mutableIntStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, tabs.lastIndex)
    val activeTab = tabs[safeIndex]
    val isSingleCodeBlock = activeTab.blocks.size == 1 &&
        ((activeTab.blocks.firstOrNull() as? ExamDocsSegment.Markdown)?.document?.blocks?.singleOrNull() is MarkdownBlock.CodeBlock)

    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerLow.copy(alpha = 0.85f)),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(tabs) { index, tab ->
                val selected = index == safeIndex
                Surface(
                    onClick = { selectedIndex = index },
                    shape = CircleShape,
                    color = if (selected) colors.primaryContainer else Color.Transparent,
                ) {
                    Text(
                        text = tab.title ?: "Tab ${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        HorizontalDivider(
            color = colors.outlineVariant.copy(alpha = 0.25f),
            thickness = 0.5.dp,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isSingleCodeBlock) 6.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            activeTab.blocks.forEach { segment ->
                renderSegment(segment)
            }
        }
    }
}
