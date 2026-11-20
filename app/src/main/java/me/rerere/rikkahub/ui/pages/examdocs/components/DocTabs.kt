package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Code
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment

@Composable
internal fun DocTabs(
    block: ExamDocsSegment.CustomBlock,
    renderChildren: @Composable (List<ExamDocsSegment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = block.blocks.filterIsInstance<ExamDocsSegment.CustomBlock>().filter { it.type == "tab" }
    if (tabs.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            renderChildren(block.blocks)
        }
        return
    }

    val isCodeTabs = block.type == "codetabs"
    var selectedIndex by rememberSaveable(tabs.size, block.type) { mutableIntStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, tabs.lastIndex)
    val shape = RoundedCornerShape(16.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isCodeTabs) {
                    Icon(
                        imageVector = HugeIcons.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 4.dp, end = 2.dp)
                            .size(15.dp),
                    )
                }
                tabs.forEachIndexed { index, tab ->
                    val selected = index == safeIndex
                    val pillColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0f)
                    }
                    val textColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(pillColor)
                            .clickable { selectedIndex = index }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tab.title ?: "Tab ${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = textColor,
                            maxLines = 1,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(borderColor.copy(alpha = 0.6f)),
            )
            Crossfade(
                targetState = safeIndex,
                label = "DocTabsContent",
            ) { index ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    renderChildren(tabs[index].blocks)
                }
            }
        }
    }
}
