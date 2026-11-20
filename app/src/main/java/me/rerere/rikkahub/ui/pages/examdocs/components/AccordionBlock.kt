package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Idea
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment

@Composable
internal fun ExamDocsAccordionBlock(
    block: ExamDocsSegment.CustomBlock,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(block.type, block.title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "accordionChevron")
    val isSolution = block.type == "solution"
    val defaultTitle = if (isSolution) "参考答案与解析" else "展开详情"
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSolution) colors.secondaryContainer.copy(alpha = 0.35f) else colors.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSolution) colors.secondary.copy(alpha = 0.28f) else colors.outlineVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = if (isSolution) colors.secondary else colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).rotate(rotation),
                )
                if (isSolution) {
                    Icon(
                        imageVector = HugeIcons.Idea,
                        contentDescription = null,
                        tint = colors.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = block.title ?: defaultTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSolution) colors.onSecondaryContainer else colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = colors.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp,
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
