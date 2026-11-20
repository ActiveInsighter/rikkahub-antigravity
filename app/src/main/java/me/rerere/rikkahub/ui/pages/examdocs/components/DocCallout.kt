package me.rerere.rikkahub.ui.pages.examdocs.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Idea
import me.rerere.rikkahub.data.examdocs.ExamDocsSegment

private data class CalloutStyle(
    val accent: Color,
    val container: Color,
    val icon: ImageVector,
    val defaultTitle: String,
)

@Composable
internal fun DocCallout(
    block: ExamDocsSegment.CustomBlock,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val style = when (block.type) {
        "tip" -> CalloutStyle(
            accent = colorScheme.secondary,
            container = colorScheme.secondaryContainer.copy(alpha = 0.55f),
            icon = HugeIcons.Idea,
            defaultTitle = "提示",
        )
        "warning" -> CalloutStyle(
            accent = colorScheme.tertiary,
            container = colorScheme.tertiaryContainer.copy(alpha = 0.55f),
            icon = HugeIcons.Alert02,
            defaultTitle = "注意",
        )
        "danger" -> CalloutStyle(
            accent = colorScheme.error,
            container = colorScheme.errorContainer.copy(alpha = 0.55f),
            icon = HugeIcons.Alert01,
            defaultTitle = "警告",
        )
        "note" -> CalloutStyle(
            accent = colorScheme.onSurfaceVariant,
            container = colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
            icon = HugeIcons.Book02,
            defaultTitle = "笔记",
        )
        else -> CalloutStyle(
            accent = colorScheme.primary,
            container = colorScheme.primaryContainer.copy(alpha = 0.45f),
            icon = HugeIcons.AlertCircle,
            defaultTitle = "说明",
        )
    }

    Surface(
        color = style.container,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(style.accent.copy(alpha = 0.7f)),
            )
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.accent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = block.title ?: style.defaultTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                content()
            }
        }
    }
}
