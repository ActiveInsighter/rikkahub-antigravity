package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Link01
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment

@Composable
internal fun ExamDocsCardsBlock(
    block: ExamDocsSegment.CustomBlock,
    renderSegment: @Composable (ExamDocsSegment) -> Unit,
    onLinkClick: (String) -> Unit,
) {
    val cards = remember(block.blocks) {
        block.blocks.filterIsInstance<ExamDocsSegment.CustomBlock>().filter { it.type == "card" }
    }
    if (cards.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            block.blocks.forEach { renderSegment(it) }
        }
        return
    }

    val useTwoColumns = cards.size > 1 && cards.all { it.blocks.isEmpty() }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (useTwoColumns) {
            cards.chunked(2).forEach { rowCards ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowCards.forEach { card ->
                        ExamDocsCardItem(
                            block = card,
                            renderSegment = renderSegment,
                            onLinkClick = onLinkClick,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    if (rowCards.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            cards.forEach { card ->
                ExamDocsCardItem(
                    block = card,
                    renderSegment = renderSegment,
                    onLinkClick = onLinkClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun ExamDocsCardItem(
    block: ExamDocsSegment.CustomBlock,
    renderSegment: @Composable (ExamDocsSegment) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meta = remember(block.title) { parseComponentMeta(block.title) }
    val title = meta.displayTitle
    val description = meta.string("description")
    val href = meta.string("href")
    val tone = meta.string("tone")
    val palette = resolveTonePalette(tone)
    val colors = MaterialTheme.colorScheme

    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (title != null || href != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (tone != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.accent)
                        )
                    }
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (href != null) {
                        Icon(
                            imageVector = if (href.startsWith("http")) HugeIcons.Link01 else HugeIcons.ArrowRight01,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (block.blocks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.blocks.forEach { renderSegment(it) }
                }
            }
        }
    }

    val shape = RoundedCornerShape(16.dp)
    val containerColor = if (tone != null) palette.container else colors.surfaceContainerLow
    val border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.3f))

    if (href != null) {
        Surface(
            onClick = { onLinkClick(href) },
            shape = shape,
            color = containerColor,
            border = border,
            modifier = modifier,
            content = cardContent,
        )
    } else {
        Surface(
            shape = shape,
            color = containerColor,
            border = border,
            modifier = modifier,
            content = cardContent,
        )
    }
}
